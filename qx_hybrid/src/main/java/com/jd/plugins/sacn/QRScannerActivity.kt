package com.jd.plugins.sacn

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.R as AppCompatR
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.exifinterface.media.ExifInterface
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.ResultPoint
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import com.jd.plugins.ClosureRegistry
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.CaptureManager
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.journeyapps.barcodescanner.DefaultDecoderFactory
import com.journeyapps.barcodescanner.Size
import com.energy.qx_hybrid.R as QxR
import org.json.JSONObject

class QRScannerActivity : AppCompatActivity() {

    private lateinit var decoratedBarcodeView: DecoratedBarcodeView
    private lateinit var rootLayout: FrameLayout
    private lateinit var chromeOverlay: QrScanChromeOverlay
    private var captureManager: CaptureManager? = null
    private var torchState = false
    private var callbackId: String = "scanQRCode"
    private var permissionDialog: AlertDialog? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var backButton: ImageButton
    private lateinit var torchButton: ImageButton
    private lateinit var albumButton: ImageButton
    private lateinit var tipView: TextView

    private var insetTop = 0
    private var insetBottom = 0

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            val bmp = loadBitmapFromUri(uri)
            if (bmp == null) {
                Toast.makeText(this, "无法读取图片", Toast.LENGTH_SHORT).show()
                return@registerForActivityResult
            }
            try {
                val text = decodeQrFromBitmap(bmp)
                if (!text.isNullOrEmpty()) {
                    returnScanResult(text)
                } else {
                    Toast.makeText(this, "未识别到二维码", Toast.LENGTH_SHORT).show()
                }
            } finally {
                if (!bmp.isRecycled) bmp.recycle()
            }
        } catch (e: Exception) {
            Log.e(NAME, "相册解码失败", e)
            Toast.makeText(this, "图片识别失败", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        const val NAME = "QRScannerActivity"
        private const val SCAN_REGION_RATIO = 0.65f
        private const val MASK_ALPHA = 153 // 0.6 * 255
        private const val BOTTOM_BUTTON_DP = 56
        private const val BOTTOM_MARGIN_DP = 30f
        private const val SIDE_INSET_DP = 40f
        private const val TIP_ABOVE_BUTTON_DP = 30f
        private const val BACK_SIZE_DP = 44f
        private const val BACK_TOP_EXTRA_DP = 15f
        private const val BACK_START_DP = 16f
        /** 相册大图先按边长采样，避免 OOM；与解码多尺度配合 */
        private const val MAX_BITMAP_SIDE = 2400
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
        )

        intent.getStringExtra("params")?.let { params ->
            try {
                val jsonParams = JSONObject(params)
                callbackId = jsonParams.optString("callbackId", "scanQRCode")
                Log.d(NAME, "扫描参数解析成功: callbackId=$callbackId")
            } catch (e: Exception) {
                Log.e(NAME, "解析参数失败", e)
            }
        }
        intent.getStringExtra("callbackId")?.takeIf { it.isNotBlank() }?.let {
            callbackId = it
        }

        if (checkCameraPermission()) {
            initScanner(savedInstanceState)
        } else {
            handlePermissionDenied(ScanQrBridge.failJson("没有相机权限"))
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun checkCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED
    }

    /** [payload] 为与 iOS 一致的 JSON 字符串（含 message + success） */
    private fun handlePermissionDenied(payload: String) {
        mainHandler.post {
            try {
                val callbackObj = ClosureRegistry.take(callbackId)
                callbackObj?.onError(payload) ?: Log.w(NAME, "回调对象为空: callbackId=$callbackId")
            } catch (e: Exception) {
                Log.e(NAME, "权限拒绝回调失败", e)
            }
        }
    }

    private fun initScanner(savedInstanceState: Bundle?) {
        try {
            setContentView(createScannerLayout())
            applyInsetsAndLayoutChrome()

            captureManager = CaptureManager(this, decoratedBarcodeView)
            captureManager?.initializeFromIntent(intent, savedInstanceState)

            decoratedBarcodeView.barcodeView.decoderFactory =
                DefaultDecoderFactory(listOf(BarcodeFormat.QR_CODE))

            decoratedBarcodeView.viewFinder.setMaskColor(Color.argb(MASK_ALPHA, 0, 0, 0))
            decoratedBarcodeView.viewFinder.setLaserVisibility(false)
            decoratedBarcodeView.statusView?.visibility = View.GONE
            decoratedBarcodeView.setStatusText("")

            chromeOverlay.bind(decoratedBarcodeView.barcodeView)

            decoratedBarcodeView.post {
                val w = decoratedBarcodeView.width
                if (w > 0) {
                    val side = (w * SCAN_REGION_RATIO).toInt()
                    decoratedBarcodeView.barcodeView.setFramingRectSize(Size(side, side))
                }
            }

            decoratedBarcodeView.decodeContinuous(barcodeCallback)
            captureManager?.onResume()
            decoratedBarcodeView.resume()
            Log.d(NAME, "扫描器初始化成功")
        } catch (e: Exception) {
            Log.e(NAME, "扫描器初始化失败", e)
            handlePermissionDenied(ScanQrBridge.failJson("未知错误"))
            finish()
        }
    }

    private val barcodeCallback = object : BarcodeCallback {
        override fun barcodeResult(result: BarcodeResult) {
            returnScanResult(result.text)
        }

        override fun possibleResultPoints(resultPoints: List<ResultPoint>) {}
    }

    private fun createScannerLayout(): View {
        rootLayout = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            )
            setBackgroundColor(Color.BLACK)
        }

        decoratedBarcodeView = DecoratedBarcodeView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            )
            setStatusText("")
        }
        rootLayout.addView(decoratedBarcodeView)

        chromeOverlay = QrScanChromeOverlay(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            )
            isClickable = false
        }
        rootLayout.addView(chromeOverlay)

        tipView = TextView(this).apply {
            text = "将二维码放入框内，即可自动扫描"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            }
        }
        rootLayout.addView(tipView)

        val buttonSize = dp(BOTTOM_BUTTON_DP).toInt()

        torchButton = createCircleIconButton(
            drawableRes = QxR.drawable.ic_qx_flashlight_off,
        ) {
            toggleTorch()
        }
        torchButton.layoutParams = FrameLayout.LayoutParams(buttonSize, buttonSize).apply {
            gravity = Gravity.BOTTOM or Gravity.START
        }
        rootLayout.addView(torchButton)

        albumButton = createCircleIconButton(
            drawableRes = QxR.drawable.ic_qx_photo,
        ) {
            pickImageLauncher.launch("image/*")
        }
        albumButton.layoutParams = FrameLayout.LayoutParams(buttonSize, buttonSize).apply {
            gravity = Gravity.BOTTOM or Gravity.END
        }
        rootLayout.addView(albumButton)

        backButton = ImageButton(this).apply {
            val backIcon = AppCompatResources.getDrawable(this@QRScannerActivity, AppCompatR.drawable.abc_ic_ab_back_material)
            backIcon?.let {
                it.setTint(Color.WHITE)
                setImageDrawable(it)
            }
            setBackgroundColor(Color.TRANSPARENT)
            contentDescription = "返回"
            setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        }
        backButton.layoutParams = FrameLayout.LayoutParams(
            dp(BACK_SIZE_DP).toInt(),
            dp(BACK_SIZE_DP).toInt(),
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
        rootLayout.addView(backButton)

        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { _, windowInsets ->
            val bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            insetTop = bars.top
            insetBottom = bars.bottom
            applyInsetsAndLayoutChrome()
            windowInsets
        }
        ViewCompat.requestApplyInsets(rootLayout)

        return rootLayout
    }

    private fun applyInsetsAndLayoutChrome() {
        if (!::rootLayout.isInitialized) return

        val sideInset = dp(SIDE_INSET_DP).toInt()
        val marginBottom = (dp(BOTTOM_MARGIN_DP) + insetBottom).toInt()
        val tipBottomAbove = (dp(TIP_ABOVE_BUTTON_DP) + dp(BOTTOM_BUTTON_DP) + marginBottom).toInt()

        (tipView.layoutParams as FrameLayout.LayoutParams).apply {
            width = FrameLayout.LayoutParams.MATCH_PARENT
            height = FrameLayout.LayoutParams.WRAP_CONTENT
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            bottomMargin = tipBottomAbove
            marginStart = dp(20f).toInt()
            marginEnd = dp(20f).toInt()
        }
        tipView.requestLayout()

        (torchButton.layoutParams as FrameLayout.LayoutParams).apply {
            marginStart = sideInset
            bottomMargin = marginBottom
        }
        (albumButton.layoutParams as FrameLayout.LayoutParams).apply {
            marginEnd = sideInset
            bottomMargin = marginBottom
        }

        (backButton.layoutParams as FrameLayout.LayoutParams).apply {
            marginStart = dp(BACK_START_DP).toInt()
            topMargin = (dp(BACK_TOP_EXTRA_DP) + insetTop).toInt()
        }

        torchButton.requestLayout()
        albumButton.requestLayout()
        backButton.requestLayout()
    }

    private fun createCircleIconButton(
        drawableRes: Int,
        onClick: () -> Unit,
    ): ImageButton {
        return ImageButton(this).apply {
            setImageResource(drawableRes)
            setBackgroundResource(QxR.drawable.qx_scan_circle_button_bg)
            scaleType = android.widget.ImageView.ScaleType.CENTER
            setColorFilter(Color.WHITE)
            setOnClickListener { onClick() }
        }
    }

    private fun dp(v: Float): Float = v * resources.displayMetrics.density

    private fun dp(v: Int): Float = v * resources.displayMetrics.density

    private fun toggleTorch() {
        torchState = !torchState
        try {
            if (torchState) {
                decoratedBarcodeView.setTorchOn()
            } else {
                decoratedBarcodeView.setTorchOff()
            }
        } catch (e: Exception) {
            Log.e(NAME, "切换闪光灯失败", e)
        }
        torchButton.setImageResource(
            if (torchState) {
                QxR.drawable.ic_qx_flashlight_on
            } else {
                QxR.drawable.ic_qx_flashlight_off
            },
        )
    }

    private fun closeTorch() {
        if (!torchState) return
        torchState = false
        try {
            decoratedBarcodeView.setTorchOff()
        } catch (_: Exception) {}
        torchButton.setImageResource(QxR.drawable.ic_qx_flashlight_off)
    }

    private fun vibrateShort() {
        val v = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(40)
            }
        } catch (_: Exception) {}
    }

    /**
     * 按 EXIF 校正方向 + 合理采样后解码；多尺度、多二值化策略（含反色），提高相册成功率。
     */
    private fun loadBitmapFromUri(uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val opts = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, MAX_BITMAP_SIDE)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: return null
        return applyExifRotation(uri, decoded)
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxSide: Int): Int {
        var inSampleSize = 1
        val maxDim = maxOf(width, height)
        while (maxDim / (inSampleSize * 2) > maxSide) {
            inSampleSize *= 2
        }
        return inSampleSize.coerceAtLeast(1)
    }

    private fun applyExifRotation(uri: Uri, bitmap: Bitmap): Bitmap {
        val rotation = contentResolver.openInputStream(uri)?.use { ins ->
            val exif = ExifInterface(ins)
            when (
                exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            ) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
        if (rotation == 0f) return bitmap
        return try {
            val matrix = Matrix().apply { postRotate(rotation) }
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated != bitmap) bitmap.recycle()
            rotated
        } catch (e: Exception) {
            Log.e(NAME, "EXIF 旋转失败", e)
            bitmap
        }
    }

    private fun decodeQrFromBitmap(bitmapOrig: Bitmap): String? {
        val hints: Map<DecodeHintType, Any> = mapOf(
            DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
            DecodeHintType.TRY_HARDER to true,
        )
        val scaledToRecycle = mutableListOf<Bitmap>()
        try {
            val variants = mutableListOf(bitmapOrig)
            var w = bitmapOrig.width
            var h = bitmapOrig.height
            repeat(5) {
                if (maxOf(w, h) <= 320) return@repeat
                w = (w * 0.65f).toInt().coerceAtLeast(1)
                h = (h * 0.65f).toInt().coerceAtLeast(1)
                val scaled = Bitmap.createScaledBitmap(bitmapOrig, w, h, true)
                variants.add(scaled)
                if (scaled !== bitmapOrig) scaledToRecycle.add(scaled)
            }
            for (b in variants) {
                decodeQrOnce(b, hints)?.let { return it }
            }
            return null
        } catch (e: Exception) {
            Log.e(NAME, "静态图解码异常", e)
            return null
        } finally {
            scaledToRecycle.forEach { if (!it.isRecycled) it.recycle() }
        }
    }

    private fun decodeQrOnce(bitmap: Bitmap, hints: Map<DecodeHintType, Any>): String? {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return null
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val source = RGBLuminanceSource(w, h, pixels)
        val binarizers: List<(LuminanceSource) -> BinaryBitmap> = listOf(
            { s -> BinaryBitmap(HybridBinarizer(s)) },
            { s -> BinaryBitmap(GlobalHistogramBinarizer(s)) },
            { s -> BinaryBitmap(HybridBinarizer(s.invert())) },
            { s -> BinaryBitmap(GlobalHistogramBinarizer(s.invert())) },
        )
        for (toBitmap in binarizers) {
            try {
                MultiFormatReader().apply { setHints(hints) }.decode(toBitmap(source)).text?.let {
                    return it
                }
            } catch (_: NotFoundException) {
            }
        }
        return null
    }

    private fun returnScanResult(result: String?) {
        mainHandler.post {
            try {
                val callbackObj = ClosureRegistry.take(callbackId)
                if (!result.isNullOrEmpty()) {
                    vibrateShort()
                    callbackObj?.onSuccess(ScanQrBridge.successPayload(result))
                } else {
                    callbackObj?.onError(ScanQrBridge.failJson("扫描结果为空"))
                }
            } catch (e: Exception) {
                Log.e(NAME, "返回扫描结果失败", e)
            } finally {
                finish()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        captureManager?.onResume()
        if (::decoratedBarcodeView.isInitialized) decoratedBarcodeView.resume()
    }

    override fun onPause() {
        closeTorch()
        super.onPause()
        captureManager?.onPause()
        if (::decoratedBarcodeView.isInitialized) decoratedBarcodeView.pause()
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
        return if (::decoratedBarcodeView.isInitialized) {
            decoratedBarcodeView.onKeyDown(keyCode, event) || super.onKeyDown(keyCode, event)
        } else {
            super.onKeyDown(keyCode, event)
        }
    }

    override fun onBackPressed() {
        try {
            ClosureRegistry.remove(callbackId)
        } catch (e: Exception) {
            Log.e(NAME, "返回时清理资源失败", e)
        }
        super.onBackPressed()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        }
    }
}
