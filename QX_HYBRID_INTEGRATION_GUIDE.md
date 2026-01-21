# QX Hybrid SDK 集成指南

## 概述

QX Hybrid SDK 是一个 Android 混合应用开发框架，提供 JavaScript 与原生 Android 之间的双向通信能力，支持扫码、蓝牙、定位、文件下载等原生功能。

## 快速开始

### 1. 添加依赖

#### 方式一：使用 AAR 文件（推荐）

./gradlew :qx_hybrid:bundleReleaseAar 

1. 将 `qx_hybrid-release.aar` 文件复制到你的项目 `app/libs/` 目录
2. 在 `app/build.gradle` 中添加：

```gradle
dependencies {
    implementation files('libs/qx_hybrid-release.aar')
    
    // 仅需添加高德定位 SDK（其他依赖已包含在 AAR 中）
    implementation 'com.amap.api:location:6.5.1'
}
```

**说明：** SDK 已通过 `api` 方式包含了所有必需依赖（WebKit、Gson、ZXing、BLE 等），主工程会自动获得这些依赖，无需重复声明。

**SDK 已包含的依赖：**

*Google/AndroidX 官方库：*
- ✅ androidx.webkit:webkit:1.10.0 - WebView 增强功能
- ✅ androidx.appcompat:appcompat:1.6.1 - Android 兼容库
- ✅ com.google.android.material:material:1.10.0 - Material Design 组件
- ✅ androidx.core:core-ktx:1.12.0 - Kotlin 扩展
- ✅ com.google.code.gson:gson:2.13.2 - JSON 解析

*第三方开源库：*
- ✅ com.google.zxing:core:3.5.2 - 二维码核心库
- ✅ com.journeyapps:zxing-android-embedded:4.3.0 - 二维码扫描 UI
- ✅ com.github.aicareles:Android-BLE:3.3.1 - 蓝牙低功耗

**需要额外添加的依赖：**
- ⚠️ com.amap.api:location:6.5.1 - 高德定位 SDK（第三方商业库，需要单独申请 Key）

#### 方式二：使用本地 Maven

```gradle
// settings.gradle
dependencyResolutionManagement {
    repositories {
        mavenLocal()
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }  // 用于 Android-BLE
    }
}

// app/build.gradle
dependencies {
    implementation 'com.energy.sdk:qx-hybrid:0.1.0'
    
    // 仅需添加高德定位 SDK
    implementation 'com.amap.api:location:6.5.1'
}
```

**发布到本地 Maven：**
```bash
./gradlew :qx_hybrid:publishToMavenLocal
```

### 第三方依赖说明

SDK 使用了以下第三方库，已通过 `api` 方式包含，主工程会自动获得：

#### 1. 高德定位 SDK (必需配置) 🔴

**类型：** 第三方商业库

**用途：** 提供定位服务（`getLocation` API）

**配置步骤：**

1. 在 [高德开放平台](https://lbs.amap.com/) 注册账号并创建应用
2. 获取 Android 平台的 Key（需要配置应用包名和 SHA1）
3. 在 `AndroidManifest.xml` 中配置：

```xml
<application>
    <meta-data
        android:name="com.amap.api.v2.apikey"
        android:value="你的高德Key" />
</application>
```

**官方文档：** https://lbs.amap.com/api/android-location-sdk/guide/create-project/get-key

#### 2. ZXing (已包含) ✅

**类型：** 第三方开源库（Apache License 2.0）

**用途：** 二维码/条形码扫描（`scanQRCode` API）

**依赖：**
- `com.google.zxing:core:3.5.2` - 核心解码库
- `com.journeyapps:zxing-android-embedded:4.3.0` - Android UI 封装

**说明：** SDK 已包含，无需额外配置

**GitHub：** https://github.com/zxing/zxing

#### 3. Android-BLE (已包含) ✅

**类型：** 第三方开源库（Apache License 2.0）

**用途：** 蓝牙低功耗设备连接（`QXBlePlugin` 相关 API）

**依赖：** `com.github.aicareles:Android-BLE:3.3.1`

**说明：** SDK 已包含，需要在 `settings.gradle` 中添加 JitPack 仓库：

```gradle
dependencyResolutionManagement {
    repositories {
        maven { url 'https://jitpack.io' }
    }
}
```

**GitHub：** https://github.com/aicareles/Android-BLE

#### 4. Gson (已包含) ✅

**类型：** Google 官方库（Apache License 2.0）

**用途：** JSON 数据序列化/反序列化

**依赖：** `com.google.code.gson:gson:2.13.2`

**说明：** SDK 内部使用，用于 JS-Native 通信的数据转换

**GitHub：** https://github.com/google/gson

#### 5. AndroidX WebKit (已包含) ✅

**类型：** Google 官方库（AndroidX）

**用途：** WebView 增强功能和兼容性

**依赖：** `androidx.webkit:webkit:1.10.0`

**说明：** 提供跨版本的 WebView API 支持

**官方文档：** https://developer.android.com/jetpack/androidx/releases/webkit

### 2. 配置 AndroidManifest.xml

SDK 已经在内部声明了所有必需的权限、Activity 和 `<queries>`，会自动合并到你的应用中。

**你只需要添加：**

```xml
<manifest>
    <!-- 网络权限（如果你的应用还没有） -->
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    
    <application
        android:usesCleartextTraffic="true">  <!-- 如果需要支持 HTTP -->
        
        <!-- 高德定位 Key（必需） -->
        <meta-data
            android:name="com.amap.api.v2.apikey"
            android:value="你的高德Key" />
            
        <!-- 你的其他配置 -->
    </application>
</manifest>
```

**SDK 已包含的权限（无需重复声明）：**
- ✅ 相机权限（扫码）
- ✅ 蓝牙权限（Android 12+ 和旧版本）
- ✅ 定位权限
- ✅ 地图应用查询（高德、百度、腾讯、Google Maps）

**SDK 已包含的 Activity（无需重复声明）：**
- ✅ `QXWebViewActivity` - WebView 容器
- ✅ `QRScannerActivity` - 二维码扫描

### 3. 配置仓库（重要）

在项目根目录的 `settings.gradle` 中添加必要的仓库：

```gradle
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }  // 必需：用于 Android-BLE
    }
}
```

### 4. 运行时权限请求

Android 6.0+ 需要在运行时请求危险权限：

```kotlin
// 在使用相关功能前请求权限
val permissions = arrayOf(
    Manifest.permission.CAMERA,              // 扫码
    Manifest.permission.ACCESS_FINE_LOCATION, // 定位
    Manifest.permission.BLUETOOTH_SCAN,       // 蓝牙（Android 12+）
    Manifest.permission.BLUETOOTH_CONNECT
)

ActivityCompat.requestPermissions(this, permissions, REQUEST_CODE)
```

**权限用途说明：**
- `CAMERA` - 二维码扫描功能
- `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` - 定位功能和蓝牙扫描（Android 12 以下）
- `BLUETOOTH_SCAN` / `BLUETOOTH_CONNECT` - 蓝牙设备扫描和连接（Android 12+）

### 5. 网络安全配置（可选）

如果需要支持 HTTP 请求，创建 `res/xml/network_security_config.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <base-config cleartextTrafficPermitted="true">
        <trust-anchors>
            <certificates src="system" />
            <certificates src="user" />
        </trust-anchors>
    </base-config>
</network-security-config>
```

在 `AndroidManifest.xml` 中引用：

```xml
<application
    android:networkSecurityConfig="@xml/network_security_config">
```

## 使用方式

### 方式一：使用内置的 QXWebViewActivity

最简单的方式，直接启动：

```java
Intent intent = new Intent(this, QXWebViewActivity.class);
intent.putExtra("url", "https://your-web-app.com");
startActivity(intent);
```

### 方式二：在自定义 Activity 中使用 JDWebView

#### Kotlin 示例

```kotlin
import com.jd.hybrid.JDWebView
import com.jd.plugins.QXBridgePluginRegister

class MyWebViewActivity : AppCompatActivity() {
    private lateinit var webView: JDWebView
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        webView = JDWebView(this)
        setContentView(webView)
        
        // 注册所有插件
        QXBridgePluginRegister.registerAllPlugins(webView)
        
        // 加载 URL
        webView.loadUrl("https://your-web-app.com")
    }
    
    override fun onStart() {
        super.onStart()
        webView.onStart()
    }
    
    override fun onResume() {
        super.onResume()
        webView.onResume()
    }
    
    override fun onPause() {
        super.onPause()
        webView.onPause()
    }
    
    override fun onStop() {
        super.onStop()
        webView.onStop()
    }
    
    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
    
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
```

#### Java 示例

```java
import com.jd.hybrid.JDWebView;
import com.jd.plugins.QXBridgePluginRegister;

public class MyWebViewActivity extends AppCompatActivity {
    private JDWebView webView;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        webView = new JDWebView(this);
        setContentView(webView);
        
        // 注册所有插件
        QXBridgePluginRegister.registerAllPlugins(webView);
        
        // 加载 URL
        webView.loadUrl("https://your-web-app.com");
    }
    
    @Override
    protected void onStart() {
        super.onStart();
        webView.onStart();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        webView.onPause();
    }
    
    @Override
    protected void onStop() {
        super.onStop();
        webView.onStop();
    }
    
    @Override
    protected void onDestroy() {
        webView.destroy();
        super.onDestroy();
    }
    
    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
```

## JavaScript API

在 Web 页面中调用原生功能：

### 基础调用格式

```javascript
// 调用原生方法
XWebView._callNative(
    'pluginName',      // 插件名称
    'methodName',      // 方法名称
    { /* params */ },  // 参数对象
    function(response) {
        // 成功回调
        console.log('Success:', response);
    },
    function(error) {
        // 失败回调
        console.error('Error:', error);
    }
);
```

### 可用功能

#### 1. 二维码扫描

```javascript
XWebView._callNative(
    'QXBasePlugin',
    'scanQRCode',
    {},
    function(result) {
        console.log('扫码结果:', result.data);
    },
    function(error) {
        console.error('扫码失败:', error);
    }
);
```

#### 2. 获取定位

```javascript
XWebView._callNative(
    'QXBasePlugin',
    'getLocation',
    { type: 'gcj02' },  // 坐标系类型：wgs84, gcj02
    function(result) {
        console.log('经度:', result.longitude);
        console.log('纬度:', result.latitude);
        console.log('地址:', result.address);
    },
    function(error) {
        console.error('定位失败:', error);
    }
);
```

#### 3. 打开地图导航

```javascript
XWebView._callNative(
    'QXBasePlugin',
    'openLocation',
    {
        latitude: 39.9042,
        longitude: 116.4074,
        name: '天安门',
        address: '北京市东城区'
    },
    function(result) {
        console.log('打开地图成功');
    }
);
```

#### 4. 获取设备信息

```javascript
XWebView._callNative(
    'QXBasePlugin',
    'getSystemInfo',
    {},
    function(info) {
        console.log('系统:', info.system);
        console.log('平台:', info.platform);
        console.log('品牌:', info.brand);
        console.log('型号:', info.model);
        console.log('屏幕宽度:', info.screenWidth);
        console.log('屏幕高度:', info.screenHeight);
    }
);
```

#### 5. 下载文件

```javascript
XWebView._callNative(
    'QXBasePlugin',
    'downloadFile',
    {
        url: 'https://example.com/file.pdf',
        fileName: 'document.pdf'
    },
    function(result) {
        console.log('下载成功:', result.filePath);
    },
    function(error) {
        console.error('下载失败:', error);
    }
);
```

#### 6. 蓝牙操作

```javascript
// 初始化蓝牙适配器 - uni.openBluetoothAdapter
XWebView._callNative('QXBlePlugin', 'openBluetoothAdapter', {}, 
    function(result) { 
        console.log('蓝牙适配器已初始化'); 
    },
    function(error) {
        console.error('初始化失败:', error);
    }
);

// 获取蓝牙适配器状态 - uni.getBluetoothAdapterState
XWebView._callNative('QXBlePlugin', 'getBluetoothAdapterState', {},
    function(result) {
        // 成功回调 - 返回标准 uni-app 格式
        console.log('蓝牙可用:', result.available);
        console.log('正在搜索:', result.discovering);
        
        if (result.available) {
            console.log('蓝牙适配器可用，可以进行蓝牙操作');
        } else {
            console.log('蓝牙适配器不可用');
        }
        
        if (result.discovering) {
            console.log('当前正在搜索蓝牙设备');
        }
    },
    function(error) {
        // 失败回调 - 根据错误码处理
        console.error('获取蓝牙适配器状态失败');
        console.error('错误码:', error.errCode);
        console.error('错误信息:', error.errMsg);
        
        switch(error.errCode) {
            case 10000:
                console.log('蓝牙适配器未初始化，请先调用 openBluetoothAdapter');
                break;
            case 10001:
                console.log('当前蓝牙适配器不可用，请检查蓝牙是否开启');
                break;
            case 10009:
                console.log('系统不支持BLE，Android版本需要4.3以上');
                break;
            case 10008:
                console.log('系统错误，可能是权限问题');
                break;
        }
    }
);

// 开始搜寻蓝牙设备 - uni.startBluetoothDevicesDiscovery
XWebView._callNative('QXBlePlugin', 'startBluetoothDevicesDiscovery', {
    services: [], // 可选：要搜索的蓝牙设备主 service 的 uuid 列表
    allowDuplicatesKey: false, // 可选：是否允许重复上报同一设备
    interval: 0 // 可选：上报设备的间隔
}, function(result) { 
    console.log('开始搜索蓝牙设备'); 
});

// 停止搜寻蓝牙设备 - uni.stopBluetoothDevicesDiscovery
XWebView._callNative('QXBlePlugin', 'stopBluetoothDevicesDiscovery', {},
    function(result) { 
        console.log('停止搜索蓝牙设备'); 
    }
);

// 获取已发现的蓝牙设备 - uni.getBluetoothDevices
XWebView._callNative('QXBlePlugin', 'getBluetoothDevices', {},
    function(result) {
        console.log('已发现设备:', result.data.devices);
        result.data.devices.forEach(device => {
            console.log('设备名称:', device.name);
            console.log('设备ID:', device.deviceId);
            console.log('信号强度:', device.RSSI);
        });
    }
);

// 获取已连接的蓝牙设备 - uni.getConnectedBluetoothDevices
XWebView._callNative('QXBlePlugin', 'getConnectedBluetoothDevices', {
    services: ['FEE7'] // 必填：蓝牙设备主 service 的 uuid 列表
}, function(result) {
    console.log('已连接设备:', result.data.devices);
});

// 连接低功耗蓝牙设备 - uni.createBLEConnection
XWebView._callNative('QXBlePlugin', 'createBLEConnection', 
    { deviceId: 'XX:XX:XX:XX:XX:XX' },
    function(result) { 
        console.log('BLE设备连接成功'); 
    }
);

// 断开低功耗蓝牙设备连接 - uni.closeBLEConnection
XWebView._callNative('QXBlePlugin', 'closeBLEConnection', 
    { deviceId: 'XX:XX:XX:XX:XX:XX' },
    function(result) { 
        console.log('BLE设备已断开连接'); 
    }
);

// 获取蓝牙设备所有服务 - uni.getBLEDeviceServices
XWebView._callNative('QXBlePlugin', 'getBLEDeviceServices', 
    { deviceId: 'XX:XX:XX:XX:XX:XX' },
    function(result) {
        console.log('设备服务:', result.data.services);
        result.data.services.forEach(service => {
            console.log('服务UUID:', service.uuid);
            console.log('是否主服务:', service.isPrimary);
        });
    }
);

// 获取蓝牙设备某个服务中所有特征值 - uni.getBLEDeviceCharacteristics
XWebView._callNative('QXBlePlugin', 'getBLEDeviceCharacteristics', {
    deviceId: 'XX:XX:XX:XX:XX:XX',
    serviceId: 'FEE7'
}, function(result) {
    console.log('服务特征值:', result.data.characteristics);
    result.data.characteristics.forEach(char => {
        console.log('特征值UUID:', char.uuid);
        console.log('特征值属性:', char.properties);
    });
});

// 启用低功耗蓝牙设备特征值变化时的 notify 功能 - uni.notifyBLECharacteristicValueChange
XWebView._callNative('QXBlePlugin', 'notifyBLECharacteristicValueChange', {
    deviceId: 'XX:XX:XX:XX:XX:XX',
    serviceId: 'FEE7',
    characteristicId: 'FEC8',
    state: true // true: 启用 notify; false: 停用 notify
}, function(result) {
    console.log('特征值通知已启用');
});

// 向低功耗蓝牙设备特征值中写入二进制数据 - uni.writeBLECharacteristicValue
XWebView._callNative('QXBlePlugin', 'writeBLECharacteristicValue', {
    deviceId: 'XX:XX:XX:XX:XX:XX',
    serviceId: 'FEE7',
    characteristicId: 'FEC7',
    value: 'aGVsbG8=', // Base64 编码的二进制数据
    writeType: 'write' // 'write' 或 'writeNoResponse'
}, function(result) {
    console.log('数据写入成功');
});

// 关闭蓝牙适配器 - uni.closeBluetoothAdapter
XWebView._callNative('QXBlePlugin', 'closeBluetoothAdapter', {},
    function(result) { 
        console.log('蓝牙适配器已关闭'); 
    }
);
```

#### 蓝牙事件监听

```javascript
// 监听寻找到新设备的事件 - uni.onBluetoothDeviceFound
// 这个事件会在 startBluetoothDevicesDiscovery 期间自动触发

// 监听蓝牙适配器状态变化事件 - uni.onBluetoothAdapterStateChange
// 返回参数：{ available: boolean, discovering: boolean }

// 监听低功耗蓝牙连接状态的改变事件 - uni.onBLEConnectionStateChange  
// 返回参数：{ deviceId: string, connected: boolean }

// 监听低功耗蓝牙设备的特征值变化事件 - uni.onBLECharacteristicValueChange
// 返回参数：{ deviceId: string, serviceId: string, characteristicId: string, value: ArrayBuffer }
```

#### 错误码说明

| 错误码 | 错误信息 | 说明 |
|--------|----------|------|
| 0 | ok | 正常 |
| 10000 | not init | 未初始化蓝牙适配器 |
| 10001 | not available | 当前蓝牙适配器不可用 |
| 10002 | no device | 没有找到指定设备 |
| 10003 | connection fail | 连接失败 |
| 10004 | no service | 没有找到指定服务 |
| 10005 | no characteristic | 没有找到指定特征值 |
| 10006 | no connection | 当前连接已断开 |
| 10007 | property not support | 当前特征值不支持此操作 |
| 10008 | system error | 其余所有系统上报的异常 |
| 10009 | system not support | Android 系统特有，系统版本低于 4.3 不支持 BLE |
| 10010 | already connect | 已连接 |
| 10011 | need pin | 配对设备需要配对码 |
| 10012 | operate time out | 连接超时 |
| 10013 | invalid_data | 连接 deviceId 为空或者是格式不正确 |

### 生命周期事件监听

Web 页面可以监听容器生命周期事件：

```javascript
// 页面显示
window.addEventListener('ContainerShow', function() {
    console.log('页面显示');
});

// 页面激活（从后台返回）
window.addEventListener('ContainerActive', function() {
    console.log('页面激活');
});

// 页面失活（进入后台）
window.addEventListener('ContainerInactive', function() {
    console.log('页面失活');
});

// 页面隐藏
window.addEventListener('ContainerHide', function() {
    console.log('页面隐藏');
});
```

## 高级用法

### 自定义插件

1. 创建插件类：

```kotlin
import com.jd.jdbridge.base.IBridgePlugin
import com.jd.jdbridge.base.IBridgeCallback
import com.jd.jdbridge.base.IBridgeWebView

class MyCustomPlugin : IBridgePlugin {
    override fun execute(
        webView: IBridgeWebView,
        method: String,
        params: String,
        callback: IBridgeCallback
    ) {
        when (method) {
            "myMethod" -> {
                // 处理业务逻辑
                val result = mapOf("status" to "success")
                callback.onSuccess(result)
            }
            else -> {
                callback.onError("Unknown method: $method")
            }
        }
    }
}
```

2. 注册插件：

```kotlin
import com.jd.jdbridge.JDBridgeManager

// 在初始化 WebView 后
JDBridgeManager.registerPlugin(webView, "MyCustomPlugin", MyCustomPlugin())
```

3. JavaScript 调用：

```javascript
XWebView._callNative('MyCustomPlugin', 'myMethod', {}, 
    function(result) { console.log(result); }
);
```

## 混淆配置

如果使用 ProGuard/R8，添加以下规则到 `proguard-rules.pro`：

```proguard
# QX Hybrid SDK
-keep class com.jd.** { *; }
-keepclassmembers class com.jd.** { *; }

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }

# WebView JavaScript Interface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
```

## 常见问题

### 1. WebView 无法加载 HTTPS 页面

确保已配置 `network_security_config.xml` 并在 AndroidManifest 中引用。

### 2. JavaScript 调用无响应

- 检查是否调用了 `QXBridgePluginRegister.registerAllPlugins(webView)`
- 确认 WebView 已完全加载（在 `onPageFinished` 后调用）
- 检查浏览器控制台是否有 JavaScript 错误

### 3. 定位功能不工作

- 确保已添加高德定位 SDK 依赖
- 检查定位权限是否已授予
- 在高德开放平台申请 Key 并配置到 AndroidManifest

### 4. 扫码功能崩溃

- 确保已添加 ZXing 依赖
- 检查相机权限是否已授予
- 确认 `QRScannerActivity` 已在 AndroidManifest 中注册

## 版本要求

- **minSdkVersion**: 19 (Android 4.4)
- **targetSdkVersion**: 36 (Android 14)
- **Kotlin**: 2.2.0+
- **Java**: 17+

## 技术支持

如有问题，请查看示例项目 `app` 模块中的实现。

## 许可证

[根据你的项目添加许可证信息]
