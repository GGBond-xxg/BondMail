plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.bond.mail"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.bond.mail"
        minSdk = 26
        targetSdk = 36
        versionCode = 134
        versionName = "1.3.0.2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf("-Xjvm-default=all")
    }


    buildTypes {
        getByName("release") {
            // Ship the same optimized runtime that is exercised by the local performance build.
            // The only remaining difference is signing: release uses the publishing key, while
            // performance uses the local debug key so it can be installed directly over test data.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }

        // Non-debuggable build signed with the local debug key for fair frame-rate testing.
        // It replaces the debug APK without clearing app data. R8/resource shrinking keeps this
        // release-like test package much smaller than the previous unshrunk performance APK.
        create("performance") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            isDebuggable = false
            // Keep the smooth release/runtime behaviour, but let R8 remove unused Compose,
            // Material icon and mail code so the install APK is substantially smaller.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            matchingFallbacks += listOf("release")
        }
    }

    packaging {
        resources.excludes += setOf(
            "META-INF/NOTICE.md",
            "META-INF/LICENSE.md",
            "META-INF/NOTICE",
            "META-INF/LICENSE",
            "META-INF/DEPENDENCIES"
        )
    }

    lint {
        abortOnError = true
    }
}


dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.06.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.1")
    implementation("androidx.navigation:navigation-compose:2.9.1")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("com.materialkolor:material-kolor:3.0.1")
    // 0.4.7 is the stable MIUIX line built against Compose 1.8.x / Kotlin 2.1.x,
    // matching this app's current toolchain. Keep MIUIX calls inside ui/theme adapters.
    implementation("top.yukonga.miuix.kmp:miuix:0.4.7")
    debugImplementation("androidx.compose.ui:ui-tooling")

    val roomVersion = "2.8.4"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")


    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation("androidx.documentfile:documentfile:1.1.0")
    implementation("androidx.work:work-runtime-ktx:2.11.2")

    // OAuth providers. MSAL owns Microsoft token refresh; Google Identity Services returns
    // short-lived access tokens for Gmail IMAP/SMTP without storing refresh tokens on-device.
    implementation("com.microsoft.identity.client:msal:8.4.1")
    implementation("com.google.android.gms:play-services-auth:21.6.0")

    // Firebase configuration is supplied by app/google-services.json. The BoM keeps all
    // Firebase libraries on a compatible release set without per-library version numbers.
    implementation(platform("com.google.firebase:firebase-bom:34.17.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-messaging")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jsoup:jsoup:1.18.3")
    implementation("androidx.webkit:webkit:1.14.0")

    // Android-compatible JavaMail implementation for IMAP/SMTP.
    implementation("com.sun.mail:android-mail:1.6.7")
    implementation("com.sun.mail:android-activation:1.6.7")

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.4.0")
}
