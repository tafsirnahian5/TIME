package com.example.timeapp.data

/**
 * Bookmarks in the Netscape bookmark-file format — what every desktop and mobile browser
 * imports and exports. Pure: [export] writes, [parse] reads, both tested in BookmarkHtmlTest.
 *
 * The file is treated strictly as data (the PDF: "parsed as data without executing
 * scripts"): tags are matched with patterns, nothing is rendered or evaluated, and entries
 * that aren't web links (bookmarklets, `place:` queries) are reported, not saved.
 *
 * Round-trip without loss (the PDF's 1.4 exit check): a note's line breaks are written as
 * `&#10;` so the single-line DD survives, and favourites ride in a `TIME_FAVOURITE`
 * attribute that other browsers ignore.
 */
object BookmarkHtml {

    fun export(bookmarks: List<Bookmark>): String = buildString {
        appendLine("<!DOCTYPE NETSCAPE-Bookmark-file-1>")
        appendLine("<!-- Exported from T!ME. -->")
        appendLine("<META HTTP-EQUIV=\"Content-Type\" CONTENT=\"text/html; charset=UTF-8\">")
        appendLine("<TITLE>Bookmarks</TITLE>")
        appendLine("<H1>Bookmarks</H1>")
        appendLine("<DL><p>")
        bookmarks.groupBy { it.folder }.toSortedMap().forEach { (folder, items) ->
            appendLine("    <DT><H3>${escape(folder)}</H3>")
            appendLine("    <DL><p>")
            items.forEach { b ->
                val fav = if (b.favourite) " TIME_FAVOURITE=\"1\"" else ""
                // ADD_DATE is the format's own "saved on", in Unix seconds.
                val added = b.addedAt?.let { " ADD_DATE=\"${it.epochSecond}\"" } ?: ""
                appendLine("        <DT><A HREF=\"${escape(b.url)}\"$added$fav>${escape(b.title)}</A>")
                b.note?.takeIf { it.isNotEmpty() }?.let { appendLine("        <DD>${escape(it)}") }
            }
            appendLine("    </DL><p>")
        }
        appendLine("</DL><p>")
    }

    /** One link as read from a file, before it's checked against the saved ones. */
    data class Parsed(
        val title: String, val url: String, val folder: String, val note: String?, val favourite: Boolean,
        val addedAt: java.time.Instant? = null,
    )

    private val token = Regex(
        "<H3[^>]*>(.*?)</H3>|<A\\s([^>]*)>(.*?)</A>|<DD>([^<]*)|<DL[^>]*>|</DL>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val hrefAttr = Regex("HREF\\s*=\\s*\"([^\"]*)\"", RegexOption.IGNORE_CASE)
    private val favAttr = Regex("TIME_FAVOURITE\\s*=\\s*\"1\"", RegexOption.IGNORE_CASE)
    private val addDateAttr = Regex("ADD_DATE\\s*=\\s*\"(\\d{1,12})\"", RegexOption.IGNORE_CASE)

    /**
     * Every link in [html], with its folder. Nested browser folders become "Parent / Child";
     * links outside any folder go to [defaultFolder]. A top-level "Bookmarks bar"-style
     * wrapper is kept as a folder name like any other — nothing is guessed away.
     */
    fun parse(html: String, defaultFolder: String = "Imported"): List<Parsed> {
        val out = mutableListOf<Parsed>()
        val folders = ArrayDeque<String>()
        var pendingFolder: String? = null
        // A <DL> opened right after an <H3> belongs to that folder; other DLs (the outer
        // wrapper) push nothing, tracked so their </DL> pops nothing either.
        val dlOpensFolder = ArrayDeque<Boolean>()
        for (m in token.findAll(html)) {
            val text = m.value
            when {
                text.startsWith("<H3", ignoreCase = true) -> pendingFolder = unescape(m.groupValues[1]).trim()
                text.startsWith("<DL", ignoreCase = true) -> {
                    val f = pendingFolder
                    if (f != null) folders.addLast(f)
                    dlOpensFolder.addLast(f != null)
                    pendingFolder = null
                }
                text.equals("</DL>", ignoreCase = true) -> {
                    if (dlOpensFolder.removeLastOrNull() == true) folders.removeLastOrNull()
                }
                text.startsWith("<A", ignoreCase = true) -> {
                    val attrs = m.groupValues[2]
                    val href = hrefAttr.find(attrs)?.groupValues?.get(1)?.let(::unescape) ?: continue
                    val title = unescape(stripTags(m.groupValues[3])).trim().ifEmpty { href }
                    out += Parsed(
                        title = title,
                        url = href.trim(),
                        folder = folders.joinToString(" / ").ifEmpty { defaultFolder },
                        note = null,
                        favourite = favAttr.containsMatchIn(attrs),
                        addedAt = addDateAttr.find(attrs)?.groupValues?.get(1)?.toLongOrNull()
                            ?.takeIf { it > 0 }?.let { java.time.Instant.ofEpochSecond(it) },
                    )
                }
                text.startsWith("<DD", ignoreCase = true) -> {
                    val note = unescape(m.groupValues[4]).trimEnd()
                    if (out.isNotEmpty() && note.isNotBlank()) out[out.lastIndex] = out.last().copy(note = note)
                }
            }
        }
        return out
    }

    /** A parsed link against what's saved: importable, a duplicate, or refused (with why). */
    data class Row(val parsed: Parsed, val url: String?, val duplicate: Boolean, val error: String?) {
        val importable: Boolean get() = error == null && url != null
    }

    fun check(parsed: List<Parsed>, saved: List<Bookmark>): List<Row> {
        val seen = saved.map { urlKey(it.url) }.toMutableSet()
        return parsed.map { p ->
            when (val c = checkUrl(p.url)) {
                is UrlCheck.Invalid -> Row(p, null, duplicate = false, error = c.reason)
                is UrlCheck.Ok -> Row(p, c.url, duplicate = !seen.add(urlKey(c.url)), error = null)
            }
        }
    }

    private fun stripTags(s: String) = s.replace(Regex("<[^>]*>"), "")

    fun escape(s: String): String = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("\r\n", "&#10;").replace("\n", "&#10;")

    fun unescape(s: String): String = s
        .replace(Regex("&#(\\d+);")) { m -> m.groupValues[1].toIntOrNull()?.let { String(Character.toChars(it)) } ?: m.value }
        .replace(Regex("&#[xX]([0-9a-fA-F]+);")) { m -> m.groupValues[1].toIntOrNull(16)?.let { String(Character.toChars(it)) } ?: m.value }
        .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'")
        .replace("&amp;", "&")
}
