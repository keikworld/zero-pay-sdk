# ZeroPay Merchant Module ProGuard Rules

# ============== Merchant Classes ==============
# Keep all merchant classes
-keep class com.zeropay.merchant.** { *; }
-keep interface com.zeropay.merchant.** { *; }
-keep enum com.zeropay.merchant.** { *; }

# Keep VerificationManager (main API)
-keep class com.zeropay.merchant.verification.VerificationManager { *; }
-keep class com.zeropay.merchant.verification.VerificationManager$* { *; }

# Keep verification classes
-keep class com.zeropay.merchant.verification.** { *; }

# Keep fraud detection
-keep class com.zeropay.merchant.fraud.** { *; }

# Keep merchant UI components
-keep class com.zeropay.merchant.ui.** { *; }

# Keep merchant models
-keep class com.zeropay.merchant.models.** { *; }

# Keep UUID scanner
-keep class com.zeropay.merchant.uuid.** { *; }

# Keep alert service
-keep class com.zeropay.merchant.alerts.** { *; }

# Keep config
-keep class com.zeropay.merchant.config.** { *; }

# ============== Jetpack Compose ==============
# Keep Compose UI classes
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Keep Compose runtime
-keep class androidx.compose.runtime.** { *; }

# Keep composable functions
-keepclassmembers class * {
    @androidx.compose.runtime.Composable *;
}

# ============== Biometric ==============
# Keep biometric classes
-keep class androidx.biometric.** { *; }
-dontwarn androidx.biometric.**

# ============== Camera (QR Code Scanner) ==============
# Keep CameraX classes
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# Keep ML Kit barcode scanning
-keep class com.google.mlkit.vision.barcode.** { *; }
-dontwarn com.google.mlkit.vision.barcode.**

# ============== Constant-Time Operations ==============
# CRITICAL: Do NOT optimize constant-time comparison
-keep class com.zeropay.merchant.verification.DigestComparator { *; }
-keepclassmembers class com.zeropay.merchant.verification.DigestComparator {
    public static *** constantTimeEquals(...);
}

# ============== ZK-SNARK Proof Generation ==============
-keep class com.zeropay.merchant.verification.ProofGenerator { *; }
-keep class com.zeropay.merchant.verification.ProofGenerator$* { *; }

# ============== Rate Limiter ==============
-keep class com.zeropay.merchant.fraud.RateLimiter { *; }
-keep class com.zeropay.merchant.fraud.RateLimiterRedis { *; }
-keepclassmembers class com.zeropay.merchant.fraud.RateLimiter {
    private static ** rwLock;
    private static ** attempts;
}

# ============== Fraud Detector ==============
-keep class com.zeropay.merchant.fraud.FraudDetector { *; }
-keep class com.zeropay.merchant.fraud.FraudDetector$* { *; }

# ============== Kotlin ==============
# Keep Kotlin metadata
-keep class kotlin.Metadata { *; }

# Keep coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# ============== Serialization ==============
-keepattributes Signature
-keepattributes *Annotation*

# Keep data classes
-keepclassmembers class * {
    @kotlinx.serialization.Serializable *;
}

# ============== Networking ==============
# Keep OkHttp
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# Keep Retrofit (if used)
-keep class retrofit2.** { *; }
-dontwarn retrofit2.**

# ============== Signal Protocol ==============
-keep class org.signal.libsignal.** { *; }
-dontwarn org.signal.libsignal.**
-dontnote org.signal.libsignal.**

# ============== Optimization ==============
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keep,allowobfuscation,allowshrinking interface kotlin.coroutines.Continuation

# CRITICAL: Do NOT optimize constant-time methods
-keep,allowshrinking class com.zeropay.merchant.verification.DigestComparator {
    public static *** constantTimeEquals(...);
}

# ============== Concurrent Collections ==============
-keep class java.util.concurrent.** { *; }
-keep class java.util.concurrent.locks.** { *; }
-dontwarn java.util.concurrent.**

# ============== Debugging ==============
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ============== Remove Logging ==============
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# ============== Warnings ==============
-dontwarn javax.annotation.**
-dontwarn javax.inject.**
-dontwarn sun.misc.Unsafe
-dontwarn java.lang.invoke.**

# ============== Android Components ==============
# Keep Activities
-keep public class * extends android.app.Activity
-keep public class * extends androidx.appcompat.app.AppCompatActivity
-keep public class * extends androidx.activity.ComponentActivity

# Keep Services
-keep public class * extends android.app.Service

# Keep BroadcastReceivers
-keep public class * extends android.content.BroadcastReceiver

# ============== Parcelable ==============
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}
