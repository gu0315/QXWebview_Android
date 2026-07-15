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
-keep class QXBlePlugin { *; }
-keep class QXBasePlugin { *; }
-keep class QXBleDefine { *; }

# ---- 保留 @JavascriptInterface 方法（WebView 桥接入口）----
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
