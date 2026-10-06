package com.snothin.ghostsam.ui.component

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snothin.ghostsam.ui.theme.NatsumeGray
import top.yukonga.miuix.kmp.basic.Text

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        color = NatsumeGray,
        modifier = modifier.padding(start = 4.dp),
    )
}
