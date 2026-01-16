package com.jd.plugins.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.StateListDrawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialog

/**
 * 优化后的地图唤起工具类（纯代码实现）
 */
class OpenMapAppUtils private constructor() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var isDialogShowing = false

    companion object {
        private const val TAG = "OpenMapAppUtils"
        private const val PKG_AMAP = "com.autonavi.minimap"
        private const val PKG_BAIDU = "com.baidu.BaiduMap"
        private const val PKG_TENCENT = "com.tencent.map"

        val instance: OpenMapAppUtils by lazy(mode = LazyThreadSafetyMode.SYNCHRONIZED) {
            OpenMapAppUtils()
        }
    }

    /**
     * 核心入口：展示地图选择弹窗
     * @param lat 纬度 (GCJ-02坐标系)
     * @param lng 经度 (GCJ-02坐标系)
     */
    fun showMapSelectSheet(activity: Activity, lat: Double, lng: Double, name: String) {
        if (activity.isFinishing || activity.isDestroyed) return
        mainHandler.post {
            if (isDialogShowing) return@post
            isDialogShowing = true
            try {
                val dialog = BottomSheetDialog(activity)
                val container = createContentView(activity, lat, lng, name, dialog)
                dialog.setContentView(container)
                dialog.setOnDismissListener { isDialogShowing = false }
                dialog.show()
            } catch (e: Exception) {
                isDialogShowing = false
                Log.e(TAG, "Dialog failed", e)
            }
        }
    }

    /**
     * 动态创建 UI 布局
     */
    private fun createContentView(
        context: Context,
        lat: Double,
        lng: Double,
        name: String,
        dialog: BottomSheetDialog
    ): View {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            // 给顶部设一点圆角逻辑（BottomSheet 默认自带圆角背景，这里确保背景干净）
        }

        // 标题
        val titleTv = createItemTextView(context, "选择地图导航", isTitle = true)
        root.addView(titleTv)
        root.addView(createDivider(context))

        // 选项列表数据
        val mapOptions = mutableListOf<Pair<String, () -> Unit>>()

        // 1. 高德
        mapOptions.add("高德地图" to { openAmap(context, lat, lng, name) })
        // 2. 百度
        mapOptions.add("百度地图" to { openBaidu(context, lat, lng, name) })
        // 3. 腾讯
        mapOptions.add("腾讯地图" to { openTencent(context, lat, lng, name) })

        // 遍历添加
        mapOptions.forEach { option ->
            val itemView = createItemTextView(context, option.first)
            itemView.setOnClickListener {
                option.second.invoke()
                dialog.dismiss()
            }
            root.addView(itemView)
            root.addView(createDivider(context))
        }

        // 取消按钮
        val cancelTv = createItemTextView(context, "取消")
        cancelTv.setTextColor(Color.parseColor("#999999"))
        cancelTv.setOnClickListener { dialog.dismiss() }
        root.addView(cancelTv)

        return root
    }

    // --- 各地图唤起逻辑 ---
    private fun openAmap(context: Context, lat: Double, lng: Double, name: String) {
        // 改用 androidamap://navi 协议
        // dev=0: GCJ02坐标, style=2: 自动模式
        val uri = "androidamap://navi?sourceApplication=app_name&poiname=${Uri.encode(name)}&lat=$lat&lon=$lng&dev=0&style=2"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply { setPackage(PKG_AMAP) }

        val webUrl = "https://uri.amap.com/navigation?to=$lng,$lat,${Uri.encode(name)}&mode=car"
        executeIntent(context, intent, webUrl, "高德地图")
    }

    private fun openBaidu(context: Context, lat: Double, lng: Double, name: String) {
        val uri = "baidumap://map/navi?location=$lat,$lng&title=${Uri.encode(name)}&coord_type=gcj02&src=andr.jd.plugin"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply { setPackage(PKG_BAIDU) }

        val webUrl = "http://api.map.baidu.com/direction?destination=latlng:$lat,$lng|name:${Uri.encode(name)}&mode=driving&output=html&coord_type=gcj02"
        executeIntent(context, intent, webUrl, "百度地图")
    }

    private fun openTencent(context: Context, lat: Double, lng: Double, name: String) {
        val encodedName = Uri.encode(name)
        val uri = "qqmap://map/marker?marker=coord:$lat,$lng;title=$encodedName&referer=myapp"

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply { setPackage(PKG_TENCENT) }
        val webUrl = "https://apis.map.qq.com/uri/v1/marker?marker=coord:$lat,$lng;title=$encodedName"
        executeIntent(context, intent, webUrl, "腾讯地图")
    }


    // --- 工具方法 ---
    private fun executeIntent(context: Context, intent: Intent, webUrl: String, mapName: String) {
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val pkg = intent.`package`
            if (pkg != null && isAppInstalled(context, pkg)) {
                context.startActivity(intent)
            } else {
                // 跳转网页
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(webUrl))
                webIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(webIntent)
                showToast(context, "未安装${mapName}，已打开网页版")
            }
        } catch (e: Exception) {
            showToast(context, "唤起失败")
        }
    }

    private fun isAppInstalled(context: Context, pkgName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(pkgName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * 构建通用的列表项 TextView
     */
    private fun createItemTextView(context: Context, text: String, isTitle: Boolean = false): TextView {
        return TextView(context).apply {
            this.text = text
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp2px(context, 55f)
            )
            gravity = Gravity.CENTER
            textSize = if (isTitle) 14f else 16f
            setTextColor(if (isTitle) Color.parseColor("#999999") else Color.parseColor("#333333"))
            if (isTitle) setTypeface(null, Typeface.BOLD)

            // 添加点击水波纹效果（仅限非标题项）
            if (!isTitle) {
                val outValue = TypedValue()
                context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
                setBackgroundResource(outValue.resourceId)
                isClickable = true
                isFocusable = true
            }
        }
    }

    private fun createDivider(context: Context): View {
        return View(context).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1)
            setBackgroundColor(Color.parseColor("#EEEEEE"))
        }
    }

    private fun dp2px(context: Context, dp: Float): Int {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, context.resources.displayMetrics).toInt()
    }

    private fun showToast(context: Context, msg: String) {
        mainHandler.post { Toast.makeText(context.applicationContext, msg, Toast.LENGTH_SHORT).show() }
    }
}