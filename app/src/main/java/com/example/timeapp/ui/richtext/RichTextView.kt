package com.example.timeapp.ui.richtext

import com.example.timeapp.data.L

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalDensity
import com.example.timeapp.ui.theme.Dimens
import com.example.timeapp.ui.theme.Shapes
import com.example.timeapp.ui.theme.TimeThemeTokens

/**
 * Read-only rendering of markdown-lite text — the display half of [RichTextField]. Also used
 * standalone wherever a field is shown without an accompanying editor (Task Detail's
 * description, a bucket-list card's note, a bookmark's note, a settled ledger row's note).
 * A blank/null [raw] renders nothing — callers should check for that themselves if they want to
 * hide an empty note section entirely.
 */
@Composable
fun RichTextView(
    raw: String,
    modifier: Modifier = Modifier,
    onToggleChecklistItem: ((lineIndex: Int) -> Unit)? = null,
) {
    val context = LocalContext.current
    val linkColor = TimeThemeTokens.colors.acc
    val codeBackground = TimeThemeTokens.colors.accS2
    val blocks = remember(raw) { parseMarkdownLite(raw) }
    // Links reuse the exact try/catch/Toast/scheme-normalization LinksScreen already uses,
    // routed through the default LinkAnnotation click path via a scoped UriHandler override —
    // this keeps parseInlineSpans a pure function with no Context dependency.
    val uriHandler = remember(context) {
        object : UriHandler {
            override fun openUri(uri: String) {
                val normalized = if (uri.contains("://")) uri else "https://$uri"
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(normalized)))
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(context, L("No app can open this link"), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    CompositionLocalProvider(LocalUriHandler provides uriHandler) {
        Column(modifier = modifier) {
            blocks.forEach { block -> RichTextBlockRow(block, linkColor, codeBackground, onToggleChecklistItem) }
        }
    }
}

@Composable
private fun RichTextBlockRow(block: RichTextBlock, linkColor: Color, codeBackground: Color, onToggleChecklistItem: ((Int) -> Unit)?) {
    when (block) {
        is RichTextBlock.Blank -> Spacer(modifier = Modifier.height(Dimens.s8))
        is RichTextBlock.Heading -> Text(
            parseInlineSpans(block.text, linkColor, codeBackground),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = Dimens.s8, bottom = Dimens.s4),
        )
        is RichTextBlock.Paragraph -> Text(
            parseInlineSpans(block.text, linkColor, codeBackground),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(vertical = Dimens.s2),
        )
        // A quote: a muted bar in the marker gutter and muted text, so it reads as someone
        // else's words without a card or colour of its own.
        is RichTextBlock.Quote -> Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(vertical = Dimens.s2),
        ) {
            Box(
                modifier = Modifier.width(Dimens.s24).fillMaxHeight(),
                contentAlignment = Alignment.CenterStart,
            ) {
                Box(Modifier.width(Dimens.s4).fillMaxHeight().background(TimeThemeTokens.colors.line2, Shapes.xs))
            }
            Text(
                parseInlineSpans(block.text, linkColor, codeBackground),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).padding(start = Dimens.s8),
            )
        }
        // Note content stays neutral: markers and checkboxes use the text colours, and only
        // links keep the accent, because they have to read as tappable. Markers used to be
        // accent-coloured, which put a purple dot or digit in front of every list item.
        //
        // Every marker — bullet, number, checkbox — sits in the same fixed-width gutter, so
        // list text starts on one column whichever marker precedes it (previously "2."
        // pushed its text further right than "1." did, and bullets sat differently again).
        // Text markers are baseline-aligned with their item, so they stay on the first line
        // when an item wraps.
        is RichTextBlock.Bullet -> Row(modifier = Modifier.fillMaxWidth().padding(vertical = Dimens.s2)) {
            ListMarkerText("•")
            Text(
                parseInlineSpans(block.text, linkColor, codeBackground),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f).alignByBaseline(),
            )
        }
        is RichTextBlock.Numbered -> Row(modifier = Modifier.fillMaxWidth().padding(vertical = Dimens.s2)) {
            ListMarkerText(com.example.timeapp.data.Lang.digits("${block.displayIndex}."))
            Text(
                parseInlineSpans(block.text, linkColor, codeBackground),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f).alignByBaseline(),
            )
        }
        is RichTextBlock.Checklist -> {
            val tappable = onToggleChecklistItem != null
            // A tappable row gets padding that makes it at least touchMin tall (rule 6); a
            // read-only one (a settled ledger row's note) keeps the tighter list rhythm.
            val rowModifier = Modifier.fillMaxWidth()
                .let { if (tappable) it.clickable {
                    com.example.timeapp.data.Haptics.perform(com.example.timeapp.data.HapticEvent.TICK)
                    onToggleChecklistItem!!(block.lineIndex)
                } else it }
                .padding(vertical = if (tappable) Dimens.s14 else Dimens.s4)
            val lineHeight = with(LocalDensity.current) { MaterialTheme.typography.bodyLarge.lineHeight.toDp() }
            val muted = MaterialTheme.colorScheme.onSurfaceVariant
            Row(modifier = rowModifier, verticalAlignment = Alignment.Top) {
                // Sized to one line of body text so the box lines up with the first line of
                // a wrapped item, not the middle of the whole block.
                Box(
                    modifier = Modifier.width(Dimens.s24).height(lineHeight),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Box(
                        modifier = Modifier
                            .size(Dimens.s16)
                            .border(2.dp, if (block.checked) muted else TimeThemeTokens.status.controlOutline, Shapes.xs),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (block.checked) {
                            Icon(Icons.Rounded.Check, contentDescription = null, tint = muted, modifier = Modifier.size(Dimens.s12))
                        }
                    }
                }
                Text(
                    parseInlineSpans(block.text, linkColor, codeBackground),
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (block.checked) TextDecoration.LineThrough else TextDecoration.None,
                    color = if (block.checked) muted else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f).padding(start = Dimens.s8),
                )
            }
        }
    }
}

/**
 * A bullet or list number in the shared marker gutter: muted text colour so it reads as
 * structure rather than content, and right-aligned so "9." and "10." line up on the dot.
 * The gap sits outside the fixed width, so a two-digit marker still gets the full gutter.
 */
@Composable
private fun RowScope.ListMarkerText(marker: String) {
    Text(
        marker,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.End,
        modifier = Modifier.padding(end = Dimens.s8).width(Dimens.s24).alignByBaseline(),
    )
}
