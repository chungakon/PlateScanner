import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.paparazzi)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "com.platescanner.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.platescanner.app"
        // Bumped from the brief's 24 to 26: POI 5.x (which the brief also
        // explicitly requires) emits `MethodHandle.invoke` instructions that
        // the Android dexer rejects on API < 26. See deliverable.md.
        minSdk = 26
        targetSdk = 34
        versionCode = 7
        versionName = "0.6.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        // Inject the API key from gradle.properties (or env). Empty by
        // default — never commit a real key. The actual request is made by
        // Retrofit with an `Authorization: Bearer <key>` header built from
        // BuildConfig.MINIMAX_API_KEY. Users without a build-time key can
        // fill in their own via the in-app Settings screen at runtime.
        buildConfigField(
            "String",
            "MINIMAX_API_KEY",
            "\"${project.findProperty("MINIMAX_API_KEY") ?: ""}\""
        )
        buildConfigField(
            "String",
            "MINIMAX_API_BASE",
            "\"${project.findProperty("MINIMAX_API_BASE") ?: "https://api.minimaxi.com"}\""
        )
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            isDebuggable = true
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.10"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*"
            )
        }
    }
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs = freeCompilerArgs + listOf(
            "-opt-in=kotlin.RequiresOptIn",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi"
        )
    }
}

dependencies {
    // Core / Lifecycle
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    // Compose (BOM)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Hilt (optional but recommended)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // CameraX
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // DataStore (Preferences) — runtime settings
    implementation(libs.androidx.datastore.preferences)

    // Networking
    implementation(libs.retrofit.core)
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit.kotlinx.serialization.converter)

    // Apache POI (xlsx export). Version pinned in libs.versions.toml.
    implementation(libs.poi)
    implementation(libs.poi.ooxml)

    // Coroutines + Logging
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.timber)

    // Unit tests
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation(libs.paparazzi)
    testImplementation(libs.robolectric)
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.32.2")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.32.2")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-junit-rule:1.32.2")
    testImplementation(libs.androidx.activity.compose)
    testImplementation("androidx.test:core:1.5.0")
    testImplementation("androidx.test.ext:junit:1.1.5")
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("androidx.compose.ui:ui-test-manifest")

    // Android tests
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")

    // For Roborazzi/Robolectric: pin a known-good Hamcrest so Espresso
    // can load its Matchers class even though the unit test only uses
    // the Compose-test API.
    testImplementation("org.hamcrest:hamcrest:2.2")
}

// Paparazzi 1.3.x uses an old internal layoutlib that trips on JDK 17's
// strong module encapsulation. Add the necessary --add-opens so the test
// JVM can reach com.google.common.collect.Sets.toImmutableEnumSet() etc.
tasks.withType<Test>().configureEach {
    jvmArgs(
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
        "--add-opens=java.base/java.lang.invoke=ALL-UNNAMED",
        "--add-opens=java.base/java.util=ALL-UNNAMED",
        "--add-opens=java.base/java.util.concurrent=ALL-UNNAMED",
        "--add-opens=java.base/java.io=ALL-UNNAMED",
        "--add-opens=java.base/java.nio=ALL-UNNAMED",
    )
    systemProperty("ANDROID_HOME", System.getenv("ANDROID_HOME")
        ?: System.getenv("ANDROID_SDK_ROOT")
        ?: "/tmp/sdkboot/android-sdk")
}
