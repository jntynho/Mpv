plugins {
  id("com.android.library")
  alias(libs.plugins.jetbrains.kotlin.android)
}
android {
  namespace = "com.github.k1rakishou.fsaf"
  compileSdk = 36
  defaultConfig { minSdk = 21 }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
}
kotlin { jvmToolchain(17) }
dependencies {
  implementation("androidx.annotation:annotation:1.9.1")
  implementation(libs.androidx.documentfile)
}
