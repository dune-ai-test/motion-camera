# Keep ML Kit model metadata
-keep class com.google.mlkit.** { *; }
-keepclassmembers class com.google.mlkit.** { *; }

# CameraX
-dontwarn androidx.camera.**

# Kotlinx coroutines
-dontwarn kotlinx.coroutines.**

# Coil
-dontwarn coil.**
