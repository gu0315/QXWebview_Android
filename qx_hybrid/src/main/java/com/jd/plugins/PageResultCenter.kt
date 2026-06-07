package com.jd.plugins

import android.os.Handler
import android.os.Looper
import com.jd.jdbridge.base.IBridgeCallback
import org.json.JSONObject

/**
 * 跨 WebView 回传：挂起回调中心（SDK 内部）。
 *
 * openWebViewForResult 打开新页面时把 callback 挂起；B 调 closeWithResult（或被返回）时回传。
 * 用 pageId 索引，支持 A→B→C 多层嵌套互不串扰。
 */
object PageResultCenter {

    /** B 容器 Intent 里携带的回传 id */
    const val EXTRA_PAGE_ID = "extra_page_id"

    private val callbacks = HashMap<String, IBridgeCallback>()
    private val mainHandler = Handler(Looper.getMainLooper())

    @Synchronized
    fun suspend(id: String, callback: IBridgeCallback) {
        callbacks[id] = callback
    }

    @Synchronized
    private fun take(id: String): IBridgeCallback? = callbacks.remove(id)

    /** 不回调、仅丢弃（打开失败时清理） */
    @Synchronized
    fun discard(id: String) {
        callbacks.remove(id)
    }

    /** B 主动回传 */
    fun resolve(id: String, data: Any?) {
        val callback = take(id) ?: return
        runOnMain { callback.onSuccess(data) }
    }

    /** 被返回（未回传）的取消兜底 */
    fun cancel(id: String) {
        val callback = take(id) ?: return
        runOnMain { callback.onSuccess(JSONObject().apply { put("cancelled", true) }) }
    }

    private fun runOnMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            mainHandler.post(action)
        }
    }
}
