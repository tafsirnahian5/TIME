package com.example.timeapp.ui.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.timeapp.data.L
import com.example.timeapp.ui.components.Illustration
import com.example.timeapp.ui.components.IllustrationImage
import com.example.timeapp.ui.theme.Dimens
import com.example.timeapp.ui.theme.TimeThemeTokens
import kotlinx.coroutines.launch

/** [picture] null: the app's own mark (the first page introduces T!ME itself). */
private class TourPage(val picture: Illustration?, val title: String, val body: String)

private fun tourPages() = listOf(
    TourPage(
        null, L("Welcome to T!ME"),
        L("One place for your time, your money and your habits. Everything stays on this phone."),
    ),
    TourPage(
        Illustration.PLAN, L("Plan your day"),
        L("Write down what you need to do. Give it a date or a time slot if you like, and pick up to three things that matter today."),
    ),
    TourPage(
        Illustration.FOCUS, L("Track your time"),
        L("Press Start when you begin a task. If something interrupts you, switch — T!ME pauses the first one and remembers where you were."),
    ),
    TourPage(
        Illustration.MONEY, L("Money you expect, money spent"),
        L("Note bills before they're due, then record what you actually paid. You always see what you have after this month's bills."),
    ),
    TourPage(
        Illustration.HABITS, L("Build habits"),
        L("Tick a habit each day you do it. Rest days never break a streak."),
    ),
    TourPage(
        Illustration.PRIVATE, L("Private and offline"),
        L("No account, no internet, no ads. Add a PIN if you like, and make backups to a folder you choose."),
    ),
)

/**
 * Phase 19A — the welcome tour, shown once before setup. Illustrated, swipeable and skippable
 * at any point; it only explains, it never asks for anything.
 */
@Composable
fun WelcomeTourScreen(onDone: () -> Unit, finishLabel: String = L("Set up T!ME")) {
    val colors = TimeThemeTokens.colors
    val pages = tourPages()
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == pages.lastIndex

    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(Dimens.screenH)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (!last) TextButton(onClick = onDone, modifier = Modifier.heightIn(min = 48.dp)) { Text(L("Skip")) }
                else Box(Modifier.height(48.dp))
            }
            HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { i ->
                val page = pages[i]
                Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    if (page.picture == null) {
                        com.example.timeapp.ui.components.TimeMark(size = 140.dp, label = null)
                    } else {
                        IllustrationImage(page.picture, width = 260.dp)
                    }
                    Text(
                        page.title,
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = Dimens.s24).semantics { heading() },
                    )
                    Text(
                        page.body,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = Dimens.s12, start = Dimens.s8, end = Dimens.s8),
                    )
                }
            }
            val position = L("Page %1\$s of %2\$s", pager.currentPage + 1, pages.size)
            Row(
                Modifier.fillMaxWidth().padding(vertical = Dimens.s16).semantics { contentDescription = position },
                horizontalArrangement = Arrangement.spacedBy(Dimens.s8, Alignment.CenterHorizontally),
            ) {
                pages.indices.forEach { i ->
                    val on = i == pager.currentPage
                    Box(
                        Modifier.height(8.dp).width(if (on) 24.dp else 8.dp)
                            .background(if (on) colors.acc else colors.ring, CircleShape),
                    )
                }
            }
            Button(
                onClick = { if (last) onDone() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text(if (last) finishLabel else L("Next")) }
            Box(Modifier.size(Dimens.s8))
        }
    }
}
