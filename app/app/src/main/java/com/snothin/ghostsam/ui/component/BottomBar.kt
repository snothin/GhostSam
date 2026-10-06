package com.snothin.ghostsam.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snothin.ghostsam.ui.AppPage
import com.snothin.ghostsam.ui.theme.LocalEnableFloatingBottomBar
import com.snothin.ghostsam.ui.theme.isInDarkTheme
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun BottomBar(
    pages: List<AppPage>,
    selected: AppPage,
    onSelected: (AppPage) -> Unit,
    modifier: Modifier = Modifier,
) {
    val enableFloating = LocalEnableFloatingBottomBar.current

    if (!enableFloating) {
        NavigationBar(
            modifier = modifier,
            color = if (isInDarkTheme()) MiuixTheme.colorScheme.surfaceContainerHigh
            else MiuixTheme.colorScheme.surface,
        ) {
            pages.forEach { page ->
                NavigationBarItem(
                    modifier = Modifier.weight(1f),
                    icon = page.icon,
                    label = stringResource(page.label),
                    selected = selected == page,
                    onClick = { onSelected(page) },
                )
            }
        }
    } else {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            FloatingBottomBar(
                modifier = modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    )
                    .padding(bottom = 12.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
                selectedIndex = { pages.indexOf(selected).coerceAtLeast(0) },
                onSelected = { onSelected(pages[it]) },
                tabsCount = pages.size,
            ) {
                pages.forEach { page ->
                    FloatingBottomBarItem(
                        onClick = { onSelected(page) },
                        modifier = Modifier.defaultMinSize(minWidth = 76.dp),
                    ) {
                        Icon(
                            imageVector = page.icon,
                            contentDescription = stringResource(page.label),
                        )
                        Text(
                            text = stringResource(page.label),
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            maxLines = 1,
                            softWrap = false,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Visible,
                        )
                    }
                }
            }
        }
    }
}
