package com.photoprint.pro.domain.measurement

/**
 * Physical-unit helpers. Every physical dimension in the app is stored in millimetres; other
 * units exist only at the UI edge (input and display).
 */
object Measurement {
    const val MM_PER_INCH = 25.4
    const val POINTS_PER_INCH = 72.0
    const val MILS_PER_INCH = 1000.0

    /** Millimetres to PDF points (1/72 inch). */
    fun mmToPoints(mm: Double): Double = mm / MM_PER_INCH * POINTS_PER_INCH

    fun pointsToMm(points: Double): Double = points / POINTS_PER_INCH * MM_PER_INCH

    /** Millimetres to mils (1/1000 inch), the unit of `android.print.PrintAttributes.MediaSize`. */
    fun mmToMils(mm: Double): Double = mm / MM_PER_INCH * MILS_PER_INCH

    fun milsToMm(mils: Double): Double = mils / MILS_PER_INCH * MM_PER_INCH
}

/** Units the user can type or see. Internally everything is millimetres. */
enum class LengthUnit(val symbol: String, val mmPerUnit: Double) {
    MM("mm", 1.0),
    CM("cm", 10.0),
    INCH("in", Measurement.MM_PER_INCH),
    ;

    fun toMm(value: Double): Double = value * mmPerUnit

    fun fromMm(mm: Double): Double = mm / mmPerUnit
}

/** Axis-aligned rectangle. Origin is the top-left corner; units depend on the producer. */
data class Rect(
    val left: Double,
    val top: Double,
    val width: Double,
    val height: Double,
) {
    val right: Double get() = left + width
    val bottom: Double get() = top + height

    fun scaled(factor: Double): Rect = Rect(left * factor, top * factor, width * factor, height * factor)
}
