# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

-keep class com.example.model.** { *; }
-keep class com.example.data.** { *; }
-keep class androidx.room.** { *; }
-dontwarn androidx.room.**

