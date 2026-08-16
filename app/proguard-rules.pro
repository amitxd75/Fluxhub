# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.SerializationKt
-keepclassmembers class * {
    *** Companion;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,allowobfuscation,allowshrinking class * {
    <init>(...);
}

# Data Models
-keep class com.liquidglass.fluxhub.data.** { *; }
-keep class com.liquidglass.fluxhub.chat.data.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# QuickJS
-keep class com.dokar.quickjs.** { *; }

# Kyant Backdrop & Capsule
-keep class com.kyant.backdrop.** { *; }
-keep class com.kyant.capsule.** { *; }