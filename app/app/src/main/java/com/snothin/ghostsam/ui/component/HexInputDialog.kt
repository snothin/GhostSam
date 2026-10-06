package com.snothin.ghostsam.ui.component

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.snothin.ghostsam.R
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
fun HexInputDialog(
    title: String,
    current: Long,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    WindowDialog(
        show = true,
        title = title,
        summary = stringResource(R.string.hex_input_hint),
        onDismissRequest = onDismiss,
        content = {
            var text by remember(current) { mutableStateOf("0x%x".format(current)) }
            TextField(
                modifier = Modifier.padding(bottom = 16.dp),
                value = text,
                maxLines = 1,
                trailingIcon = {
                    Text(
                        text = "hex",
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                    )
                },
                onValueChange = { newValue ->
                    val normalized = newValue.removePrefix("0x").removePrefix("0X")
                    val valid = normalized.isEmpty() ||
                        normalized.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }
                    if (valid) text = newValue
                },
            )
            DialogConfirmRow(
                onCancel = onDismiss,
                onConfirm = {
                    val parsed = text.removePrefix("0x").removePrefix("0X")
                        .toLongOrNull(16)
                    onConfirm(parsed ?: current)
                },
            )
        },
    )
}
