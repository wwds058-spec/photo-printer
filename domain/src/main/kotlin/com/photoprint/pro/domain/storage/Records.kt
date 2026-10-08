package com.photoprint.pro.domain.storage

import com.photoprint.pro.domain.model.CropState
import com.photoprint.pro.domain.model.Margin
import com.photoprint.pro.domain.model.PaperSize
import com.photoprint.pro.domain.model.PhotoSize
import com.photoprint.pro.domain.model.PrintProject
import com.photoprint.pro.domain.model.PrintTemplate
import com.photoprint.pro.domain.model.ProjectPhoto
import com.photoprint.pro.domain.model.Spacing
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Flat, database-friendly shapes of the domain models. Room entities and DataStore keys mirror these field
 * for field, so all the real mapping logic (and its corner cases) lives here, in pure Kotlin, under test.
 *
 * Only metadata is stored. Photo pixels stay in app-private files; [ProjectRecord.photosJson] holds the
 * file references, sizes, copies and crops.
 */
data class ProjectRecord(
    val id: String,
    val name: String,
    val photosJson: String,
    val photoName: String,
    val photoWidthMm: Double,
    val photoHeightMm: Double,
    val paperName: String,
    val paperWidthMm: Double,
    val paperHeightMm: Double,
    val marginTopMm: Double,
    val marginBottomMm: Double,
    val marginLeftMm: Double,
    val marginRightMm: Double,
    val spacingHorizontalMm: Double,
    val spacingVerticalMm: Double,
    val allowRotation: Boolean,
    val showCutLines: Boolean,
    val fillSheets: Int?,
    val printerId: String?,
    val createdAtMillis: Long,
    val modifiedAtMillis: Long,
)

data class TemplateRecord(
    val id: String,
    val name: String,
    val photoName: String,
    val photoWidthMm: Double,
    val photoHeightMm: Double,
    val paperName: String,
    val paperWidthMm: Double,
    val paperHeightMm: Double,
    val marginTopMm: Double,
    val marginBottomMm: Double,
    val marginLeftMm: Double,
    val marginRightMm: Double,
    val spacingHorizontalMm: Double,
    val spacingVerticalMm: Double,
    val allowRotation: Boolean,
    val showCutLines: Boolean,
)

object StorageCodec {
    /** Bump if the photos JSON shape ever changes incompatibly; readers can then migrate by version. */
    const val PHOTOS_VERSION = 1

    private val json = Json { ignoreUnknownKeys = true }

    // ---- photos --------------------------------------------------------------------------

    fun encodePhotos(photos: List<ProjectPhoto>): String = buildJsonObject {
        put("v", PHOTOS_VERSION)
        put(
            "photos",
            JsonArray(
                photos.map { p ->
                    buildJsonObject {
                        put("id", p.photoId)
                        put("ref", p.sourceRef)
                        put("w", p.widthPx)
                        put("h", p.heightPx)
                        put("copies", p.copies)
                        put("zoom", p.crop.zoom)
                        put("panX", p.crop.panX)
                        put("panY", p.crop.panY)
                        put("rot", p.crop.rotationDegrees)
                        put("bright", p.crop.brightness)
                        put("contrast", p.crop.contrast)
                    }
                },
            ),
        )
    }.toString()

    /**
     * Null when the text is not a photos document at all (corrupt row). Individual malformed entries are
     * skipped rather than failing the whole project; a field that is missing falls back to its default.
     */
    fun decodePhotos(text: String): List<ProjectPhoto>? {
        val root = try { json.parseToJsonElement(text).jsonObject } catch (_: Exception) { return null }
        val array = try { root["photos"]?.jsonArray } catch (_: Exception) { null } ?: return null
        return array.mapNotNull { el ->
            try {
                val o = el.jsonObject
                val id = o.str("id") ?: return@mapNotNull null
                val ref = o.str("ref") ?: return@mapNotNull null
                val w = o.int("w")?.takeIf { it > 0 } ?: return@mapNotNull null
                val h = o.int("h")?.takeIf { it > 0 } ?: return@mapNotNull null
                ProjectPhoto(
                    photoId = id,
                    sourceRef = ref,
                    widthPx = w,
                    heightPx = h,
                    copies = o.int("copies")?.coerceAtLeast(1) ?: 1,
                    crop = CropState(
                        zoom = o.dbl("zoom") ?: 1.0,
                        panX = o.dbl("panX") ?: 0.0,
                        panY = o.dbl("panY") ?: 0.0,
                        rotationDegrees = o.int("rot")?.takeIf { it % 90 == 0 } ?: 0,
                        brightness = o.dbl("bright") ?: 0.0,
                        contrast = o.dbl("contrast") ?: 0.0,
                    ),
                )
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
    private fun JsonObject.int(k: String): Int? = (this[k] as? JsonPrimitive)?.intOrNull
    private fun JsonObject.dbl(k: String): Double? = (this[k] as? JsonPrimitive)?.doubleOrNull?.takeIf { it.isFinite() }

    // ---- projects ------------------------------------------------------------------------

    fun toRecord(p: PrintProject) = ProjectRecord(
        id = p.id, name = p.name, photosJson = encodePhotos(p.photos),
        photoName = p.photoSize.name, photoWidthMm = p.photoSize.widthMm, photoHeightMm = p.photoSize.heightMm,
        paperName = p.paperSize.name, paperWidthMm = p.paperSize.widthMm, paperHeightMm = p.paperSize.heightMm,
        marginTopMm = p.margin.topMm, marginBottomMm = p.margin.bottomMm, marginLeftMm = p.margin.leftMm, marginRightMm = p.margin.rightMm,
        spacingHorizontalMm = p.spacing.horizontalMm, spacingVerticalMm = p.spacing.verticalMm,
        allowRotation = p.allowRotation, showCutLines = p.showCutLines, fillSheets = p.fillSheets, printerId = p.printerId,
        createdAtMillis = p.createdAtMillis, modifiedAtMillis = p.modifiedAtMillis,
    )

    /** Null if the stored photos are unreadable, so one damaged row cannot break the project list. */
    fun toProject(r: ProjectRecord): PrintProject? {
        val photos = decodePhotos(r.photosJson) ?: return null
        return PrintProject(
            id = r.id, name = r.name, photos = photos,
            photoSize = PhotoSize(r.photoName, r.photoWidthMm, r.photoHeightMm),
            paperSize = PaperSize(r.paperName, r.paperWidthMm, r.paperHeightMm),
            margin = Margin(r.marginTopMm, r.marginBottomMm, r.marginLeftMm, r.marginRightMm),
            spacing = Spacing(r.spacingHorizontalMm, r.spacingVerticalMm),
            allowRotation = r.allowRotation, showCutLines = r.showCutLines,
            fillSheets = r.fillSheets?.takeIf { it >= 1 }, printerId = r.printerId,
            createdAtMillis = r.createdAtMillis, modifiedAtMillis = r.modifiedAtMillis,
        )
    }

    // ---- templates -----------------------------------------------------------------------

    fun toRecord(t: PrintTemplate) = TemplateRecord(
        id = t.id, name = t.name,
        photoName = t.photoSize.name, photoWidthMm = t.photoSize.widthMm, photoHeightMm = t.photoSize.heightMm,
        paperName = t.paperSize.name, paperWidthMm = t.paperSize.widthMm, paperHeightMm = t.paperSize.heightMm,
        marginTopMm = t.margin.topMm, marginBottomMm = t.margin.bottomMm, marginLeftMm = t.margin.leftMm, marginRightMm = t.margin.rightMm,
        spacingHorizontalMm = t.spacing.horizontalMm, spacingVerticalMm = t.spacing.verticalMm,
        allowRotation = t.allowRotation, showCutLines = t.showCutLines,
    )

    /** Stored templates are always user templates; built-ins live in code. */
    fun toTemplate(r: TemplateRecord) = PrintTemplate(
        id = r.id, name = r.name,
        photoSize = PhotoSize(r.photoName, r.photoWidthMm, r.photoHeightMm),
        paperSize = PaperSize(r.paperName, r.paperWidthMm, r.paperHeightMm),
        margin = Margin(r.marginTopMm, r.marginBottomMm, r.marginLeftMm, r.marginRightMm),
        spacing = Spacing(r.spacingHorizontalMm, r.spacingVerticalMm),
        allowRotation = r.allowRotation, showCutLines = r.showCutLines, builtIn = false,
    )
}

/** Unit stored by name; unknown names (e.g. from a newer app version) fall back to the default. */
internal inline fun <reified E : Enum<E>> enumOrNull(name: String?): E? = name?.let { n -> enumValues<E>().firstOrNull { it.name == n } }

