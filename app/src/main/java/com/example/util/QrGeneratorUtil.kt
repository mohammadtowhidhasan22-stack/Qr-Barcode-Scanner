package com.example.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object QrGeneratorUtil {

    fun generateQrBitmap(
        content: String,
        sizePx: Int,
        foregroundColor: Int,
        backgroundColor: Int
    ): Result<Bitmap> = runCatching {
        require(content.isNotBlank()) { "QR content cannot be empty." }
        val safeSize = sizePx.coerceIn(256, 2048)
        val hints = mapOf(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 2
        )
        val bitMatrix = QRCodeWriter().encode(
            content,
            BarcodeFormat.QR_CODE,
            safeSize,
            safeSize,
            hints
        )
        val width = bitMatrix.width
        val height = bitMatrix.height
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            val offset = y * width
            for (x in 0 until width) {
                pixels[offset + x] = if (bitMatrix[x, y]) foregroundColor else backgroundColor
            }
        }
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, width, 0, 0, width, height)
        }
    }

    suspend fun saveQrToGallery(
        context: Context,
        bitmap: Bitmap,
        fileNamePrefix: String = "QRCode"
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val fileName = "${fileNamePrefix}_${System.currentTimeMillis()}.png"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        "${Environment.DIRECTORY_PICTURES}/QRScanner"
                    )
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: error("Unable to create image entry in MediaStore.")
                try {
                    resolver.openOutputStream(uri)?.use { out ->
                        if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                            error("Failed to encode PNG image.")
                        }
                    } ?: error("Unable to open output stream.")
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                } catch (t: Throwable) {
                    resolver.delete(uri, null, null)
                    throw t
                }
                "Saved to Pictures/QRScanner/$fileName"
            } else {
                // Pre-Android 10: save into app-accessible Pictures directory without requesting storage permissions
                val picturesDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                    ?: File(context.filesDir, "Pictures")
                val targetDir = File(picturesDir, "QRScanner").apply { mkdirs() }
                val targetFile = File(targetDir, fileName)
                FileOutputStream(targetFile).use { out ->
                    if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                        error("Failed to encode PNG image.")
                    }
                }
                "Saved as $fileName"
            }
        }
    }

    suspend fun writeBitmapToUri(
        context: Context,
        uri: Uri,
        bitmap: Bitmap
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                    error("Failed to compress PNG image.")
                }
            } ?: error("Unable to write to destination URI.")
            Unit
        }
    }

    suspend fun shareQrBitmap(context: Context, bitmap: Bitmap): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val shareDir = File(context.cacheDir, "shared_qr").apply { mkdirs() }
                // Clean old temp files to keep cache tiny
                shareDir.listFiles()?.forEach { old ->
                    if (System.currentTimeMillis() - old.lastModified() > 60_000L) {
                        old.delete()
                    }
                }
                val file = File(shareDir, "qr_${System.currentTimeMillis()}.png")
                FileOutputStream(file).use { out ->
                    if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                        error("Failed to encode PNG for sharing.")
                    }
                }
                val contentUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                withContext(Dispatchers.Main) {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "image/png"
                        putExtra(Intent.EXTRA_STREAM, contentUri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    val chooser = Intent.createChooser(shareIntent, "Share QR Code").apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(chooser)
                }
            }
        }
}
