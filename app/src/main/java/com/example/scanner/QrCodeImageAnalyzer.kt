package com.example.scanner

import android.graphics.ImageFormat
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * CameraX ImageAnalysis.Analyzer powered by ZXing Core for fast QR code scanning,
 * optimized for laptop/projector screens using both Hybrid and GlobalHistogram binarizers.
 */
class QrCodeImageAnalyzer(
    private val onQrCodeDetected: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private val isPaused = AtomicBoolean(false)

    private val reader = MultiFormatReader().apply {
        setHints(
            mapOf(
                DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                DecodeHintType.TRY_HARDER to true
            )
        )
    }

    fun pauseAnalysis() {
        isPaused.set(true)
    }

    fun resumeAnalysis() {
        isPaused.set(false)
    }

    override fun analyze(imageProxy: ImageProxy) {
        if (isPaused.get()) {
            imageProxy.close()
            return
        }

        try {
            if (imageProxy.format != ImageFormat.YUV_420_888 &&
                imageProxy.format != ImageFormat.YUV_422_888 &&
                imageProxy.format != ImageFormat.YUV_444_888
            ) {
                return
            }

            val yPlane = imageProxy.planes[0]
            val yBuffer = yPlane.buffer
            val rowStride = yPlane.rowStride
            val width = imageProxy.width
            val height = imageProxy.height

            val yBytes = ByteArray(width * height)
            if (rowStride == width) {
                yBuffer.rewind()
                yBuffer.get(yBytes, 0, width * height)
            } else {
                val rowData = ByteArray(rowStride)
                for (row in 0 until height) {
                    yBuffer.position(row * rowStride)
                    val remaining = yBuffer.remaining()
                    val length = minOf(rowStride, remaining)
                    yBuffer.get(rowData, 0, length)
                    System.arraycopy(rowData, 0, yBytes, row * width, minOf(width, length))
                }
            }

            val source = PlanarYUVLuminanceSource(
                yBytes,
                width,
                height,
                0,
                0,
                width,
                height,
                false
            )

            val decodedText = decodeWithFallback(source)
            if (!decodedText.isNullOrBlank()) {
                onQrCodeDetected(decodedText)
            }
        } catch (_: Exception) {
            // Ignore malformed frame errors and keep scanning smoothly
        } finally {
            reader.reset()
            imageProxy.close()
        }
    }

    private fun decodeWithFallback(source: PlanarYUVLuminanceSource): String? {
        return try {
            reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text
        } catch (_: NotFoundException) {
            reader.reset()
            try {
                reader.decodeWithState(BinaryBitmap(GlobalHistogramBinarizer(source))).text
            } catch (_: NotFoundException) {
                null
            }
        }
    }
}
