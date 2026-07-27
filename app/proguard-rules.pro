# ProGuard / R8 rules for FlashGameBox

# Keep webview / JS bridge classes
-keep class com.flashbox.app.bridge.** { *; }
-keep class com.flashbox.app.webview.** { *; }
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# WebView & JS
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class android.webkit.WebView { *; }

# Standard Android keep rules
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends androidx.appcompat.app.AppCompatActivity
-keep public class * extends android.preference.Preference

# Material / AppCompat
-dontwarn com.google.android.material.**
-dontwarn androidx.**

# R8 full mode
-allowaccessmodification
-repackageclasses
