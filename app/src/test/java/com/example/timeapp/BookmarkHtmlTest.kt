package com.example.timeapp

import com.example.timeapp.data.Bookmark
import com.example.timeapp.data.BookmarkHtml
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookmarkHtmlTest {

    /** The PDF's 1.4 exit check: notes and URLs round-trip without loss. */
    @Test
    fun `export then parse gives back exactly what went in`() {
        val saved = listOf(
            Bookmark(1, "Kotlin & \"Compose\" <docs>", "https://developer.android.com/jetpack/compose?hl=en#top", "Coding", true,
                "**Read** this first\n- [ ] codelab\n- [x] setup"),
            Bookmark(2, "Rent portal", "https://example.com/pay", "Personal / Home"),
        )
        val back = BookmarkHtml.parse(BookmarkHtml.export(saved))
        assertEquals(2, back.size)
        val a = back.single { it.url.startsWith("https://developer") }
        assertEquals(saved[0].title, a.title)
        assertEquals(saved[0].url, a.url)
        assertEquals(saved[0].note, a.note)
        assertEquals("Coding", a.folder)
        assertTrue(a.favourite)
        val b = back.single { it.title == "Rent portal" }
        assertEquals("Personal / Home", b.folder)
        assertFalse(b.favourite)
    }

    /** A real browser export: nested folders, a bookmarks-bar wrapper, a bookmarklet. */
    @Test
    fun `browser files keep nesting and refuse non-web entries`() {
        val html = """
            <!DOCTYPE NETSCAPE-Bookmark-file-1>
            <DL><p>
                <DT><H3 ADD_DATE="1">Bookmarks bar</H3>
                <DL><p>
                    <DT><A HREF="https://news.example.com/" ADD_DATE="2">News</A>
                    <DT><H3>Dev</H3>
                    <DL><p>
                        <DT><A HREF="https://github.com/">GitHub</A>
                        <DD>Where code lives
                    </DL><p>
                    <DT><A HREF="javascript:alert(document.cookie)">Evil bookmarklet</A>
                </DL><p>
                <DT><A HREF="https://loose.example.com">Loose</A>
            </DL><p>
        """.trimIndent()
        val parsed = BookmarkHtml.parse(html)
        assertEquals(listOf("News", "GitHub", "Evil bookmarklet", "Loose"), parsed.map { it.title })
        assertEquals("Bookmarks bar", parsed[0].folder)
        assertEquals("Bookmarks bar / Dev", parsed[1].folder)
        assertEquals("Where code lives", parsed[1].note)
        assertEquals("Imported", parsed[3].folder)

        val rows = BookmarkHtml.check(parsed, saved = listOf(Bookmark(9, "GH", "http://www.github.com", "Dev")))
        assertTrue(rows[1].duplicate)
        assertTrue(rows[2].error != null) // the bookmarklet is never saved
        assertTrue(rows[3].importable)
    }
}
