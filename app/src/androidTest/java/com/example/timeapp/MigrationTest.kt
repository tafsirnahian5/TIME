package com.example.timeapp

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.timeapp.data.db.ALL_MIGRATIONS
import com.example.timeapp.data.db.MIGRATION_13_14
import com.example.timeapp.data.db.MIGRATION_14_15
import com.example.timeapp.data.db.MIGRATION_15_16
import com.example.timeapp.data.db.MIGRATION_16_17
import com.example.timeapp.data.db.MIGRATION_19_20
import com.example.timeapp.data.db.MIGRATION_25_26
import com.example.timeapp.data.db.MIGRATION_26_27
import com.example.timeapp.data.db.MIGRATION_27_28
import com.example.timeapp.data.db.MIGRATION_28_29
import com.example.timeapp.data.db.MIGRATION_29_30
import com.example.timeapp.data.db.MIGRATION_30_31
import com.example.timeapp.data.db.MIGRATION_31_32
import com.example.timeapp.data.db.MIGRATION_32_33
import com.example.timeapp.data.db.MIGRATION_33_34
import com.example.timeapp.data.db.MIGRATION_34_35
import com.example.timeapp.data.db.MIGRATION_35_36
import com.example.timeapp.data.db.MIGRATION_36_37
import com.example.timeapp.data.db.MIGRATION_37_38
import com.example.timeapp.data.db.TimeDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Every migration from 13 on must keep existing rows and produce exactly the schema Room
 * expects (runMigrationsAndValidate checks it against the exported JSON). Needs a device or
 * emulator: `./gradlew connectedDebugAndroidTest`.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        TimeDatabase::class.java,
    )

    @Test
    fun migrate13To14KeepsRecordsAndAddsHabits() {
        helper.createDatabase(DB, 13).use { db ->
            db.execSQL(
                "INSERT INTO wallets (id, name, kind, openingBalanceMinor, openingDate) " +
                    "VALUES (1, 'Cash', 'CASH', 500075, '2026-09-01')",
            )
        }
        helper.runMigrationsAndValidate(DB, 14, true, MIGRATION_13_14).use { db ->
            db.query("SELECT openingBalanceMinor FROM wallets WHERE id = 1").use {
                it.moveToFirst()
                assertEquals(500075L, it.getLong(0))
            }
            db.execSQL("INSERT INTO habits (name, schedule, startedOn, archived, sortOrder) VALUES ('Read', 'DAILY', '2026-09-27', 0, 0)")
            db.execSQL("INSERT INTO habit_checks (habitId, date, mark) VALUES (1, '2026-09-27', 'DONE')")
        }
    }

    @Test
    fun migrate14To15KeepsTasksAndAddsProjectsAndTags() {
        helper.createDatabase(DB, 14).use { db ->
            db.execSQL(
                "INSERT INTO entries (id, kind, title, status, isFixed, priority) " +
                    "VALUES (7, 'TASK', 'Portfolio page', 'PLANNED', 0, 'NORMAL')",
            )
        }
        helper.runMigrationsAndValidate(DB, 15, true, MIGRATION_14_15).use { db ->
            db.query("SELECT title, projectId FROM entries WHERE id = 7").use {
                it.moveToFirst()
                assertEquals("Portfolio page", it.getString(0))
                assertEquals(true, it.isNull(1))
            }
            db.execSQL("INSERT INTO tags (id, name) VALUES (1, 'android')")
            db.execSQL("INSERT INTO entry_tags (entryId, tagId) VALUES (7, 1)")
        }
    }

    @Test
    fun migrate15To16KeepsExistingCardOrder() {
        helper.createDatabase(DB, 15).use { db ->
            db.execSQL("INSERT INTO entries (id, kind, title, status, isFixed, priority) VALUES (3, 'TASK', 'b', 'PLANNED', 0, 'NORMAL')")
            db.execSQL("INSERT INTO entries (id, kind, title, status, isFixed, priority) VALUES (9, 'TASK', 'a', 'PLANNED', 0, 'NORMAL')")
        }
        helper.runMigrationsAndValidate(DB, 16, true, MIGRATION_15_16).use { db ->
            db.query("SELECT id FROM entries ORDER BY boardOrder").use {
                it.moveToFirst(); assertEquals(3L, it.getLong(0))
                it.moveToNext(); assertEquals(9L, it.getLong(0))
            }
        }
    }

    @Test
    fun migrate16To17AddsRefundLink() {
        helper.createDatabase(DB, 16).use { db ->
            db.execSQL(
                "INSERT INTO money_records (id, direction, title, amountMinor, paidMinor, linkedToPlan) " +
                    "VALUES (5, 'COST', 'Headphones', 350000, 350000, 0)",
            )
        }
        helper.runMigrationsAndValidate(DB, 17, true, MIGRATION_16_17).use { db ->
            db.query("SELECT paidMinor, refundOfId FROM money_records WHERE id = 5").use {
                it.moveToFirst()
                assertEquals(350000L, it.getLong(0))
                assertEquals(true, it.isNull(1))
            }
        }
    }

    @Test
    fun migrate19To20SeedsTheDefaultColumns() {
        helper.createDatabase(DB, 19).close()
        helper.runMigrationsAndValidate(DB, 20, true, MIGRATION_19_20).use { db ->
            db.query("SELECT name, status FROM board_columns ORDER BY sortOrder").use {
                assertEquals(6, it.count)
                it.moveToFirst(); assertEquals("Inbox", it.getString(0)); assertEquals("UNSCHEDULED", it.getString(1))
            }
        }
    }

    /** A task already done gets its completion time from the status log; one with no logged
     * completion stays null rather than being given a guessed date. */
    @Test
    fun migrate25To26RecoversCompletionTimes() {
        helper.createDatabase(DB, 25).use { db ->
            db.execSQL(
                "INSERT INTO entries (id, kind, title, status, isFixed, priority, boardOrder) VALUES " +
                    "(1, 'TASK', 'Logged', 'DONE', 0, 'NORMAL', 0), (2, 'TASK', 'Unlogged', 'DONE', 0, 'NORMAL', 0), " +
                    "(3, 'TASK', 'Open', 'PLANNED', 0, 'NORMAL', 0)",
            )
            db.execSQL(
                "INSERT INTO entry_events (entryId, at, fromStatus, toStatus) VALUES " +
                    "(1, 1000, 'PLANNED', 'DONE'), (1, 2000, 'DONE', 'PLANNED'), (1, 3000, 'PLANNED', 'DONE'), " +
                    "(3, 4000, 'PLANNED', 'DONE'), (3, 5000, 'DONE', 'PLANNED')",
            )
            db.execSQL("INSERT INTO wallets (id, name, kind, openingBalanceMinor, openingDate) VALUES (1, 'Cash', 'CASH', 500, '2026-01-01')")
        }
        helper.runMigrationsAndValidate(DB, 26, true, MIGRATION_25_26).use { db ->
            db.query("SELECT id, completedAt, archivedAt FROM entries ORDER BY id").use {
                it.moveToFirst(); assertEquals(3000L, it.getLong(1)); assertEquals(true, it.isNull(2))
                it.moveToNext(); assertEquals(true, it.isNull(1))
                it.moveToNext(); assertEquals(true, it.isNull(1))
            }
            db.query("SELECT openingBalanceMinor, archivedAt FROM wallets").use {
                it.moveToFirst(); assertEquals(500L, it.getLong(0)); assertEquals(true, it.isNull(1))
            }
        }
    }

    /** Sessions survive the rebuild; one running at upgrade becomes the timer's running stopwatch. */
    @Test
    fun migrate26To27KeepsSessionsAndAdoptsTheRunningOne() {
        helper.createDatabase(DB, 26).use { db ->
            db.execSQL("INSERT INTO entries (id, kind, title, status, isFixed, priority, boardOrder) VALUES (4, 'TASK', 'Essay', 'DOING', 0, 'NORMAL', 0)")
            db.execSQL("INSERT INTO work_sessions (id, entryId, startedAt, endedAt) VALUES (1, 4, 1000, 2000), (2, 4, 5000, NULL)")
        }
        helper.runMigrationsAndValidate(DB, 27, true, MIGRATION_26_27).use { db ->
            db.query("SELECT id, mode, needsReview FROM work_sessions ORDER BY id").use {
                assertEquals(2, it.count)
                it.moveToFirst(); assertEquals("TASK", it.getString(1)); assertEquals(0, it.getInt(2))
            }
            db.query("SELECT entryId, phase, running, stretchStartedAt FROM focus_runtime").use {
                it.moveToFirst(); assertEquals(4L, it.getLong(0)); assertEquals("WORK", it.getString(1)); assertEquals(1, it.getInt(2)); assertEquals(5000L, it.getLong(3))
            }
        }
    }

    @Test
    fun migrate27To28KeepsTheTimelineAndAddsCommitments() {
        helper.createDatabase(DB, 27).use { db ->
            db.execSQL("INSERT INTO workday_segments (id, date, kind, startedAt, endedAt) VALUES (1, '2026-09-28', 'ON_DUTY', 1000, 2000)")
        }
        helper.runMigrationsAndValidate(DB, 28, true, MIGRATION_27_28).use { db ->
            db.query("SELECT startedAt, correctionReason FROM workday_segments").use {
                it.moveToFirst(); assertEquals(1000L, it.getLong(0)); assertEquals(true, it.isNull(1))
            }
            db.query("SELECT COUNT(*) FROM commitments").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
        }
    }

    @Test
    fun migrate28To29KeepsRemindersWithoutATiming() {
        helper.createDatabase(DB, 28).use { db ->
            db.execSQL("INSERT INTO reminders (id, ownerKind, ownerId, scheduledAt, delivered) VALUES (1, 'TASK', 3, 5000, 0)")
        }
        helper.runMigrationsAndValidate(DB, 29, true, MIGRATION_28_29).use { db ->
            db.query("SELECT scheduledAt, dayOffset, minutesBefore FROM reminders").use {
                it.moveToFirst(); assertEquals(5000L, it.getLong(0)); assertEquals(true, it.isNull(1)); assertEquals(true, it.isNull(2))
            }
        }
    }

    @Test
    fun migrate29To30KeepsHabitHistory() {
        helper.createDatabase(DB, 29).use { db ->
            db.execSQL("INSERT INTO habits (id, name, schedule, startedOn, archived, sortOrder) VALUES (1, 'Read', 'DAILY', '2026-09-01', 0, 0)")
            db.execSQL("INSERT INTO habit_checks (habitId, date, mark) VALUES (1, '2026-09-02', 'DONE')")
        }
        helper.runMigrationsAndValidate(DB, 30, true, MIGRATION_29_30).use { db ->
            db.query("SELECT mark, value, note FROM habit_checks").use {
                it.moveToFirst(); assertEquals("DONE", it.getString(0)); assertEquals(true, it.isNull(1))
            }
            db.query("SELECT targetAtMost FROM habits").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
        }
    }

    @Test
    fun migrate30To31AddsLoans() {
        helper.createDatabase(DB, 30).close()
        helper.runMigrationsAndValidate(DB, 31, true, MIGRATION_30_31).use { db ->
            db.query("SELECT COUNT(*) FROM loans").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
        }
    }

    @Test
    fun migrate31To32KeepsCategories() {
        helper.createDatabase(DB, 31).use { db ->
            db.execSQL("INSERT INTO categories (id, name, colorArgb, kind, archived, sortOrder) VALUES (1, 'Food', 4278190080, 'MONEY', 0, 0)")
        }
        helper.runMigrationsAndValidate(DB, 32, true, MIGRATION_31_32).use { db ->
            db.query("SELECT name, icon FROM categories").use { it.moveToFirst(); assertEquals("Food", it.getString(0)); assertEquals(true, it.isNull(1)) }
        }
    }

    @Test
    fun migrate32To33GivesPlansNormalPriority() {
        helper.createDatabase(DB, 32).use { db ->
            db.execSQL("INSERT INTO money_records (id, direction, title, amountMinor, paidMinor, status, linkedToPlan) VALUES (1, 'COST', 'Rent', 100, 0, 'PLANNED', 0)")
        }
        helper.runMigrationsAndValidate(DB, 33, true, MIGRATION_32_33).use { db ->
            db.query("SELECT priority, taskId FROM money_records").use { it.moveToFirst(); assertEquals("NORMAL", it.getString(0)); assertEquals(true, it.isNull(1)) }
        }
    }

    @Test
    fun migrate33To34AddsAttachments() {
        helper.createDatabase(DB, 33).close()
        helper.runMigrationsAndValidate(DB, 34, true, MIGRATION_33_34).use { db ->
            db.query("SELECT COUNT(*) FROM attachments").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
        }
    }

    @Test
    fun migrate34To35KeepsLinksWithUnknownDates() {
        helper.createDatabase(DB, 34).use { db ->
            db.execSQL("INSERT INTO bookmarks (id, title, url, folder, favourite, note) VALUES (1, 'Docs', 'https://a.b', 'Work', 0, NULL)")
        }
        helper.runMigrationsAndValidate(DB, 35, true, MIGRATION_34_35).use { db ->
            db.query("SELECT addedAt, archivedAt FROM bookmarks WHERE id = 1").use {
                it.moveToFirst(); assertTrue(it.isNull(0)); assertTrue(it.isNull(1))
            }
        }
    }

    @Test
    fun migrate35To36AddsEmptyScheduleBlocks() {
        helper.createDatabase(DB, 35).close()
        helper.runMigrationsAndValidate(DB, 36, true, MIGRATION_35_36).use { db ->
            db.query("SELECT COUNT(*) FROM schedule_blocks").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
        }
    }

    @Test
    fun migrate36To37AddsIndexes() {
        helper.createDatabase(DB, 36).close()
        helper.runMigrationsAndValidate(DB, 37, true, MIGRATION_36_37).use { db ->
            db.query("SELECT COUNT(*) FROM sqlite_master WHERE type = 'index' AND name LIKE 'index_money_records_%'").use {
                it.moveToFirst(); assertEquals(4, it.getInt(0))
            }
        }
    }

    @Test
    fun migrate37To38AddsEmptyInbox() {
        helper.createDatabase(DB, 37).close()
        helper.runMigrationsAndValidate(DB, 38, true, MIGRATION_37_38).use { db ->
            db.query("SELECT COUNT(*) FROM inbox_items").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
        }
    }

    /** The whole chain, so a future bump that forgets to register its migration fails here. */
    @Test
    fun migrateAllFrom13() {
        helper.createDatabase(DB, 13).close()
        helper.runMigrationsAndValidate(DB, LATEST, true, *ALL_MIGRATIONS).close()
    }

    private companion object {
        const val DB = "migration-test"
        const val LATEST = com.example.timeapp.data.db.DB_VERSION
    }
}
