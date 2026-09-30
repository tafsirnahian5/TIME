package com.example.timeapp.ui.components

import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.timeapp.ui.BottomTabs
import com.example.timeapp.ui.TabDest
import com.example.timeapp.ui.theme.Dimens
import com.example.timeapp.ui.theme.Shapes
import com.example.timeapp.ui.theme.TimeThemeTokens

private fun iconFor(name: String): ImageVector = when (name) {
    "home" -> Icons.Rounded.Home
    "calendar_month" -> Icons.Rounded.CalendarMonth
    "timer" -> Icons.Rounded.Timer
    "account_balance_wallet" -> Icons.Rounded.AccountBalanceWallet
    "menu_book" -> Icons.AutoMirrored.Rounded.MenuBook
    "link" -> Icons.Rounded.Link
    else -> Icons.Rounded.Home
}

/** 6 destinations, 74dp, pill indicator on the selected icon. */
@Composable
fun TimeNavBar(
    currentRoute: String?,
    onSelect: (TabDest) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = TimeThemeTokens.colors
    // The background column extends behind the system nav bar / gesture inset (matching the
    // edge-to-edge look), but the tappable Row sits above it — otherwise, on gesture-nav
    // devices, taps near the bottom land in the system's home-gesture zone instead of the tab.
    Column(modifier = modifier.fillMaxWidth().background(colors.accS)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // A minimum, not a fixed height: at the largest font sizes the labels would clip.
                .heightIn(min = Dimens.navBarH)
                .padding(top = Dimens.s10, bottom = Dimens.s14, start = Dimens.s2, end = Dimens.s2),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            BottomTabs.forEach { tab ->
                val selected = currentRoute == tab.route
                NavItem(tab = tab, selected = selected, onSelect = onSelect)
            }
        }
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}

@Composable
private fun NavItem(
    tab: TabDest,
    selected: Boolean,
    onSelect: (TabDest) -> Unit,
) {
    val colors = TimeThemeTokens.colors
    val ink = if (selected) colors.accInk else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = Modifier
            .selectable(
                selected = selected,
                onClick = { onSelect(tab) },
                role = Role.Tab,
            )
            .width(56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .height(32.dp)
                .width(56.dp)
                .background(
                    color = if (selected) colors.accC else androidx.compose.ui.graphics.Color.Transparent,
                    shape = Shapes.pill,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // The label below names the tab; naming the icon too made TalkBack say it twice.
            Icon(
                imageVector = iconFor(tab.icon),
                contentDescription = null,
                tint = ink,
                modifier = Modifier.size(23.dp),
            )
        }
        Text(
            // The label is the English key; translated here, where it's shown (Routes can't — it's built once).
            text = com.example.timeapp.data.L(tab.label),
            color = ink,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = Dimens.s4),
        )
    }
}

/**
 * The same six destinations as a side rail, for windows 600dp and wider. The rail's
 * background runs behind the status and navigation bars; its items sit inside them.
 */
@Composable
fun TimeNavRail(
    currentRoute: String?,
    onSelect: (TabDest) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = TimeThemeTokens.colors
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(Dimens.navBarH + Dimens.s10)
            .background(colors.accS)
            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Vertical + WindowInsetsSides.Start))
            .padding(vertical = Dimens.s16),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.s14),
    ) {
        BottomTabs.forEach { tab ->
            NavItem(tab = tab, selected = currentRoute == tab.route, onSelect = onSelect)
        }
    }
}

