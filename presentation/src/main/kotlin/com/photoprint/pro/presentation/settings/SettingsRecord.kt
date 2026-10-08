package com.photoprint.pro.presentation.settings

import com.photoprint.pro.domain.model.PaperSize
import com.photoprint.pro.domain.model.PhotoSize

/**
 * Flat, all-nullable shape of [AppSettings] for DataStore. Null means "not stored yet", so a first run, a
 * partly written file, or a value written by a newer app version all resolve to a sensible default.
 */
data class SettingsRecord(
    val photoName: String? = null,
    val photoWidthMm: Double? = null,
    val photoHeightMm: Double? = null,
    val paperName: String? = null,
    val paperWidthMm: Double? = null,
    val paperHeightMm: Double? = null,
    val spacingMm: Double? = null,
    val marginMm: Double? = null,
    val unit: String? = null,
    val quality: String? = null,
    val theme: String? = null,
    val language: String? = null,
)

object SettingsCodec {
    fun toRecord(s: AppSettings) = SettingsRecord(
        photoName = s.defaultPhotoSize.name, photoWidthMm = s.defaultPhotoSize.widthMm, photoHeightMm = s.defaultPhotoSize.heightMm,
        paperName = s.defaultPaperSize.name, paperWidthMm = s.defaultPaperSize.widthMm, paperHeightMm = s.defaultPaperSize.heightMm,
        spacingMm = s.defaultSpacingMm, marginMm = s.defaultMarginMm,
        unit = s.unit.name, quality = s.quality.name, theme = s.theme.name, language = s.language,
    )

    fun fromRecord(r: SettingsRecord): AppSettings {
        val d = AppSettings()
        fun good(v: Double?) = v?.takeIf { it.isFinite() }
        // A size is taken only as a complete, plausible set; half a size is worse than the default.
        val photo = sizeOrNull(r.photoName, good(r.photoWidthMm), good(r.photoHeightMm), min = 1.0)?.let { (n, w, h) -> PhotoSize(n, w, h) }
        val paper = sizeOrNull(r.paperName, good(r.paperWidthMm), good(r.paperHeightMm), min = 20.0)?.let { (n, w, h) -> PaperSize(n, w, h) }
        return AppSettings(
            defaultPhotoSize = photo ?: d.defaultPhotoSize,
            defaultPaperSize = paper ?: d.defaultPaperSize,
            defaultSpacingMm = good(r.spacingMm) ?: d.defaultSpacingMm,
            defaultMarginMm = good(r.marginMm) ?: d.defaultMarginMm,
            unit = enumOr(r.unit, d.unit),
            quality = enumOr(r.quality, d.quality),
            theme = enumOr(r.theme, d.theme),
            language = r.language,
        ).sanitized()
    }

    private fun sizeOrNull(name: String?, w: Double?, h: Double?, min: Double): Triple<String, Double, Double>? =
        if (name != null && w != null && h != null && w >= min && h >= min) Triple(name, w, h) else null

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: default
}

