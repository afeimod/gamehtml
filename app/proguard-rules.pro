# Add project specific ProGuard rules here.
-keep class com.flashbox.app.** { *; }
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keepattributes JavascriptInterface
-keep class org.jetbrains.annotations.** { *; }
