# Line numbers for crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Strip verbose/debug logcat calls from release builds. Combined with
# AppLog's BuildConfig.DEBUG gate, this keeps precise-location traces
# out of logcat as well as the on-disk log.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *
-keepclassmembers class * {
    @androidx.room.* <methods>;
}

# Gson
-keepattributes Signature
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# MapLibre
-keep class org.maplibre.android.** { *; }
-dontwarn org.maplibre.**

# Domain models (serialized)
-keep class com.celltowerid.android.domain.model.** { *; }
-keep class com.celltowerid.android.export.** { *; }
-keep class com.celltowerid.android.data.entity.** { *; }
