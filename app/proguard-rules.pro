# Keep Gson model classes
-keep class com.fametv.app.model.** { *; }

# Keep Leanback internals (auto-handled by AAR rules, but explicit)
-keep class androidx.leanback.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Media3
-keep class androidx.media3.** { *; }
