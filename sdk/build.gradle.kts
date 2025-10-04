/*
 * ZeroPay SDK - Complete Gradle Configuration
 * Production-ready with all security dependencies
 */

plugins {
    kotlin("multiplatform")
    id("com.android.library")
    id("org.jetbrains.dokka") version "1.9.10"
    kotlin("plugin.serialization") version "1.9.22"
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
                
                // ============== UTILITIES ==============
                
                // Timber (logging - optional but recommended)
                implementation("com.jakewharton.timber:timber:5.0.1")
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
    }
}

android {
    namespace = "com.zeropay.sdk"
    compileSdk = 34
    
    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    
    defaultConfig {
        minSdk = 26  // Android 8.0 (required for KeyStore features)
        targetSdk = 34
        
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // Consumer ProGuard rules
        consumerProguardFiles("consumer-rules.pro")
        
        // BuildConfig fields (if needed)
        buildConfigField("String", "SDK_VERSION", "\"1.0.0\"")
        buildConfigField("boolean", "DEBUG_MODE", "false")
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        
        // Enable desugaring for newer Java APIs on older Android versions
        isCoreLibraryDesugaringEnabled = true
    }
    
    kotlinOptions {
        jvmTarget = "17"
        
        // Enable explicit API mode for library
        freeCompilerArgs += listOf(
            "-Xopt-in=kotlin.RequiresOptIn",
            "-Xopt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-Xopt-in=kotlinx.coroutines.FlowPreview"
        )
    }
    
    buildTypes {
        release {
            isMinifyEnabled = true
            
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            
            // Disable debugging in release
            isDebuggable = false
            isJniDebuggable = false
            
            // Optimize build
            isShrinkResources = false  // Set to true if you have resources
        }
        
        debug {
            isMinifyEnabled = false
            isDebuggable = true
            
            // Debug ProGuard rules (optional)
            proguardFiles(
                getDefaultProguardFile("proguard-android.txt"),
                "proguard-rules.pro"
            )
        }
        
        // Create a 'staging' build type (optional)
        create("staging") {
            initWith(getByName("release"))
            isDebuggable = true
            applicationIdSuffix = ".staging"
        }
    }
    
    buildFeatures {
        buildConfig = true
        compose = true
        viewBinding = false  // Not needed for Compose-only
    }
    
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.3"
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
        
        // Enable test coverage
        unitTests.all {
            it.useJUnitPlatform()  // If using JUnit 5
        }
    }
}

dependencies {
    // Desugaring library for Java 8+ APIs on older Android
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")
    
    // Additional Android dependencies that can't go in sourceSets
    
    // Annotation processors (if any)
    // kapt("...")  // Add if using Kapt
    
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
            sourceLink {
                localDirectory.set(file("src/commonMain/kotlin"))
                remoteUrl.set(
                    java.net.URL("https://github.com/yourusername/zeropay-sdk/tree/main/sdk/src/commonMain/kotlin")
                )
                remoteLineSuffix.set("#L")
            }
        }
    }
}

// Task to print dependencies (useful for debugging)
tasks.register("printDependencies") {
    doLast {
        configurations.getByName("androidMainImplementation").dependencies.forEach {
            println("${it.group}:${it.name}:${it.version}")
        }
    }
}

// Task to check for dependency updates
tasks.register("dependencyUpdates") {
    doLast {
        println("Run: ./gradlew dependencyUpdates to check for updates")
        println("Requires: id(\"com.github.ben-manes.versions\") version \"0.50.0\" in plugins")
    }
}

// Ensure all tests run before publishing
tasks.named("publish") {
    dependsOn("test", "connectedAndroidTest")
}

// ============== MAVEN PUBLISHING (OPTIONAL) ==============

/*
// Uncomment if you want to publish to Maven
apply(plugin = "maven-publish")

publishing {
    publications {
        create<MavenPublication>("release") {
            from(components["release"])
            
            groupId = "com.zeropay"
            artifactId = "zeropay-sdk"
            version = "1.0.0"
            
            pom {
                name.set("ZeroPay SDK")
                description.set("Multi-factor authentication SDK with zero-knowledge proofs")
                url.set("https://github.com/yourusername/zeropay-sdk")
                
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
                
                developers {
                    developer {
                        id.set("yourusername")
                        name.set("Your Name")
                        email.set("you@example.com")
                    }
                }
                
                scm {
                    connection.set("scm:git:git://github.com/yourusername/zeropay-sdk.git")
                    developerConnection.set("scm:git:ssh://github.com/yourusername/zeropay-sdk.git")
                    url.set("https://github.com/yourusername/zeropay-sdk")
                }
            }
        }
    }
    
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/yourusername/zeropay-sdk")
            credentials {
                username = project.findProperty("gpr.user") as String? ?: System.getenv("USERNAME")
                password = project.findProperty("gpr.key") as String? ?: System.getenv("TOKEN")
            }
        }
    }
}
*/

// ============== BUILD CACHE ==============

buildscript {
    repositories {
        google()
        mavenCentral()
    }
}

// Enable Gradle build cache
buildCache {
    local {
        isEnabled = true
        directory = File(rootDir, ".gradle/build-cache")
        removeUnusedEntriesAfterDays = 7
    }
}
