/*
 * MIT License
 *
 * Copyright (c) 2022 JD.com, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */
package com.jd.jdbridge

import android.util.Log
import org.json.JSONException
import org.json.JSONObject

data class Response(
    val status: String,
    val callbackId: String? = null,
    val data: Any? = null,
    /**
     * `msg` 允许是 `String`、`JSONObject` 或 `JSONArray`：
     * - 当 Plugin 通过 `IBridgeCallback.onError(...)` 传递的是结构化 JSON 字符串
     *   （例如 `QXBridgeError.make(...)` 的输出）时，上层会先把它解析成 `JSONObject`
     *   再塞进来，H5 侧拿到的就是一个对象（与 iOS `NSError.userInfo` 对齐），
     *   而不是一段 JSON 字符串，避免 H5 还要再 `JSON.parse(res.message)`。
     */
    val msg: Any? = null,
    val complete: Boolean = true
) {

    override fun toString(): String {
        val jsonObj = JSONObject()
        try {
            jsonObj.put("status", status)
            jsonObj.put("callbackId", callbackId)
            jsonObj.put("data", data)
            jsonObj.put("msg", msg)
            jsonObj.put("complete", complete)
        } catch (e: JSONException) {
            if (JDBridgeManager.webDebug) {
                Log.e("${JDBridgeConstant.MODULE_TAG}-Response", e.message, e)
            }
        }
        return WebUtils.string2JsStr(jsonObj.toString())
    }
}

internal fun JSONObject.toResponse(): Response {
    return Response(
        optString("status", "0"),
        optString("callbackId", "").ifEmpty { null },
        opt("data"),
        opt("msg")?.takeIf { it != JSONObject.NULL },
        optBoolean("complete", true))
}
