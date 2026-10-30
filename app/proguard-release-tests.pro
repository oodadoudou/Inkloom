# Only for -PinkloomReleaseTests: the instrumented tests call into the app's classes directly.
-keep class app.inkloom.** { *; }
-dontobfuscate
# Annotations the test libraries reference only at compile time.
-dontwarn com.google.errorprone.annotations.**
# Libraries the test APK shares with the app and calls directly. What stays shrunk is checked:
# ML Kit, Play services and the rest of the app's third-party code.
-keep class kotlin.** { *; }
-keep class kotlinx.** { *; }
-keep class androidx.** { *; }
# PrivacyManifestTest drives ML Kit's log transport directly to prove logs are refused.
-keep class com.google.android.datatransport.** { *; }
