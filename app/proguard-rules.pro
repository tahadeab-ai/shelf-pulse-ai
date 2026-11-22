# Keep native JNI methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep native bridge package, engine, and members
-keep class com.shelfpulse.ai.nativebridge.** { *; }
-keepclassmembers class com.shelfpulse.ai.nativebridge.** { *; }

# Keep domain models passed across JNI / Room / ViewModel
-keep class com.shelfpulse.ai.domain.model.** { *; }
-keepclassmembers class com.shelfpulse.ai.domain.model.** { *; }

# Keep Room entities and DAOs
-keep class com.shelfpulse.ai.data.db.** { *; }
-keepclassmembers class com.shelfpulse.ai.data.db.** { *; }
-keep class com.shelfpulse.ai.data.model.** { *; }
-keepclassmembers class com.shelfpulse.ai.data.model.** { *; }

# Keep zxingcpp JNI classes and methods
-keep class zxingcpp.** { *; }
-keepclassmembers class zxingcpp.** { *; }

# Keep WorkManager worker and sync classes invoked by reflection / serialization
-keep class com.shelfpulse.ai.data.sync.** { *; }
-keepclassmembers class com.shelfpulse.ai.data.sync.** { *; }
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# Keep AppCompat locale metadata holder service for per-app language
-keep class androidx.appcompat.app.AppLocalesMetadataHolderService { *; }
