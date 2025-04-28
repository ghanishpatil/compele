pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven { url = uri("https://androidx.dev/snapshots/builds/6543454/artifacts/repository") }
        maven { url = uri("https://jitpack.io") }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://androidx.dev/snapshots/builds/6543454/artifacts/repository") }
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "MyStartup"
include(":app")
 