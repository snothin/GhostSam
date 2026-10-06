pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // libadb-android (from App Manager; published on JitPack only)
        maven("https://jitpack.io")
    }
}

rootProject.name = "GhostSam"
include(":app")
include(":companion")
