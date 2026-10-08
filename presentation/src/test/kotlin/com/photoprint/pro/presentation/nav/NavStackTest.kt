package com.photoprint.pro.presentation.nav

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NavStackTest {
    @Test
    fun `push and pop walk the flow and report direction`() {
        val nav = NavStack()
        nav.push(Route.SelectPhotos)
        nav.push(Route.PhotoSize)
        assertEquals(Route.PhotoSize, nav.current)
        assertEquals(NavDirection.FORWARD, nav.state.value.direction)
        assertTrue(nav.pop())
        assertEquals(Route.SelectPhotos, nav.current)
        assertEquals(NavDirection.BACK, nav.state.value.direction)
    }

    @Test
    fun `pop at the root returns false so the system can exit`() {
        val nav = NavStack()
        assertFalse(nav.pop())
        assertEquals(listOf<Route>(Route.Home), nav.state.value.stack)
        assertFalse(nav.state.value.canGoBack)
    }

    @Test
    fun `double tapping a button does not stack the same route twice`() {
        val nav = NavStack()
        nav.push(Route.SelectPhotos)
        nav.push(Route.SelectPhotos)
        assertEquals(2, nav.state.value.stack.size)
    }

    @Test
    fun `popTo returns to an earlier step`() {
        val nav = NavStack()
        listOf(Route.SelectPhotos, Route.PhotoSize, Route.PaperSize, Route.LayoutSettings, Route.Preview).forEach(nav::push)
        assertTrue(nav.popTo { it == Route.PhotoSize })
        assertEquals(Route.PhotoSize, nav.current)
        assertEquals(listOf(Route.Home, Route.SelectPhotos, Route.PhotoSize), nav.state.value.stack)
        assertFalse(nav.popTo { it == Route.FinalCheck }, "unknown target leaves the stack alone")
        assertEquals(Route.PhotoSize, nav.current)
    }

    @Test
    fun `switching tabs makes the tab the root`() {
        val nav = NavStack()
        nav.push(Route.SelectPhotos)
        nav.switchTab(Route.Projects)
        assertEquals(listOf<Route>(Route.Projects), nav.state.value.stack)
        assertFalse(nav.pop())
        assertEquals(listOf(Route.Home, Route.Projects, Route.Templates, Route.Settings), Route.tabs)
    }

    @Test
    fun `replaceTop and resetTo`() {
        val nav = NavStack()
        nav.push(Route.EditPhoto("a"))
        nav.replaceTop(Route.EditPhoto("b"))
        assertEquals(listOf(Route.Home, Route.EditPhoto("b")), nav.state.value.stack)
        nav.resetTo(Route.Home)
        assertEquals(listOf<Route>(Route.Home), nav.state.value.stack)
    }

    @Test
    fun `workflow step indices follow the product flow`() {
        assertEquals(0, Workflow.stepIndex(Route.SelectPhotos))
        assertEquals(0, Workflow.stepIndex(Route.EditPhoto("x")))
        assertEquals(4, Workflow.stepIndex(Route.Preview))
        assertEquals(4, Workflow.stepIndex(Route.EditPlacement("x")))
        assertEquals(5, Workflow.stepIndex(Route.PrintSettings))
        assertEquals(6, Workflow.stepIndex(Route.FinalCheck))
        assertNull(Workflow.stepIndex(Route.Home))
        assertNull(Workflow.stepIndex(Route.Settings))
    }
}
