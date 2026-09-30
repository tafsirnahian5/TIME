package com.example.timeapp

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.timeapp.data.AppSettingsDataStore
import com.example.timeapp.data.BackupManager
import com.example.timeapp.data.PreviewOutcome
import com.example.timeapp.data.RestoreResult
import com.example.timeapp.data.db.TimeDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The PDF's 1.5 exit check: a protected backup opens only with its password, and a wrong
 * password changes nothing. Uses the app's real database file. Needs a device.
 */
@RunWith(AndroidJUnit4::class)
class BackupPasswordTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val manager = BackupManager(context, TimeDatabase.getInstance(context), AppSettingsDataStore(context))

    @Test
    fun protectedBackupNeedsItsPasswordAndAWrongOneChangesNothing() = runBlocking<Unit> {
        val file = File(context.cacheDir, "test.timebackup").apply { delete() }
        assertTrue(manager.createBackup(Uri.fromFile(file), "correct horse".toCharArray()))

        assertEquals(PreviewOutcome.NeedsPassword, manager.previewRestore(Uri.fromFile(file)))
        assertEquals(PreviewOutcome.WrongPassword, manager.previewRestore(Uri.fromFile(file), "wrong horse".toCharArray()))
        val ok = manager.previewRestore(Uri.fromFile(file), "correct horse".toCharArray())
        assertTrue(ok is PreviewOutcome.Ok && ok.encrypted)

        val dbBefore = context.getDatabasePath(TimeDatabase.FILE_NAME).lastModified()
        assertEquals(RestoreResult.WrongPassword, manager.restoreBackup(Uri.fromFile(file), "wrong horse".toCharArray()))
        assertEquals(dbBefore, context.getDatabasePath(TimeDatabase.FILE_NAME).lastModified())
        file.delete()
    }

    @Test
    fun plainBackupStillOpensWithoutAPassword() = runBlocking<Unit> {
        val file = File(context.cacheDir, "test.zip").apply { delete() }
        assertTrue(manager.createBackup(Uri.fromFile(file)))
        val ok = manager.previewRestore(Uri.fromFile(file))
        assertTrue(ok is PreviewOutcome.Ok && !ok.encrypted)
        file.delete()
    }
}
