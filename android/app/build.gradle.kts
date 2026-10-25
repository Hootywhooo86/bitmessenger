plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("io.github.takahirom.roborazzi")
}

android {
    namespace = "app.bitmessenger"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.bitmessenger"
        minSdk = 30 // same as bitcall: Android 11+
        targetSdk = 35
        versionCode = (System.getenv("BITMESSENGER_VERSION_CODE") ?: "1").toInt()
        versionName = System.getenv("BITMESSENGER_VERSION_NAME") ?: "0.1.0-pre"
    }

    // Signed with the 128bit family key from CI secrets (the same key as bitcall), so Bitter can
    // borrow bitcall's on-device AI. Without the secrets the release build uses the debug key.
    val keystore = System.getenv("BITCALL_KEYSTORE_PATH")
    signingConfigs {
        if (keystore != null) create("release") {
            storeFile = file(keystore)
            storePassword = System.getenv("BITCALL_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("BITCALL_KEY_ALIAS")
            keyPassword = System.getenv("BITCALL_KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true // screenshot tests render the real font and icons
    }
    packaging {
        resources.excludes += listOf("META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*", "META-INF/*.kotlin_module")
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    // Bitter's cloud brain (used only when the user adds their own Claude API key).
    implementation("com.anthropic:anthropic-java:2.34.0")
    testImplementation("junit:junit:4.13.2")
    // Screenshot previews of the screens, rendered on the JVM (./gradlew recordRoborazziDebug).
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.38.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.38.0")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
