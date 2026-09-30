package com.example.timeapp.ui.inbox

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.Handshake
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NotificationImportant
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.timeapp.data.InboxItem
import com.example.timeapp.data.InboxKind
import com.example.timeapp.data.InboxSection
import com.example.timeapp.data.L
import com.example.timeapp.data.displayPattern
import com.example.timeapp.data.inboxSections
import com.example.timeapp.ui.components.EmptyState
import com.example.timeapp.ui.components.TimeTopBar
import com.example.timeapp.ui.theme.Dimens
import com.example.timeapp.ui.theme.TimeThemeTokens
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/**
 * The bell: what T!ME has told you lately — reminders, missed ones, budget and backup notices —
 * kept for 30 days even after the shade is cleared or when Android's notifications are off.
 * Leaving the page marks everything read, so the badge counts only what you haven't seen.
 */
@Composable
fun InboxScreen(
    viewModel: InboxViewModel,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
) {
    val items by viewModel.items.collectAsState()
    val unread by viewModel.unreadCount.collectAsState()
    var menu by remember { mutableStateOf(false) }
    var confirmingClear by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now()
    DisposableEffect(Unit) { onDispose { viewModel.markAllRead() } }

    Scaffold(
        topBar = {
            TimeTopBar(title = L("Notifications"), onBack = onBack, actions = {
                if (unread > 0) TextButton(onClick = { viewModel.markAllRead() }) { Text(L("Mark all read")) }
                if (!items.isNullOrEmpty()) {
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = L("More")) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text(L("Clear all")) }, onClick = { menu = false; confirmingClear = true })
                        }
                    }
                }
            })
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val list = items
        when {
            list == null -> Unit // still loading — nothing flashes
            list.isEmpty() -> EmptyState(
                icon = Icons.Rounded.NotificationsNone,
                headline = L("No notifications yet"),
                supporting = L("Reminders, missed reminders, budget alerts and backup notices appear here — even if Android's notifications are off for T!ME."),
                modifier = Modifier.fillMaxSize().padding(padding).padding(Dimens.s24),
            )
            else -> LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                inboxSections(list, today, zone).forEach { (section, rows) ->
                    item(key = section.name) {
                        Text(
                            when (section) {
                                InboxSection.TODAY -> L("TODAY")
                                InboxSection.YESTERDAY -> L("YESTERDAY")
                                InboxSection.EARLIER -> L("EARLIER")
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.semantics { heading() }.padding(start = Dimens.s16, top = Dimens.s16, bottom = Dimens.s6),
                        )
                    }
                    items(rows, key = { it.id }) { row ->
                        InboxRow(row, if (section == InboxSection.TODAY) "HH:mm" else "EEE d MMM · HH:mm", zone) {
                            val route = row.route
                            viewModel.markRead(row.id)
                            if (route != null) scope.launch {
                                if (viewModel.stillThere(route)) onOpen(route)
                                else snackbar.showSnackbar(L("That's no longer in T!ME — it was deleted."))
                            }
                        }
                    }
                }
                item { Box(Modifier.padding(bottom = Dimens.s24)) }
            }
        }
    }

    if (confirmingClear) {
        AlertDialog(
            onDismissRequest = { confirmingClear = false },
            title = { Text(L("Clear all notifications?")) },
            text = { Text(L("This empties the list. Your tasks, bills and reminders stay as they are.")) },
            confirmButton = { TextButton(onClick = { confirmingClear = false; viewModel.clear() }) { Text(L("Clear all")) } },
            dismissButton = { TextButton(onClick = { confirmingClear = false }) { Text(L("Cancel")) } },
        )
    }
}

@Composable
private fun InboxRow(item: InboxItem, timePattern: String, zone: ZoneId, onClick: () -> Unit) {
    val colors = TimeThemeTokens.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { if (!item.read) stateDescription = L("Unread") }
            .padding(horizontal = Dimens.s16, vertical = Dimens.s12),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.size(40.dp).background(colors.accC, CircleShape), contentAlignment = Alignment.Center) {
            Icon(iconFor(item.kind), contentDescription = null, tint = colors.accInk, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f).padding(start = Dimens.s12)) {
            Text(
                item.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (item.read) FontWeight.Normal else FontWeight.SemiBold,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            if (item.body.isNotBlank()) {
                Text(
                    item.body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = Dimens.s2),
                )
            }
            Text(
                item.createdAt.atZone(zone).format(displayPattern(timePattern)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Dimens.s4),
            )
        }
        if (!item.read) {
            Box(Modifier.padding(start = Dimens.s8, top = Dimens.s6).size(8.dp).background(colors.acc, CircleShape))
        }
    }
}

private fun iconFor(kind: InboxKind): ImageVector = when (kind) {
    InboxKind.TASK -> Icons.Rounded.CheckCircle
    InboxKind.BOOKING -> Icons.Rounded.Event
    InboxKind.BILL -> Icons.Rounded.Payments
    InboxKind.HABIT -> Icons.Rounded.EventRepeat
    InboxKind.LOAN -> Icons.Rounded.Handshake
    InboxKind.TIMER, InboxKind.FOCUS -> Icons.Rounded.Timer
    InboxKind.MISSED -> Icons.Rounded.NotificationImportant
    InboxKind.BUDGET -> Icons.Rounded.AccountBalanceWallet
    InboxKind.BACKUP -> Icons.Rounded.Backup
    InboxKind.WORK -> Icons.Rounded.RestartAlt
    InboxKind.OTHER -> Icons.Rounded.Notifications
}
