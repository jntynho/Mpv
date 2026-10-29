// Modified for Luma Player: interface, appearance, or build configuration.
import java.security.MessageDigest
import java.io.File
import java.util.Properties
import java.util.zip.ZipFile
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import com.android.build.api.variant.FilterConfiguration
import io.gitlab.arturbosch.detekt.Detekt
import org.apache.commons.io.output.ByteArrayOutputStream

plugins {
  alias(libs.plugins.ksp)
  alias(libs.plugins.android.application)
  alias(libs.plugins.jetbrains.kotlin.android)
  alias(libs.plugins.kotlin.compose.compiler)
  alias(libs.plugins.room)
  alias(libs.plugins.detekt)
  alias(libs.plugins.about.libraries)
  alias(libs.plugins.kotlinx.serialization)
}


// Resolve the original published library, then replace only its two ARM libmpv binaries.
// Classes, JNI wrapper, FFmpeg libraries, resources and manifest remain from 0.1.9.
val originalMpvRuntime = configurations.create("originalMpvRuntime") {
  isCanBeConsumed = false
  isCanBeResolved = true
  isTransitive = false
  attributes.attribute(org.gradle.api.attributes.Usage.USAGE_ATTRIBUTE, objects.named(org.gradle.api.attributes.Usage.JAVA_RUNTIME))
}
val patchedMpvAar = layout.buildDirectory.file("generated/mpv-runtime/mpv-android-lib-0.1.9-pause2.aar")
val nativePauseDirectory = rootProject.file("third_party/mpv-pause")

fun sha256Of(file: File): String {
  val digest = MessageDigest.getInstance("SHA-256")
  file.inputStream().use { input ->
    val bytes = ByteArray(1024 * 1024)
    while (true) {
      val count = input.read(bytes)
      if (count < 0) break
      digest.update(bytes, 0, count)
    }
  }
  return digest.digest().joinToString("") { "%02x".format(it) }
}

val prepareMpvRuntime = tasks.register("prepareMpvRuntime") {
  inputs.files(originalMpvRuntime)
  inputs.dir(nativePauseDirectory.resolve("native"))
  inputs.file(nativePauseDirectory.resolve("native-hashes.properties"))
  outputs.file(patchedMpvAar)
  doLast {
    val original = originalMpvRuntime.singleFile
    check(sha256Of(original) == "500e6b23fb331b0bf121d23218aa7332f58f042533ecb6685222e667933ed15b") {
      "Published mpv-android-lib 0.1.9 changed"
    }
    val hashes = Properties().apply {
      nativePauseDirectory.resolve("native-hashes.properties").inputStream().use(::load)
    }
    val replacements = listOf("arm64-v8a", "armeabi-v7a").associate { abi ->
      val binary = nativePauseDirectory.resolve("native/$abi/libmpv.so")
      check(sha256Of(binary) == hashes.getProperty(abi)) { "Native pause fix changed for $abi" }
      "jni/$abi/libmpv.so" to binary
    }
    val output = patchedMpvAar.get().asFile
    output.parentFile.mkdirs()
    var replaced = 0
    ZipFile(original).use { source ->
      ZipOutputStream(output.outputStream().buffered()).use { target ->
        target.setLevel(9)
        source.entries().asSequence().sortedBy { it.name }.forEach { entry ->
          if (entry.isDirectory) return@forEach
          if (entry.name.startsWith("jni/") &&
              !entry.name.startsWith("jni/arm64-v8a/") &&
              !entry.name.startsWith("jni/armeabi-v7a/")) return@forEach
          target.putNextEntry(ZipEntry(entry.name).apply { time = 315532800000L })
          val replacement = replacements[entry.name]
          if (replacement != null) {
            replacement.inputStream().use { it.copyTo(target) }
            replaced++
          } else {
            source.getInputStream(entry).use { it.copyTo(target) }
          }
          target.closeEntry()
        }
      }
    }
    check(replaced == 2) { "Both ARM pause fixes must replace their upstream binaries" }
  }
}

android {
  namespace = "live.mehiz.mpvkt"
  compileSdk = 36
  ndkVersion = "27.0.12077973"
  // Incomplete upstream translations intentionally fall back to English.
  lint { warning += "MissingTranslation" }

  defaultConfig {
    applicationId = "io.github.jntynho.luma"
    ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    minSdk = 21
    targetSdk = 36
    versionCode = 28
    versionName = "0.20.0"

    vectorDrawables {
      useSupportLibrary = true
    }

    buildConfigField("String", "GIT_SHA", "\"${getCommitSha()}\"")
    buildConfigField("int", "GIT_COUNT", getCommitCount())
  }
  splits {
    abi {
      isEnable = true
      reset()
      include("armeabi-v7a", "arm64-v8a")
      isUniversalApk = true
    }
  }

  buildTypes {
    named("release") {
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro",
      )
    }
    create("preview") {
      initWith(getByName("release"))
      matchingFallbacks += listOf("release")

      signingConfig = signingConfigs["debug"]
      applicationIdSuffix = ".preview"
      versionNameSuffix = "-${getCommitCount()}"
    }
    named("debug") {
      applicationIdSuffix = ".debug"
      versionNameSuffix = "-${getCommitCount()}"
    }
  }
  testOptions {
    unitTests.isIncludeAndroidResources = true
    unitTests.all { test ->
      // Limit Robolectric's SDK download/cache writes to this project's test workspace.
      val testHome = layout.buildDirectory.dir("test-home").get().asFile
      test.systemProperty("user.home", testHome.absolutePath)
      test.systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2")
      listOf("http.proxyHost", "http.proxyPort", "https.proxyHost", "https.proxyPort", "http.nonProxyHosts").forEach { key ->
        System.getProperty(key)?.let { test.systemProperty(key, it) }
      }
      test.doFirst { testHome.mkdirs() }
    }
  }
  compileOptions {
    isCoreLibraryDesugaringEnabled = true
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  buildFeatures {
    compose = true
    viewBinding = true
    buildConfig = true
  }
  composeCompiler {
    includeSourceInformation = true
  }
  packaging {
    // Keep compressed native downloads; installers extract the native libraries.
    jniLibs.useLegacyPackaging = true
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
  }
  val abiCodes = mapOf(
    "armeabi-v7a" to 1,
    "arm64-v8a" to 2,
    "x86" to 3,
    "x86_64" to 4,
  )
  androidComponents {
    onVariants { variant ->
      variant.outputs.forEach { output ->
        val abi = output.filters.find { it.filterType == FilterConfiguration.FilterType.ABI }?.identifier
        output.versionCode.set((output.versionCode.orNull ?: 0) * 10 + (abiCodes[abi] ?: 0))
      }
    }
  }
  androidResources {
    generateLocaleConfig = true
  }
}

kotlin {
  compilerOptions {
    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    freeCompilerArgs.addAll("-Xwhen-guards", "-Xcontext-parameters")
  }
}

room {
  schemaDirectory("$projectDir/schemas")
}

dependencies {
  testImplementation("junit:junit:4.13.2")
  // Host-only rendering of the actual Compose menu; none of these enter a preview/release APK.
  testImplementation("org.robolectric:robolectric:4.16.1")
  testImplementation("androidx.compose.ui:ui-test-junit4")
  debugImplementation("androidx.compose.ui:ui-test-manifest")
  implementation(libs.androidx.activity.compose)
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.ui)
  implementation(libs.androidx.ui.graphics)
  implementation(libs.androidx.material3.android)
  implementation(libs.androidx.ui.tooling.preview)
  debugImplementation(libs.androidx.ui.tooling)
  implementation(libs.bundles.compose.navigation3)
  implementation(libs.androidx.appcompat)
  implementation(libs.androidx.compose.constraintlayout)
  implementation(libs.material)
  implementation(libs.androidx.preferences.ktx)
  implementation(libs.androidx.documentfile)
  implementation(libs.mediasession)
  implementation(libs.saveable)

  add(originalMpvRuntime.name, libs.mpv.lib)
  implementation(files(prepareMpvRuntime))

  implementation(platform(libs.koin.bom))
  implementation(libs.bundles.koin)

  implementation(project(":third_party:seeker"))
  implementation(libs.compose.prefs)
  implementation(libs.bundles.about.libs)

  implementation(libs.room.runtime)
  ksp(libs.room.compiler)
  implementation(libs.room.ktx)

  detektPlugins(libs.detekt.rules.compose)
  detektPlugins(libs.detekt.formatter)

  implementation(libs.kotlinx.immutable.collections)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.truetype.parser)
  implementation("org.jsoup:jsoup:1.18.3")
  coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
  implementation(project(":third_party:fsaf"))
}

detekt {
  parallel = true
  allRules = false
  buildUponDefaultConfig = true
  config.setFrom("$rootDir/config/detekt/detekt.yml")
}

tasks.withType<Detekt>().configureEach {
  setSource(files(project.projectDir))
  exclude("**/build/**")
  reports {
    html.required.set(true)
    md.required.set(true)
  }
}

fun getCommitCount(): String = runCommand("git rev-list --count HEAD").ifBlank { "0" }
fun getCommitSha(): String = runCommand("git rev-parse --short HEAD").ifBlank { "local" }
fun runCommand(command: String): String {
  val stdOut = ByteArrayOutputStream()
  exec {
    commandLine = command.split(' ')
    standardOutput = stdOut
    errorOutput = ByteArrayOutputStream()
    isIgnoreExitValue = true
  }
  return String(stdOut.toByteArray()).trim()
}

aboutLibraries {
  collect { configPath.set(rootProject.layout.projectDirectory.dir("config/aboutlibraries")) }
  excludeFields = arrayOf("generated")
}

// Validate the declared upstream runtime and the exact native artifact that enters the APK.
tasks.register("verifyMpvRuntime") {
  group = "verification"
  dependsOn(prepareMpvRuntime)
  doLast {
    val modules = configurations.getByName("debugRuntimeClasspath").incoming.resolutionResult
      .allComponents.mapNotNull { it.id as? ModuleComponentIdentifier }
    check(modules.none { it.group == "app.gyrolet.mpvlib" }) { "Removed gyrolet runtime is still present" }
    val original = originalMpvRuntime.incoming.resolutionResult.allComponents.mapNotNull {
      it.id as? ModuleComponentIdentifier
    }.single { it.group == "io.github.abdallahmehiz" && it.module == "mpv-android-lib" }
    check(original.version == "0.1.9")
    check(sha256Of(originalMpvRuntime.singleFile) == "500e6b23fb331b0bf121d23218aa7332f58f042533ecb6685222e667933ed15b")
    ZipFile(originalMpvRuntime.singleFile).use { upstream ->
      ZipFile(patchedMpvAar.get().asFile).use { patched ->
        upstream.entries().asSequence().filter { !it.isDirectory }.forEach { entry ->
          if (entry.name.startsWith("jni/") &&
              !entry.name.startsWith("jni/arm64-v8a/") &&
              !entry.name.startsWith("jni/armeabi-v7a/")) return@forEach
          if (entry.name in setOf("jni/arm64-v8a/libmpv.so", "jni/armeabi-v7a/libmpv.so")) return@forEach
          check(upstream.getInputStream(entry).use { it.readBytes() }.contentEquals(
            patched.getInputStream(patched.getEntry(entry.name)).use { it.readBytes() }
          )) { "Unexpected upstream runtime change: ${entry.name}" }
        }
      }
    }
    logger.lifecycle("Verified io.github.abdallahmehiz:mpv-android-lib:0.1.9 with ARM native pause fix; gyrolet absent")
  }
}
