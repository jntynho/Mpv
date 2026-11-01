plugins {
  id("com.android.library")
  alias(libs.plugins.jetbrains.kotlin.android)
}

group = "io.github.jntynho"
version = "0.1.12"
android {
  namespace = "io.github.jntynho.mpv"
  compileSdk = 36
  defaultConfig { minSdk = 21 }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
}
kotlin { jvmToolchain(17) }
dependencies {
  api("io.github.abdallahmehiz:mpv-android-lib:0.1.12")
  testImplementation("junit:junit:4.13.2")
}
