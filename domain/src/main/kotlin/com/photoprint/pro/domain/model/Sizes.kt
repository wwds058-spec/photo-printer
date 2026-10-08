package com.photoprint.pro.domain.model

import com.photoprint.pro.domain.measurement.LengthUnit

/**
 * Physical size of one printed photo, in millimetres.
 *
 * Models deliberately do not throw on bad values: the UI edits custom sizes field by field, so
 * intermediate states can be invalid. [com.photoprint.pro.domain.layout.LayoutEngine] validates
 * and reports a typed error instead.
 */
data class PhotoSize(
    val name: String,
    val widthMm: Double,
    val heightMm: Double,
) {
    companion object {
        fun custom(width: Double, height: Double, unit: LengthUnit, name: String = "Custom") =
            PhotoSize(name, unit.toMm(width), unit.toMm(height))
    }
}

/** Physical size of the paper, in millimetres, as loaded in the printer (width × height). */
data class PaperSize(
    val name: String,
    val widthMm: Double,
    val heightMm: Double,
) {
    companion object {
        fun custom(width: Double, height: Double, unit: LengthUnit, name: String = "Custom") =
            PaperSize(name, unit.toMm(width), unit.toMm(height))
    }
}

/** Paper margins in millimetres. */
data class Margin(
    val topMm: Double,
    val bottomMm: Double,
    val leftMm: Double,
    val rightMm: Double,
) {
    companion object {
        val ZERO = Margin(0.0, 0.0, 0.0, 0.0)

        fun uniform(mm: Double) = Margin(mm, mm, mm, mm)
    }
}

/** Gap between neighbouring photos in millimetres. */
data class Spacing(
    val horizontalMm: Double,
    val verticalMm: Double,
) {
    companion object {
        val ZERO = Spacing(0.0, 0.0)

        fun uniform(mm: Double) = Spacing(mm, mm)
    }
}

/**
 * Built-in photo presets. These are configurable starting points, NOT a claim that a size is
 * accepted for any particular government document.
 */
object PhotoSizePresets {
    val Passport = PhotoSize("Passport", 35.0, 45.0)
    val IdPhoto = PhotoSize("ID Photo", 25.0, 35.0)
    val TwoByTwoInch = PhotoSize("2 × 2 inch", 51.0, 51.0)
    val Visa = PhotoSize("Visa", 50.0, 50.0)
    val Pan = PhotoSize("PAN Photo", 35.0, 45.0)

    val popular = listOf(Passport, IdPhoto, TwoByTwoInch)
    val international = listOf(Visa, Pan)
    val all = popular + international
}

object PaperSizePresets {
    val Photo4x6 = PaperSize("4 × 6 inch", 102.0, 152.0)
    val Photo5x7 = PaperSize("5 × 7 inch", 127.0, 178.0)
    val Photo6x8 = PaperSize("6 × 8 inch", 152.0, 203.0)
    val A4 = PaperSize("A4", 210.0, 297.0)
    val A5 = PaperSize("A5", 148.0, 210.0)
    val Letter = PaperSize("Letter", 216.0, 279.0)

    val photoPaper = listOf(Photo4x6, Photo5x7, Photo6x8)
    val standardPaper = listOf(A4, A5, Letter)
    val all = photoPaper + standardPaper
}
