package com.example.timeapp

import com.example.timeapp.data.Bookmark
import com.example.timeapp.data.UrlCheck
import com.example.timeapp.data.checkUrl
import com.example.timeapp.data.duplicateOf
import com.example.timeapp.data.urlKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinksTest {
    private fun ok(raw: String) = (checkUrl(raw) as UrlCheck.Ok).url

    @Test
    fun `bare domains get https and keep their path exactly`() {
        assertEquals("https://example.com", ok("example.com"))
        assertEquals("https://example.com:8080/A/b?q=1#Top", ok("example.com:8080/A/b?q=1#Top"))
        assertEquals("http://example.com/Path", ok("HTTP://Example.COM/Path"))
    }

    @Test
    fun `only web links are accepted`() {
        assertTrue(checkUrl("javascript:alert(1)") is UrlCheck.Invalid)
        assertTrue(checkUrl("file:///sdcard/x") is UrlCheck.Invalid)
        assertTrue(checkUrl("mailto:me@example.com") is UrlCheck.Invalid)
        assertTrue(checkUrl("not a url") is UrlCheck.Invalid)
        assertTrue(checkUrl("localhostish") is UrlCheck.Invalid)
        assertTrue(checkUrl("") is UrlCheck.Invalid)
    }

    @Test
    fun `duplicates ignore scheme, www, trailing slash and fragment`() {
        assertEquals(urlKey("https://www.example.com/docs/"), urlKey("http://example.com/docs#intro"))
        assertTrue(urlKey("example.com/a") != urlKey("example.com/b"))
        assertTrue(urlKey("example.com/a?x=1") != urlKey("example.com/a?x=2"))
    }

    @Test
    fun `duplicateOf skips the link being edited`() {
        val saved = listOf(Bookmark(1, "Docs", "https://example.com/docs", "Work"))
        assertEquals(1L, saved.duplicateOf("example.com/docs/")?.id)
        assertNull(saved.duplicateOf("example.com/docs", exceptId = 1))
    }

    @Test
    fun credentialsKeepTheirCaseButOnlyTheHostIsLowered() {
        val ok = checkUrl("https://Me:SeCret@Example.COM/Path") as UrlCheck.Ok
        assertEquals("https://Me:SeCret@example.com/Path", ok.url)
        assertTrue(com.example.timeapp.data.hasCredentials(ok.url))
        assertTrue(!com.example.timeapp.data.hasCredentials("https://example.com/a@b"))
    }

    @Test
    fun displayUrlNeverShowsCredentials() {
        assertEquals("example.com/x?q=1", com.example.timeapp.data.displayUrl("https://me:pw@example.com/x?q=1"))
        assertEquals("example.com:8080/a@b", com.example.timeapp.data.displayUrl("https://example.com:8080/a@b"))
        assertEquals("example.com", com.example.timeapp.data.displayUrl("example.com"))
    }

    @Test
    fun jsonExportEscapesAndMarksArchived() {
        val at = java.time.Instant.parse("2026-09-28T09:00:00Z")
        val json = com.example.timeapp.data.BookmarkJson.export(
            listOf(Bookmark(1, "Say \"hi\"", "https://a.b", "Work\\Home", note = "line1\nline2\u0001", addedAt = at, archivedAt = at)),
            at,
        )
        assertTrue(json.contains("\"title\": \"Say \\\"hi\\\"\""))
        assertTrue(json.contains("\"folder\": \"Work\\\\Home\""))
        assertTrue(json.contains("\"note\": \"line1\\nline2\\u0001\""))
        assertTrue(json.contains("\"addedAt\": \"2026-09-28T09:00:00Z\""))
        assertTrue(json.contains("\"archived\": true"))
        assertTrue(com.example.timeapp.data.BookmarkJson.export(emptyList(), at).contains("\"links\": []"))
    }

    @Test
    fun htmlCarriesTheSavedDate() {
        val at = java.time.Instant.ofEpochSecond(1_790_000_000)
        val html = com.example.timeapp.data.BookmarkHtml.export(listOf(Bookmark(1, "A", "https://a.b", "F", addedAt = at)))
        assertEquals(at, com.example.timeapp.data.BookmarkHtml.parse(html).single().addedAt)
    }
}
