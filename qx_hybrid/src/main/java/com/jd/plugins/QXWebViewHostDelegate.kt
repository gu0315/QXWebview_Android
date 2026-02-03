package com.jd.plugins

/**
 * QXWebView 宿主 APP 回调协议
 * 
 * SDK 对外暴露的接口，宿主 APP 需要实现此接口来处理 SDK 的回调
 * 
 * 功能说明：
 * - 提供 H5 调用宿主 APP 功能的桥接能力
 * - 支持打开宿主 APP 页面
 * - 支持调用宿主 APP 自定义方法
 * 
 * 作者：顾钱想
 * 日期：2025/01/29
 * 版本：1.0.0
 */
interface QXWebViewHostDelegate {
    
    /**
     * SDK 请求打开宿主 APP 的页面
     * 
     * @param url 页面路由/URL
     * @param params 页面参数（可选）
     * @param completion 执行结果回调，返回任意数据给 H5
     */
    fun webViewRequestOpenPage(
        url: String,
        params: Map<*, *>?,
        completion: (Any?) -> Unit
    )
    
    /**
     * SDK 请求调用宿主 APP 的自定义方法
     * 
     * @param methodName 方法名
     * @param params 方法参数（可选）
     * @param completion 执行结果回调，返回任意数据给 H5
     */
    fun webViewRequestCustomMethod(
        methodName: String,
        params: Map<String, Any?>?,
        completion: (Any?) -> Unit
    )
}
