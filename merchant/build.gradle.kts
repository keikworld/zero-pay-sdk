/*
 * ZeroPay Merchant Module - Kotlin Multiplatform Configuration
 * Merchant-side verification application
 * Version: 2.0.0
 */

plugins {
    kotlin("multiplatform")
    id("com.android.application")
    kotlin("plugin.serialization") version "1.9.22"
}

kotlin {
    // ============== ANDROID TARGET ==============
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

    // ============== SOURCE SETS ==============
    sourceSets {
        // -------- Common (Shared) --------
        val commonMain by getting {
            dependencies {
                // ZeroPay SDK
                implementation(project(":sdk"))

                implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.22")

                // Coroutines (multiplatform)
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

                // Serialization (multiplatform)
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
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
                implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")

                // Coroutines Android
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

                // ============== UI (Compose) ==============
                val composeVersion = "1.5.4"
                implementation("androidx.compose.ui:ui:$composeVersion")
                implementation("androidx.compose.material3:material3:1.1.2")
                implementation("androidx.compose.ui:ui-tooling-preview:$composeVersion")
                implementation("androidx.compose.runtime:runtime:$composeVersion")
                implementation("androidx.compose.foundation:foundation:$composeVersion")

                // Compose Activity integration
                implementation("androidx.activity:activity-compose:1.8.2")

                // Biometric authentication
                implementation("androidx.biometric:biometric:1.1.0")
                implementation("androidx.fragment:fragment-ktx:1.6.2")
            }
        }

        val androidUnitTest by getting {
            dependencies {
                implementation("junit:junit:4.13.2")
                implementation("org.jetbrains.kotlin:kotlin-test:1.9.22")
                implementation("org.jetbrains.kotlin:kotlin-test-junit:1.9.22")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")

                // MockK for mocking
                implementation("io.mockk:mockk:1.13.8")
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

                // MockK for mocking dependencies
                implementation("io.mockk:mockk-android:1.13.8")
                implementation("io.mockk:mockk-agent:1.13.8")

                // Coroutines test
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
            }
        }
    }
}

// ============== ANDROID CONFIGURATION ==============
android {
    namespace = "com.zeropay.merchant"
    compileSdk = 34

    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")

    defaultConfig {
        applicationId = "com.zeropay.merchant"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        }

        debug {
            isDebuggable = true
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        compose = true
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
