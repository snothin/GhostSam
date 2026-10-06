package com.snothin.ghostsam.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
fun NumberInputDialog(
    title: String,
    summary: String,
    current: Double,
    min: Double,
    max: Double,
    suffix: String,
    integerOnly: Boolean = false,
    onConfirm: (Double) -> Unit,
    onDismiss: () -> Unit,
) {
    WindowDialog(
        show = true,
        title = title,
        summary = summary,
        onDismissRequest = onDismiss,
        content = {
            var text by remember(title, current, min, max) {
                mutableStateOf(if (integerOnly) current.toInt().toString() else current.toString())
            }
            TextField(
                modifier = Modifier.padding(bottom = 16.dp),
                value = text,
                maxLines = 1,
                trailingIcon = {
                    Text(
                        text = suffix,
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                    )
                },
                onValueChange = { newValue ->
                    val valid = if (integerOnly) {
                        newValue.isEmpty() || newValue.all { it.isDigit() }
                    } else {
                        newValue.isEmpty() ||
                            (newValue.all { it.isDigit() || it == '.' } &&
                                newValue.count { it == '.' } <= 1)
                    }
                    if (valid) text = newValue
                },
            )
            DialogConfirmRow(
                onCancel = onDismiss,
                onConfirm = {
                    val parsed = text.toDoubleOrNull()
                    val clamped = parsed?.coerceIn(min, max) ?: current
                    onConfirm(clamped)
                },
            )
        },
    )
}

@Composable
internal fun DialogConfirmRow(
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    confirmText: String = stringResource(android.R.string.ok),
) {
    Row(horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(
            text = stringResource(android.R.string.cancel),
            onClick = onCancel,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(20.dp))
        TextButton(
            text = confirmText,
            onClick = onConfirm,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.textButtonColorsPrimary(),
        )
    }
}
