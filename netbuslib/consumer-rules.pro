# NetStatusBus 通过反射查找并调用 @NetSubscribe 注解的方法，
# 使用方开启 R8/ProGuard 混淆时需要保留这些方法及注解信息。
-keepattributes *Annotation*
-keep @interface com.sunchen.netbus.annotation.NetSubscribe
-keepclassmembers class * {
    @com.sunchen.netbus.annotation.NetSubscribe <methods>;
}
-keep enum com.sunchen.netbus.type.** { *; }
