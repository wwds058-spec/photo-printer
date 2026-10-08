package com.photoprint.pro.presentation.format

import com.photoprint.pro.domain.measurement.LengthUnit
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

class FormattingTest {
    @Test
    fun `sizes show in the chosen unit`() {
        assertEquals("35 × 45 mm", Formatting.size(35.0, 45.0))
        assertEquals("3.5 × 4.5 cm", Formatting.size(35.0, 45.0, LengthUnit.CM))
        assertEquals("2 × 2 in", Formatting.size(50.8, 50.8, LengthUnit.INCH))
        assertEquals("102 × 152 mm · 4.02 × 5.98 in", Formatting.sizeBoth(102.0, 152.0))
        assertEquals("25.5 × 35 mm", Formatting.size(25.5, 35.0))
    }

    @Test
    fun `counts are pluralised`() {
        assertEquals("No photos selected", Formatting.selected(0))
        assertEquals("1 photo selected", Formatting.selected(1))
        assertEquals("8 photos selected", Formatting.selected(8))
        assertEquals("1 sheet", Formatting.sheets(1))
        assertEquals("4 sheets", Formatting.sheets(4))
        assertEquals("Sheet 1 / 4", Formatting.sheetOf(0, 4))
        assertEquals("125%", Formatting.percent(125.4))
    }

    @Test
    fun `dates read naturally`() {
        val zone = ZoneId.of("Asia/Kolkata")
        fun ms(y: Int, mo: Int, d: Int, h: Int, mi: Int) = ZonedDateTime.of(y, mo, d, h, mi, 0, 0, zone).toInstant().toEpochMilli()
        val now = ms(2026, 10, 8, 18, 0)
        assertEquals("Today, 14:05", Formatting.when_(ms(2026, 10, 8, 14, 5), now, zone, Locale.ENGLISH))
        assertEquals("Yesterday, 23:59", Formatting.when_(ms(2026, 10, 7, 23, 59), now, zone, Locale.ENGLISH))
        assertEquals("1 Oct 2026", Formatting.when_(ms(2026, 10, 1, 9, 0), now, zone, Locale.ENGLISH))
        // "Yesterday" is by calendar day, not by 24 hours.
        assertEquals("Yesterday, 00:01", Formatting.when_(ms(2026, 10, 7, 0, 1), now, zone, Locale.ENGLISH))
    }

    @Test
    fun `dpi converts to pixels per millimetre`() {
        assertEquals(6.299, Formatting.pxPerMmFromDpi(160.0), 0.001)
    }
}
