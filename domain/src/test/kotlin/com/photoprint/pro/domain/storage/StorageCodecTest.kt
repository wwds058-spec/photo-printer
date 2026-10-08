package com.photoprint.pro.domain.storage

import com.photoprint.pro.domain.model.BuiltInTemplates
import com.photoprint.pro.domain.model.CropState
import com.photoprint.pro.domain.model.Margin
import com.photoprint.pro.domain.model.PaperSize
import com.photoprint.pro.domain.model.PhotoSize
import com.photoprint.pro.domain.model.PrintProject
import com.photoprint.pro.domain.model.ProjectPhoto
import com.photoprint.pro.domain.model.Spacing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class StorageCodecTest {
    private val crop = CropState(zoom = 2.5, panX = -0.125, panY = 0.0625, rotationDegrees = 270, brightness = 0.3, contrast = -0.45)

    private fun project(photos: List<ProjectPhoto>, fill: Int? = 3, printer: String? = "epson-1") = PrintProject(
        id = "proj-1", name = "Passport set", photos = photos,
        photoSize = PhotoSize("Passport", 35.0, 45.0), paperSize = PaperSize("4 × 6 inch", 102.0, 152.0),
        margin = Margin(3.0, 4.0, 5.5, 6.25), spacing = Spacing(2.0, 1.5),
        allowRotation = false, showCutLines = true, fillSheets = fill, printerId = printer,
        createdAtMillis = 1_700_000_000_123, modifiedAtMillis = 1_700_000_999_456,
    )

    @Test
    fun `a project survives a round trip exactly`() {
        val p = project(listOf(ProjectPhoto("a", "/data/photos/a", 1200, 1600, 4, crop), ProjectPhoto("b", "/data/photos/b", 800, 600)))
        assertEquals(p, StorageCodec.toProject(StorageCodec.toRecord(p)))
    }

    @Test
    fun `optional fields round trip as null`() {
        val p = project(listOf(ProjectPhoto("a", "r", 10, 10)), fill = null, printer = null)
        val back = StorageCodec.toProject(StorageCodec.toRecord(p))!!
        assertNull(back.fillSheets)
        assertNull(back.printerId)
        assertEquals(p, back)
    }

    @Test
    fun `awkward text and numbers are preserved`() {
        val tricky = "He said \"hi\" \\ /path with spaces/é中文\nnewline\ttab"
        val photos = listOf(ProjectPhoto("id-\"1\"", tricky, 4000, 3000, 7, CropState(zoom = 1.0 / 3.0, panX = 1e-9, panY = -0.0)))
        val back = StorageCodec.decodePhotos(StorageCodec.encodePhotos(photos))!!
        assertEquals(photos[0].sourceRef, back[0].sourceRef)
        assertEquals(photos[0].photoId, back[0].photoId)
        assertEquals(1.0 / 3.0, back[0].crop.zoom, 0.0)
        assertEquals(1e-9, back[0].crop.panX, 0.0)
        val named = project(emptyList()).copy(name = tricky)
        assertEquals(tricky, StorageCodec.toProject(StorageCodec.toRecord(named))!!.name)
    }

    @Test
    fun `an empty photo list is valid`() {
        assertEquals(emptyList(), StorageCodec.decodePhotos(StorageCodec.encodePhotos(emptyList())))
    }

    @Test
    fun `corrupt photo text gives null rather than a crash`() {
        for (bad in listOf("", "not json", "[]", "{}", "{\"photos\":5}", "{\"photos\":\"x\"}", "{\"v\":1", "null", "\u0000")) {
            assertNull(StorageCodec.decodePhotos(bad), "should reject: $bad")
        }
    }

    @Test
    fun `a project with unreadable photos is skipped by toProject`() {
        val r = StorageCodec.toRecord(project(listOf(ProjectPhoto("a", "r", 10, 10)))).copy(photosJson = "garbage")
        assertNull(StorageCodec.toProject(r))
    }

    @Test
    fun `malformed entries are dropped but good ones survive`() {
        val text = """{"v":1,"photos":[
            {"id":"ok","ref":"r1","w":100,"h":200,"copies":2},
            {"id":"nosize","ref":"r2"},
            {"id":"zero","ref":"r3","w":0,"h":10},
            {"ref":"noid","w":10,"h":10},
            "not an object",
            {"id":"ok2","ref":"r4","w":10,"h":20}
        ]}"""
        val got = StorageCodec.decodePhotos(text)!!
        assertEquals(listOf("ok", "ok2"), got.map { it.photoId })
        assertEquals(2, got[0].copies)
    }

    @Test
    fun `missing or invalid optional fields fall back to defaults`() {
        val text = """{"photos":[{"id":"a","ref":"r","w":10,"h":10,"copies":0,"zoom":"fast","rot":45,"bright":1e999}]}"""
        val p = StorageCodec.decodePhotos(text)!!.single()
        assertEquals(1, p.copies, "copies are at least 1")
        assertEquals(CropState(), p.crop, "non-numeric zoom, non-quarter rotation and non-finite values are ignored")
    }

    @Test
    fun `unknown extra keys from a newer version are ignored`() {
        val text = """{"v":2,"future":true,"photos":[{"id":"a","ref":"r","w":10,"h":10,"newField":[1,2,3]}]}"""
        assertEquals(1, StorageCodec.decodePhotos(text)!!.size)
    }

    @Test
    fun `stored fill sheets below one are treated as unset`() {
        val r = StorageCodec.toRecord(project(listOf(ProjectPhoto("a", "r", 10, 10)))).copy(fillSheets = 0)
        assertNull(StorageCodec.toProject(r)!!.fillSheets)
    }

    @Test
    fun `templates round trip and always come back as user templates`() {
        val t = BuiltInTemplates.passport4x6.copy(id = "mine", name = "My Studio", builtIn = true)
        val back = StorageCodec.toTemplate(StorageCodec.toRecord(t))
        assertEquals(t.copy(builtIn = false), back)
        assertFalse(back.builtIn)
        assertNotNull(back.margin)
    }

    @Test
    fun `photo list order is kept`() {
        val photos = (1..30).map { ProjectPhoto("p$it", "ref$it", 10 + it, 20 + it, it) }
        assertEquals(photos.map { it.photoId }, StorageCodec.decodePhotos(StorageCodec.encodePhotos(photos))!!.map { it.photoId })
    }
}
