// Modified for Luma Player: interface, appearance, or build configuration.
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
    minSdk = 21
    targetSdk = 36
    versionCode = 8
    versionName = "0.7.0"

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
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  kotlinOptions {
    jvmTarget = "17"
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
    freeCompilerArgs.addAll("-Xwhen-guards", "-Xcontext-parameters")
  }
}

room {
  schemaDirectory("$projectDir/schemas")
}

dependencies {
  testImplementation("junit:junit:4.13.2")
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

  implementation(libs.mpv.lib)

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
