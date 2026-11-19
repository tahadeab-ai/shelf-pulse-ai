# Keep native JNI methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep Room entities and DAOs
-keep class com.shelfpulse.ai.data.db.** { *; }
