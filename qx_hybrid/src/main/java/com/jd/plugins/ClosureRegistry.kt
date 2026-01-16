package com.jd.plugins

import com.jd.jdbridge.base.IBridgeCallback
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 闭包回调注册器（单例）
 * 核心作用：管理异步操作（如二维码扫描）的闭包回调，支持自定义/自动生成回调ID，保证多线程安全
 * 设计特点：
 * 1. 线程安全：基于ConcurrentHashMap实现，适配多线程场景
 * 2. 灵活ID：支持自定义回调ID，也可自动生成UUID
 * 3. 自动清理：执行/取消回调后自动移除，避免内存泄漏
 */
object ClosureRegistry {
    /**
     * 线程安全的映射表：存储「回调ID - 闭包」关系
     * Key：自定义/自动生成的唯一回调ID
     * Value：接收String?类型结果的闭包（null表示操作取消/失败）
     */
    private val map = ConcurrentHashMap<String, IBridgeCallback?>()

    /**
     * 注册闭包回调（支持自定义ID，无则自动生成UUID）
     * @param closure 待注册的闭包，参数为异步操作结果（null=取消/失败）
     * @param callbackId 自定义回调ID（可选），传null/空字符串则自动生成UUID
     * @return 最终绑定的回调ID（自定义ID有效则用自定义，否则用UUID）
     */
    fun register(callbackId: String, closure: IBridgeCallback?): String {
        var finalCallbackId = callbackId
        if (callbackId.isBlank()) {
            finalCallbackId = UUID.randomUUID().toString()
        }
        map[finalCallbackId] = closure
        return finalCallbackId
    }
    

    /**
     * 根据回调ID执行闭包，并执行后移除（避免重复调用）
     * @param id 注册时返回的回调ID
     * @param result 异步操作结果（如扫描结果），null表示无结果/操作失败
     */
    fun invoke(id: String, result: String?) {
        // 先移除再执行，防止多线程下重复触发
        map.remove(id)?.onSuccess(result)
    }

    /**
     * 根据回调ID取消闭包（从映射表中移除，避免内存泄漏）
     * 适用场景：操作取消、Activity销毁时清理未执行的闭包
     * @param id 注册时返回的回调ID
     */
    fun take(id: String) : IBridgeCallback? {
        return map.remove(id)
    }

    /**
     * 根据回调ID移除闭包（手动触发后调用）
     * @param id 注册时返回的回调ID
     */
    fun remove(id: String) {
        map.remove(id)
    }
    
    /**
     * 根据回调ID获取闭包（用于手动触发）
     * @param id 注册时返回的回调ID
     * @return 对应ID的闭包，null表示ID不存在或已被移除
     */
    fun get(id: String): IBridgeCallback? {
        return map[id]
    }

    /**
     * 清空所有闭包（插件销毁/应用退出时调用）
     */
    fun clearAll() {
        map.clear()
    }
}