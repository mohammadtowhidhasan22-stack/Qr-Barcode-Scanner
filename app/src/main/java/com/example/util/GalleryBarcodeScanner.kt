package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.example.data.model.ScanResultData
import com.example.data.model.ScanSource
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object GalleryBarcodeScanner {

    val allSupportedFormatsOptions: BarcodeScannerOptions by lazy {
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_QR_CODE,
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_UPC_A,
                Barcode.FORMAT_UPC_E,
                Barcode.FORMAT_CODE_128,
                Barcode.FORMAT_CODE_39,
                Barcode.FORMAT_CODE_93,
                Barcode.FORMAT_CODABAR,
                Barcode.FORMAT_ITF,
                Barcode.FORMAT_PDF417,
                Barcode.FORMAT_AZTEC,
                Barcode.FORMAT_DATA_MATRIX
            )
            .build()
    }

    suspend fun scanFromUri(context: Context, uri: Uri): Result<ScanResultData> =
        withContext(Dispatchers.IO) {
            runCatching {
                val inputImage = loadSafeInputImage(context, uri)
                val scanner = BarcodeScanning.getClient(allSupportedFormatsOptions)
                try {
                    val barcodes = suspendCancellableCoroutine<List<Barcode>> { cont ->
                        scanner.process(inputImage)
                            .addOnSuccessListener { results ->
                                if (cont.isActive) cont.resume(results)
                            }
                            .addOnFailureListener { exc ->
                                if (cont.isActive) cont.resumeWithException(exc)
                            }
                    }

                    val matched = barcodes.firstOrNull { !it.rawValue.isNullOrBlank() }
                        ?: throw NoSuchElementException("No QR code or supported barcode was found in this image.")

                    val raw = matched.rawValue ?: throw IllegalStateException("Detected code has empty content.")
                    BarcodeParser.parse(
                        rawValue = raw,
                        formatCode = matched.format,
                        timestamp = System.currentTimeMillis(),
                        source = ScanSource.GALLERY
                    )
                } finally {
                    scanner.close()
                }
            }
        }

    private fun loadSafeInputImage(context: Context, uri: Uri): InputImage {
        // First inspect bounds to avoid OOM on low-end devices with very large camera photos
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOpts)
        }
        val maxDim = maxOf(boundsOpts.outWidth, boundsOpts.outHeight)
        if (maxDim in 1..2048) {
            return InputImage.fromFilePath(context, uri)
        }

        val sampleSize = calculateInSampleSize(boundsOpts.outWidth, boundsOpts.outHeight, 1600, 1600)
        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOpts)
        } ?: return InputImage.fromFilePath(context, uri)

        return InputImage.fromBitmap(bitmap, 0)
    }

    private fun calculateInSampleSize(width: Int, height: Int, reqWidth: Int, reqHeight: Int): Int {
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}
