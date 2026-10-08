package com.photoprint.pro.presentation.input

import com.photoprint.pro.domain.layout.LayoutEngine
import com.photoprint.pro.domain.measurement.LengthUnit
import java.util.Locale

enum class SizeKind(val minMm: Double, val maxMm: Double) {
    PHOTO(LayoutEngine.MIN_PHOTO_MM, 1000.0),
    PAPER(20.0, 2000.0),
}

enum class SizeFieldError { EMPTY, NOT_A_NUMBER, TOO_SMALL, TOO_LARGE }

/** Raw text as typed, so the form can show half-finished input without losing it. */
data class SizeForm(
    val kind: SizeKind,
    val widthText: String = "",
    val heightText: String = "",
    val unit: LengthUnit = LengthUnit.MM,
) {
    data class Field(val valueMm: Double?, val error: SizeFieldError?)

    val width: Field get() = parse(widthText)
    val height: Field get() = parse(heightText)

    /** Both dimensions in millimetres, or null while either is missing/invalid. */
    val sizeMm: Pair<Double, Double>? get() {
        val w = width.valueMm
        val h = height.valueMm
        return if (w != null && h != null) w to h else null
    }

    val isValid: Boolean get() = sizeMm != null

    fun withUnit(newUnit: LengthUnit): SizeForm {
        if (newUnit == unit) return this
        // Convert what is already valid, so 35 mm becomes 3.5 cm and not 35 cm.
        fun convert(f: Field, text: String) = f.valueMm?.let { format(newUnit.fromMm(it)) } ?: text
        return copy(unit = newUnit, widthText = convert(width, widthText), heightText = convert(height, heightText))
    }

    private fun parse(text: String): Field {
        val t = text.trim().replace(',', '.')
        if (t.isEmpty()) return Field(null, SizeFieldError.EMPTY)
        val v = t.toDoubleOrNull()?.takeIf { it.isFinite() } ?: return Field(null, SizeFieldError.NOT_A_NUMBER)
        val mm = unit.toMm(v)
        return when {
            mm < kind.minMm -> Field(null, SizeFieldError.TOO_SMALL)
            mm > kind.maxMm -> Field(null, SizeFieldError.TOO_LARGE)
            else -> Field(mm, null)
        }
    }

    companion object {
        fun fromMm(kind: SizeKind, widthMm: Double, heightMm: Double, unit: LengthUnit = LengthUnit.MM) = SizeForm(
            kind, format(unit.fromMm(widthMm)), format(unit.fromMm(heightMm)), unit,
        )

        /** Up to 2 decimals, no trailing zeros: 35.0 → "35", 3.5 → "3.5", 1.3779 → "1.38". */
        fun format(v: Double): String =
            String.format(Locale.ROOT, "%.2f", v).trimEnd('0').trimEnd('.').ifEmpty { "0" }
    }
}
