# Android & iOS BLE 插件返回参数对齐对照表

## 文件结构对比

| iOS | Android | 说明 |
|-----|---------|------|
| `QXBleDefine.swift` | `QXBleDefine.kt` | ✅ 常量定义文件 |
| `QXBlePlugin.swift` | `QXBlePlugin.kt` | ✅ 插件主类 |
| `QXBleCentralManager.swift` | - | iOS特有（中央管理器） |
| `QXBlePeripheralManager.swift` | - | iOS特有（外设管理器） |

## 回调格式对比

### 统一格式
```json
{
  "code": 0,           // iOS & Android 一致
  "message": "提示信息", // iOS & Android 一致
  "data": {}           // iOS & Android 一致
}
```

### 回调方法对比

| 场景 | iOS | Android | 状态 |
|------|-----|---------|------|
| 成功回调 | `callback.onSuccess(result)` | `callback.onSuccess(result)` | ✅ 一致 |
| 失败回调 | `callback.onFail(result)` | `callback.onFail(result)` | ✅ 一致 |

## API 返回参数对比

### 1. openBluetoothAdapter（初始化蓝牙）

#### iOS
```swift
// 成功
{
  "code": 0,
  "message": "蓝牙初始化成功",
  "data": {}
}
```

#### Android
```kotlin
// 成功
{
  "code": 0,
  "message": "蓝牙初始化成功",
  "data": {}
}
```

**状态：** ✅ 完全一致

---

### 2. startBluetoothDevicesDiscovery（开始扫描）

#### iOS
```swift
// 立即返回
{
  "code": 0,
  "message": "开始扫描蓝牙设备",
  "data": {}
}
```

#### Android
```kotlin
// 立即返回
{
  "code": 0,
  "message": "开始扫描蓝牙设备",
  "data": {}
}
```

**状态：** ✅ 完全一致

---

### 3. onBluetoothDeviceFound（设备发现事件）

#### iOS
```swift
{
  "name": "设备名称",
  "rssi": -50,
  "deviceId": "UUID",
  "eventName": "onBluetoothDeviceFound"
}
```

#### Android（修改后）
```kotlin
{
  "name": "设备名称",
  "rssi": -50,
  "deviceId": "MAC地址",
  "eventName": "onBluetoothDeviceFound"
}
```

**状态：** ✅ 字段一致（deviceId格式不同：iOS用UUID，Android用MAC地址）

---

### 4. createBLEConnection（连接设备）

#### iOS
```swift
{
  "code": 0,
  "message": "设备连接成功",
  "data": {
    "deviceId": "UUID",
    "name": "设备名称"
  }
}
```

#### Android（修改后）
```kotlin
{
  "code": 0,
  "message": "设备连接成功",
  "data": {
    "deviceId": "MAC地址",
    "name": "设备名称"
  }
}
```

**状态：** ✅ 完全一致

---

### 5. getBLEDeviceServices（获取服务列表）

#### iOS
```swift
{
  "code": 0,
  "message": "发现服务成功，共2个服务",
  "data": {
    "services": [
      {
        "serviceId": "0000FF00-...",
        "isPrimary": true
      }
    ]
  }
}
```

#### Android（修改后）
```kotlin
{
  "code": 0,
  "message": "发现服务成功，共2个服务",
  "data": {
    "services": [
      {
        "serviceId": "0000ff00-...",
        "isPrimary": true
      }
    ]
  }
}
```

**状态：** ✅ 完全一致

---

### 6. getBLEDeviceCharacteristics（获取特征列表）

#### iOS
```swift
{
  "code": 0,
  "message": "获取特征成功，共5个特征",
  "data": {
    "characteristics": [
      {
        "serviceId": "0000FF00-...",
        "characteristicId": "0000FF01-...",
        "properties": ["read", "write", "notify"],
        "isNotifying": false
      }
    ]
  }
}
```

#### Android（修改后）
```kotlin
{
  "code": 0,
  "message": "获取特征成功，共5个特征",
  "data": {
    "characteristics": [
      {
        "serviceId": "0000ff00-...",
        "characteristicId": "0000ff01-...",
        "properties": ["read", "write", "notify"],
        "isNotifying": false
      }
    ]
  }
}
```

**状态：** ✅ 完全一致

---

### 7. writeBLECharacteristicValue（写入特征值）

#### iOS
```swift
{
  "code": 0,
  "message": "写入特征值成功",
  "data": {
    "characteristicId": "0000FF01-...",
    "value": "SGVsbG8="  // Base64
  }
}
```

#### Android（修改后）
```kotlin
{
  "code": 0,
  "message": "写入特征值成功",
  "data": {
    "characteristicId": "0000ff01-...",
    "value": "SGVsbG8="  // Base64
  }
}
```

**状态：** ✅ 完全一致

---

### 8. notifyBLECharacteristicValueChange（设置通知）

#### iOS
```swift
{
  "code": 0,
  "message": "通知已启用",
  "data": {
    "characteristicId": "0000FF01-...",
    "enabled": true
  }
}
```

#### Android（修改后）
```kotlin
{
  "code": 0,
  "message": "通知已启用",
  "data": {
    "characteristicId": "0000ff01-...",
    "enabled": true
  }
}
```

**状态：** ✅ 完全一致

---

### 9. getBluetoothDevices（获取已发现设备）

#### iOS
```swift
{
  "code": 0,
  "message": "获取蓝牙设备列表成功",
  "data": {
    "devices": [
      {
        "name": "设备名称",
        "rssi": -50,
        "deviceId": "UUID"
      }
    ]
  }
}
```

#### Android（修改后）
```kotlin
{
  "code": 0,
  "message": "获取已发现设备成功",
  "data": {
    "devices": [
      {
        "name": "设备名称",
        "rssi": -50,
        "deviceId": "MAC地址"
      }
    ]
  }
}
```

**状态：** ✅ 字段一致（message略有差异，不影响使用）

---

### 10. getBluetoothAdapterState（获取蓝牙状态）

#### iOS
```swift
{
  "code": 0,
  "message": "获取蓝牙适配器状态成功",
  "data": {
    "available": true,
    "discovering": false
  }
}
```

#### Android
```kotlin
{
  "code": 0,
  "message": "获取蓝牙适配器状态成功",
  "data": {
    "available": true,
    "discovering": false
  }
}
```

**状态：** ✅ 完全一致

---

## 错误码对比

| 错误码 | iOS | Android | 说明 |
|--------|-----|---------|------|
| 0 | SUCCESS | SUCCESS | ✅ 操作成功 |
| 10000 | notInit | NOT_INIT | ✅ 未初始化 |
| 10001 | notAvailable | NOT_AVAILABLE | ✅ 不可用 |
| 10002 | noDevice | NO_DEVICE | ✅ 未找到设备 |
| 10003 | connectionFail | CONNECTION_FAIL | ✅ 连接失败 |
| 10004 | noService | NO_SERVICE | ✅ 未找到服务 |
| 10005 | noCharacteristic | NO_CHARACTERISTIC | ✅ 未找到特征 |
| 10006 | noConnection | NO_CONNECTION | ✅ 连接已断开 |
| 10007 | propertyNotSupport | PROPERTY_NOT_SUPPORT | ✅ 不支持操作 |
| 10008 | systemError | SYSTEM_ERROR | ✅ 系统错误 |
| 10009 | systemNotSupport | SYSTEM_NOT_SUPPORT | ✅ 系统不支持 |
| 10010 | alreadyConnect | ALREADY_CONNECT | ✅ 已连接 |
| 10011 | needPin | NEED_PIN | ✅ 需要配对码 |
| 10012 | operateTimeOut | OPERATE_TIME_OUT | ✅ 操作超时 |
| 10013 | invalidData | INVALID_DATA | ✅ 数据无效 |
| -1 | bluetoothNotOpen | BLUETOOTH_NOT_OPEN | ✅ 蓝牙未开启 |
| -2 | permissionDenied | PERMISSION_DENIED | ✅ 权限被拒绝 |
| -3 | deviceNotFound | DEVICE_NOT_FOUND | ✅ 设备未找到 |
| -4 | connectTimeout | CONNECT_TIMEOUT | ✅ 连接超时 |
| -5 | characteristicNotFound | CHARACTERISTIC_NOT_FOUND | ✅ 特征未找到 |
| -6 | writeNotSupported | WRITE_NOT_SUPPORTED | ✅ 不支持写入 |
| -7 | permissionNotDetermined | PERMISSION_NOT_DETERMINED | ✅ 权限未确定 |
| -8 | scanNotAvailable | SCAN_NOT_AVAILABLE | ✅ 扫描不可用 |
| -9 | peripheralNil | PERIPHERAL_NIL | ✅ 外设为空 |
| -99 | unknownError | UNKNOWN_ERROR | ✅ 未知错误 |

**状态：** ✅ 完全一致

---

## 特征属性对比

| 属性 | iOS | Android | 状态 |
|------|-----|---------|------|
| 读 | "read" | "read" | ✅ |
| 写 | "write" | "write" | ✅ |
| 无响应写 | "writeWithoutResponse" | "writeWithoutResponse" | ✅ |
| 通知 | "notify" | "notify" | ✅ |
| 指示 | "indicate" | "indicate" | ✅ |
| 广播 | "broadcast" | "broadcast" | ✅ |
| 签名写 | "authenticatedSignedWrites" | "authenticatedSignedWrites" | ✅ |
| 扩展属性 | "extendedProperties" | "extendedProperties" | ✅ |

**状态：** ✅ 完全一致

---

## 总结

### ✅ 已对齐项目
1. 回调格式统一为 `{code, message, data}`
2. 成功使用 `onSuccess()`，失败使用 `onFail()`
3. 所有API返回参数字段名称一致
4. 错误码完全一致（uni-app标准 + 自定义扩展）
5. 特征属性名称完全一致
6. 文件结构对齐（QXBleDefine + QXBlePlugin）

### ⚠️ 平台差异（不可避免）
1. **deviceId格式**
   - iOS: UUID格式（如 `12345678-1234-1234-1234-123456789ABC`）
   - Android: MAC地址格式（如 `AA:BB:CC:DD:EE:FF`）
   - 原因：系统API限制

2. **UUID大小写**
   - iOS: 大写（如 `0000FF00-...`）
   - Android: 小写（如 `0000ff00-...`）
   - 影响：前端需要统一转换为小写比较

### 📝 前端适配建议
```javascript
// 统一处理 deviceId 和 UUID
function normalizeUUID(uuid) {
  return uuid.toLowerCase();
}

// 比较 UUID 时忽略大小写
function isSameUUID(uuid1, uuid2) {
  return normalizeUUID(uuid1) === normalizeUUID(uuid2);
}
```

---

**对齐完成时间：** 2025/01/23  
**对齐状态：** ✅ 100% 对齐（除平台固有差异）
