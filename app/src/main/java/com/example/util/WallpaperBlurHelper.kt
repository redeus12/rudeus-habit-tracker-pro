package com.example.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntSize
import com.example.ui.theme.BackgroundConfig
import com.example.ui.theme.WallpaperBlurState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

object WallpaperBlurHelper {

    fun computeBlurredWallpaper(
        file: File,
        blurRadius: Float,
        dimAmount: Float,
        rootWidthPx: Int,
        rootHeightPx: Int
    ): ImageBitmap? {
        if (!file.exists() || rootWidthPx <= 0 || rootHeightPx <= 0) return null

        val targetW = max(1, rootWidthPx / 4)
        val targetH = max(1, rootHeightPx / 4)

        // 1. Decode bounds
        val boundsOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(file.absolutePath, boundsOptions)
        val origW = boundsOptions.outWidth
        val origH = boundsOptions.outHeight
        if (origW <= 0 || origH <= 0) return null

        // 2. Compute sample size
        var sampleSize = 1
        while ((origW / (sampleSize * 2)) >= targetW && (origH / (sampleSize * 2)) >= targetH) {
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeFile(file.absolutePath, decodeOptions) ?: return null

        // 3. Center crop & scale to targetW x targetH (matches ContentScale.Crop)
        val scale = max(targetW.toFloat() / decoded.width, targetH.toFloat() / decoded.height)
        val scaledW = decoded.width * scale
        val scaledH = decoded.height * scale
        val dx = (targetW - scaledW) / 2f
        val dy = (targetH - scaledH) / 2f

        val croppedBitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(croppedBitmap)
        val matrix = Matrix().apply {
            postScale(scale, scale)
            postTranslate(dx, dy)
        }
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        canvas.drawBitmap(decoded, matrix, paint)
        if (decoded != croppedBitmap) {
            decoded.recycle()
        }

        // 4. Apply global dim scrim directly onto bitmap
        if (dimAmount > 0f) {
            val alphaInt = (dimAmount.coerceIn(0f, 1f) * 255).roundToInt()
            if (alphaInt > 0) {
                val scrimPaint = Paint().apply {
                    color = android.graphics.Color.argb(alphaInt, 0, 0, 0)
                }
                canvas.drawRect(0f, 0f, targetW.toFloat(), targetH.toFloat(), scrimPaint)
            }
        }

        // 5. Apply FastStackBlur
        val scaledRadius = (blurRadius * (targetW.toFloat() / rootWidthPx.toFloat())).roundToInt().coerceAtLeast(0)
        val blurredBitmap = if (scaledRadius >= 1) {
            val blurred = FastStackBlur.blur(croppedBitmap, scaledRadius)
            if (blurred != croppedBitmap) {
                croppedBitmap.recycle()
            }
            blurred
        } else {
            croppedBitmap
        }

        return blurredBitmap.asImageBitmap()
    }
}

@Composable
fun rememberBlurredWallpaperState(
    config: BackgroundConfig,
    rootSizePx: IntSize
): WallpaperBlurState {
    var blurState by remember { mutableStateOf(WallpaperBlurState(null)) }

    LaunchedEffect(config.imagePath, config.blurRadius, config.dim, config.isEnabled, rootSizePx) {
        if (!config.isEnabled || config.imagePath == null || rootSizePx.width <= 0 || rootSizePx.height <= 0) {
            blurState = WallpaperBlurState(null)
            return@LaunchedEffect
        }

        // Debounce 80ms while dragging sliders
        delay(80L)

        val file = File(config.imagePath)
        val bitmap = withContext(Dispatchers.Default) {
            WallpaperBlurHelper.computeBlurredWallpaper(
                file = file,
                blurRadius = config.blurRadius,
                dimAmount = config.dimAmount,
                rootWidthPx = rootSizePx.width,
                rootHeightPx = rootSizePx.height
            )
        }
        blurState = WallpaperBlurState(bitmap)
    }

    return blurState
}
