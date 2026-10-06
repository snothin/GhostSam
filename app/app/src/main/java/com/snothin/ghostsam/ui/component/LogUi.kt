package com.snothin.ghostsam.ui.component

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun LogText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        fontFamily = FontFamily.Monospace,
        fontSize = 10.sp,
        lineHeight = 15.sp,
        color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f),
    )
}

// Tail-follow auto-scroll: active only while already at the bottom; [key] = content
// length/version (not the content); shared by all three log views.
@Composable
fun AutoScrollToBottom(scrollState: ScrollState, key: Any?) {
    var followTail by remember { mutableStateOf(true) }
// Stop following as soon as a scroll starts; on settle, re-decide by position (160px tolerance).
    LaunchedEffect(scrollState) {
        snapshotFlow { scrollState.isScrollInProgress }
            .collect { inProgress ->
                followTail = if (inProgress) {
                    false
                } else {
                    scrollState.maxValue - scrollState.value < 160
                }
            }
    }
    LaunchedEffect(key) {
        if (followTail) {
            delay(40)
            if (!scrollState.isScrollInProgress) scrollState.scrollTo(scrollState.maxValue)
        }
    }
}
