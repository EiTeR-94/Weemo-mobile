package fr.eiter.plexiwine

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.io.File

/** Mirrors iOS WineImageUtils.compressJPEG */
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

    fun compressFile(file: File, maxDimension: Int = 1600, quality: Int = 82): File {
        val bytes = compressJPEG(file.readBytes(), maxDimension, quality)
        val out = File(file.parentFile, "compressed_${file.name}")
        out.writeBytes(bytes)
        return out
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
     * Empreinte visuelle étiquette (dHash + aHash), crop centre — miroir de
     * app/static/js/label-cache.js côté serveur (mêmes constantes : crop 78%,
     * grille dHash 9x8, grille aHash 8x8, luma 0.299/0.587/0.114).
     * @return Pair(dHashHex16, aHashHex16) ou null si le bitmap est invalide.
     */
    fun computeLabelPrint(bmp: Bitmap): Pair<String, String>? {
        if (bmp.width <= 0 || bmp.height <= 0) return null
        val side = (minOf(bmp.width, bmp.height) * 0.78f).toInt().coerceAtLeast(1)
        val cx = ((bmp.width - side) / 2).coerceIn(0, bmp.width - side)
        val cy = ((bmp.height - side) / 2).coerceIn(0, bmp.height - side)
        val cropped = Bitmap.createBitmap(bmp, cx, cy, side, side)
        try {
            val d = dHash(cropped)
            val a = aHash(cropped)
            return d to a
        } finally {
            if (cropped !== bmp) cropped.recycle()
        }
    }

    private fun luma(px: Int): Float {
        val r = (px shr 16) and 0xFF
        val g = (px shr 8) and 0xFF
        val b = px and 0xFF
        return 0.299f * r + 0.587f * g + 0.114f * b
    }

    private fun bitsToHex(bits: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < bits.length) {
            val nibble = bits.substring(i, minOf(i + 4, bits.length))
            sb.append(Integer.toString(Integer.parseInt(nibble, 2), 16))
            i += 4
        }
        return sb.toString()
    }

    private fun dHash(src: Bitmap): String {
        val w = 9
        val h = 8
        val scaled = Bitmap.createScaledBitmap(src, w, h, true)
        val gray = Array(h) { y -> FloatArray(w) { x -> luma(scaled.getPixel(x, y)) } }
        if (scaled !== src) scaled.recycle()
        val bits = StringBuilder()
        for (y in 0 until h) {
            for (x in 0 until w - 1) {
                bits.append(if (gray[y][x] < gray[y][x + 1]) '1' else '0')
            }
        }
        return bitsToHex(bits.toString())
    }

    private fun aHash(src: Bitmap): String {
        val size = 8
        val scaled = Bitmap.createScaledBitmap(src, size, size, true)
        val n = size * size
        val gray = FloatArray(n)
        var sum = 0f
        var i = 0
        for (y in 0 until size) {
            for (x in 0 until size) {
                val v = luma(scaled.getPixel(x, y))
                gray[i] = v
                sum += v
                i += 1
            }
        }
        if (scaled !== src) scaled.recycle()
        val avg = sum / n
        val bits = StringBuilder()
        for (v in gray) bits.append(if (v >= avg) '1' else '0')
        return bitsToHex(bits.toString())
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
}
