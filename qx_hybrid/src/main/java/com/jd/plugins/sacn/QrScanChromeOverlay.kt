package com.jd.plugins.sacn

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import android.view.View
import com.journeyapps.barcodescanner.BarcodeView

/**
 * 在 [BarcodeView] 取景框上绘制四角描边与横向渐变扫描线（对齐 iOS QXScanner 视觉）。
 * 半透明遮罩由 [com.journeyapps.barcodescanner.ViewfinderView] 负责。
 */
class QrScanChromeOverlay(context: Context) : View(context) {

    private var barcodeView: BarcodeView? = null

    private val themeColor = Color.parseColor("#236982")
    private val cornerLen: Float
    private val cornerThick: Float
    private val scanLineH: Float

    private val cornerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = themeColor
        style = Paint.Style.FILL
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var scanLineY = 0
    private val scanStepMs = 15L

    private val scanRunnable = object : Runnable {
        override fun run() {
            val frame = barcodeView?.framingRect
            if (frame != null) {
                val maxY = (frame.height() - scanLineH).toInt().coerceAtLeast(0)
                scanLineY = if (scanLineY >= maxY) 0 else scanLineY + 1
            }
            invalidate()
            postDelayed(this, scanStepMs)
        }
    }

    init {
        setWillNotDraw(false)
        val d = resources.displayMetrics.density
        cornerLen = 20f * d
        cornerThick = 3f * d
        scanLineH = 3f * d
    }

    fun bind(barcodeView: BarcodeView) {
        this.barcodeView = barcodeView
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        removeCallbacks(scanRunnable)
        post(scanRunnable)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(scanRunnable)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val frame: Rect = barcodeView?.framingRect ?: return

        drawCorners(canvas, frame)
        drawScanLine(canvas, frame)
    }

    private fun drawCorners(canvas: Canvas, frame: Rect) {
        val l = frame.left.toFloat()
        val t = frame.top.toFloat()
        val r = frame.right.toFloat()
        val b = frame.bottom.toFloat()
        val w = cornerLen
        val th = cornerThick

        // 左上
        canvas.drawRect(l, t, l + w, t + th, cornerPaint)
        canvas.drawRect(l, t, l + th, t + w, cornerPaint)
        // 右上
        canvas.drawRect(r - w, t, r, t + th, cornerPaint)
        canvas.drawRect(r - th, t, r, t + w, cornerPaint)
        // 左下
        canvas.drawRect(l, b - th, l + w, b, cornerPaint)
        canvas.drawRect(l, b - w, l + th, b, cornerPaint)
        // 右下
        canvas.drawRect(r - w, b - th, r, b, cornerPaint)
        canvas.drawRect(r - th, b - w, r, b, cornerPaint)
    }

    private fun drawScanLine(canvas: Canvas, frame: Rect) {
        val top = frame.top + scanLineY
        val shader = LinearGradient(
            frame.left.toFloat(), 0f,
            frame.right.toFloat(), 0f,
            intArrayOf(
                Color.argb(0, Color.red(themeColor), Color.green(themeColor), Color.blue(themeColor)),
                themeColor,
                Color.argb(0, Color.red(themeColor), Color.green(themeColor), Color.blue(themeColor)),
            ),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP,
        )
        linePaint.shader = shader
        canvas.drawRect(
            frame.left.toFloat(),
            top.toFloat(),
            frame.right.toFloat(),
            (top + scanLineH).toFloat(),
            linePaint,
        )
        linePaint.shader = null
    }
}
