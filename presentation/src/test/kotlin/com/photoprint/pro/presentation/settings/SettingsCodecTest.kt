package com.photoprint.pro.presentation.settings

import com.photoprint.pro.domain.measurement.LengthUnit
import com.photoprint.pro.domain.model.PaperSize
import com.photoprint.pro.domain.model.PaperSizePresets
import com.photoprint.pro.domain.model.PhotoSize
import com.photoprint.pro.domain.model.PhotoSizePresets
import com.photoprint.pro.domain.printing.PrintQuality
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SettingsCodecTest {
    @Test
    fun `settings round trip`() {
        val s = AppSettings(
            defaultPhotoSize = PhotoSize("Custom", 40.0, 60.0), defaultPaperSize = PaperSize("5 × 7", 127.0, 178.0),
            defaultSpacingMm = 1.5, defaultMarginMm = 8.0, unit = LengthUnit.INCH, quality = PrintQuality.DRAFT,
            theme = ThemeMode.DARK, language = "en",
        )
        assertEquals(s, SettingsCodec.fromRecord(SettingsCodec.toRecord(s)))
    }

    @Test
    fun `defaults round trip including no language`() {
        val back = SettingsCodec.fromRecord(SettingsCodec.toRecord(AppSettings()))
        assertEquals(AppSettings(), back)
        assertNull(back.language)
    }

    @Test
    fun `an empty record is the default settings`() {
        assertEquals(AppSettings(), SettingsCodec.fromRecord(SettingsRecord()))
    }

    @Test
    fun `unknown enum names fall back individually`() {
        val r = SettingsCodec.toRecord(AppSettings(unit = LengthUnit.CM)).copy(unit = "FURLONG", theme = "SOLARIZED", quality = null)
        val s = SettingsCodec.fromRecord(r)
        assertEquals(AppSettings().unit, s.unit)
        assertEquals(AppSettings().theme, s.theme)
        assertEquals(AppSettings().quality, s.quality)
    }

    @Test
    fun `half a size or an implausible size is replaced by the default`() {
        assertEquals(PhotoSizePresets.Passport, SettingsCodec.fromRecord(SettingsRecord(photoName = "X", photoWidthMm = 40.0)).defaultPhotoSize)
        assertEquals(PhotoSizePresets.Passport, SettingsCodec.fromRecord(SettingsRecord(photoName = "X", photoWidthMm = 0.0, photoHeightMm = 40.0)).defaultPhotoSize)
        assertEquals(PaperSizePresets.Photo4x6, SettingsCodec.fromRecord(SettingsRecord(paperName = "tiny", paperWidthMm = 5.0, paperHeightMm = 5.0)).defaultPaperSize)
        assertEquals(PhotoSizePresets.Passport, SettingsCodec.fromRecord(SettingsRecord(photoName = "X", photoWidthMm = Double.NaN, photoHeightMm = 40.0)).defaultPhotoSize)
    }

    @Test
    fun `out of range margins and spacing are pulled in`() {
        val s = SettingsCodec.fromRecord(SettingsRecord(marginMm = 500.0, spacingMm = -3.0))
        assertEquals(AppSettings.MAX_MARGIN_MM, s.defaultMarginMm)
        assertEquals(0.0, s.defaultSpacingMm)
        assertEquals(AppSettings().defaultMarginMm, SettingsCodec.fromRecord(SettingsRecord(marginMm = Double.NaN)).defaultMarginMm)
    }
}
