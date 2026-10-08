package com.photoprint.pro.presentation.nav

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

sealed interface Route {
    data object Home : Route
    data object SelectPhotos : Route
    data class EditPhoto(val photoId: String) : Route
    data object PhotoSize : Route
    data object PaperSize : Route
    data object LayoutSettings : Route
    data object Preview : Route
    data class EditPlacement(val photoId: String) : Route
    data object Printers : Route
    data object PrintSettings : Route
    data object FinalCheck : Route
    data object Projects : Route
    data object Templates : Route
    data object Settings : Route
    data object Calibration : Route

    companion object {
        /** Bottom-navigation destinations. */
        val tabs: List<Route> = listOf(Home, Projects, Templates, Settings)
    }
}

enum class NavDirection { FORWARD, BACK, NONE }

data class NavState(val stack: List<Route>, val direction: NavDirection = NavDirection.NONE) {
    val current: Route get() = stack.last()
    val canGoBack: Boolean get() = stack.size > 1
}

/**
 * Back stack. The user's work lives in [com.photoprint.pro.presentation.session.PrintSession], not in
 * screens, so popping a route never loses anything: going back to an earlier step shows the same data.
 */
class NavStack(initial: Route = Route.Home) {
    private val _state = MutableStateFlow(NavState(listOf(initial)))
    val state: StateFlow<NavState> get() = _state

    val current: Route get() = _state.value.current

    fun push(route: Route) {
        val s = _state.value
        if (s.current == route) return // double taps must not stack duplicates
        _state.value = NavState(s.stack + route, NavDirection.FORWARD)
    }

    /** @return false when already at the root, so the caller can let the system handle back (exit). */
    fun pop(): Boolean {
        val s = _state.value
        if (!s.canGoBack) return false
        _state.value = NavState(s.stack.dropLast(1), NavDirection.BACK)
        return true
    }

    /** Pops until [matches] is on top. If nothing matches, the stack is unchanged. */
    fun popTo(matches: (Route) -> Boolean): Boolean {
        val s = _state.value
        val idx = s.stack.indexOfLast(matches)
        if (idx < 0) return false
        if (idx == s.stack.lastIndex) return true
        _state.value = NavState(s.stack.take(idx + 1), NavDirection.BACK)
        return true
    }

    /** Switch bottom-navigation tab: the tab becomes the root. */
    fun switchTab(tab: Route) {
        val s = _state.value
        if (s.stack.size == 1 && s.current == tab) return
        _state.value = NavState(listOf(tab), NavDirection.NONE)
    }

    fun replaceTop(route: Route) {
        val s = _state.value
        _state.value = NavState(s.stack.dropLast(1) + route, NavDirection.FORWARD)
    }

    /** Finish a flow (e.g. after printing) and show [route] alone. */
    fun resetTo(route: Route) {
        _state.value = NavState(listOf(route), NavDirection.BACK)
    }
}

/** The step order of the main flow, for the progress indicator. */
object Workflow {
    val steps: List<Route> = listOf(
        Route.SelectPhotos,
        Route.PhotoSize,
        Route.PaperSize,
        Route.LayoutSettings,
        Route.Preview,
        Route.Printers,
        Route.FinalCheck,
    )

    /** 0-based step for a route, or null when the route is not part of the linear flow. */
    fun stepIndex(route: Route): Int? = when (route) {
        is Route.EditPhoto -> 0
        is Route.EditPlacement -> 4
        is Route.PrintSettings -> 5
        else -> steps.indexOf(route).takeIf { it >= 0 }
    }
}
