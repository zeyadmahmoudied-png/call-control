rootProject.name = "CallControl"

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

// Phase 1: pure-Kotlin domain module (no Android dependencies, fully unit-testable on the JVM).
include(":domain")

// Phase 2: storage.
//  :storage-core    pure Kotlin/JVM  - storage ports, CallControlRepository, ScreeningProcessor (unit-tested on the JVM)
//  :storage-android Android library  - Room + DataStore implementations of those ports
include(":storage-core")
include(":storage-android")

// Phase 3+: the installable application (UI + call screening service).
include(":app")
