# Line numbers for crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

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
-keep class com.terrycollins.celltowerid.domain.model.** { *; }
-keep class com.terrycollins.celltowerid.export.** { *; }
-keep class com.terrycollins.celltowerid.data.entity.** { *; }
