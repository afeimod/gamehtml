# 4399 App ProGuard 规则

# 保留 WebView 与 JavascriptInterface（必须，否则 JS 回调 Native 失效）
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keepclassmembers class com.game4399.app.webview.WebAppInterface {
    public *;
}

# 保留 WebView 相关类
-keep class android.webkit.** { *; }
-keep class android.webkit.WebView { *; }
-keep class android.webkit.WebViewClient { *; }
-keep class android.webkit.WebChromeClient { *; }

# 保留数据模型
-keep class com.game4399.app.data.** { *; }

# Kotlin 元数据
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions
-dontwarn kotlin.**
