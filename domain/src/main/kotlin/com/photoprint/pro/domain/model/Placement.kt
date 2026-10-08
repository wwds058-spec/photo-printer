package com.photoprint.pro.domain.model

import com.photoprint.pro.domain.measurement.Rect

/**
 * How the source image sits inside its fixed physical frame. Editing changes this, never the
 * frame's physical size.
 *
 * @param zoom 1.0 = image covers the frame; larger values zoom in.
 * @param panX / [panY] pan as a fraction of the frame size, 0 = centred.
 * @param rotationDegrees user rotation of the image content within the frame (multiple of 90 for now).
 */
data class CropState(
    val zoom: Double = 1.0,
    val panX: Double = 0.0,
    val panY: Double = 0.0,
    val rotationDegrees: Int = 0,
    val brightness: Double = 0.0,
    val contrast: Double = 0.0,
)

/**
 * One photo frame on a sheet. All values are millimetres from the paper's top-left corner.
 *
 * [widthMm]/[heightMm] are the dimensions the frame occupies ON THE PAPER. When the engine turns
 * the photo 90° to fit better, [rotation] is 90 and width/height are already swapped; the image
 * content must be drawn rotated by [rotation] inside this frame.
 */
data class PhotoPlacement(
    val photoId: String,
    val xMm: Double,
    val yMm: Double,
    val widthMm: Double,
    val heightMm: Double,
    val rotation: Int = 0,
    val cropState: CropState = CropState(),
) {
    val rightMm: Double get() = xMm + widthMm
    val bottomMm: Double get() = yMm + heightMm
    val rectMm: Rect get() = Rect(xMm, yMm, widthMm, heightMm)
}

/** A cutting guide segment in millimetres. Never crosses the interior of any photo frame. */
data class CutLine(
    val x1Mm: Double,
    val y1Mm: Double,
    val x2Mm: Double,
    val y2Mm: Double,
)

/**
 * One physical sheet. The single source of truth for preview, PDF and print.
 *
 * @param sheetIndex zero-based; display as `sheetIndex + 1`.
 */
data class SheetLayout(
    val paperWidthMm: Double,
    val paperHeightMm: Double,
    val placements: List<PhotoPlacement>,
    val cutLines: List<CutLine>,
    val sheetIndex: Int,
    val totalSheets: Int,
) {
    val sheetNumber: Int get() = sheetIndex + 1
}
