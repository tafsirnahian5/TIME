package com.example.timeapp.ui.components

import androidx.compose.material.icons.rounded.Person
import androidx.compose.ui.semantics.contentDescription
import com.example.timeapp.data.L

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading

import androidx.compose.foundation.layout.heightIn

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.timeapp.ui.theme.Dimens
import com.example.timeapp.ui.theme.TimeThemeTokens

enum class RootTopBarTrailing { SETTINGS, MORE_VERT, TUNE, NONE }

/** The bell's state, provided once at the root so every tab bar can show it without each screen
 * taking new parameters. Null (previews, setup) means no bell. */
data class InboxBell(val unread: Int, val open: () -> Unit)

val LocalInboxBell = androidx.compose.runtime.staticCompositionLocalOf<InboxBell?> { null }

/** The bell beside search: T!ME's own list of what it has notified you about, with an unread badge. */
@Composable
fun InboxBellButton() {
    val bell = LocalInboxBell.current ?: return
    val description = if (bell.unread == 0) L("Notifications") else if (bell.unread == 1) L("Notifications, 1 unread") else L("Notifications, %1\$s unread", bell.unread)
    androidx.compose.material3.IconButton(onClick = bell.open, modifier = Modifier.semantics { contentDescription = description }) {
        androidx.compose.material3.BadgedBox(badge = {
            if (bell.unread > 0) androidx.compose.material3.Badge {
                Text(if (bell.unread > 99) "99+" else com.example.timeapp.data.Lang.digits(bell.unread.toString()))
            }
        }) {
            Icon(
                if (bell.unread > 0) Icons.Rounded.Notifications else Icons.Rounded.NotificationsNone,
                contentDescription = null,
            )
        }
    }
}

/**
 * The bar used on every root tab screen: title, search, and one trailing
 * action — `settings` (Plan) or `more_vert` (Money/Ideas) opens the real
 * Settings hub ([Routes.Settings]); `tune` (Focus history) is a no-op
 * placeholder here.
 */
@Composable
fun RootTopBar(
    title: String,
    onSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: RootTopBarTrailing = RootTopBarTrailing.SETTINGS,
    /** What this tab is for: a purpose line under the title and a *?* sheet (Phase 18B). */
    help: TabHelp? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.appBarH)
            .padding(horizontal = Dimens.s12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (help != null) {
            TabTitle(title, help, Modifier.weight(1f))
        } else {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f).padding(start = Dimens.s4).semantics { heading() },
            )
        }
        androidx.compose.material3.IconButton(onClick = onSearch) {
            Icon(Icons.Rounded.Search, contentDescription = L("Search"))
        }
        InboxBellButton()
        androidx.compose.material3.IconButton(onClick = onOpenSettings) {
            when (trailing) {
                RootTopBarTrailing.SETTINGS -> Icon(Icons.Rounded.Settings, contentDescription = L("Settings"))
                RootTopBarTrailing.MORE_VERT -> Icon(Icons.Rounded.MoreVert, contentDescription = L("More"))
                RootTopBarTrailing.TUNE -> Icon(Icons.Rounded.Tune, contentDescription = L("Filter"))
                RootTopBarTrailing.NONE -> {}
            }
        }
    }
}

/**
 * A3's specific top row: T!ME wordmark, search, avatar (opens Me), gear
 * (opens the real Settings hub, same as [RootTopBar]).
 */
@Composable
fun TodayTopBar(
    onSearch: () -> Unit,
    onAvatar: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    /** The first letter of the user's name — it used to be a hard-coded "B" for everyone. */
    initial: String = "",
) {
    val colors = TimeThemeTokens.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.appBarH)
            .padding(horizontal = Dimens.s12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TimeMark(size = 28.dp, modifier = Modifier.padding(start = Dimens.s4), label = null)
        Text(
            "T!ME",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = Dimens.s8),
        )
        TabHelpButton(TabHelps.today)
        androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
        androidx.compose.material3.IconButton(onClick = onSearch) {
            Icon(Icons.Rounded.Search, contentDescription = L("Search"))
        }
        InboxBellButton()
        androidx.compose.material3.IconButton(onClick = onAvatar) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.size(32.dp).background(colors.accC, CircleShape)
                    .semantics { contentDescription = L("Me — your profile and progress") },
                contentAlignment = Alignment.Center,
            ) {
                if (initial.isNotBlank()) {
                    Text(initial.take(1).uppercase(), color = colors.accInk, style = MaterialTheme.typography.bodyLarge)
                } else {
                    Icon(androidx.compose.material.icons.Icons.Rounded.Person, contentDescription = null, tint = colors.accInk, modifier = Modifier.size(20.dp))
                }
            }
        }
        androidx.compose.material3.IconButton(onClick = onOpenSettings) {
            Icon(Icons.Rounded.Settings, contentDescription = L("Settings"))
        }
    }
}
