/*
 * ZeroPay SDK Gradle Dependencies
 * Path: sdk/build.gradle.kts
 * 
 * ⚠️ INSTRUCTIONS:
 * 1. Open your existing sdk/build.gradle.kts file
 * 2. Find the "dependencies {" section
 * 3. ADD these dependencies to that section (don't replace the whole file!)
 * 4. Sync Gradle
 */

plugins {
    kotlin("multiplatform")
    id("com.android.library")
}

kotlin {
    android {
        compilations.all {
            kotlinOptions {
                jvmTarget = "17"
            }
        }
    }
    
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlin:kotlin-stdlib")
            }
        }
        val androidMain by getting {
            dependencies {
                implementation("androidx.core:core-ktx:1.13.1")
            }
        }
    }
}

dependencies {
    // ============== EXISTING DEPENDENCIES ==============
    // Keep all your existing dependencies here
    
    // ============== ADD THESE NEW DEPENDENCIES ==============
    
    // -------- Security --------
    // AndroidX Security (EncryptedSharedPreferences, KeyStore)
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    
    // Biometrics (Face, Fingerprint authentication)
    implementation("androidx.biometric:biometric:1.2.0-alpha05")
    
    // -------- Networking --------
    // OkHttp (HTTP client with certificate pinning)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    
    // OkHttp Logging (useful for debugging - optional)
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    
    // -------- Google Play Services --------
    // SafetyNet (device integrity check)
    implementation("com.google.android.gms:play-services-safetynet:18.0.1")
    
    // Play Services Base (required for SafetyNet)
    implementation("com.google.android.gms:play-services-base:18.3.0")
    
    // -------- Kotlin Coroutines --------
    // Coroutines for async operations
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    
    // -------- JSON Serialization (Optional but Recommended) --------
    // Kotlinx Serialization for JSON handling
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
    
    // OR if you prefer Gson:
    // implementation("com.google.code.gson:gson:2.10.1")
    
    // -------- Testing --------
    // Keep your existing test dependencies and add these:
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test:1.9.22")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test:rules:1.5.0")
}

// ============== ADDITIONAL GRADLE CONFIGURATION ==============

android {
    namespace = "com.zeropay.sdk"
    compileSdk = 34
    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
   // Add this to enable ProGuard
    buildTypes {
        release {
            isMinifyEnabled = true  // Enable ProGuard
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"  // Your ProGuard rules file
            )
        }
    }
    
    // Add this to enable R8 optimization
    buildFeatures {
        buildConfig = true
    }
    
    // Minimum SDK version (adjust if needed)
    defaultConfig {
        minSdk = 26  // Android 8.0 (required for KeyStore features)
        targetSdk = 34  // Latest Android
        
        // Test instrumentation runner
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}
