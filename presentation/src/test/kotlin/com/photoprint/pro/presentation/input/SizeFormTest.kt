package com.photoprint.pro.presentation.input

import com.photoprint.pro.domain.measurement.LengthUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SizeFormTest {
    private fun photo(w: String, h: String, unit: LengthUnit = LengthUnit.MM) = SizeForm(SizeKind.PHOTO, w, h, unit)

    @Test
    fun `valid millimetres`() {
        val f = photo("35", "45")
        assertTrue(f.isValid)
        assertEquals(35.0 to 45.0, f.sizeMm)
    }

    @Test
    fun `units are normalised to millimetres`() {
        assertEquals(35.0 to 45.0, photo("3.5", "4.5", LengthUnit.CM).sizeMm)
        val (w, h) = photo("2", "2", LengthUnit.INCH).sizeMm!!
        assertEquals(50.8, w, 1e-9)
        assertEquals(50.8, h, 1e-9)
    }

    @Test
    fun `comma decimals and whitespace are accepted`() {
        assertEquals(35.5 to 45.0, photo(" 35,5 ", "45").sizeMm)
    }

    @Test
    fun `each field reports its own error`() {
        val f = photo("", "abc")
        assertEquals(SizeFieldError.EMPTY, f.width.error)
        assertEquals(SizeFieldError.NOT_A_NUMBER, f.height.error)
        assertFalse(f.isValid)
        assertNull(f.sizeMm)
        assertEquals(SizeFieldError.TOO_SMALL, photo("0", "45").width.error)
        assertEquals(SizeFieldError.TOO_SMALL, photo("-3", "45").width.error)
        assertEquals(SizeFieldError.TOO_LARGE, photo("35", "5000").height.error)
        assertEquals(SizeFieldError.NOT_A_NUMBER, photo("NaN", "45").width.error)
        assertEquals(SizeFieldError.NOT_A_NUMBER, photo("Infinity", "45").width.error)
    }

    @Test
    fun `limits differ for photos and paper`() {
        assertTrue(SizeForm(SizeKind.PHOTO, "10", "10").isValid)
        assertEquals(SizeFieldError.TOO_SMALL, SizeForm(SizeKind.PAPER, "10", "10").width.error)
        assertTrue(SizeForm(SizeKind.PAPER, "1189", "841").isValid) // A0
        assertEquals(SizeFieldError.TOO_LARGE, SizeForm(SizeKind.PHOTO, "1189", "841").width.error)
    }

    @Test
    fun `switching unit converts the numbers instead of relabelling them`() {
        val cm = photo("35", "45").withUnit(LengthUnit.CM)
        assertEquals("3.5", cm.widthText)
        assertEquals("4.5", cm.heightText)
        assertEquals(35.0 to 45.0, cm.sizeMm, "same physical size")
        val inch = photo("25.4", "50.8").withUnit(LengthUnit.INCH)
        assertEquals("1", inch.widthText)
        assertEquals("2", inch.heightText)
    }

    @Test
    fun `switching unit keeps text that is not yet valid`() {
        val f = photo("35", "4x").withUnit(LengthUnit.CM)
        assertEquals("3.5", f.widthText)
        assertEquals("4x", f.heightText)
    }

    @Test
    fun `format drops trailing zeros and limits decimals`() {
        assertEquals("35", SizeForm.format(35.0))
        assertEquals("3.5", SizeForm.format(3.5))
        assertEquals("1.38", SizeForm.format(1.3779))
        assertEquals("0", SizeForm.format(0.0))
        val f = SizeForm.fromMm(SizeKind.PAPER, 102.0, 152.0, LengthUnit.INCH)
        assertEquals("4.02", f.widthText)
        assertEquals("5.98", f.heightText)
    }
}
