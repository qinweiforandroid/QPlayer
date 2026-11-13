# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
# ---- Media3 / ExoPlayer ----
# Media3 的 AAR 自带 consumer 规则，这里只补内核通过反射/序列化间接使用的部分。
-keep class androidx.media3.exoplayer.ExoPlayer { *; }
-keep class androidx.media3.common.PlaybackException { *; }
-dontwarn androidx.media3.**

# 播放内核对外协议由 app 与 player-media3 跨模块直接引用，
# 保持公开 API，避免 release 包中类名被打包器改写后跨模块调用失配。
-keep class com.qw.player.core.engine.** { *; }
-keep class com.qw.player.core.media.** { *; }
-keep class com.qw.player.core.session.** { *; }
-keep class com.qw.player.media3.** { *; }
