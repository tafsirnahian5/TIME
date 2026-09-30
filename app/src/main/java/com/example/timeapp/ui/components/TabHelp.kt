package com.example.timeapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.EventNote
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.ViewKanban
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.timeapp.data.L
import com.example.timeapp.ui.theme.Dimens
import com.example.timeapp.ui.theme.TimeThemeTokens

/** One point of a tab's "how it works" sheet. */
data class HelpPoint(val icon: ImageVector, val title: String, val body: String)

/**
 * What a tab is for (Phase 18B): a one-line [purpose] under its title, and a *?* that opens a short
 * plain-language sheet. Built from the user's first real use — "I don't understand the app".
 */
data class TabHelp(val tab: String, val purpose: String, val points: List<HelpPoint>)

object TabHelps {
    /**
     * The first-visit tip for a route (Phase 19C): the tab's own help, keyed by the tab's route so
     * each shows once. Habits isn't a tab but gets one too — it's a whole area of the app.
     */
    fun forTip(key: String?): TabHelp? = when (key) {
        com.example.timeapp.ui.Routes.Today -> today
        com.example.timeapp.ui.Routes.Plan -> plan
        com.example.timeapp.ui.Routes.Focus -> focus
        com.example.timeapp.ui.Routes.Money -> money
        com.example.timeapp.ui.Routes.Ideas -> ideas
        com.example.timeapp.ui.Routes.Links -> links
        com.example.timeapp.ui.Routes.Habits -> habits
        else -> null
    }

    val today get() = TabHelp(
        L("Today"), L("Your day at a glance"),
        listOf(
            HelpPoint(Icons.Rounded.PlayCircle, L("What's happening now"), L("The purple card shows what you're working on, with its timer. Pause, switch to something else or mark it done from there. When nothing runs, it suggests what to start.")),
            HelpPoint(Icons.Rounded.CheckCircle, L("Today's 3"), L("Pick up to three things that would make today count. Tick them off here.")),
            HelpPoint(Icons.Rounded.EventNote, L("Rest of today"), L("Everything still to come today, in time order: booked time, work that's due, habit reminders and bills.")),
            HelpPoint(Icons.Rounded.Add, L("Add anything"), L("The + button adds a task, booked time, a habit, a bill you expect, money you spent or a link.")),
        ),
    )
    val plan get() = TabHelp(
        L("Plan"), L("Your tasks — as a list, a board or a calendar"),
        listOf(
            HelpPoint(Icons.Rounded.ViewList, L("List"), L("Everything you have to do, grouped by when it's due. Tap the circle to finish a task, tap the task to open it.")),
            HelpPoint(Icons.Rounded.ViewKanban, L("Board"), L("The same tasks as cards moving through Inbox → Ready → Doing → Done. Hold a card to drag it, or use its ⋮ menu.")),
            HelpPoint(Icons.Rounded.CalendarMonth, L("Calendar"), L("Book time for a task or an appointment. Tap an empty hour to book it. T!ME warns you about overlaps and never moves things by itself.")),
            HelpPoint(Icons.Rounded.AutoAwesome, L("A task or booked time?"), L("A task is something to do (a due date is when it should be done). Booked time is time you set aside on the calendar to do it.")),
        ),
    )
    val focus get() = TabHelp(
        L("Focus"), L("Time your work — stopwatch, countdown or Pomodoro"),
        listOf(
            HelpPoint(Icons.Rounded.Timer, L("One timer for work"), L("Start a timer on a task — or with no task — and the time is recorded. Only one runs at a time, so nothing is counted twice.")),
            HelpPoint(Icons.Rounded.SwapHoriz, L("Interrupted?"), L("Use Switch: the current task is paused with a note to yourself, and the new one starts at the same moment.")),
            HelpPoint(Icons.Rounded.History, L("Your history"), L("Every stretch of work is kept. Fix a wrong start or end time from Session history in the ⋮ menu.")),
            HelpPoint(Icons.Rounded.Timer, L("Timers"), L("Kitchen-style timers for tea or laundry. They ring, but they never count as work.")),
        ),
    )
    val money get() = TabHelp(
        L("Money"), L("What you have, what's coming, what you spent"),
        listOf(
            HelpPoint(Icons.Rounded.AccountBalanceWallet, L("Overview"), L("Your wallets (cash, bank, bKash…) and how much is left after the bills you expect this month.")),
            HelpPoint(Icons.Rounded.Receipt, L("Planned"), L("Bills and costs you expect to pay. Planning one moves no money — nothing changes until you record a payment.")),
            HelpPoint(Icons.Rounded.Payments, L("History"), L("Money that actually moved: income, spending and transfers between your wallets.")),
            HelpPoint(Icons.Rounded.Add, L("Paying a planned bill"), L("Open it and tap Record payment. You can pay part now and the rest later.")),
        ),
    )
    val ideas get() = TabHelp(
        L("Ideas"), L("Thoughts that aren't tasks yet"),
        listOf(
            HelpPoint(Icons.Rounded.Lightbulb, L("Capture quickly"), L("Jot down anything without giving it a date. Nothing here reminds you or counts as overdue.")),
            HelpPoint(Icons.Rounded.Collections, L("Collections"), L("Group ideas — Sparks for quick thoughts, the Bucket list for life goals, and your own collections.")),
            HelpPoint(Icons.Rounded.CheckCircle, L("Ready to act?"), L("Open an idea and turn it into a task when you want to actually do it.")),
        ),
    )
    val links get() = TabHelp(
        L("Links"), L("Web pages you want to keep"),
        listOf(
            HelpPoint(Icons.Rounded.Share, L("Save from your browser"), L("In any browser, tap Share and choose T!ME. Or paste an address with the + button.")),
            HelpPoint(Icons.Rounded.Folder, L("Folders and favourites"), L("Sort links into folders, star the ones you use most, and search by title, address or note.")),
            HelpPoint(Icons.Rounded.Link, L("Link to a task"), L("Attach a link to the task it belongs to, so it's there when you start work.")),
        ),
    )
    /** Help & guides only (Phase 19D) — not a tab. */
    val reminders get() = TabHelp(
        L("Reminders"), L("Notifications that make sure you don't forget"),
        listOf(
            HelpPoint(Icons.Rounded.Notifications, L("Turn one on"), L("Switch on Remind me when you add a task, a bill or booked time, and choose when. Android asks for permission the first time.")),
            HelpPoint(Icons.Rounded.Snooze, L("Snooze, don't lose"), L("Snoozing moves the reminder, never the deadline. A bill reminder only reminds you — it never pays anything.")),
            HelpPoint(Icons.Rounded.Bedtime, L("Quiet hours"), L("At night reminders arrive silently instead of being dropped. Timers you started still ring.")),
        ),
    )
    /** Help & guides only (Phase 19D) — not a tab. */
    val yourData get() = TabHelp(
        L("Your data"), L("Private, offline, and yours to keep"),
        listOf(
            HelpPoint(Icons.Rounded.Lock, L("Stays on this phone"), L("T!ME has no account and never uses the internet. Nobody else can see your records.")),
            HelpPoint(Icons.Rounded.Backup, L("Make backups"), L("Settings › Data & backup saves everything to one file, with a password if you like. Automatic backups can go to a folder you choose every day.")),
            HelpPoint(Icons.Rounded.Fingerprint, L("Lock the app"), L("Add a PIN or fingerprint in Settings › Security & app lock. Widgets and notifications then hide the details.")),
        ),
    )
    val habits get() = TabHelp(
        L("Habits"), L("Small things you want to do regularly"),
        listOf(
            HelpPoint(Icons.Rounded.WbSunny, L("Check in"), L("Tap a day to mark it done. Tap again for a rest day — rest days never break a streak.")),
            HelpPoint(Icons.Rounded.CheckCircle, L("Your streak"), L("How many days (or weeks) in a row you kept it up. Missing today isn't counted until the day is over.")),
        ),
    )
}

/**
 * A tab's title with its purpose underneath and a *?* beside it — drop-in for the bare title text
 * in a tab's top bar.
 */
@Composable
fun TabTitle(title: String, help: TabHelp?, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f, fill = false).padding(start = Dimens.s4, top = Dimens.s6, bottom = Dimens.s6)) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            if (help != null) {
                Text(
                    help.purpose,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    // The one line that says what the tab is for — cut off mid-word ("or Pom…") beside a menu button.
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (help != null) {
            IconButton(onClick = { open = true }) {
                Icon(
                    Icons.AutoMirrored.Rounded.HelpOutline,
                    contentDescription = L("How %1\$s works", help.tab),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
    if (open && help != null) HelpSheet(help) { open = false }
}

/** Just the *?* — for a top bar that shows a wordmark instead of a tab title (Today). */
@Composable
fun TabHelpButton(help: TabHelp) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) {
        Icon(
            Icons.AutoMirrored.Rounded.HelpOutline,
            contentDescription = L("How %1\$s works", help.tab),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
    if (open) HelpSheet(help) { open = false }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpSheet(help: TabHelp, firstVisit: Boolean = false, onDismiss: () -> Unit) {
    val colors = TimeThemeTokens.colors
    // Fully open: half-open left "Got it" under the navigation bar on a real phone.
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Dimens.s24).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Dimens.s16),
        ) {
            Column {
                Text(L("How %1\$s works", help.tab), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                Text(help.purpose, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            help.points.forEach { p ->
                Row(verticalAlignment = Alignment.Top) {
                    Box(Modifier.size(40.dp).background(colors.accC, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(p.icon, contentDescription = null, tint = colors.accInk, modifier = Modifier.size(22.dp))
                    }
                    Column(Modifier.padding(start = Dimens.s14)) {
                        Text(p.title, style = MaterialTheme.typography.titleSmall)
                        Text(p.body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (firstVisit) {
                Text(
                    L("You can open this again any time with the ? next to the title."),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.touchMin).padding(bottom = Dimens.s16)) {
                Text(L("Got it"))
            }
        }
    }
}

