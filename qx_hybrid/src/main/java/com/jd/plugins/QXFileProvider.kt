package com.jd.plugins

import androidx.core.content.FileProvider

/**
 * SDK 专用 FileProvider 子类。
 *
 * 用独立子类（而非直接用 androidx.core.content.FileProvider）是为了避免与接入方 App 已声明的
 * FileProvider 在 manifest 合并时因 android:name 相同而冲突。接入方无需任何配置。
 */
class QXFileProvider : FileProvider()
