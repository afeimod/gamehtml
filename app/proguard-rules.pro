# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep game classes
-keep class com.snakegame.app.game.** { *; }
-keep class com.snakegame.app.ui.** { *; }
-keep class com.snakegame.app.utils.** { *; }

# Kotlin specific
-keepattributes *Annotation*
-keep class kotlin.** { *; }
-keep class org.jetbrains.** { *; }