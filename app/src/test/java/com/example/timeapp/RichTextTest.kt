package com.example.timeapp

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import com.example.timeapp.ui.richtext.EditHistory
import com.example.timeapp.ui.richtext.RichTextBlock
import com.example.timeapp.ui.richtext.RichTextEditing
import com.example.timeapp.ui.richtext.parseInlineSpans
import com.example.timeapp.ui.richtext.parseMarkdownLite
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RichTextTest {
    private val link = Color.Blue

    @Test fun quoteLines() {
        val blocks = parseMarkdownLite("> said it\n>tight\n- > bullet wins\n>")
        assertEquals(RichTextBlock.Quote(0, "said it"), blocks[0])
        assertEquals(RichTextBlock.Quote(1, "tight"), blocks[1])
        assertTrue(blocks[2] is RichTextBlock.Bullet)
        assertEquals(RichTextBlock.Paragraph(3, ">"), blocks[3])
    }

    @Test fun strikethroughSpan() {
        val s = parseInlineSpans("a ~~gone~~ b", link)
        assertEquals("a gone b", s.text)
        val span = s.spanStyles.single()
        assertEquals(TextDecoration.LineThrough, span.item.textDecoration)
        assertEquals(2, span.start); assertEquals(6, span.end)
    }

    @Test fun inlineCodeIsLiteral() {
        val s = parseInlineSpans("run `**x**` now", link)
        assertEquals("run **x** now", s.text)
        assertEquals(FontFamily.Monospace, s.spanStyles.single().item.fontFamily)
    }

    @Test fun unmatchedMarkersStayLiteral() {
        assertEquals("a ~~b", parseInlineSpans("a ~~b", link).text)
        assertEquals("a `b", parseInlineSpans("a `b", link).text)
        assertEquals("``", parseInlineSpans("``", link).text)
        assertEquals("~x~", parseInlineSpans("~x~", link).text)
        assertTrue(parseInlineSpans("a ~~b", link).spanStyles.isEmpty())
    }

    @Test fun boldAndStrikeOnOneLine() {
        val s = parseInlineSpans("**bold** and ~~x~~", link)
        assertEquals("bold and x", s.text)
        assertEquals(2, s.spanStyles.size)
    }

    @Test fun quoteToggleRoundTrips() {
        val strip = Regex("^> ?")
        val on = RichTextEditing.toggleLinePrefix(TextFieldValue("line", TextRange(2)), "> ", strip)
        assertEquals("> line", on.text)
        val off = RichTextEditing.toggleLinePrefix(on, "> ", strip)
        assertEquals("line", off.text)
    }

    @Test fun historyCoalescesTypingAndSplitsOnPause() {
        val h = EditHistory(TextFieldValue(""))
        h.record(TextFieldValue("a"), now = 0)
        h.record(TextFieldValue("ab"), now = 300)
        h.record(TextFieldValue("abc"), now = 600)
        h.record(TextFieldValue("abcd"), now = 5_000) // pause → new step
        assertEquals("abc", h.undo()!!.text)
        assertEquals("", h.undo()!!.text)
        assertFalse(h.canUndo)
        assertEquals("abc", h.redo()!!.text)
        assertEquals("abcd", h.redo()!!.text)
        assertNull(h.redo())
    }

    @Test fun toolbarActionIsItsOwnStepAndClearsRedo() {
        val h = EditHistory(TextFieldValue("x"))
        h.record(TextFieldValue("xy"), now = 0)
        h.record(TextFieldValue("**xy**"), now = 100, boundary = true)
        assertEquals("xy", h.undo()!!.text)
        assertTrue(h.canRedo)
        h.record(TextFieldValue("xyz"), now = 200)
        assertFalse(h.canRedo)
        assertEquals("xy", h.undo()!!.text)
    }

    @Test fun selectionOnlyChangeIsNotAStep() {
        val h = EditHistory(TextFieldValue("abc"))
        h.record(TextFieldValue("abc", TextRange(1)), now = 0)
        assertFalse(h.canUndo)
    }

    @Test fun pasteIsItsOwnStep() {
        val h = EditHistory(TextFieldValue(""))
        h.record(TextFieldValue("a"), now = 0)
        h.record(TextFieldValue("a pasted text"), now = 100)
        assertEquals("a", h.undo()!!.text)
    }
}
