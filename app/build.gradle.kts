// Modified for Luma Player: interface, appearance, or build configuration.
import java.security.MessageDigest
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.type.ArtifactTypeDefinition
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

android {
  namespace = "live.mehiz.mpvkt"
  compileSdk = 36
  ndkVersion = "27.0.12077973"
  // Incomplete upstream translations intentionally fall back to English.
  lint { warning += "MissingTranslation" }

  defaultConfig {
    applicationId = "io.github.jntynho.luma"
    ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    minSdk = 24
    targetSdk = 36
    versionCode = 20
    versionName = "0.16.0"

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
    // Retain compressed downloads when raising minSdk; installers extract the native libraries.
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

  implementation(variantOf(libs.mpv.lib) { artifactType("aar") })

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
  excludeFields = arrayOf("generated")
}

// The producer publishes an AAR without Maven metadata. Verify the exact native artifact
// and reject an accidental reintroduction of the previous runtime before release validation.
tasks.register("verifyMpvRuntime") {
  group = "verification"
  doLast {
    val configuration = configurations.getByName("debugRuntimeClasspath")
    val modules = configuration.incoming.resolutionResult.allComponents.mapNotNull { it.id as? ModuleComponentIdentifier }
    check(modules.none { it.group == "io.github.abdallahmehiz" && it.module == "mpv-android-lib" }) {
      "The replaced mpv runtime is still present"
    }
    val runtime = modules.single { it.group == "app.gyrolet.mpvlib" && it.module == "mpvlib" }
    check(runtime.version == "1.1.4")
    val artifact = configuration.incoming.artifactView {
      componentFilter { it is ModuleComponentIdentifier && it.group == "app.gyrolet.mpvlib" && it.module == "mpvlib" }
      attributes.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, "aar")
    }.files.singleFile
    val digest = MessageDigest.getInstance("SHA-256")
    artifact.inputStream().use { input ->
      val buffer = ByteArray(1024 * 1024)
      while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        digest.update(buffer, 0, count)
      }
    }
    val actual = digest.digest().joinToString("") { "%02x".format(it) }
    check(actual == "4abc71bfc1a0e0c8d5008e8fb3a8ccf83f3ced6e430a04b51156f75ae53bb285") {
      "mpvlib 1.1.4 artifact changed: $actual"
    }
    logger.lifecycle("Verified app.gyrolet.mpvlib:mpvlib:1.1.4; previous runtime absent")
  }
}
