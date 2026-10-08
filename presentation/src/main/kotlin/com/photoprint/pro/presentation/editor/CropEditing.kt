package com.photoprint.pro.presentation.editor

import com.photoprint.pro.domain.model.CropState
import com.photoprint.pro.domain.pdf.ImageFit
import kotlin.math.abs

/**
 * Aspect of the on-screen crop window. The physical frame size is fixed by the chosen photo size;
 * this only controls the shape of the editing window before/while a size is chosen.
 */
sealed interface AspectOption {
    val label: String

    /** Window follows the image's own shape (nothing is cropped away by shape). */
    data object Free : AspectOption {
        override val label = "Free"
    }

    data class Ratio(val w: Double, val h: Double, override val label: String) : AspectOption {
        val value: Double get() = w / h
    }

    companion object {
        val Passport = Ratio(35.0, 45.0, "35:45")
        val Square = Ratio(1.0, 1.0, "1:1")
        val ThreeFour = Ratio(3.0, 4.0, "3:4")
        val FourFive = Ratio(4.0, 5.0, "4:5")
        val presets: List<AspectOption> = listOf(Free, Passport, Square, ThreeFour, FourFive)

        fun custom(w: Double, h: Double): Ratio? =
            if (w.isFinite() && h.isFinite() && w > 0 && h > 0) Ratio(w, h, "Custom") else null

        /** The ratio of a photo size, labelled by its dimensions. */
        fun of(widthMm: Double, heightMm: Double) = Ratio(widthMm, heightMm, "${widthMm.toLong()}:${heightMm.toLong()}")
    }
}

/** Gesture and slider maths for the photo editor. Everything is clamped so the frame is always covered. */
object CropEditing {
    const val MIN_ZOOM = 1.0
    const val MAX_ZOOM = 8.0

    /** Frame shape used for pan limits: the editing window's aspect (only the ratio matters). */
    data class Frame(val widthUnits: Double, val heightUnits: Double)

    fun frameFor(aspect: AspectOption, imgW: Int, imgH: Int): Frame = when (aspect) {
        is AspectOption.Free -> Frame(imgW.toDouble(), imgH.toDouble())
        is AspectOption.Ratio -> Frame(aspect.w, aspect.h)
    }

    /** Pan by a fraction of the frame size (drag distance ÷ frame size in px). */
    fun panBy(crop: CropState, dxFraction: Double, dyFraction: Double, frame: Frame, imgW: Int, imgH: Int): CropState =
        clamp(crop.copy(panX = crop.panX + dxFraction, panY = crop.panY + dyFraction), frame, imgW, imgH)

    /**
     * Pinch zoom by [factor] about a focal point given as a fraction of the frame from its centre
     * (−0.5..0.5), so the image point under the fingers stays put.
     */
    fun zoomBy(
        crop: CropState,
        factor: Double,
        focalX: Double,
        focalY: Double,
        frame: Frame,
        imgW: Int,
        imgH: Int,
    ): CropState {
        if (!factor.isFinite() || factor <= 0) return crop
        val newZoom = (crop.zoom * factor).coerceIn(MIN_ZOOM, MAX_ZOOM)
        val k = newZoom / crop.zoom
        val moved = crop.copy(
            zoom = newZoom,
            panX = focalX * (1 - k) + crop.panX * k,
            panY = focalY * (1 - k) + crop.panY * k,
        )
        return clamp(moved, frame, imgW, imgH)
    }

    fun setZoom(crop: CropState, zoom: Double, frame: Frame, imgW: Int, imgH: Int): CropState =
        clamp(crop.copy(zoom = zoom.coerceIn(MIN_ZOOM, MAX_ZOOM)), frame, imgW, imgH)

    fun rotateClockwise(crop: CropState, frame: Frame, imgW: Int, imgH: Int): CropState =
        // The covering scale changes with the turn, so pan is re-clamped (and re-centred: the old pan means nothing now).
        clamp(crop.copy(rotationDegrees = (crop.rotationDegrees + 90) % 360, panX = 0.0, panY = 0.0), frame, imgW, imgH)

    fun reset(): CropState = CropState()

    fun setBrightness(crop: CropState, v: Double) = crop.copy(brightness = v.coerceIn(-1.0, 1.0))

    fun setContrast(crop: CropState, v: Double) = crop.copy(contrast = v.coerceIn(-1.0, 1.0))

    fun isDefault(crop: CropState) = crop == CropState()

    fun clamp(crop: CropState, frame: Frame, imgW: Int, imgH: Int): CropState {
        // Limits depend on zoom, so clamp zoom first and measure the limits against the clamped value.
        val zoomed = crop.copy(zoom = crop.zoom.coerceIn(MIN_ZOOM, MAX_ZOOM))
        val limits = ImageFit.panLimits(frame.widthUnits, frame.heightUnits, imgW, imgH, zoomed)
        return zoomed.copy(
            panX = zoomed.panX.coerceIn(-limits.maxX, limits.maxX),
            panY = zoomed.panY.coerceIn(-limits.maxY, limits.maxY),
        )
    }
}

/** Brightness/contrast as a 4×5 colour matrix (Android `ColorMatrix` layout, offsets in 0..255). */
object ColorAdjust {
    fun isIdentity(brightness: Double, contrast: Double) = abs(brightness) < 1e-9 && abs(contrast) < 1e-9

    /**
     * [brightness] and [contrast] in −1..1. Contrast scales around mid-grey (0 = unchanged, −1 = flat grey,
     * +1 = double); brightness shifts by up to ±255 levels.
     */
    fun matrix(brightness: Double, contrast: Double): FloatArray {
        val scale = (1 + contrast.coerceIn(-1.0, 1.0)).toFloat()
        val shift = (128f * (1 - scale)) + (brightness.coerceIn(-1.0, 1.0).toFloat() * 255f)
        return floatArrayOf(
            scale, 0f, 0f, 0f, shift,
            0f, scale, 0f, 0f, shift,
            0f, 0f, scale, 0f, shift,
            0f, 0f, 0f, 1f, 0f,
        )
    }
}
