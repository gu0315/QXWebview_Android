package com.jd.plugins

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * 无 UI 的选图/拍照中转 Activity（SDK 内部）。
 * 由 QXBasePlugin.chooseImage 启动；完成后通过 [PageResultCenter] 把结果回传给挂起的 H5 callback。
 *
 * - 相册：ACTION_GET_CONTENT（走系统 SAF，免存储权限）
 * - 拍照：ACTION_IMAGE_CAPTURE + FileProvider（需 CAMERA 运行时权限）
 * 返回：{ count, images: [{ base64, path, width, height, size }] }
 */
class ImagePickerActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ImagePickerActivity"
        const val EXTRA_SOURCES = "sources"        // String[]: album / camera
        const val EXTRA_MAX_SIZE = "max_size"      // Int，最长边像素
        const val EXTRA_QUALITY = "quality"        // Int，JPEG 质量 1-100
        const val EXTRA_REQUEST_ID = "request_id"  // 对应 PageResultCenter 的挂起 id
        private const val REQ_CAMERA_PERMISSION = 9001
    }

    private var requestId: String? = null
    private var maxSize = 1280
    private var quality = 80
    private var cameraOutputUri: Uri? = null
    private var resolved = false

    private val pickLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            handleResultUri(result.data?.data)
        } else {
            cancelAndFinish()
        }
    }

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            handleResultUri(cameraOutputUri)
        } else {
            cancelAndFinish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestId = intent.getStringExtra(EXTRA_REQUEST_ID)
        maxSize = intent.getIntExtra(EXTRA_MAX_SIZE, 1280)
        quality = intent.getIntExtra(EXTRA_QUALITY, 80).coerceIn(1, 100)

        val sources = intent.getStringArrayExtra(EXTRA_SOURCES)?.map { it.lowercase() } ?: listOf("album", "camera")
        val allowAlbum = sources.contains("album")
        val allowCamera = sources.contains("camera")

        when {
            allowAlbum && allowCamera -> showChooser()
            allowCamera -> startCamera()
            else -> startAlbum()
        }
    }

    private fun showChooser() {
        AlertDialog.Builder(this)
            .setItems(arrayOf("拍照", "从相册选择")) { _, which ->
                if (which == 0) startCamera() else startAlbum()
            }
            .setOnCancelListener { cancelAndFinish() }
            .show()
    }

    private fun startAlbum() {
        try {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "image/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            pickLauncher.launch(Intent.createChooser(intent, "选择图片"))
        } catch (e: Exception) {
            failAndFinish("无法打开相册: ${e.message}")
        }
    }

    private fun startCamera() {
        // 若 App 声明了 CAMERA 权限，使用 ACTION_IMAGE_CAPTURE 也需运行时授权
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), REQ_CAMERA_PERMISSION)
            return
        }
        launchCamera()
    }

    private fun launchCamera() {
        try {
            val file = File(cacheDir, "cam_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(this, "$packageName.qxfileprovider", file)
            cameraOutputUri = uri
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra(MediaStore.EXTRA_OUTPUT, uri)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
            cameraLauncher.launch(intent)
        } catch (e: Exception) {
            failAndFinish("无法打开相机: ${e.message}")
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_CAMERA_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                launchCamera()
            } else {
                failAndFinish("没有相机权限")
            }
        }
    }

    private fun handleResultUri(uri: Uri?) {
        if (uri == null) {
            cancelAndFinish()
            return
        }
        Thread {
            try {
                val json = processImage(uri)
                resolve(json)
            } catch (e: Exception) {
                Log.e(TAG, "处理图片失败", e)
                resolve(JSONObject().apply { put("error", e.message ?: "处理图片失败") })
            } finally {
                runOnUiThread { finish() }
            }
        }.start()
    }

    private fun processImage(uri: Uri): JSONObject {
        val decoded = decodeSampledBitmap(uri, maxSize)
            ?: throw IllegalStateException("无法解码图片")
        // 按 EXIF 朝向校正（部分机型拍照会带旋转信息）
        val rotated = applyExifRotation(decoded, uri)
        val resized = resizeBitmap(rotated, maxSize)
        val baos = ByteArrayOutputStream()
        resized.compress(Bitmap.CompressFormat.JPEG, quality, baos)
        val bytes = baos.toByteArray()

        val base64 = "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
        val outFile = File(cacheDir, "img_${System.currentTimeMillis()}.jpg")
        FileOutputStream(outFile).use { it.write(bytes) }

        val item = JSONObject().apply {
            put("base64", base64)
            put("path", Uri.fromFile(outFile).toString()) // file://...
            put("width", resized.width)
            put("height", resized.height)
            put("size", bytes.size)
        }
        return JSONObject().apply {
            put("count", 1)
            put("images", JSONArray().put(item))
        }
    }

    /** 先读边界按 inSampleSize 降采样，避免大图 OOM */
    private fun decodeSampledBitmap(uri: Uri, reqSize: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val longSide = maxOf(bounds.outWidth, bounds.outHeight)
        var sample = 1
        if (reqSize > 0) {
            while (longSide / sample > reqSize * 2) sample *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }

    /** 读取 EXIF 朝向并旋转 Bitmap（兼容相册 content uri 与拍照 file uri） */
    private fun applyExifRotation(bitmap: Bitmap, uri: Uri): Bitmap {
        val degrees = try {
            contentResolver.openInputStream(uri)?.use { input ->
                val exif = ExifInterface(input)
                when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        } catch (e: Exception) {
            0f
        }
        if (degrees == 0f) return bitmap
        return try {
            val matrix = Matrix().apply { postRotate(degrees) }
            val out = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (out != bitmap) bitmap.recycle()
            out
        } catch (e: Exception) {
            bitmap
        }
    }

    /** 等比缩放到最长边不超过 maxSize */
    private fun resizeBitmap(src: Bitmap, maxSize: Int): Bitmap {
        val w = src.width
        val h = src.height
        val longSide = maxOf(w, h)
        if (maxSize <= 0 || longSide <= maxSize) return src
        val scale = maxSize.toFloat() / longSide
        return Bitmap.createScaledBitmap(src, (w * scale).toInt(), (h * scale).toInt(), true)
    }

    private fun resolve(json: JSONObject) {
        if (resolved) return
        resolved = true
        requestId?.let { PageResultCenter.resolve(it, json) }
    }

    private fun cancelAndFinish() {
        if (!resolved) {
            resolved = true
            requestId?.let { PageResultCenter.cancel(it) }
        }
        finish()
    }

    private fun failAndFinish(msg: String) {
        resolve(JSONObject().apply { put("error", msg) })
        finish()
    }
}
