package com.photoprint.pro.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.photoprint.pro.domain.storage.ProjectRecord
import com.photoprint.pro.domain.storage.TemplateRecord

/**
 * Room tables. They mirror [ProjectRecord] / [TemplateRecord] field for field; every rule about what the
 * values mean (and what to do with damaged ones) lives in `StorageCodec`, which is unit-tested.
 * Only metadata is stored: photo pixels stay in app-private files and are referenced from [photosJson].
 */
@Entity(tableName = "projects", indices = [Index("modifiedAtMillis")])
data class ProjectEntity(
    @PrimaryKey val id: String,
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

@Entity(tableName = "templates")
data class TemplateEntity(
    @PrimaryKey val id: String,
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

fun ProjectRecord.toEntity() = ProjectEntity(
    id, name, photosJson, photoName, photoWidthMm, photoHeightMm, paperName, paperWidthMm, paperHeightMm,
    marginTopMm, marginBottomMm, marginLeftMm, marginRightMm, spacingHorizontalMm, spacingVerticalMm,
    allowRotation, showCutLines, fillSheets, printerId, createdAtMillis, modifiedAtMillis,
)

fun ProjectEntity.toRecord() = ProjectRecord(
    id, name, photosJson, photoName, photoWidthMm, photoHeightMm, paperName, paperWidthMm, paperHeightMm,
    marginTopMm, marginBottomMm, marginLeftMm, marginRightMm, spacingHorizontalMm, spacingVerticalMm,
    allowRotation, showCutLines, fillSheets, printerId, createdAtMillis, modifiedAtMillis,
)

fun TemplateRecord.toEntity() = TemplateEntity(
    id, name, photoName, photoWidthMm, photoHeightMm, paperName, paperWidthMm, paperHeightMm,
    marginTopMm, marginBottomMm, marginLeftMm, marginRightMm, spacingHorizontalMm, spacingVerticalMm,
    allowRotation, showCutLines,
)

fun TemplateEntity.toRecord() = TemplateRecord(
    id, name, photoName, photoWidthMm, photoHeightMm, paperName, paperWidthMm, paperHeightMm,
    marginTopMm, marginBottomMm, marginLeftMm, marginRightMm, spacingHorizontalMm, spacingVerticalMm,
    allowRotation, showCutLines,
)
