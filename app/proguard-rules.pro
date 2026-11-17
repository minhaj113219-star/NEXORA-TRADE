# NEXORA TRADE ProGuard Rules
-keepattributes *Annotation*
-keepclassmembers class * extends androidx.room.RoomDatabase {
    <init>();
}