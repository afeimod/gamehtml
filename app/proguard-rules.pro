# WebView 加载的 HTML/JS 内部已经用字符串引用资源路径, R8 不能动
-keep class com.game4399.app.** { *; }
-keepclassmembers class com.game4399.app.** { *; }
-keepattributes *Annotation*,SourceFile,LineNumberTable
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
