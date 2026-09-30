package com.example.timeapp

import com.example.timeapp.data.DraftStore
import org.junit.Assert.assertEquals
import org.junit.Test

class DraftStoreTest {
    @Test fun fieldsRoundTripWithNewlinesBackslashesAndEquals() {
        val fields = mapOf("title" to "Rent = 18k", "note" to "line 1\nline 2\\n not a newline", "amount" to "")
        assertEquals(fields, DraftStore.decode(DraftStore.encode(fields)))
    }

    @Test fun nothingStoredIsEmpty() = assertEquals(emptyMap<String, String>(), DraftStore.decode(null))
}
