import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Release signing comes from environment variables only (set by the release workflow); nothing secret is in the repo.
val keystorePath: String? = System.getenv("CC_KEYSTORE_PATH")

android {
    namespace = "com.callcontrol.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.callcontrol.app"
        minSdk = 29 // CallScreeningService silencing + ROLE_CALL_SCREENING need Android 10
        targetSdk = 35
        versionCode = System.getenv("CC_VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = System.getenv("CC_VERSION_NAME") ?: "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("CC_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("CC_KEY_ALIAS")
                keyPassword = System.getenv("CC_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // Not minified on purpose: a smaller APK is not worth the risk of a shrinker breaking Room/DataStore
            // reflection-free code paths that the release pipeline cannot exercise end to end.
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    testOptions {
        // Lets instrumented tests run `adb shell am instrument` style without orchestrator.
        animationsDisabled = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":storage-android"))

    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
}
