package com.example.timeapp

import com.example.timeapp.data.Attachments
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AttachmentsTest {
    @Test
    fun `only photos and PDFs up to 10 MB`() {
        assertNull(Attachments.problem("image/jpeg", 2_000_000))
        assertNull(Attachments.problem("application/pdf", null))
        assertNotNull(Attachments.problem("text/html", 10))
        assertNotNull(Attachments.problem("image/png", 11L * 1024 * 1024))
        assertNotNull(Attachments.problem("image/png", 0))
    }

    /** A backup's zip is untrusted input: only our own names come out of it. */
    @Test
    fun `zip entries can't escape the attachments folder`() {
        assertEquals("3f2a9c1e-77aa-4b1b-9d1e-0a1b2c3d4e5f.jpg", Attachments.storedNameFromZip("attachments/3f2a9c1e-77aa-4b1b-9d1e-0a1b2c3d4e5f.jpg"))
        assertNull(Attachments.storedNameFromZip("attachments/../time.db"))
        assertNull(Attachments.storedNameFromZip("attachments/sub/abcdef12.jpg"))
        assertNull(Attachments.storedNameFromZip("attachments/abcdef12.exe"))
        assertNull(Attachments.storedNameFromZip("time.db"))
    }
}
