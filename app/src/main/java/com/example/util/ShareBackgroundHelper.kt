package com.example.util

import android.content.Context
import android.graphics.*
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.ColorUtils
import com.example.ui.theme.BackgroundPreferences
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

data class ExportBackgroundBundle(
    val baseBackground: Bitmap,
    val blurredBackground: Bitmap,
    val accentColor: Color,
    val glassOpacity: Float,
    val dim: Float
)

object ShareBackgroundHelper {

    fun prepareBackground(
        context: Context,
        targetWidth: Int = 1080,
        targetHeight: Int = 1920
    ): ExportBackgroundBundle {
        BackgroundPreferences.init(context)
        val config = BackgroundPreferences.config.value
        val imagePath = config.imagePath
        val accentColor = Color(config.accentColorHex)
        val glassOpacity = config.glassOpacity.coerceIn(0.10f, 0.60f)

        if (imagePath != null) {
            val file = File(imagePath)
            if (file.exists() && file.length() > 0) {
                try {
                    val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(imagePath, boundsOptions)
                    val rawWidth = boundsOptions.outWidth
                    val rawHeight = boundsOptions.outHeight

                    if (rawWidth > 0 && rawHeight > 0) {
                        var sampleSize = 1
                        while (rawWidth / (sampleSize * 2) >= targetWidth) {
                            sampleSize *= 2
                        }

                        val decodeOptions = BitmapFactory.Options().apply {
                            inSampleSize = sampleSize
                            inPreferredConfig = Bitmap.Config.ARGB_8888
                        }
                        val decoded = BitmapFactory.decodeFile(imagePath, decodeOptions)
                        if (decoded != null) {
                            // Scale & center crop to target dimensions
                            val scale = max(
                                targetWidth.toFloat() / decoded.width,
                                targetHeight.toFloat() / decoded.height
                            )
                            val scaledW = (decoded.width * scale).roundToInt().coerceAtLeast(targetWidth)
                            val scaledH = (decoded.height * scale).roundToInt().coerceAtLeast(targetHeight)
                            val scaled = Bitmap.createScaledBitmap(decoded, scaledW, scaledH, true)

                            val dx = ((scaledW - targetWidth) / 2).coerceAtLeast(0)
                            val dy = ((scaledH - targetHeight) / 2).coerceAtLeast(0)

                            val croppedSource = Bitmap.createBitmap(
                                scaled,
                                dx,
                                dy,
                                targetWidth,
                                targetHeight
                            )
                            val cropped = croppedSource.copy(Bitmap.Config.ARGB_8888, true)
                            if (croppedSource !== scaled && croppedSource !== cropped) {
                                croppedSource.recycle()
                            }
                            if (scaled !== decoded && scaled !== cropped) {
                                scaled.recycle()
                            }
                            if (decoded !== cropped) {
                                decoded.recycle()
                            }

                            // Check luminance of the cropped wallpaper
                            val sampleStep = 32
                            var totalLuminance = 0.0
                            var sampleCount = 0
                            for (y in 0 until targetHeight step sampleStep) {
                                for (x in 0 until targetWidth step sampleStep) {
                                    val pixel = cropped.getPixel(x, y)
                                    totalLuminance += ColorUtils.calculateLuminance(pixel)
                                    sampleCount++
                                }
                            }
                            val avgLuminance = if (sampleCount > 0) totalLuminance / sampleCount else 0.2
                            // Use the app's dim value but cap it at 0.20 for export (Fix B: keep wallpaper vivid)
                            val effectiveDim = config.dim.coerceIn(0.0f, 0.20f)

                            // Apply dim scrim directly to the base background
                            val canvas = Canvas(cropped)
                            if (effectiveDim > 0.01f) {
                                val scrimPaint = Paint().apply {
                                    color = android.graphics.Color.BLACK
                                    alpha = (effectiveDim * 255).roundToInt()
                                }
                                canvas.drawRect(0f, 0f, targetWidth.toFloat(), targetHeight.toFloat(), scrimPaint)
                            }

                            // Pre-compute ONE blurred bitmap (downscale to 1/4, stack blur, scale back up)
                            val lowResW = max(1, targetWidth / 4)
                            val lowResH = max(1, targetHeight / 4)
                            val lowRes = Bitmap.createScaledBitmap(cropped, lowResW, lowResH, true)
                            val blurRad = (config.blurRadius / 2f).roundToInt().coerceIn(3, 25)
                            val blurredLowRes = FastStackBlur.blur(lowRes, blurRad)
                            val blurred = Bitmap.createScaledBitmap(blurredLowRes, targetWidth, targetHeight, true)
                            if (lowRes !== cropped && lowRes !== blurredLowRes) {
                                lowRes.recycle()
                            }
                            if (blurredLowRes !== blurred) {
                                blurredLowRes.recycle()
                            }

                            return ExportBackgroundBundle(
                                baseBackground = cropped,
                                blurredBackground = blurred,
                                accentColor = accentColor,
                                glassOpacity = glassOpacity,
                                dim = effectiveDim
                            )
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        // Fallback: Dark gradient built from the accent
        val base = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(base)

        val accentInt = config.accentColorHex.toInt()
        val topColor = ColorUtils.blendARGB(
            android.graphics.Color.parseColor("#151622"),
            accentInt,
            0.22f
        )
        val midColor = android.graphics.Color.parseColor("#0F1017")
        val bottomColor = android.graphics.Color.parseColor("#090A0E")

        val gradient = LinearGradient(
            0f, 0f, 0f, targetHeight.toFloat(),
            intArrayOf(topColor, midColor, bottomColor),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        val paint = Paint().apply { shader = gradient }
        canvas.drawRect(0f, 0f, targetWidth.toFloat(), targetHeight.toFloat(), paint)

        // Accent ambient radial glow on top right
        val glowPaint = Paint().apply {
            shader = RadialGradient(
                targetWidth * 0.85f,
                targetHeight * 0.12f,
                targetWidth * 0.7f,
                ColorUtils.setAlphaComponent(accentInt, 50),
                android.graphics.Color.TRANSPARENT,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(targetWidth * 0.85f, targetHeight * 0.12f, targetWidth * 0.7f, glowPaint)

        // Precompute blurred version
        val lowResW = max(1, targetWidth / 4)
        val lowResH = max(1, targetHeight / 4)
        val lowRes = Bitmap.createScaledBitmap(base, lowResW, lowResH, true)
        val blurredLowRes = FastStackBlur.blur(lowRes, 12)
        val blurred = Bitmap.createScaledBitmap(blurredLowRes, targetWidth, targetHeight, true)

        return ExportBackgroundBundle(
            baseBackground = base,
            blurredBackground = blurred,
            accentColor = accentColor,
            glassOpacity = glassOpacity,
            dim = config.dim.coerceIn(0.0f, 0.20f)
        )
    }
}
