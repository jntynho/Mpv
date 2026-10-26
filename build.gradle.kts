// Top-level build file where you can add configuration options common to all sub-projects/modules.
// Modified for Luma: build pinned supporting libraries from source.
plugins {
  id("com.android.library") version "8.13.0" apply false
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.jetbrains.kotlin.android) apply false
}
