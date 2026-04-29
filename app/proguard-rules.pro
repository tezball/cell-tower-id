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
# Keeping the class alone is not enough under AGP 9.x's R8 — it strips
# constructors that are only reachable via reflection (e.g.
# WorkManagerInitializer reflectively constructing WorkDatabase_Impl,
# which crashes at startup with NoSuchMethodException). Explicitly
# keep both the RoomDatabase subclasses' constructors and any
# generated `_Impl` classes' constructors.
-keep class * extends androidx.room.RoomDatabase {
    <init>(...);
}
-keep class **.*_Impl {
    <init>(...);
}
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
