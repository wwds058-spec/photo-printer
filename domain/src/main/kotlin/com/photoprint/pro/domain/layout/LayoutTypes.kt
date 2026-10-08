package com.photoprint.pro.domain.layout

import com.photoprint.pro.domain.model.CropState
import com.photoprint.pro.domain.model.Margin
import com.photoprint.pro.domain.model.PaperSize
import com.photoprint.pro.domain.model.PhotoSize
import com.photoprint.pro.domain.model.SheetLayout
import com.photoprint.pro.domain.model.Spacing

data class PhotoCopies(val photoId: String, val copies: Int)

/** How many photos to place. */
sealed interface FillStrategy {
    /** Exactly these photos and copy counts, in order. */
    data class Copies(val items: List<PhotoCopies>) : FillStrategy {
        companion object {
            fun of(photoId: String, copies: Int) = Copies(listOf(PhotoCopies(photoId, copies)))
        }
    }

    /** Auto Fill Paper: fill [sheets] complete sheets, cycling through [photoIds] in order. */
    data class FillSheets(val photoIds: List<String>, val sheets: Int = 1) : FillStrategy
}

/** Everything the engine needs. Pure data; no Android or UI types. */
data class LayoutRequest(
    val paper: PaperSize,
    val photo: PhotoSize,
    val fill: FillStrategy,
    val margin: Margin = Margin.ZERO,
    val spacing: Spacing = Spacing.ZERO,
    /** Allow turning photos 90° when that fits more per sheet. */
    val allowRotation: Boolean = true,
    val showCutLines: Boolean = false,
    /** Crop state given to every new placement. */
    val defaultCrop: CropState = CropState(),
)

enum class PhotoOrientation(val rotationDegrees: Int) {
    UPRIGHT(0),
    ROTATED_90(90),
}

/**
 * The chosen grid for one sheet. Every sheet uses this same grid, so cut positions are identical
 * across sheets. The grid is centred in the usable (inside-margin) area.
 */
data class GridPlan(
    val paperWidthMm: Double,
    val paperHeightMm: Double,
    val orientation: PhotoOrientation,
    val columns: Int,
    val rows: Int,
    /** Frame size on paper (already swapped when [orientation] is rotated). */
    val cellWidthMm: Double,
    val cellHeightMm: Double,
    val spacingXMm: Double,
    val spacingYMm: Double,
    val originXMm: Double,
    val originYMm: Double,
    val usableWidthMm: Double,
    val usableHeightMm: Double,
) {
    val photosPerSheet: Int get() = columns * rows
    val gridWidthMm: Double get() = columns * cellWidthMm + (columns - 1) * spacingXMm
    val gridHeightMm: Double get() = rows * cellHeightMm + (rows - 1) * spacingYMm

    /** Usable width left over after the grid (split evenly left/right). */
    val unusedWidthMm: Double get() = usableWidthMm - gridWidthMm
    val unusedHeightMm: Double get() = usableHeightMm - gridHeightMm
}

/** Complete multi-sheet result. */
data class LayoutPlan(
    val grid: GridPlan,
    val totalPhotos: Int,
    val sheets: List<SheetLayout>,
) {
    val photosPerSheet: Int get() = grid.photosPerSheet
    val sheetCount: Int get() = sheets.size
}

/** Why a layout could not be produced. The UI maps these to friendly, localised messages. */
sealed interface LayoutError {
    data class InvalidPaper(val widthMm: Double, val heightMm: Double) : LayoutError
    data class InvalidPhoto(val widthMm: Double, val heightMm: Double) : LayoutError
    data object InvalidMargin : LayoutError
    data object InvalidSpacing : LayoutError

    /** Margins leave no printable area. */
    data class MarginsConsumePaper(val usableWidthMm: Double, val usableHeightMm: Double) : LayoutError

    /** Not even one photo fits in either orientation. */
    data class PhotoDoesNotFit(
        val photoWidthMm: Double,
        val photoHeightMm: Double,
        val usableWidthMm: Double,
        val usableHeightMm: Double,
        val rotationWasAllowed: Boolean,
    ) : LayoutError

    data object NoPhotos : LayoutError
    data object InvalidCopies : LayoutError
    data class TooManyPhotos(val requested: Long, val limit: Int) : LayoutError
}

sealed interface LayoutOutcome<out T> {
    data class Success<T>(val value: T) : LayoutOutcome<T>
    data class Failure(val error: LayoutError) : LayoutOutcome<Nothing>
}
