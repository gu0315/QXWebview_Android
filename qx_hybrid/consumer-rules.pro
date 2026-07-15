# ============================================================
# qx-hybrid SDK 混淆保留规则（随 AAR 下发给宿主 App，宿主无需再配）
# ============================================================

# ---- 保留注解 / 泛型 / 内部类等元信息（反射、动态代理依赖）----
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod, Exceptions

# ---- Android-BLE (aicareles) ----
# 库内部通过「动态代理 + 反射调用构造方法」创建对象，混淆后会抛 NoSuchMethodException，必须整体保留
-keep class cn.com.heaton.blelibrary.** { *; }
-keep interface cn.com.heaton.blelibrary.** { *; }
-dontwarn cn.com.heaton.blelibrary.**

# 保留业务侧继承的蓝牙回调子类（库会反射/代理实例化它们）
-keep class * extends cn.com.heaton.blelibrary.ble.callback.BleScanCallback { *; }
-keep class * extends cn.com.heaton.blelibrary.ble.callback.BleConnectCallback { *; }
-keep class * extends cn.com.heaton.blelibrary.ble.callback.BleNotifyCallback { *; }
-keep class * extends cn.com.heaton.blelibrary.ble.callback.BleWriteCallback { *; }
-keep class * extends cn.com.heaton.blelibrary.ble.callback.BleMtuCallback { *; }

# ---- qx-hybrid 桥接层与插件（通过桥接/反射调用，需保留）----
-keep class com.jd.** { *; }
-keep class com.energy.qx_hybrid.** { *; }
# QXBlePlugin 在默认（根）包，不在 com.jd.** 覆盖范围内，单独保留
-keep class QXBlePlugin { *; }

# ---- 保留 @JavascriptInterface 方法（WebView 桥接入口）----
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# ---- Gson：防御性规则（当前未强用，防未来序列化 SDK 数据类踩坑）----
-keepattributes AnnotationDefault
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
# 若直接用字段名（未加 @SerializedName）序列化，需保留对应数据类字段
-keep class com.jd.jdbridge.Request { *; }
-keep class com.jd.jdbridge.Response { *; }

# ---- zxing 扫码（journeyapps 自带 consumer 规则，此处仅保险）----
-dontwarn com.google.zxing.**
-dontwarn com.journeyapps.**
