package com.example.timeapp.ui.components

import com.example.timeapp.data.L

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.timeapp.data.db.normaliseTag
import com.example.timeapp.ui.theme.Dimens
import com.example.timeapp.ui.theme.Shapes
import com.example.timeapp.ui.theme.TimeThemeTokens

/**
 * Tags on a task: current tags as removable chips, a field that adds on Done or comma, and
 * the most-used existing tags as one-tap suggestions — so "android" is reused rather than
 * re-typed as "Android" (names are normalised either way).
 */
@Composable
fun TagEditor(
    tags: List<String>,
    onTagsChange: (List<String>) -> Unit,
    suggestions: List<String>,
    modifier: Modifier = Modifier,
) {
    val colors = TimeThemeTokens.colors
    var draft by remember { mutableStateOf("") }
    fun commit(raw: String) {
        val name = normaliseTag(raw)
        if (name.isNotEmpty() && name !in tags) onTagsChange(tags + name)
        draft = ""
    }
    Column(modifier = modifier.fillMaxWidth()) {
        if (tags.isNotEmpty()) {
            FlowRow(modifier = Modifier.fillMaxWidth().padding(bottom = Dimens.s8)) {
                tags.forEach { tag ->
                    Row(
                        modifier = Modifier
                            .padding(end = Dimens.s8, bottom = Dimens.s8)
                            .background(colors.accC, Shapes.pill)
                            .clickable { onTagsChange(tags - tag) }
                            .padding(start = Dimens.s12, end = Dimens.s8, top = Dimens.s6, bottom = Dimens.s6),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("#$tag", style = MaterialTheme.typography.labelMedium, color = colors.accInk)
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = L("Remove tag %1\$s", tag),
                            tint = colors.accInk,
                            modifier = Modifier.padding(start = Dimens.s4).size(14.dp),
                        )
                    }
                }
            }
        }
        OutlinedTextField(
            value = draft,
            onValueChange = { value ->
                // A comma ends a tag, like most tag fields.
                if (value.endsWith(",")) commit(value.dropLast(1)) else draft = value
            },
            placeholder = { Text(L("Add a tag")) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { commit(draft) }),
            modifier = Modifier.fillMaxWidth(),
        )
        val prefix = normaliseTag(draft)
        val offered = suggestions.filter { it !in tags && (prefix.isEmpty() || it.startsWith(prefix)) }.take(8)
        if (offered.isNotEmpty()) {
            FlowRow(modifier = Modifier.fillMaxWidth().padding(top = Dimens.s8)) {
                offered.forEach { tag ->
                    Text(
                        "#$tag",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(end = Dimens.s8, bottom = Dimens.s8)
                            .border(Dimens.hairline, colors.line, Shapes.pill)
                            .clickable { commit(tag) }
                            .padding(horizontal = Dimens.s12, vertical = Dimens.s6),
                    )
                }
            }
        }
    }
}
