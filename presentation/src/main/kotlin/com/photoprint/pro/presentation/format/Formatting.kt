package com.photoprint.pro.presentation.format

import com.photoprint.pro.domain.measurement.LengthUnit
import com.photoprint.pro.domain.measurement.Measurement
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Text for physical sizes, dates and counts. English; localisation hooks in here later. */
object Formatting {

    /** "35 × 45 mm". */
    fun size(widthMm: Double, heightMm: Double, unit: LengthUnit = LengthUnit.MM): String {
        val digits = if (unit == LengthUnit.INCH) 2 else 1
        fun f(mm: Double) = String.format(Locale.ROOT, "%.${digits}f", unit.fromMm(mm)).trimEnd('0').trimEnd('.')
        return "${f(widthMm)} × ${f(heightMm)} ${unit.symbol}"
    }

    /** Both millimetres and inches, as the paper screen requires: "102 × 152 mm · 4.02 × 5.98 in". */
    fun sizeBoth(widthMm: Double, heightMm: Double): String =
        "${size(widthMm, heightMm, LengthUnit.MM)} · ${size(widthMm, heightMm, LengthUnit.INCH)}"

    /** "Sheet 2 / 4". */
    fun sheetOf(index: Int, total: Int) = "Sheet ${index + 1} / $total"

    fun photos(n: Int) = if (n == 1) "1 photo" else "$n photos"

    fun sheets(n: Int) = if (n == 1) "1 sheet" else "$n sheets"

    fun selected(n: Int) = if (n == 0) "No photos selected" else "${photos(n)} selected"

    fun percent(p: Double) = "${Math.round(p)}%"

    /** Pixels per mm for the device's real-world size: 160 dpi → 6.30 px/mm. */
    fun pxPerMmFromDpi(dpi: Double) = dpi / Measurement.MM_PER_INCH

    /** "Today, 14:05" / "Yesterday, 09:30" / "12 Oct 2026". */
    fun when_(epochMillis: Long, nowMillis: Long, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): String {
        val then = Instant.ofEpochMilli(epochMillis).atZone(zone)
        val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
        val days = ChronoUnit.DAYS.between(then.toLocalDate(), now.toLocalDate())
        val time = DateTimeFormatter.ofPattern("HH:mm", locale).format(then)
        return when {
            days == 0L -> "Today, $time"
            days == 1L -> "Yesterday, $time"
            else -> DateTimeFormatter.ofPattern("d MMM yyyy", locale).format(then)
        }
    }

}
