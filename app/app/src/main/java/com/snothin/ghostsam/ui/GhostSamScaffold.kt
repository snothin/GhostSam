package com.snothin.ghostsam.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.snothin.ghostsam.R
import com.snothin.ghostsam.data.SystemToolsLegacy
import com.snothin.ghostsam.ui.component.BottomBar
import com.snothin.ghostsam.ui.navigation3.LocalNavigator
import com.snothin.ghostsam.ui.navigation3.Route
import com.snothin.ghostsam.ui.navigation3.rememberNavigator
import com.snothin.ghostsam.ui.screen.ExploitScreen
import com.snothin.ghostsam.ui.screen.HistoryScreen
import com.snothin.ghostsam.ui.screen.HomeScreen
import com.snothin.ghostsam.ui.screen.MoreScreen
import com.snothin.ghostsam.ui.screen.PayloadProfileScreen
import com.snothin.ghostsam.ui.screen.SettingsScreen
import com.snothin.ghostsam.ui.screen.SystemTerminalScreen
import com.snothin.ghostsam.ui.screen.SystemToolsScreen
import com.snothin.ghostsam.ui.screen.ThemeSettingsScreen
import kotlinx.coroutines.launch

enum class AppPage(@StringRes val label: Int, val icon: ImageVector) {
    Home(R.string.nav_home, Icons.Rounded.Home),
    History(R.string.nav_history, Icons.Rounded.History),
    Settings(R.string.nav_settings, Icons.Rounded.Settings),
}

@Composable
fun GhostSamScaffold(
    onRefreshSettings: () -> Unit,
) {
    val navigator = rememberNavigator(Route.Main)

    CompositionLocalProvider(LocalNavigator provides navigator) {
        // Surface background: a bare render would show the window's default black under the outgoing page.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.surface),
        ) {
            NavDisplay(
                backStack = navigator.backStack,
                onBack = { navigator.pop() },
                entryProvider = entryProvider {
                    entry<Route.Main> {
                        MainScaffold()
                    }
                    entry<Route.ColorPalette> {
                        ThemeSettingsScreen(
                            onBack = { navigator.pop() },
                            onSettingsChanged = onRefreshSettings,
                        )
                    }
                    entry<Route.More> {
                        MoreScreen(
                            onBack = { navigator.pop() },
                        )
                    }
                    entry<Route.Exploit> {
                        ExploitScreen(
                            onBack = { navigator.pop() },
                        )
                    }
                    entry<Route.SystemTools> {
                        SystemToolsScreen(
                            onBack = { navigator.pop() },
                        )
                    }
                    entry<Route.SystemTerminal> {
                        SystemTerminalScreen(
                            onBack = { navigator.pop() },
                        )
                    }
                    entry<Route.SystemToolsMore> {
                        SystemToolsScreen(
                            onBack = { navigator.pop() },
                            tools = SystemToolsLegacy,
                            titleRes = R.string.tools_more_title,
                            showMoreEntry = false,
                        )
                    }
                    entry<Route.PayloadProfiles> {
                        PayloadProfileScreen(
                            onBack = { navigator.pop() },
                        )
                    }
                }
            )
        }
    }
}

@Composable
private fun MainScaffold() {
    val pages = AppPage.entries
    val pagerState = rememberPagerState(initialPage = 0) { pages.size }
    var historyDetailMode by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val bottomBar: @Composable () -> Unit = {
        if (!historyDetailMode) {
            BottomBar(
                pages = pages,
                selected = pages[pagerState.currentPage.coerceIn(0, pages.lastIndex)],
                onSelected = { page ->
                    scope.launch { pagerState.animateScrollToPage(pages.indexOf(page)) }
                },
            )
        }
    }

    @Composable
    fun PageContent(page: AppPage, padding: PaddingValues, isVisible: Boolean) {
        when (page) {
            AppPage.Home -> HomeScreen(
                modifier = Modifier.padding(padding),
            )

            AppPage.History -> HistoryScreen(
                modifier = Modifier.padding(padding),
                isVisible = isVisible,
                onDetailModeChanged = { historyDetailMode = it },
            )

            AppPage.Settings -> SettingsScreen(
                modifier = Modifier.padding(padding),
            )
        }
    }

    // Pages own their top-app-bar insets; the outer scaffold keeps only the horizontal safe area.
    top.yukonga.miuix.kmp.basic.Scaffold(
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal),
        bottomBar = bottomBar,
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            overscrollEffect = null,
            beyondViewportPageCount = 1,
            userScrollEnabled = !historyDetailMode,
        ) { pageIndex ->
            PageContent(pages[pageIndex], padding, isVisible = pageIndex == pagerState.currentPage)
        }
    }
}
