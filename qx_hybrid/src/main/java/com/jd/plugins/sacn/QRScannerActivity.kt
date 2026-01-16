package com.jd.plugins.sacn

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import androidx.appcompat.R
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import com.google.zxing.ResultPoint
import com.jd.plugins.ClosureRegistry
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.CaptureManager
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import org.json.JSONObject

/**
 * 二维码扫描Activity
 */
class QRScannerActivity : AppCompatActivity() {
    private lateinit var barcodeView: DecoratedBarcodeView
    private var captureManager: CaptureManager? = null
    private var torchState = false
    private var callbackId: String = "scanQRCode"
    private var permissionDialog: AlertDialog? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    companion object {
        const val NAME = "QRScannerActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 沉浸式全屏 + 保持屏幕常亮
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        // 解析参数（容错处理）
        intent.getStringExtra("params")?.let { params ->
            try {
                val jsonParams = JSONObject(params)
                callbackId = jsonParams.optString("callbackId", "scanQRCode")
                Log.d(NAME, "扫描参数解析成功: callbackId=$callbackId")
            } catch (e: Exception) {
                Log.e(NAME, "解析参数失败", e)
            }
        }
        // 检查相机权限
        if (checkCameraPermission()) {
            initScanner(savedInstanceState)
        } else {
            handlePermissionDenied("相机权限未开启")
            // 触发系统返回
            onBackPressedDispatcher.onBackPressed()
        }
    }

    /**
     * 检查相机权限
     */
    private fun checkCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * 处理权限拒绝（主线程执行回调）
     */
    private fun handlePermissionDenied(errorMsg: String) {
        mainHandler.post {
            try {
                val callbackObj = ClosureRegistry.take(callbackId)
                callbackObj?.onError(errorMsg) ?: Log.w(NAME, "回调对象为空: callbackId=$callbackId")
            } catch (e: Exception) {
                Log.e(NAME, "权限拒绝回调失败", e)
            }
        }
    }

    /**
     * 初始化扫描器
     */
    private fun initScanner(savedInstanceState: Bundle?) {
        try {
            setContentView(createScannerLayout())

            // 初始化CaptureManager（仅用于生命周期管理，不处理解码）
            captureManager = CaptureManager(this, barcodeView)
            captureManager?.initializeFromIntent(intent, savedInstanceState)
            // 核心修复：decodeContinuous是barcodeView的方法，不是captureManager的
            barcodeView.decodeContinuous(barcodeCallback)
            captureManager?.onResume() // 启动相机预览
            barcodeView.resume()
            Log.d(NAME, "扫描器初始化成功")
        } catch (e: Exception) {
            Log.e(NAME, "扫描器初始化失败", e)
            handlePermissionDenied("扫描器初始化失败：${e.message}")
            finish()
        }
    }

    /**
     * 扫描回调
     */
    private val barcodeCallback = object : BarcodeCallback {
        override fun barcodeResult(result: BarcodeResult) {
            // 扫描成功： 返回结果
            returnScanResult(result.text)
        }
        override fun possibleResultPoints(resultPoints: List<ResultPoint>) {}
    }

    /**
     * 创建扫描布局
     */
    private fun createScannerLayout(): View {
        val rootLayout = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        // 初始化扫描视图
        barcodeView = DecoratedBarcodeView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setStatusText("") // 隐藏默认提示文字
        }
        rootLayout.addView(barcodeView)

        // 返回按钮
        rootLayout.addView(createBackButton())

        // 闪光灯按钮
        rootLayout.addView(createTorchButton())
        return rootLayout
    }

    /**
     * 创建返回按钮
     */
    private fun createBackButton(): Button {
        return Button(this).apply {
            val backIcon = AppCompatResources.getDrawable(this@QRScannerActivity, R.drawable.abc_ic_ab_back_material)
            backIcon?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    it.setTint(Color.WHITE)
                }
                it.setBounds(0, 0, it.intrinsicWidth, it.intrinsicHeight)
                setCompoundDrawables(it, null, null, null)
            }
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.TRANSPARENT)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(20, 50, 20, 20)
                gravity = Gravity.TOP or Gravity.START
            }
            setOnClickListener {
                onBackPressedDispatcher.onBackPressed()
            }
        }
    }

    /**
     * 创建闪光灯按钮
     */
    private fun createTorchButton(): Button {
        return Button(this).apply {
            text = "闪光灯"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.TRANSPARENT)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(20, 50, 20, 20)
                gravity = Gravity.TOP or Gravity.END
            }
            setOnClickListener {
                try {
                    toggleTorch()
                } catch (e: Exception) {
                    Log.e(NAME, "切换闪光灯失败", e)
                }
            }
        }
    }

    /**
     * 切换闪光灯
     */
    private fun toggleTorch() {
        torchState = !torchState
        if (torchState) {
            barcodeView.setTorchOn()
        } else {
            barcodeView.setTorchOff()
        }
    }

    /**
     * 返回扫描结果
     */
    private fun returnScanResult(result: String?) {
        mainHandler.post {
            try {
                val callbackObj = ClosureRegistry.take(callbackId)
                if (!result.isNullOrEmpty()) {
                    val responseJson = JSONObject().apply {
                        put("data", result)
                        put("success", true)
                    }
                    callbackObj?.onSuccess(responseJson.toString())
                } else {
                    callbackObj?.onError("扫描结果为空")
                }
            } catch (e: Exception) {
                Log.e(NAME, "返回扫描结果失败", e)
            } finally {
                finish()
            }
        }
    }

    // ========== 生命周期管理 ==========
    override fun onResume() {
        super.onResume()
        captureManager?.onResume()
        barcodeView.takeIf { ::barcodeView.isInitialized }?.resume()
    }

    override fun onPause() {
        super.onPause()
        captureManager?.onPause()
        barcodeView.takeIf { ::barcodeView.isInitialized }?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        captureManager?.onDestroy()
        permissionDialog?.dismiss()
        mainHandler.removeCallbacksAndMessages(null)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        captureManager?.onSaveInstanceState(outState)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return barcodeView.takeIf { ::barcodeView.isInitialized }?.onKeyDown(keyCode, event)
            ?: super.onKeyDown(keyCode, event)
    }

    /**
     * 适配API 33+ onBackPressed废弃
     */
    override fun onBackPressed() {
        try {
            ClosureRegistry.remove(callbackId)
        } catch (e: Exception) {
            Log.e(NAME, "返回时清理资源失败", e)
        }
        super.onBackPressed()
    }

    // 添加onWindowFocusChanged方法以支持侧滑返回
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // 保持全屏但启用系统手势
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        }
    }
}