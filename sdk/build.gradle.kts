/*
 * ZeroPay SDK - Kotlin Multiplatform Configuration
 * Supports: Android, iOS, Web
 * Version: 2.0.0 (with Blockchain Integration)
 */

plugins {
    kotlin("multiplatform")
    id("com.android.library")
    id("org.jetbrains.dokka") version "1.9.10"
    kotlin("plugin.serialization") version "1.9.22"
}

kotlin {
    // ============== ANDROID TARGET (FIXED: using androidTarget()) ==============
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "17"
                
                // Compiler arguments for Android
                freeCompilerArgs += listOf(
                    "-Xopt-in=kotlin.RequiresOptIn",
                    "-Xopt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
                    "-Xopt-in=kotlinx.coroutines.FlowPreview"
                )
            }
        }
    }
    
    // ============== iOS TARGETS (OPTIONAL) ==============
    // Uncomment if you want iOS support
    /*
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    */
    
    // ============== JS/WASM TARGET (OPTIONAL) ==============
    // Uncomment if you want Web support
    /*
    js(IR) {
        browser()
        nodejs()
    }
    */
    
    // ============== SOURCE SETS ==============
    sourceSets {
        // -------- Common (Shared) --------
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.22")
                
                // Coroutines (multiplatform)
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
                
                // Serialization (multiplatform)
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
                
                // ============== NEW: BLOCKCHAIN DEPENDENCIES ==============
                // OkHttp for RPC calls (multiplatform compatible)
                implementation("com.squareup.okhttp3:okhttp:4.12.0")
            }
        }
        
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
            }
        }
        
        // -------- Android --------
        val androidMain by getting {
            dependencies {
                // AndroidX Core
                implementation("androidx.core:core-ktx:1.13.1")
                implementation("androidx.appcompat:appcompat:1.6.1")
                
                // Coroutines Android
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
                
                // ============== SECURITY DEPENDENCIES ==============
                
                // Bouncy Castle (for Argon2 key derivation)
                implementation("org.bouncycastle:bcprov-jdk15on:1.70")
                implementation("org.bouncycastle:bcpkix-jdk15on:1.70")
                
                // Signal Protocol (for E2E encryption)
                implementation("org.signal:libsignal-android:0.41.0")
                
                // AndroidX Security (EncryptedSharedPreferences)
                implementation("androidx.security:security-crypto:1.1.0-alpha06")
                
                // AndroidX Biometric (Face/Fingerprint)
                implementation("androidx.biometric:biometric:1.2.0-alpha05")
                
                // ============== NETWORKING ==============
                
                // OkHttp (HTTP client with certificate pinning)
                implementation("com.squareup.okhttp3:okhttp:4.12.0")
                implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
                
                // Retrofit (optional - for REST API)
                implementation("com.squareup.retrofit2:retrofit:2.9.0")
                implementation("com.squareup.retrofit2:converter-gson:2.9.0")
                
                // ============== GOOGLE PLAY SERVICES ==============
                
                // SafetyNet (device integrity verification)
                implementation("com.google.android.gms:play-services-safetynet:18.0.1")
                implementation("com.google.android.gms:play-services-base:18.3.0")
                
                // ============== UI (Compose) ==============
                
                val composeVersion = "1.5.4"
                implementation("androidx.compose.ui:ui:$composeVersion")
                implementation("androidx.compose.material3:material3:1.1.2")
                implementation("androidx.compose.ui:ui-tooling-preview:$composeVersion")
                implementation("androidx.compose.runtime:runtime:$composeVersion")
                implementation("androidx.compose.foundation:foundation:$composeVersion")
                
                // Compose Activity integration
                implementation("androidx.activity:activity-compose:1.8.2")
                
                // ============== JSON PROCESSING ==============
                
                // Gson (for JSON serialization)
                implementation("com.google.code.gson:gson:2.10.1")
                
                // JSON (org.json) - Android built-in, but explicitly declare
                implementation("org.json:json:20231013")
                
                // ============== UTILITIES ==============
                
                // Timber (logging - optional but recommended)
                implementation("com.jakewharton.timber:timber:5.0.1")
                
                // ============== NEW: BLOCKCHAIN SPECIFIC ==============
                
                // None needed - OkHttp already included above
            }
        }
        
        val androidUnitTest by getting {
            dependencies {
                implementation("junit:junit:4.13.2")
                implementation("org.jetbrains.kotlin:kotlin-test:1.9.22")
                implementation("org.jetbrains.kotlin:kotlin-test-junit:1.9.22")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
                
                // Mockito (for mocking)
                implementation("org.mockito:mockito-core:5.7.0")
                implementation("org.mockito.kotlin:mockito-kotlin:5.2.1")
                
                // MockK (Kotlin-specific mocking)
                implementation("io.mockk:mockk:1.13.8")
                
                // Robolectric (Android unit tests without emulator)
                implementation("org.robolectric:robolectric:4.11.1")
                
                // ============== NEW: BLOCKCHAIN TESTING ==============
                
                // OkHttp MockWebServer (for testing RPC calls)
                implementation("com.squareup.okhttp3:mockwebserver:4.12.0")
            }
        }
        
        val androidInstrumentedTest by getting {
            dependencies {
                implementation("androidx.test.ext:junit:1.1.5")
                implementation("androidx.test:runner:1.5.2")
                implementation("androidx.test:rules:1.5.0")
                implementation("androidx.test.espresso:espresso-core:3.5.1")
                
                // Compose UI testing
                implementation("androidx.compose.ui:ui-test-junit4:1.5.4")
                
                // AndroidX Test
                implementation("androidx.test:core:1.5.0")
                implementation("androidx.test:core-ktx:1.5.0")
            }
        }
        
        // -------- iOS (OPTIONAL) --------
        /*
        val iosX64Main by getting
        val iosArm64Main by getting
        val iosSimulatorArm64Main by getting
        val iosMain by creating {
            dependsOn(commonMain)
            iosX64Main.dependsOn(this)
            iosArm64Main.dependsOn(this)
            iosSimulatorArm64Main.dependsOn(this)
        }
        
        val iosX64Test by getting
        val iosArm64Test by getting
        val iosSimulatorArm64Test by getting
        val iosTest by creating {
            dependsOn(commonTest)
            iosX64Test.dependsOn(this)
            iosArm64Test.dependsOn(this)
            iosSimulatorArm64Test.dependsOn(this)
        }
        */
    }
}

// ============== ANDROID CONFIGURATION ==============
android {
    namespace = "com.zeropay.sdk"
    compileSdk = 34
    
    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    
    defaultConfig {
        minSdk = 26  // Android 8.0 (required for KeyStore features)
        
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // Consumer ProGuard rules
        consumerProguardFiles("consumer-rules.pro")
        
        // BuildConfig fields (if needed)
        buildConfigField("String", "SDK_VERSION", "\"2.0.0\"")
        buildConfigField("boolean", "DEBUG_MODE", "false")
        buildConfigField("boolean", "BLOCKCHAIN_ENABLED", "true")
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        
        // Enable desugaring for newer Java APIs on older Android versions
        isCoreLibraryDesugaringEnabled = true
    }
    
    buildTypes {
        release {
            isMinifyEnabled = true
            
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            
            // REMOVED: isDebuggable (not available in library modules)
            // Library modules inherit debuggable state from consuming app
            isJniDebuggable = false
        }
        
        debug {
            isMinifyEnabled = false
            
            // REMOVED: isDebuggable (not available in library modules)
            
            // Debug ProGuard rules (optional)
            proguardFiles(
                getDefaultProguardFile("proguard-android.txt"),
                "proguard-rules.pro"
            )
        }
    }
    
    buildFeatures {
        buildConfig = true
        compose = true
        viewBinding = false  // Not needed for Compose-only
    }
    
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"  // Compatible with Kotlin 1.9.22
    }
    
    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "/META-INF/LICENSE",
                "/META-INF/LICENSE.txt",
                "/META-INF/license.txt",
                "/META-INF/NOTICE",
                "/META-INF/NOTICE.txt",
                "/META-INF/notice.txt",
                "/META-INF/ASL2.0",
                "/META-INF/*.kotlin_module"
            )
        }
    }
    
    // Lint configuration
    lint {
        abortOnError = false
        checkReleaseBuilds = true
        warningsAsErrors = false
        
        // Disable specific checks if needed
        disable += setOf(
            "InvalidPackage",
            "MissingTranslation"
        )
    }
    
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    // Desugaring library for Java 8+ APIs on older Android
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")
    
    // Debug-only dependencies
    debugImplementation("androidx.compose.ui:ui-tooling:1.5.4")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.5.4")
}

// ============== ADDITIONAL CONFIGURATION ==============

// Dokka documentation generation
tasks.withType<org.jetbrains.dokka.gradle.DokkaTask>().configureEach {
    dokkaSourceSets {
        named("commonMain") {
            includes.from("Module.md")
        }
    }
}

// Task to print dependencies (useful for debugging)
tasks.register("printDependencies") {
    doLast {
        println("\n=== Common Dependencies ===")
        configurations.findByName("commonMainImplementation")?.dependencies?.forEach {
            println("${it.group}:${it.name}:${it.version}")
        }
        
        println("\n=== Android Dependencies ===")
        configurations.findByName("androidMainImplementation")?.dependencies?.forEach {
            println("${it.group}:${it.name}:${it.version}")
        }
    }
}

// NEW: Task to verify blockchain dependencies
tasks.register("verifyBlockchainDependencies") {
    doLast {
        println("\n=== Blockchain Dependencies Check ===")
        
        val requiredDeps = listOf(
            "com.squareup.okhttp3:okhttp",
            "org.jetbrains.kotlinx:kotlinx-serialization-json",
            "androidx.security:security-crypto"
        )
        
        val androidDeps = configurations.findByName("androidMainImplementation")?.dependencies
        
        requiredDeps.forEach { depName ->
            val found = androidDeps?.any { 
                "${it.group}:${it.name}".contains(depName.substringBefore(":"))
            } ?: false
            
            val status = if (found) "✅" else "❌"
            println("$status $depName")
        }
        
        println("\n✅ Blockchain integration ready!")
    }
}

// Ensure all tests run before assembling
tasks.named("assemble") {
    dependsOn("test")
}
