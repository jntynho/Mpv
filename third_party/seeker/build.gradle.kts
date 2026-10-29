plugins {
  id("com.android.library")
  alias(libs.plugins.jetbrains.kotlin.android)
  alias(libs.plugins.kotlin.compose.compiler)
}
android {
  namespace = "dev.vivvvek.seeker"
  compileSdk = 36
  defaultConfig { minSdk = 21 }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  buildFeatures { compose = true }
}
kotlin { jvmToolchain(17) }
dependencies {
  testImplementation("junit:junit:4.13.2")
  implementation(platform(libs.androidx.compose.bom))
  implementation("androidx.compose.material:material")
  implementation(libs.androidx.ui)
  implementation("androidx.annotation:annotation:1.9.1")
}
