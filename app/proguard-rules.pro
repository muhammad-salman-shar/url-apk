# WebView + JS interface — keep JS-called methods
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class com.urlapk.app.webview.** { *; }

# Kotlin metadata
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**

# Coroutines
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }

# Compose
-dontwarn androidx.compose.**

# Keep line numbers for crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
