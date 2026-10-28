// Top-level build file where you can add configuration options common to all sub-projects/modules.
// Modified for Luma: build pinned supporting libraries from source.
buildscript {
  repositories { google() }
  dependencies {
    // The mpvlib artifact requires Kotlin 2.4; AGP 8.13's bundled R8 predates that metadata.
    classpath("com.android.tools:r8:9.5.28")
  }
}

plugins {
  id("com.android.library") version "8.13.0" apply false
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.jetbrains.kotlin.android) apply false
}
