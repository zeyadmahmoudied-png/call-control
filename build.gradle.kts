plugins {
    kotlin("jvm") version "2.0.21" apply false

    // Phase 2 (Android storage module). Versions are chosen to be mutually compatible
    // (Kotlin 2.0.21 <-> KSP 2.0.21-1.0.27, AGP 8.7.x needs Gradle 8.9+; the wrapper uses 8.14.3).
    // They could not be resolved or built in the delivery environment (no network): verify in Android Studio.
    id("com.android.library") version "8.7.3" apply false
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("com.google.devtools.ksp") version "2.0.21-1.0.27" apply false
}
