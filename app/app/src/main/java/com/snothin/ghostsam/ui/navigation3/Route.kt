package com.snothin.ghostsam.ui.navigation3

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation3.runtime.NavKey

sealed interface Route : NavKey {
    data object Main : Route

    data object ColorPalette : Route

    data object More : Route

    data object Exploit : Route

    data object SystemTools : Route

    data object SystemTerminal : Route

    data object SystemToolsMore : Route

    data object PayloadProfiles : Route
}

class Navigator(initialStack: List<NavKey>) {
    val backStack: SnapshotStateList<NavKey> = mutableStateListOf<NavKey>().apply { addAll(initialStack) }

    fun push(key: NavKey) {
        if (backStack.lastOrNull() != key) backStack.add(key)
    }

    fun pop() {
        // Keep the bottom entry: NavDisplay requires a non-empty back stack.
        if (backStack.size > 1) backStack.removeLastOrNull()
    }
}

@Composable
fun rememberNavigator(startRoute: NavKey): Navigator =
    rememberSaveable(saver = NavigatorSaver) { Navigator(listOf(startRoute)) }

// Routes are data-object singletons stored by ordinal; the decode when is exhaustive:
// a new Route fails compilation until registered here.
private val NavigatorSaver = listSaver<Navigator, Int>(
    save = { navigator -> navigator.backStack.map { (it as? Route)?.code() ?: 0 } },
    restore = { codes ->
        Navigator(codes.map { routeOfCode(it) }.ifEmpty { listOf(Route.Main) })
    },
)

private fun Route.code(): Int = when (this) {
    Route.Main -> 0
    Route.ColorPalette -> 1
    Route.More -> 2
    Route.Exploit -> 3
    Route.SystemTools -> 4
    Route.SystemTerminal -> 5
    Route.SystemToolsMore -> 6
    Route.PayloadProfiles -> 7
}

private fun routeOfCode(code: Int): Route = when (code) {
    0 -> Route.Main
    1 -> Route.ColorPalette
    2 -> Route.More
    3 -> Route.Exploit
    4 -> Route.SystemTools
    5 -> Route.SystemTerminal
    6 -> Route.SystemToolsMore
    7 -> Route.PayloadProfiles
    else -> Route.Main
}

val LocalNavigator = staticCompositionLocalOf<Navigator> {
    error("LocalNavigator not provided")
}
