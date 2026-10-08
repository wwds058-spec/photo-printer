package com.photoprint.pro.domain.model

import com.photoprint.pro.domain.layout.FillStrategy
import com.photoprint.pro.domain.layout.LayoutRequest
import com.photoprint.pro.domain.layout.PhotoCopies

/**
 * A photo in a project. Pixels never live in the database: [sourceRef] is an opaque reference the
 * app resolves to a file in its private storage (the domain does not interpret it).
 */
data class ProjectPhoto(
    val photoId: String,
    val sourceRef: String,
    val widthPx: Int,
    val heightPx: Int,
    val copies: Int = 1,
    val crop: CropState = CropState(),
)

/** Everything needed to reopen, re-layout and reprint. Timestamps are epoch milliseconds. */
data class PrintProject(
    val id: String,
    val name: String,
    val photos: List<ProjectPhoto>,
    val photoSize: PhotoSize,
    val paperSize: PaperSize,
    val margin: Margin = Margin.ZERO,
    val spacing: Spacing = Spacing.ZERO,
    val allowRotation: Boolean = true,
    val showCutLines: Boolean = false,
    /** Auto Fill Paper: fill this many whole sheets, cycling the photos. Null = use per-photo [ProjectPhoto.copies]. */
    val fillSheets: Int? = null,
    /** Last-used printer id, a convenience only; the printer may no longer exist. */
    val printerId: String? = null,
    val createdAtMillis: Long,
    val modifiedAtMillis: Long,
) {
    val requestedCopies: Int get() = photos.sumOf { it.copies }
}

/** The one place a project becomes a layout request, so preview, PDF and print agree. */
fun PrintProject.toLayoutRequest(): LayoutRequest = LayoutRequest(
    paper = paperSize,
    photo = photoSize,
    fill = fillSheets?.let { FillStrategy.FillSheets(photos.map { p -> p.photoId }, it) }
        ?: FillStrategy.Copies(photos.map { PhotoCopies(it.photoId, it.copies) }),
    margin = margin,
    spacing = spacing,
    allowRotation = allowRotation,
    showCutLines = showCutLines,
    crops = photos.associate { it.photoId to it.crop },
)

/** Layout settings only: templates never duplicate photos. */
data class PrintTemplate(
    val id: String,
    val name: String,
    val photoSize: PhotoSize,
    val paperSize: PaperSize,
    val margin: Margin = Margin.ZERO,
    val spacing: Spacing = Spacing.ZERO,
    val allowRotation: Boolean = true,
    val showCutLines: Boolean = false,
    val builtIn: Boolean = false,
) {
    fun toProject(id: String, name: String, photos: List<ProjectPhoto>, nowMillis: Long) = PrintProject(
        id = id,
        name = name,
        photos = photos,
        photoSize = photoSize,
        paperSize = paperSize,
        margin = margin,
        spacing = spacing,
        allowRotation = allowRotation,
        showCutLines = showCutLines,
        createdAtMillis = nowMillis,
        modifiedAtMillis = nowMillis,
    )
}

object BuiltInTemplates {
    private val defaultMargin = Margin.uniform(3.0)
    private val defaultSpacing = Spacing.uniform(2.0)

    private fun t(id: String, name: String, photo: PhotoSize, paper: PaperSize) =
        PrintTemplate(id, name, photo, paper, defaultMargin, defaultSpacing, builtIn = true)

    val passport4x6 = t("builtin.passport-4x6", "Passport 35×45 → 4×6", PhotoSizePresets.Passport, PaperSizePresets.Photo4x6)
    val id4x6 = t("builtin.id-4x6", "ID 25×35 → 4×6", PhotoSizePresets.IdPhoto, PaperSizePresets.Photo4x6)
    val twoByTwo4x6 = t("builtin.2x2-4x6", "2×2 → 4×6", PhotoSizePresets.TwoByTwoInch, PaperSizePresets.Photo4x6)
    val passportA4 = t("builtin.passport-a4", "Passport → A4", PhotoSizePresets.Passport, PaperSizePresets.A4)

    val all = listOf(passport4x6, id4x6, twoByTwo4x6, passportA4)
}
