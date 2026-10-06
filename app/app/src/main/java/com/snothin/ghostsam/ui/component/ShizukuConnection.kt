package com.snothin.ghostsam.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.snothin.ghostsam.data.channel.ShizukuController

@Composable
fun rememberShizukuConnection(): Boolean {
    val granted by ShizukuController.granted.collectAsState()
    return granted
}
