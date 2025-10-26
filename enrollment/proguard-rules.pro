# ZeroPay Enrollment Module ProGuard Rules

# ============== Enrollment Classes ==============
# Keep all enrollment classes
-keep class com.zeropay.enrollment.** { *; }
-keep interface com.zeropay.enrollment.** { *; }
-keep enum com.zeropay.enrollment.** { *; }

# Keep EnrollmentManager (main API)
-keep class com.zeropay.enrollment.EnrollmentManager { *; }
-keep class com.zeropay.enrollment.EnrollmentManager$* { *; }

# Keep enrollment factors
-keep class com.zeropay.enrollment.factors.** { *; }

# Keep enrollment UI components
-keep class com.zeropay.enrollment.ui.** { *; }

# Keep enrollment models
-keep class com.zeropay.enrollment.models.** { *; }

# Keep payment providers
-keep class com.zeropay.enrollment.payment.** { *; }
-keep class com.zeropay.enrollment.payment.providers.** { *; }

# Keep consent management
-keep class com.zeropay.enrollment.consent.** { *; }

# Keep security classes
-keep class com.zeropay.enrollment.security.** { *; }

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

# ============== Payment Providers ==============
# Stripe
-keep class com.stripe.android.** { *; }
-dontwarn com.stripe.android.**

# Google Pay
-keep class com.google.android.gms.wallet.** { *; }
-dontwarn com.google.android.gms.wallet.**

# Solana (Phantom wallet)
-keep class com.solana.** { *; }
-dontwarn com.solana.**

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

# ============== Optimization ==============
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keep,allowobfuscation,allowshrinking interface kotlin.coroutines.Continuation

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
