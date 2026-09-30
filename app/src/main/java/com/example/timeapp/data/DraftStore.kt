package com.example.timeapp.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.draftsDataStore by preferencesDataStore(name = "drafts")

/**
 * Editor drafts (Phase 15): the text typed into a *new* record's form, kept until it's saved or
 * discarded, so leaving a half-written task, bill or link — or the app being killed — loses
 * nothing. One entry per form, its fields encoded by [encode]. Drafts are device-local scratch:
 * not in backups, and never created for an edit of an existing record (the record itself is
 * the saved version there).
 */
class DraftStore(private val context: Context) {
    private fun key(form: String) = stringPreferencesKey("draft_$form")

    suspend fun load(form: String): Map<String, String> =
        decode(context.draftsDataStore.data.first()[key(form)])

    suspend fun save(form: String, fields: Map<String, String>) {
        context.draftsDataStore.edit { prefs ->
            if (fields.values.all { it.isBlank() }) prefs.remove(key(form)) else prefs[key(form)] = encode(fields)
        }
    }

    suspend fun clear(form: String) {
        context.draftsDataStore.edit { it.remove(key(form)) }
    }

    companion object {
        /** `name=value` lines with `\` and newlines escaped, so notes keep their line breaks. */
        fun encode(fields: Map<String, String>): String = fields.entries.joinToString("\n") { (k, v) ->
            "$k=" + v.replace("\\", "\\\\").replace("\n", "\\n")
        }

        fun decode(raw: String?): Map<String, String> = raw.orEmpty().lines().filter { '=' in it }.associate { line ->
            val k = line.substringBefore('=')
            val v = buildString {
                val s = line.substringAfter('=')
                var i = 0
                while (i < s.length) {
                    val c = s[i]
                    if (c == '\\' && i + 1 < s.length) {
                        append(if (s[i + 1] == 'n') '\n' else s[i + 1]); i += 2
                    } else {
                        append(c); i++
                    }
                }
            }
            k to v
        }
    }
}
