package com.photoprint.pro.domain.project

import com.photoprint.pro.domain.layout.LayoutEngine
import com.photoprint.pro.domain.layout.LayoutOutcome
import com.photoprint.pro.domain.model.BuiltInTemplates
import com.photoprint.pro.domain.model.CropState
import com.photoprint.pro.domain.model.PrintProject
import com.photoprint.pro.domain.model.ProjectPhoto
import com.photoprint.pro.domain.model.toLayoutRequest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProjectServiceTest {
    private var now = 1_000L
    private var counter = 0
    private val repo = InMemoryProjectRepository()
    private val service = ProjectService(repo, { "id-${++counter}" }, { now })
    private val photos = listOf(ProjectPhoto("a", "ref-a", 1200, 1500, copies = 4))

    private fun <T> ok(o: ProjectOutcome<T>): T = assertIs<ProjectOutcome.Success<T>>(o).value

    private fun err(o: ProjectOutcome<*>): ProjectError = assertIs<ProjectOutcome.Failure>(o).error

    private fun create(name: String = "Passport set") = ok(runBlocking { service.createFromTemplate(BuiltInTemplates.passport4x6, name, photos) })

    @Test
    fun `create from template copies settings not photos`() {
        val p = create()
        assertEquals("id-1", p.id)
        assertEquals(BuiltInTemplates.passport4x6.photoSize, p.photoSize)
        assertEquals(BuiltInTemplates.passport4x6.paperSize, p.paperSize)
        assertEquals(BuiltInTemplates.passport4x6.margin, p.margin)
        assertEquals(1_000L, p.createdAtMillis)
        assertEquals(p.createdAtMillis, p.modifiedAtMillis)
        assertEquals(p, runBlocking { repo.get(p.id) })
    }

    @Test
    fun `create validates name and photos and saves nothing on failure`() {
        assertEquals(ProjectError.BLANK_NAME, err(runBlocking { service.createFromTemplate(BuiltInTemplates.passport4x6, "   ", photos) }))
        assertEquals(ProjectError.NAME_TOO_LONG, err(runBlocking { service.createFromTemplate(BuiltInTemplates.passport4x6, "x".repeat(81), photos) }))
        assertEquals(ProjectError.NO_PHOTOS, err(runBlocking { service.createFromTemplate(BuiltInTemplates.passport4x6, "ok", emptyList()) }))
        assertTrue(runBlocking { repo.observeAll().first() }.isEmpty())
        assertEquals(0, counter, "no id is consumed by a rejected create")
    }

    @Test
    fun `rename trims and bumps modified time only`() {
        val p = create()
        now = 5_000
        val r = ok(runBlocking { service.rename(p.id, "  Visa sheet ") })
        assertEquals("Visa sheet", r.name)
        assertEquals(5_000L, r.modifiedAtMillis)
        assertEquals(1_000L, r.createdAtMillis)
        assertEquals(p.photos, r.photos)
    }

    @Test
    fun `rename rejects bad names and unknown ids`() {
        val p = create()
        assertEquals(ProjectError.BLANK_NAME, err(runBlocking { service.rename(p.id, "") }))
        assertEquals(ProjectError.NAME_TOO_LONG, err(runBlocking { service.rename(p.id, "y".repeat(200)) }))
        assertEquals(ProjectError.NOT_FOUND, err(runBlocking { service.rename("nope", "x") }))
        assertEquals("Passport set", runBlocking { repo.get(p.id) }!!.name)
    }

    @Test
    fun `update cannot change identity or creation time`() {
        val p = create()
        now = 9_000
        val u = ok(runBlocking { service.update(p.id) { it.copy(id = "hijack", createdAtMillis = 0, showCutLines = true) } })
        assertEquals(p.id, u.id)
        assertEquals(1_000L, u.createdAtMillis)
        assertEquals(9_000L, u.modifiedAtMillis)
        assertTrue(u.showCutLines)
        assertNull(runBlocking { repo.get("hijack") })
    }

    @Test
    fun `duplicate gets new id name and timestamps but the same content`() {
        val p = create()
        now = 7_000
        val d = ok(runBlocking { service.duplicate(p.id) })
        assertNotEquals(p.id, d.id)
        assertEquals("Passport set (copy)", d.name)
        assertEquals(7_000L, d.createdAtMillis)
        assertEquals(p.photos, d.photos)
        assertEquals(p.photoSize, d.photoSize)
        assertEquals(2, runBlocking { repo.observeAll().first() }.size)
        // Copying a copy keeps names within the limit.
        val long = ok(runBlocking { service.rename(p.id, "z".repeat(80)) })
        assertTrue(ok(runBlocking { service.duplicate(long.id) }).name.length <= ProjectService.MAX_NAME)
    }

    @Test
    fun `delete removes and reports unknown ids`() {
        val p = create()
        ok(runBlocking { service.delete(p.id) })
        assertNull(runBlocking { repo.get(p.id) })
        assertEquals(ProjectError.NOT_FOUND, err(runBlocking { service.delete(p.id) }))
        assertEquals(ProjectError.NOT_FOUND, err(runBlocking { service.duplicate(p.id) }))
    }

    @Test
    fun `projects list most recently modified first`() {
        val a = create("A")
        now = 2_000
        val b = create("B")
        now = 3_000
        ok(runBlocking { service.rename(a.id, "A2") })
        assertEquals(listOf(a.id, b.id), runBlocking { repo.observeAll().first() }.map { it.id })
    }

    // ---- project → layout ----------------------------------------------------------------

    private fun plan(p: PrintProject) = assertIs<LayoutOutcome.Success<com.photoprint.pro.domain.layout.LayoutPlan>>(LayoutEngine().calculate(p.toLayoutRequest())).value

    @Test
    fun `per-photo crops reach the placements`() {
        val zoomed = CropState(zoom = 2.0, panX = 0.25)
        val p = create().copy(
            photos = listOf(
                ProjectPhoto("a", "ra", 1000, 1000, copies = 2, crop = zoomed),
                ProjectPhoto("b", "rb", 1000, 1000, copies = 1),
            ),
        )
        val placements = plan(p).sheets[0].placements
        assertEquals(listOf("a", "a", "b"), placements.map { it.photoId })
        assertEquals(listOf(zoomed, zoomed, CropState()), placements.map { it.cropState })
    }

    @Test
    fun `auto fill uses whole sheets and ignores per-photo copies`() {
        val p = create().copy(photos = listOf(ProjectPhoto("a", "ra", 1000, 1000, copies = 1), ProjectPhoto("b", "rb", 1000, 1000, copies = 1)), fillSheets = 2)
        val pl = plan(p)
        assertEquals(2, pl.sheetCount)
        assertEquals(pl.photosPerSheet * 2, pl.totalPhotos)
        assertTrue(pl.sheets.all { it.placements.size == pl.photosPerSheet })
    }

    @Test
    fun `every built-in template yields a valid layout`() {
        for (t in BuiltInTemplates.all) {
            val p = t.toProject("x", t.name, photos, 0)
            val pl = plan(p)
            assertTrue(pl.photosPerSheet >= 1, t.name)
            assertTrue(t.builtIn)
        }
        // Spot-check one against the hand-computed result: 35×45, margin 3, spacing 2 on 4×6 → 2×3.
        assertEquals(6, plan(BuiltInTemplates.passport4x6.toProject("x", "x", photos, 0).copy(allowRotation = false)).photosPerSheet)
        assertEquals(setOf("builtin.passport-4x6", "builtin.id-4x6", "builtin.2x2-4x6", "builtin.passport-a4"), BuiltInTemplates.all.map { it.id }.toSet())
    }

    @Test
    fun `requested copies sums per-photo copies`() {
        assertEquals(7, create().copy(photos = listOf(ProjectPhoto("a", "r", 1, 1, copies = 3), ProjectPhoto("b", "r", 1, 1, copies = 4))).requestedCopies)
    }
}
