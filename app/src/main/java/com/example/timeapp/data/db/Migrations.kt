package com.example.timeapp.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Hand-written schema migrations, from version 13 on.
 *
 * Up to 13 the database used a destructive fallback — pre-release, nothing to preserve. From
 * 14 the app holds real records (and a restored older backup is opened, and so migrated, by
 * this same code), so every schema bump from here on needs a [Migration] in [ALL_MIGRATIONS]
 * whose SQL matches the exported schema JSON in `app/schemas/` exactly — Room validates the
 * result on open and crashes on any mismatch, which is the point.
 */
/** The current schema version — `@Database(version = ...)` reads this. */
const val DB_VERSION = 38

/** Oldest schema a migration path exists from. Anything older would be wiped by the
 * destructive fallback, so restore refuses it rather than "succeeding" into an empty app. */
const val OLDEST_MIGRATABLE_VERSION = 13

val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `habits` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, `schedule` TEXT NOT NULL, `startedOn` TEXT NOT NULL, `categoryId` INTEGER, " +
                "`remindMinuteOfDay` INTEGER, `archived` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `habit_checks` (`habitId` INTEGER NOT NULL, `date` TEXT NOT NULL, " +
                "`mark` TEXT NOT NULL, PRIMARY KEY(`habitId`, `date`), FOREIGN KEY(`habitId`) REFERENCES `habits`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
    }
}

/** Projects (a new category kind — text, so no DDL) and reusable tags with task↔tag links. */
val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `entries` ADD COLUMN `projectId` INTEGER")
        db.execSQL("CREATE TABLE IF NOT EXISTS `tags` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tags_name` ON `tags` (`name`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `entry_tags` (`entryId` INTEGER NOT NULL, `tagId` INTEGER NOT NULL, " +
                "PRIMARY KEY(`entryId`, `tagId`), " +
                "FOREIGN KEY(`entryId`) REFERENCES `entries`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`tagId`) REFERENCES `tags`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_entry_tags_tagId` ON `entry_tags` (`tagId`)")
    }
}

/** Manual Board order. Existing cards keep the order they already showed in (by id). */
val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `entries` ADD COLUMN `boardOrder` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE `entries` SET `boardOrder` = `id`")
    }
}

/** Refunds: a REFUND row points at the expense it gives money back for. */
val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `money_records` ADD COLUMN `refundOfId` INTEGER")
    }
}

/** Task state transitions and interruption reasons. History starts now — nothing is back-filled. */
val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `entry_events` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`entryId` INTEGER NOT NULL, `at` INTEGER NOT NULL, `fromStatus` TEXT, `toStatus` TEXT NOT NULL, " +
                "`note` TEXT, `relatedEntryId` INTEGER, FOREIGN KEY(`entryId`) REFERENCES `entries`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_entry_events_entryId` ON `entry_events` (`entryId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_entry_events_at` ON `entry_events` (`at`)")
        // Overdue is display-only; rows an old edit path saved as OVERDUE go back to Planned,
        // so a task whose due date was moved later stops showing as overdue.
        db.execSQL("UPDATE `entries` SET `status` = 'PLANNED' WHERE `status` = 'OVERDUE'")
    }
}

/** Task dependencies: a task waits on its prerequisites. */
val MIGRATION_18_19 = object : Migration(18, 19) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `task_dependencies` (`taskId` INTEGER NOT NULL, `prerequisiteId` INTEGER NOT NULL, " +
                "PRIMARY KEY(`taskId`, `prerequisiteId`), " +
                "FOREIGN KEY(`taskId`) REFERENCES `entries`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`prerequisiteId`) REFERENCES `entries`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_dependencies_prerequisiteId` ON `task_dependencies` (`prerequisiteId`)")
    }
}

/** Custom Board columns, seeded with the six the Board always had, so it looks the same. */
val MIGRATION_19_20 = object : Migration(19, 20) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `board_columns` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, `status` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL, `wipLimit` INTEGER)",
        )
        com.example.timeapp.data.DefaultBoardColumns.forEachIndexed { i, (name, status) ->
            db.execSQL("INSERT INTO `board_columns` (`name`, `status`, `sortOrder`) VALUES ('$name', '${status.name}', $i)")
        }
        db.execSQL("ALTER TABLE `entries` ADD COLUMN `columnId` INTEGER")
    }
}

/** Task ↔ bookmark links. */
val MIGRATION_20_21 = object : Migration(20, 21) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `task_bookmarks` (`entryId` INTEGER NOT NULL, `bookmarkId` INTEGER NOT NULL, " +
                "PRIMARY KEY(`entryId`, `bookmarkId`), " +
                "FOREIGN KEY(`entryId`) REFERENCES `entries`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`bookmarkId`) REFERENCES `bookmarks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_bookmarks_bookmarkId` ON `task_bookmarks` (`bookmarkId`)")
    }
}

/** Imported calendar events remember their UID, so a re-import updates instead of copying. */
val MIGRATION_21_22 = object : Migration(21, 22) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `entries` ADD COLUMN `icsUid` TEXT")
    }
}

/** Savings goals and the money set aside for them. */
val MIGRATION_22_23 = object : Migration(22, 23) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `savings_goals` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                "`targetMinor` INTEGER NOT NULL, `targetDate` TEXT, `archived` INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `goal_allocations` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `goalId` INTEGER NOT NULL, " +
                "`walletId` INTEGER NOT NULL, `amountMinor` INTEGER NOT NULL, `at` INTEGER NOT NULL, " +
                "FOREIGN KEY(`goalId`) REFERENCES `savings_goals`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_goal_allocations_goalId` ON `goal_allocations` (`goalId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_goal_allocations_walletId` ON `goal_allocations` (`walletId`)")
    }
}

/** Utility timers — never work time. */
val MIGRATION_23_24 = object : Migration(23, 24) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `utility_timers` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `label` TEXT NOT NULL, " +
                "`durationMs` INTEGER NOT NULL, `endsAt` INTEGER, `remainingMs` INTEGER NOT NULL)",
        )
    }
}

/** Versioned weekly work targets. */
val MIGRATION_24_25 = object : Migration(24, 25) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `work_targets` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`weeklyMinutes` INTEGER NOT NULL, `effectiveFrom` TEXT NOT NULL)",
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_work_targets_effectiveFrom` ON `work_targets` (`effectiveFrom`)")
    }
}

/**
 * The PDF's Task.completedAt / archivedAt and Wallet.archivedAt. A task already done gets its
 * completion time back from the status log where one exists; one done before the log existed
 * stays null — counted in no period rather than given a guessed date.
 */
val MIGRATION_25_26 = object : Migration(25, 26) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `entries` ADD COLUMN `completedAt` INTEGER")
        db.execSQL("ALTER TABLE `entries` ADD COLUMN `archivedAt` INTEGER")
        db.execSQL("ALTER TABLE `wallets` ADD COLUMN `archivedAt` INTEGER")
        db.execSQL(
            "UPDATE `entries` SET `completedAt` = (SELECT MAX(e.`at`) FROM `entry_events` e " +
                "WHERE e.`entryId` = `entries`.`id` AND e.`toStatus` = 'DONE') WHERE `status` = 'DONE'",
        )
    }
}

/**
 * One work timer (Phase 9). `work_sessions` is rebuilt — SQLite can't make `entryId` nullable
 * in place — keeping every row, now with a mode, a note, a review flag and monotonic anchors,
 * plus indices. The timer's runtime row, session corrections and timer presets are new. A
 * session running at upgrade becomes the timer's running stopwatch, so nothing is lost or
 * left running unseen.
 */
val MIGRATION_26_27 = object : Migration(26, 27) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `work_sessions_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `entryId` INTEGER, " +
                "`startedAt` INTEGER NOT NULL, `endedAt` INTEGER, `mode` TEXT NOT NULL DEFAULT 'TASK', `note` TEXT, " +
                "`needsReview` INTEGER NOT NULL DEFAULT 0, `anchorElapsed` INTEGER, `bootCount` INTEGER, `checkpointAt` INTEGER)",
        )
        db.execSQL(
            "INSERT INTO `work_sessions_new` (`id`, `entryId`, `startedAt`, `endedAt`, `mode`, `needsReview`, `checkpointAt`) " +
                "SELECT `id`, `entryId`, `startedAt`, `endedAt`, 'TASK', 0, COALESCE(`endedAt`, `startedAt`) FROM `work_sessions`",
        )
        db.execSQL("DROP TABLE `work_sessions`")
        db.execSQL("ALTER TABLE `work_sessions_new` RENAME TO `work_sessions`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_work_sessions_entryId` ON `work_sessions` (`entryId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_work_sessions_startedAt` ON `work_sessions` (`startedAt`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `focus_runtime` (`id` INTEGER NOT NULL, `mode` TEXT NOT NULL, `phase` TEXT NOT NULL, " +
                "`running` INTEGER NOT NULL, `entryId` INTEGER, `targetMs` INTEGER, `accumulatedMs` INTEGER NOT NULL, " +
                "`stretchStartedAt` INTEGER, `stretchAnchor` INTEGER, `bootCount` INTEGER, `cycles` INTEGER NOT NULL, " +
                "`laps` TEXT NOT NULL, `awaitingChoice` INTEGER NOT NULL, `countdownMinutes` INTEGER NOT NULL, " +
                "`workMinutes` INTEGER NOT NULL, `breakMinutes` INTEGER NOT NULL, `longBreakMinutes` INTEGER NOT NULL, " +
                "`cyclesPerLongBreak` INTEGER NOT NULL, `autoStart` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL(
            "INSERT INTO `focus_runtime` SELECT 1, 'STOPWATCH', 'WORK', 1, `entryId`, NULL, 0, `startedAt`, NULL, NULL, 0, '', 0, " +
                "30, 25, 5, 15, 4, 0 FROM `work_sessions` WHERE `endedAt` IS NULL LIMIT 1",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `session_corrections` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sessionId` INTEGER NOT NULL, " +
                "`at` INTEGER NOT NULL, `oldStartedAt` INTEGER NOT NULL, `oldEndedAt` INTEGER, `newStartedAt` INTEGER NOT NULL, " +
                "`newEndedAt` INTEGER, `reason` TEXT NOT NULL, FOREIGN KEY(`sessionId`) REFERENCES `work_sessions`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_session_corrections_sessionId` ON `session_corrections` (`sessionId`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `timer_presets` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                "`mode` TEXT NOT NULL, `minutes` INTEGER NOT NULL, `breakMinutes` INTEGER, `sortOrder` INTEGER NOT NULL DEFAULT 0)",
        )
    }
}

/** Workday corrections keep the original times and a reason; Today's commitments (Phase 10). */
val MIGRATION_27_28 = object : Migration(27, 28) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `workday_segments` ADD COLUMN `originalStartedAt` INTEGER")
        db.execSQL("ALTER TABLE `workday_segments` ADD COLUMN `originalEndedAt` INTEGER")
        db.execSQL("ALTER TABLE `workday_segments` ADD COLUMN `correctionReason` TEXT")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `commitments` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `date` TEXT NOT NULL, " +
                "`entryId` INTEGER NOT NULL, `committedAt` INTEGER NOT NULL, `removedAt` INTEGER, `removeNote` TEXT, " +
                "`position` INTEGER NOT NULL, FOREIGN KEY(`entryId`) REFERENCES `entries`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_commitments_date_entryId` ON `commitments` (`date`, `entryId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_commitments_entryId` ON `commitments` (`entryId`)")
    }
}

/** A reminder remembers how its time was chosen (Phase 11: custom reminder times). */
val MIGRATION_28_29 = object : Migration(28, 29) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `reminders` ADD COLUMN `dayOffset` INTEGER")
        db.execSQL("ALTER TABLE `reminders` ADD COLUMN `localTime` TEXT")
        db.execSQL("ALTER TABLE `reminders` ADD COLUMN `minutesBefore` INTEGER")
    }
}

/** Habits 2.0: measurable targets, timed habits, day amounts and notes. */
val MIGRATION_29_30 = object : Migration(29, 30) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `habits` ADD COLUMN `targetAmount` REAL")
        db.execSQL("ALTER TABLE `habits` ADD COLUMN `targetUnit` TEXT")
        db.execSQL("ALTER TABLE `habits` ADD COLUMN `targetAtMost` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `habits` ADD COLUMN `timerMinutes` INTEGER")
        db.execSQL("ALTER TABLE `habit_checks` ADD COLUMN `value` REAL")
        db.execSQL("ALTER TABLE `habit_checks` ADD COLUMN `note` TEXT")
        db.execSQL("ALTER TABLE `utility_timers` ADD COLUMN `habitId` INTEGER")
    }
}

/** Loans & dues (Phase 13): the loans table, and ledger rows linked to a loan. */
val MIGRATION_30_31 = object : Migration(30, 31) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `loans` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `person` TEXT NOT NULL, " +
                "`lent` INTEGER NOT NULL, `startedOn` TEXT NOT NULL, `dueOn` TEXT, `note` TEXT, `closedAt` INTEGER)",
        )
        db.execSQL("ALTER TABLE `money_records` ADD COLUMN `loanId` INTEGER")
    }
}

/** Category icons (Phase 13). */
val MIGRATION_31_32 = object : Migration(31, 32) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `categories` ADD COLUMN `icon` TEXT")
    }
}

/** Plan priority and task/project links on money rows (Phase 13). */
val MIGRATION_32_33 = object : Migration(32, 33) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `money_records` ADD COLUMN `priority` TEXT NOT NULL DEFAULT 'NORMAL'")
        db.execSQL("ALTER TABLE `money_records` ADD COLUMN `taskId` INTEGER")
        db.execSQL("ALTER TABLE `money_records` ADD COLUMN `projectId` INTEGER")
    }
}

/** Receipt attachments (Phase 13). */
val MIGRATION_33_34 = object : Migration(33, 34) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `attachments` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `recordId` INTEGER NOT NULL, " +
                "`fileName` TEXT NOT NULL, `mime` TEXT NOT NULL, `sizeBytes` INTEGER NOT NULL, `sha256` TEXT NOT NULL, " +
                "`addedAt` INTEGER NOT NULL, `displayName` TEXT, FOREIGN KEY(`recordId`) REFERENCES `money_records`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_attachments_recordId` ON `attachments` (`recordId`)")
    }
}

/** Links: when saved (null for older ones — unknown, not guessed) and archive. */
val MIGRATION_34_35 = object : Migration(34, 35) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `bookmarks` ADD COLUMN `addedAt` INTEGER")
        db.execSQL("ALTER TABLE `bookmarks` ADD COLUMN `archivedAt` INTEGER")
    }
}

/** A task's additional blocks of reserved time (Phase 14). Starts empty: every existing block
 * is a task's first one and stays on the entry. */
val MIGRATION_35_36 = object : Migration(35, 36) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `schedule_blocks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `entryId` INTEGER NOT NULL, " +
                "`startAt` INTEGER NOT NULL, `endAt` INTEGER NOT NULL, `isFixed` INTEGER NOT NULL, FOREIGN KEY(`entryId`) REFERENCES " +
                "`entries`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_schedule_blocks_entryId` ON `schedule_blocks` (`entryId`)")
    }
}

/** Indexes for 10k-record performance (Phase 16). No data changes. */
val MIGRATION_36_37 = object : Migration(36, 37) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_entries_seriesId` ON `entries` (`seriesId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_money_records_status_settledAt` ON `money_records` (`status`, `settledAt`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_money_records_seriesId` ON `money_records` (`seriesId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_money_records_loanId` ON `money_records` (`loanId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_money_records_refundOfId` ON `money_records` (`refundOfId`)")
    }
}

/** The bell's notification list. Starts empty — nothing sent before it existed is invented. */
val MIGRATION_37_38 = object : Migration(37, 38) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `inbox_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `kind` TEXT NOT NULL, " +
                "`title` TEXT NOT NULL, `body` TEXT NOT NULL, `route` TEXT, `createdAt` INTEGER NOT NULL, `readAt` INTEGER)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_inbox_items_createdAt` ON `inbox_items` (`createdAt`)")
    }
}

val ALL_MIGRATIONS = arrayOf(
    MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20,
    MIGRATION_20_21, MIGRATION_21_22, MIGRATION_22_23, MIGRATION_23_24, MIGRATION_24_25, MIGRATION_25_26, MIGRATION_26_27, MIGRATION_27_28, MIGRATION_28_29, MIGRATION_29_30, MIGRATION_30_31, MIGRATION_31_32, MIGRATION_32_33, MIGRATION_33_34, MIGRATION_34_35, MIGRATION_35_36, MIGRATION_36_37, MIGRATION_37_38,
)
