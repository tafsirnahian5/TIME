package com.example.timeapp.ui.plan

import com.example.timeapp.data.L

import com.example.timeapp.data.displayPattern

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.timeapp.ui.theme.Shapes
import com.example.timeapp.ui.theme.TimeThemeTokens
import java.time.ZoneId

/**
 * C3 — Overlap: warn, never silently move. Required behavior: surface and
 * confirm, never auto-resolve. The fixed block stays put; the user chooses
 * what happens to the new one.
 */
@Composable
fun ConflictDialog(
    proposal: ConflictProposal,
    onChoice: (ConflictChoice) -> Unit,
) {
    val colors = TimeThemeTokens.colors
    val fmt = displayPattern("HH:mm")
    AlertDialog(
        onDismissRequest = { onChoice(ConflictChoice.CANCEL) },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Error, contentDescription = null, tint = colors.acc)
                Text(L("These times overlap"), style = MaterialTheme.typography.headlineSmall)
            }
        },
        text = {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TimeThemeTokens.colors.card, Shapes.md)
                        .padding(vertical = 6.dp),
                ) {
                    Column {
                        ConflictRow(
                            proposal.candidateTitle,
                            L("new · %1\$s", proposal.candidateStart.atZone(ZoneId.systemDefault()).toLocalTime().format(fmt)) +
                                L(" – %1\$s · flexible", proposal.candidateEnd.atZone(ZoneId.systemDefault()).toLocalTime().format(fmt)),
                            colors.acc,
                        )
                        ConflictRow(
                            proposal.existing.title,
                            L("existing · %1\$s", proposal.existing.startAt?.atZone(ZoneId.systemDefault())?.toLocalTime()?.format(fmt)) +
                                L(" – %1\$s · fixed by you", proposal.existing.endAt?.atZone(ZoneId.systemDefault())?.toLocalTime()?.format(fmt)),
                            com.example.timeapp.ui.theme.MarkerMoney,
                        )
                    }
                }
                Text(
                    L("These minutes collide. The fixed booking stays where it is — you choose what happens to the new one."),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onChoice(ConflictChoice.MOVE_NEW) }) { Text(L("Move the new one")) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onChoice(ConflictChoice.KEEP_BOTH) }) { Text(L("Keep both — overlap on purpose")) }
                TextButton(onClick = { onChoice(ConflictChoice.CANCEL) }) { Text(L("Cancel")) }
            }
        },
    )
}

@Composable
private fun ConflictRow(title: String, subtitle: String, barColor: Color) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.width(4.dp).height(26.dp).background(barColor, Shapes.xs),
        )
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
    }
}
