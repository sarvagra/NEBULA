# ==============================================================================
# NEBULA - R8 / ProGuard Optimization & Obfuscation Rules
# ==============================================================================

# ------------------------------------------------------------------------------
# 1. General Optimization & Obfuscation Settings
# ------------------------------------------------------------------------------
-optimizationpasses 5
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-verbose

# Preserve line numbers and source file names for crash reporting (Sentry / Firebase Crashlytics)
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable,EnclosingMethod,InnerClasses,Signature,*Annotation*

# ------------------------------------------------------------------------------
# 2. Nebula Domain & Data Models (Protect JSON serialization & reflection)
# ------------------------------------------------------------------------------
-keep class com.nebula.app.domain.model.** { *; }
-keepclassmembers enum com.nebula.app.domain.model.** { *; }

-keep class com.nebula.app.data.backend.** { *; }
-keep class com.nebula.app.data.local.** { *; }
-keep class com.nebula.app.data.repository.** { *; }

# Keep all serializable and JSON parsable classes
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    !private <fields>;
    !private <methods>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# ------------------------------------------------------------------------------
# 3. Jetpack Compose
# ------------------------------------------------------------------------------
-keep class androidx.compose.ui.platform.** { *; }
-keep class androidx.compose.runtime.** { *; }
-keepclassmembers class * {
    @androidx.compose.runtime.Composable *;
    androidx.compose.runtime.RecomposeScopeImpl *;
}

# ------------------------------------------------------------------------------
# 4. Kotlin & Coroutines
# ------------------------------------------------------------------------------
-keepclassmembers class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**
-keepattributes *Annotation*,InnerClasses,EnclosingMethod

# ------------------------------------------------------------------------------
# 5. Socket.io & Engine.io
# ------------------------------------------------------------------------------
-keep class io.socket.** { *; }
-keep class io.socket.client.** { *; }
-keep class io.socket.engineio.client.** { *; }
-dontwarn io.socket.**

# ------------------------------------------------------------------------------
# 6. OkHttp & Networking
# ------------------------------------------------------------------------------
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# ------------------------------------------------------------------------------
# 7. Firebase & Google Identity Services
# ------------------------------------------------------------------------------
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# ------------------------------------------------------------------------------
# 8. Coil Image Loader
# ------------------------------------------------------------------------------
-keep class coil.** { *; }
-dontwarn coil.**

# ------------------------------------------------------------------------------
# 9. Media3 / ExoPlayer
# ------------------------------------------------------------------------------
# ExoPlayer loads renderers, extractors and HLS segments reflectively through
# androidx.media3.common.util, so R8 must not remove the implementations it looks up
# by name. Without these rules a release build plays YouTube (a WebView) but shows a
# black surface for every direct .mp4/.m3u8 link.
-keep class androidx.media3.** { *; }
-keep interface androidx.media3.** { *; }
-dontwarn androidx.media3.**

# ------------------------------------------------------------------------------
# 10. Strip Debug Log Statements in Production
# ------------------------------------------------------------------------------
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
}
