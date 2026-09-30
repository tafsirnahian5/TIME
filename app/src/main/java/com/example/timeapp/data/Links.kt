package com.example.timeapp.data

import java.net.URI

/**
 * Checking and comparing saved links. Pure — tested in LinksTest.
 *
 * Only web links are saved. `javascript:`, `file:`, `data:` and the like are refused rather
 * than stored, since opening one from a list would run or expose something, not show a page.
 */
sealed class UrlCheck {
    data class Ok(val url: String) : UrlCheck()
    data class Invalid(val reason: String) : UrlCheck()
}

private val schemePattern = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:")

/** "example.com/x" → "https://example.com/x"; scheme and host lower-cased, the rest untouched
 * so a URL round-trips exactly. */
fun checkUrl(raw: String): UrlCheck {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return UrlCheck.Invalid(L("Enter a web address."))
    if (trimmed.any { it.isWhitespace() }) return UrlCheck.Invalid(L("A web address can't contain spaces."))
    val hasScheme = trimmed.contains("://") ||
        // "mailto:x" / "javascript:x" — but not "example.com:8080/path"
        (schemePattern.containsMatchIn(trimmed) && !trimmed.substringBefore(':').contains('.'))
    val withScheme = if (hasScheme) trimmed else "https://$trimmed"
    val scheme = withScheme.substringBefore(':').lowercase()
    if (scheme != "http" && scheme != "https") return UrlCheck.Invalid(L("Only web links (http or https) can be saved."))
    val uri = runCatching { URI(withScheme) }.getOrNull() ?: return UrlCheck.Invalid(L("That doesn't look like a web address."))
    val host = uri.host ?: return UrlCheck.Invalid(L("That doesn't look like a web address."))
    if (!host.contains('.') && host != "localhost") return UrlCheck.Invalid(L("That doesn't look like a web address."))
    val rest = withScheme.substringAfter("://").substringAfter(uri.rawAuthority)
    // Only the host is case-insensitive; a sign-in name or password in the address is kept
    // exactly (it used to be lower-cased along with the host).
    val userInfo = uri.rawUserInfo?.let { "$it@" } ?: ""
    val port = if (uri.port == -1) "" else ":${uri.port}"
    return UrlCheck.Ok("$scheme://$userInfo${host.lowercase()}$port$rest")
}

/**
 * What makes two links "the same page" for duplicate warnings: http vs https, a leading
 * "www.", a trailing slash and the #fragment don't matter; the path and query do.
 */
fun urlKey(url: String): String {
    val ok = (checkUrl(url) as? UrlCheck.Ok)?.url ?: return url.trim().lowercase()
    val uri = runCatching { URI(ok) }.getOrNull() ?: return ok
    val host = uri.host.lowercase().removePrefix("www.")
    val port = if (uri.port == -1) "" else ":${uri.port}"
    val path = (uri.rawPath ?: "").trimEnd('/')
    val query = uri.rawQuery?.let { "?$it" } ?: ""
    return "$host$port$path$query"
}

/** The saved link [url] would duplicate, if any (ignoring [exceptId], the one being edited). */
fun List<Bookmark>.duplicateOf(url: String, exceptId: Long? = null): Bookmark? {
    val key = urlKey(url)
    return firstOrNull { it.id != exceptId && urlKey(it.url) == key }
}

/** True when [url] carries a sign-in name or password (`https://user:pass@host`). */
fun hasCredentials(url: String): Boolean =
    runCatching { URI(if (url.contains("://")) url else "https://$url").rawUserInfo != null }.getOrDefault(false)

/**
 * [url] as shown on screen: the scheme and any sign-in name/password left out (the PDF:
 * credentials in a URL are never displayed). The saved address, and Open/Copy/Share, keep them.
 */
fun displayUrl(url: String): String {
    val withoutScheme = url.substringAfter("://")
    val authority = withoutScheme.substringBefore('/').substringBefore('?').substringBefore('#')
    val rest = withoutScheme.substring(authority.length)
    return authority.substringAfterLast('@') + rest
}

/**
 * Every link as JSON — the PDF's bookmark export beside the browser HTML. Hand-written so it
 * stays pure and testable; notes, titles and folders are escaped per RFC 8259. Archived links
 * are included and marked, so the file is a whole copy.
 */
object BookmarkJson {
    fun export(bookmarks: List<Bookmark>, exportedAt: java.time.Instant): String = buildString {
        append("{\n  \"format\": \"time-links\",\n  \"version\": 1,\n")
        append("  \"exportedAt\": \"").append(exportedAt).append("\",\n  \"links\": [")
        bookmarks.forEachIndexed { i, b ->
            append(if (i == 0) "\n" else ",\n")
            append("    {")
            append("\"title\": ").append(str(b.title))
            append(", \"url\": ").append(str(b.url))
            append(", \"folder\": ").append(str(b.folder))
            append(", \"favourite\": ").append(b.favourite)
            append(", \"note\": ").append(b.note?.let(::str) ?: "null")
            append(", \"addedAt\": ").append(b.addedAt?.let { str(it.toString()) } ?: "null")
            append(", \"archived\": ").append(b.archivedAt != null)
            append("}")
        }
        append(if (bookmarks.isEmpty()) "]\n}\n" else "\n  ]\n}\n")
    }

    fun str(s: String): String = buildString {
        append('"')
        s.forEach { c ->
            when {
                c == '"' -> append("\\\"")
                c == '\\' -> append("\\\\")
                c == '\n' -> append("\\n")
                c == '\r' -> append("\\r")
                c == '\t' -> append("\\t")
                c < ' ' -> append("\\u%04x".format(c.code))
                else -> append(c)
            }
        }
        append('"')
    }
}
