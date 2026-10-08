package com.photoprint.pro.presentation.settings

import com.photoprint.pro.domain.measurement.LengthUnit
import com.photoprint.pro.domain.model.PaperSize
import com.photoprint.pro.domain.model.PaperSizePresets
import com.photoprint.pro.domain.model.PhotoSize
import com.photoprint.pro.domain.model.PhotoSizePresets
import com.photoprint.pro.domain.printing.PrintQuality
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** User defaults. Millimetres by default: the physical-print workflow's native unit. */
data class AppSettings(
    val defaultPhotoSize: PhotoSize = PhotoSizePresets.Passport,
    val defaultPaperSize: PaperSize = PaperSizePresets.Photo4x6,
    val defaultSpacingMm: Double = 2.0,
    val defaultMarginMm: Double = 3.0,
    val unit: LengthUnit = LengthUnit.MM,
    val quality: PrintQuality = PrintQuality.BEST,
    val theme: ThemeMode = ThemeMode.LIGHT,
    /** BCP-47 tag, or null to follow the device language. */
    val language: String? = null,
) {
    companion object {
        const val MAX_MARGIN_MM = 20.0
        const val MAX_SPACING_MM = 10.0
    }

    /** Values outside the ranges the layout screen offers are pulled back inside them. */
    fun sanitized() = copy(
        defaultSpacingMm = defaultSpacingMm.coerceIn(0.0, MAX_SPACING_MM),
        defaultMarginMm = defaultMarginMm.coerceIn(0.0, MAX_MARGIN_MM),
    )
}

/** DataStore-backed in the app module. */
interface SettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun update(transform: (AppSettings) -> AppSettings)
}

class InMemorySettingsRepository(initial: AppSettings = AppSettings()) : SettingsRepository {
    private val state = MutableStateFlow(initial)
    override val settings: Flow<AppSettings> get() = state

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        state.value = transform(state.value).sanitized()
    }
}
