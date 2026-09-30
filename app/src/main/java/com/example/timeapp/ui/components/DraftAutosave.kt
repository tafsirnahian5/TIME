package com.example.timeapp.ui.components

import com.example.timeapp.data.L

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.timeapp.data.DraftStore
import com.example.timeapp.ui.theme.Dimens
import com.example.timeapp.ui.theme.Shapes
import com.example.timeapp.ui.theme.TimeThemeTokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** What a form needs from its draft: whether one was restored, and how to clear or drop it. */
class DraftHandle internal constructor(
    val restored: Boolean,
    /** Call just before saving: the draft has become a real record. */
    val clear: () -> Unit,
    /** The banner's *Discard*: forget the draft and empty the fields. */
    val discard: () -> Unit,
)

/**
 * Autosaves [fields] for a new record's form half a second after the last change, and on first
 * open hands back what was there last time through [onRestore]. [enabled] is false when editing
 * an existing record — drafts are only for things not saved yet.
 */
@Composable
fun rememberDraft(
    form: String,
    enabled: Boolean,
    fields: Map<String, String>,
    onRestore: (Map<String, String>) -> Unit,
): DraftHandle {
    val context = LocalContext.current
    val store = remember { DraftStore(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var loaded by remember { mutableStateOf(!enabled) }
    var restored by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    LaunchedEffect(form, enabled) {
        if (!enabled) return@LaunchedEffect
        val draft = store.load(form)
        if (draft.values.any { it.isNotBlank() }) {
            onRestore(draft)
            restored = true
        }
        loaded = true
    }
    LaunchedEffect(fields, loaded) {
        if (!enabled || !loaded || done) return@LaunchedEffect
        delay(500)
        store.save(form, fields)
    }
    return DraftHandle(
        restored = restored,
        clear = { done = true; scope.launch { store.clear(form) } },
        discard = {
            restored = false
            scope.launch { store.clear(form) }
            onRestore(fields.mapValues { "" })
        },
    )
}

/** "Restored your unsaved draft · Discard" above a form. */
@Composable
fun DraftBanner(handle: DraftHandle, modifier: Modifier = Modifier) {
    if (!handle.restored) return
    Row(
        modifier = modifier.fillMaxWidth().background(TimeThemeTokens.colors.accS2, Shapes.md).padding(start = Dimens.s12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            L("Restored your unsaved draft."),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = handle.discard) { Text(L("Discard")) }
    }
}
