package com.photoprint.pro.presentation.settings

import com.photoprint.pro.domain.measurement.LengthUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class AppSettingsTest {
    @Test
    fun `defaults are the professional workflow`() {
        val s = AppSettings()
        assertEquals(LengthUnit.MM, s.unit)
        assertEquals("Passport", s.defaultPhotoSize.name)
        assertEquals(102.0, s.defaultPaperSize.widthMm)
    }

    @Test
    fun `updates are sanitised into the ranges the layout screen offers`() {
        val repo = InMemorySettingsRepository()
        runBlocking { repo.update { it.copy(defaultMarginMm = 99.0, defaultSpacingMm = -1.0, unit = LengthUnit.INCH) } }
        val s = runBlocking { repo.settings.first() }
        assertEquals(AppSettings.MAX_MARGIN_MM, s.defaultMarginMm)
        assertEquals(0.0, s.defaultSpacingMm)
        assertEquals(LengthUnit.INCH, s.unit)
    }
}
