package com.photoprint.pro.platform

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.File

/** Reading photos stored in app-private storage: size, EXIF orientation, downsampled decode. */
internal object PhotoFiles {
    /** Clockwise degrees the picture must be turned to be upright (0, 90, 180, 270). */
    fun exifRotation(file: File): Int = try {
        when (ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    } catch (_: Exception) {
        0
    }

    /** Pixel size as displayed (after EXIF rotation), or null if the file is not a readable image. */
    fun uprightSize(file: File): Pair<Int, Int>? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val swap = exifRotation(file).let { it == 90 || it == 270 }
        return if (swap) bounds.outHeight to bounds.outWidth else bounds.outWidth to bounds.outHeight
    }

    /**
     * Decodes upright. With [maxSide] the image is downsampled so its longest side is at least that long
     * (never smaller than asked); null means full resolution.
     */
    fun decodeUpright(file: File, maxSide: Int?): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        if (maxSide != null) {
            val longest = maxOf(bounds.outWidth, bounds.outHeight)
            while (longest / (sample * 2) >= maxSide) sample *= 2
        }
        val raw = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val rotation = exifRotation(file)
        if (rotation == 0) return raw
        val turned = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, Matrix().apply { postRotate(rotation.toFloat()) }, true)
        if (turned !== raw) raw.recycle()
        return turned
    }

    fun isJpeg(bytes: ByteArray) = bytes.size > 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()

    /**
     * Number of colour components declared by the JPEG's frame header (3 = RGB, 1 = grey, 4 = CMYK),
     * or null if it can't be found. CMYK and other oddities are re-encoded rather than embedded as-is.
     */
    fun jpegComponents(bytes: ByteArray): Int? {
        var i = 2
        while (i + 9 < bytes.size) {
            if (bytes[i] != 0xFF.toByte()) { i++; continue }
            val marker = bytes[i + 1].toInt() and 0xFF
            if (marker == 0xFF) { i++; continue }
            if (marker in 0xD0..0xD9 || marker == 0x01) { i += 2; continue }
            val length = ((bytes[i + 2].toInt() and 0xFF) shl 8) or (bytes[i + 3].toInt() and 0xFF)
            val isFrame = marker in 0xC0..0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC
            if (isFrame) return bytes[i + 9].toInt() and 0xFF
            i += 2 + length
        }
        return null
    }
}
