package fr.eiter.plexiwine

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.io.File

/** Mirrors iOS WineImageUtils.compressJPEG + crop cadre scan. */
object ImageUtils {
    fun compressJPEG(input: ByteArray, maxDimension: Int = 1600, quality: Int = 82): ByteArray {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(input, 0, input.size, opts)
        val w = opts.outWidth
        val h = opts.outHeight
        if (w <= 0 || h <= 0) return input

        var sample = 1
        val maxSide = maxOf(w, h)
        while (maxSide / sample > maxDimension * 2) sample *= 2

        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = BitmapFactory.decodeByteArray(input, 0, input.size, decodeOpts) ?: return input
        val scaled = if (maxOf(bmp.width, bmp.height) > maxDimension) {
            val scale = maxDimension.toFloat() / maxOf(bmp.width, bmp.height)
            val nw = (bmp.width * scale).toInt().coerceAtLeast(1)
            val nh = (bmp.height * scale).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(bmp, nw, nh, true).also {
                if (it !== bmp) bmp.recycle()
            }
        } else bmp

        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
        if (scaled !== bmp) scaled.recycle()
        return out.toByteArray()
    }

    /**
     * Crop JPEG selon un rect normalisé (0–1) dans l’espace image déjà orienté.
     * [nx],[ny],[nw],[nh] = fraction largeur/hauteur de la photo.
     */
    fun cropNormalized(
        input: ByteArray,
        nx: Float,
        ny: Float,
        nw: Float,
        nh: Float,
        quality: Int = 90
    ): ByteArray {
        val bmp = BitmapFactory.decodeByteArray(input, 0, input.size) ?: return input
        try {
            val x = (nx * bmp.width).toInt().coerceIn(0, bmp.width - 1)
            val y = (ny * bmp.height).toInt().coerceIn(0, bmp.height - 1)
            val w = (nw * bmp.width).toInt().coerceIn(1, bmp.width - x)
            val h = (nh * bmp.height).toInt().coerceIn(1, bmp.height - y)
            if (w < 32 || h < 32) return input
            val cropped = Bitmap.createBitmap(bmp, x, y, w, h)
            val out = ByteArrayOutputStream()
            cropped.compress(Bitmap.CompressFormat.JPEG, quality, out)
            if (cropped !== bmp) cropped.recycle()
            return out.toByteArray()
        } finally {
            bmp.recycle()
        }
    }

    /**
     * Map un rect en coords preview (pixels) → fractions image, pour ScaleType.FILL_CENTER.
     * Preview = view size, image = photo size (orientée).
     */
    fun fillCenterViewRectToImageNorm(
        viewW: Int,
        viewH: Int,
        imageW: Int,
        imageH: Int,
        rectL: Float,
        rectT: Float,
        rectR: Float,
        rectB: Float
    ): FloatArray {
        if (viewW <= 0 || viewH <= 0 || imageW <= 0 || imageH <= 0) {
            return floatArrayOf(0.1f, 0.15f, 0.8f, 0.7f)
        }
        // FILL_CENTER : scale = max, image peut dépasser la vue
        val scale = maxOf(viewW.toFloat() / imageW, viewH.toFloat() / imageH)
        val dispW = imageW * scale
        val dispH = imageH * scale
        val offX = (viewW - dispW) / 2f
        val offY = (viewH - dispH) / 2f
        fun toIx(vx: Float) = ((vx - offX) / scale).coerceIn(0f, imageW.toFloat())
        fun toIy(vy: Float) = ((vy - offY) / scale).coerceIn(0f, imageH.toFloat())
        val x0 = toIx(rectL)
        val y0 = toIy(rectT)
        val x1 = toIx(rectR)
        val y1 = toIy(rectB)
        val nx = (x0 / imageW).coerceIn(0f, 1f)
        val ny = (y0 / imageH).coerceIn(0f, 1f)
        val nw = ((x1 - x0) / imageW).coerceIn(0.05f, 1f - nx)
        val nh = ((y1 - y0) / imageH).coerceIn(0.05f, 1f - ny)
        return floatArrayOf(nx, ny, nw, nh)
    }

    fun compressFile(file: File, maxDimension: Int = 1600, quality: Int = 82): File {
        val bytes = compressJPEG(file.readBytes(), maxDimension, quality)
        val out = File(file.parentFile, "compressed_${file.name}")
        out.writeBytes(bytes)
        return out
    }
}
