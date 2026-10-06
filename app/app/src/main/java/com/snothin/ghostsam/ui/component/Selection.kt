package com.snothin.ghostsam.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

class SelectionState {
    var selectionMode by mutableStateOf(false)
        private set

    var selectedIds by mutableStateOf<Set<String>>(emptySet())
        private set

    fun enter(id: String) {
        selectionMode = true
        selectedIds = setOf(id)
    }

    fun toggle(id: String) {
        selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
    }

    fun exit() {
        selectionMode = false
        selectedIds = emptySet()
    }
}

@Composable
fun rememberSelection(): SelectionState = remember { SelectionState() }
