package com.example.timeapp.ui.add

import com.example.timeapp.data.L

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Upcoming
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.timeapp.ui.theme.Dimens
import com.example.timeapp.ui.theme.TimeThemeTokens

enum class RecordType { TASK, SCHEDULE_BLOCK, HABIT, COST, TRANSACTION, LINK, WALLET }

private data class AddRow(
    val type: RecordType, val title: String, val subtitle: String,
    val icon: ImageVector,
    // Takes both palettes: the accent-tinted rows vary by colorway, the money rows use the
    // semantic status colours, which are colorway-independent but mode-dependent.
    val bg: (com.example.timeapp.ui.theme.TimeColors, com.example.timeapp.ui.theme.StatusColors) -> Color,
    val ink: (com.example.timeapp.ui.theme.TimeColors, com.example.timeapp.ui.theme.StatusColors) -> Color,
)

private val rows = listOf(
    AddRow(
        RecordType.TASK, L("Task"), L("A title is enough — details later"),
        Icons.Rounded.CheckCircle, { c, _ -> c.accC }, { c, _ -> c.acc },
    ),
    AddRow(
        RecordType.SCHEDULE_BLOCK, L("Book time"), L("A start and end on the calendar — a meeting, a class, study time"),
        Icons.Rounded.CalendarMonth, { c, _ -> c.accC }, { c, _ -> c.acc },
    ),
    // Not in the real design canvas — habits post-date it; see ui/habits/HabitsScreen.kt.
    AddRow(
        RecordType.HABIT, L("Habit"), L("Something to do regularly — keep a streak going"),
        Icons.Rounded.EventRepeat, { c, _ -> c.accC }, { c, _ -> c.acc },
    ),
    AddRow(
        RecordType.COST, L("Bill to pay"), L("Money you'll need to pay later — nothing is paid yet"),
        Icons.Rounded.Upcoming, { _, s -> s.warn }, { _, s -> s.warnInk },
    ),
    AddRow(
        RecordType.TRANSACTION, L("Money in or out"), L("Spending or income that already happened — updates a wallet"),
        Icons.Rounded.Payments, { _, s -> s.ok }, { _, s -> s.okInk },
    ),
    AddRow(
        RecordType.LINK, L("Link"), L("Paste a URL, or share to T!ME from a browser"),
        Icons.Rounded.Link, { c, _ -> c.accC }, { c, _ -> c.acc },
    ),
    // Not in the real design canvas (like Links, see ui/links/LinksScreen.kt) — added because
    // there was otherwise no entry point anywhere in the app to create a new wallet.
    AddRow(
        RecordType.WALLET, L("Wallet"), L("Cash, bank or mobile money — manually maintained"),
        Icons.Rounded.AccountBalanceWallet, { c, _ -> c.accC }, { c, _ -> c.acc },
    ),
)

/** B1 — the Add sheet: keeps the record types distinct, most of all plan vs. payment. */
@Composable
fun AddSheetContent(
    onPick: (RecordType) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = TimeThemeTokens.colors
    val status = TimeThemeTokens.status
    Column(modifier = modifier.fillMaxWidth().padding(bottom = Dimens.s16)) {
        Text(
            L("Add"),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = Dimens.screenH, vertical = Dimens.s12),
        )
        rows.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPick(row.type) }
                    .padding(horizontal = Dimens.screenH, vertical = Dimens.s12),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.size(40.dp).background(row.bg(colors, status), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(row.icon, contentDescription = null, tint = row.ink(colors, status))
                }
                Column(modifier = Modifier.padding(start = Dimens.s16)) {
                    Text(row.title, style = MaterialTheme.typography.bodyLarge)
                    Text(row.subtitle, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
