package com.jd.plugins

import com.jd.jdbridge.base.IBridgeCallback
import com.jd.jdbridge.base.IBridgePlugin
import com.jd.jdbridge.base.IBridgeWebView
import com.jd.jdbridge.base.callJS
import org.json.JSONObject
import java.util.Collections
import java.util.WeakHashMap

class QXLifecyclePlugin : IBridgePlugin {

    companion object {
        const val NAME = "QXLifecyclePlugin"
        private const val EVENT_NAME = "onPageLifecycle"

        private val lifecycleLock = Any()
        private val subscribedWebViews: MutableSet<IBridgeWebView> =
            Collections.newSetFromMap(WeakHashMap<IBridgeWebView, Boolean>())
        private val latestStates: MutableMap<IBridgeWebView, JSONObject> = WeakHashMap()

        fun dispatchPageLifecycle(webView: IBridgeWebView?, type: String, nativeType: String) {
            webView ?: return
            val state = buildState(webView, type, nativeType)
            val isSubscribed = synchronized(lifecycleLock) {
                latestStates[webView] = state
                subscribedWebViews.contains(webView)
            }
            if (!isSubscribed) {
                return
            }
            webView.callJS(NAME, state, object : IBridgeCallback {
                override fun onSuccess(result: Any?) = Unit
            })
        }

        fun clear(webView: IBridgeWebView?) {
            webView ?: return
            synchronized(lifecycleLock) {
                subscribedWebViews.remove(webView)
                latestStates.remove(webView)
            }
        }

        private fun buildState(webView: IBridgeWebView, type: String, nativeType: String): JSONObject {
            return JSONObject().apply {
                put("eventName", EVENT_NAME)
                put("type", type)
                put("nativeType", nativeType)
                put("timestamp", System.currentTimeMillis())
                put("url", webView.getUrl().orEmpty())
                put("isForeground", type == "pageWillShow" || type == "pageShow")
            }
        }

        private fun currentState(webView: IBridgeWebView): JSONObject {
            return synchronized(lifecycleLock) {
                latestStates[webView]
            } ?: buildState(webView, "unknown", "unknown")
        }
    }

    override fun execute(
        webView: IBridgeWebView?,
        method: String?,
        params: String?,
        callback: IBridgeCallback?
    ): Boolean {
        val targetWebView = webView ?: run {
            callback?.onError(QXBridgeError.notFound("WebView 不存在"))
            return true
        }
        when (method) {
            "subscribePageLifecycle", "subscribe" -> {
                synchronized(lifecycleLock) {
                    subscribedWebViews.add(targetWebView)
                }
                callback?.onSuccess(
                    JSONObject().apply {
                        put("subscribed", true)
                        put("state", currentState(targetWebView))
                    }
                )
            }
            "unsubscribePageLifecycle", "unsubscribe" -> {
                synchronized(lifecycleLock) {
                    subscribedWebViews.remove(targetWebView)
                }
                callback?.onSuccess(JSONObject().apply { put("subscribed", false) })
            }
            "getPageLifecycleState" -> {
                callback?.onSuccess(currentState(targetWebView))
            }
            else -> {
                callback?.onError(QXBridgeError.unsupported("未知生命周期操作: ${method.orEmpty()}"))
            }
        }
        return true
    }
}
