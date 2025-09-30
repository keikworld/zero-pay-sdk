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
}