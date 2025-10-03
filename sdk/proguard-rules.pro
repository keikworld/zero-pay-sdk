# ZeroPay SDK ProGuard Rules
# Add this file to: sdk/proguard-rules.pro

# ============== ZeroPay SDK ==============
# Keep all SDK classes
-keep class com.zeropay.sdk.** { *; }
-keep interface com.zeropay.sdk.** { *; }
-keep enum com.zeropay.sdk.** { *; }

# Keep factor classes (used via reflection)
-keep class com.zeropay.sdk.factors.** { *; }

# Keep crypto utilities
-keep class com.zeropay.sdk.crypto.** { *; }

# Keep security classes
-keep class com.zeropay.sdk.security.** { *; }

# Keep biometric classes
-keep class com.zeropay.sdk.biometrics.** { *; }

# Keep network classes
-keep class com.zeropay.sdk.network.** { *; }

# Keep storage classes
-keep class com.zeropay.sdk.storage.** { *; }

# Keep telemetry classes
-keep class com.zeropay.sdk.telemetry.** { *; }

# ============== Android Components ==============
# Keep AndroidX Biometric
-keep class androidx.biometric.** { *; }
-dontwarn androidx.biometric.**

# Keep AndroidX Security (EncryptedSharedPreferences)
-keep class androidx.security.crypto.** { *; }
-dontwarn androidx.security.crypto.**

# ============== Networking ==============
# Keep OkHttp (certificate pinning)
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# Keep Retrofit (if you add it later)
-keep class retrofit2.** { *; }
-dontwarn retrofit2.**

# ============== Google Play Services ==============
# Keep SafetyNet
-keep class com.google.android.gms.safetynet.** { *; }
-dontwarn com.google.android.gms.safetynet.**

# Keep Google Play Services base
-keep class com.google.android.gms.common.** { *; }
-dontwarn com.google.android.gms.**

# ============== Kotlin ==============
# Keep Kotlin metadata
-keep class kotlin.Metadata { *; }

# Keep coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# ============== Security - Remove Logging ==============
# Remove all Log.d, Log.v calls in release builds
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# Remove println calls
-assumenosideeffects class java.io.PrintStream {
    public void println(%);
    public void println(**);
}

# ============== Serialization ==============
# If you use Gson (for JSON)
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }

# If you use kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ============== Data Classes ==============
# Keep data classes used in SDK
-keepclassmembers class * {
    @kotlinx.serialization.Serializable *;
}

# Keep parcelable
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# ============== Reflection ==============
# Keep names for reflection
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# ============== Optimization ==============
# Don't optimize away these
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keep,allowobfuscation,allowshrinking interface kotlin.coroutines.Continuation

# Optimize code but keep stack traces readable
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
-dontoptimize
-dontpreverify

# ============== Warnings ==============
# Ignore warnings for missing classes (common in Android)
-dontwarn javax.annotation.**
-dontwarn javax.inject.**
-dontwarn sun.misc.Unsafe
-dontwarn java.lang.invoke.**

# ============== Debugging ==============
# Keep source file names and line numbers for better crash reports
-keepattributes SourceFile,LineNumberTable

# Rename source file attribute to hide real file names
-renamesourcefileattribute SourceFile

# ============== End of ZeroPay SDK ProGuard Rules ==============
