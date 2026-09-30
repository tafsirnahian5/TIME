package com.example.timeapp.data

/**
 * Receipt attachments (Phase 13; the PDF: "attachments are copied into app-managed storage
 * after a file-picker selection, with size limits, safe filenames and MIME checks. Backups
 * include those bytes, not stale references"). Pure rules; tested in AttachmentsTest.
 */
object Attachments {
    const val MAX_BYTES = 10L * 1024 * 1024
    val ALLOWED = mapOf("image/jpeg" to "jpg", "image/png" to "png", "image/webp" to "webp", "application/pdf" to "pdf")
    const val DIR = "attachments"

    /** Why this file can't be attached, or null when it can. */
    fun problem(mime: String?, sizeBytes: Long?): String? = when {
        mime == null || mime !in ALLOWED -> "Only photos (JPEG, PNG, WebP) and PDFs can be attached."
        sizeBytes != null && sizeBytes > MAX_BYTES -> L("That file is over 10 MB.")
        sizeBytes == 0L -> L("That file is empty.")
        else -> null
    }

    private val safeName = Regex("^[a-f0-9-]{8,64}\\.(jpg|png|webp|pdf)$")

    /** The stored file name for a zip entry, or null when the entry isn't one of ours — nothing
     * outside `attachments/`, no sub-folders, no `..`, only names the app itself would make. */
    fun storedNameFromZip(entry: String): String? {
        if (!entry.startsWith("$DIR/")) return null
        val name = entry.removePrefix("$DIR/")
        return name.takeIf { safeName.matches(it) }
    }
}
