// Modified for Luma Player: project identity and dependency repositories.
pluginManagement {
  repositories {
    google {
      content {
        includeGroupByRegex("com\\.android.*")
        includeGroupByRegex("com\\.google.*")
        includeGroupByRegex("androidx.*")
      }
    }
    mavenCentral()
    gradlePluginPortal()
  }
}
dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
    exclusiveContent {
      forRepository {
        ivy {
          name = "mpvlibGitHubReleases"
          url = uri("https://github.com/Riteshp2001/mpvlibAndroid/releases/download")
          patternLayout { artifact("v[revision]/[artifact]-[revision].[ext]") }
          artifactPattern("https://raw.githubusercontent.com/jntynho/Mpv/f8d492f2425303162e049af1708c54568f4d6aa3/gradle/mpvlib-metadata/[artifact]-[revision].[ext]")
          metadataSources { artifact() }
        }
      }
      filter { includeGroup("app.gyrolet.mpvlib") }
    }

  }
}

rootProject.name = "LumaPlayer"
include(":app")

include(":third_party:seeker", ":third_party:fsaf")
