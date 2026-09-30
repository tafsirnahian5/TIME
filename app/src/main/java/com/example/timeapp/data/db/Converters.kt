package com.example.timeapp.data.db

import androidx.room.TypeConverter
import com.example.timeapp.data.CollectionKind
import com.example.timeapp.data.Direction
import com.example.timeapp.data.EntryKind
import com.example.timeapp.data.EntryStatus
import com.example.timeapp.data.PlanStatus
import com.example.timeapp.data.ReminderOwnerKind
import com.example.timeapp.data.ReviewPeriod
import com.example.timeapp.data.WalletKind
import com.example.timeapp.data.WorkdaySegmentKind
import java.time.Instant
import java.time.LocalDate

class Converters {
    @TypeConverter fun instantToEpochMilli(value: Instant?): Long? = value?.toEpochMilli()
    @TypeConverter fun epochMilliToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    @TypeConverter fun localDateToIso(value: LocalDate?): String? = value?.toString()
    @TypeConverter fun isoToLocalDate(value: String?): LocalDate? = value?.let(LocalDate::parse)

    @TypeConverter fun entryKindToString(value: EntryKind): String = value.name
    @TypeConverter fun stringToEntryKind(value: String): EntryKind = EntryKind.valueOf(value)

    @TypeConverter fun categoryKindToString(value: com.example.timeapp.data.CategoryKind): String = value.name
    @TypeConverter fun stringToCategoryKind(value: String): com.example.timeapp.data.CategoryKind = com.example.timeapp.data.CategoryKind.valueOf(value)

    @TypeConverter fun priorityToString(value: com.example.timeapp.data.Priority): String = value.name
    @TypeConverter fun stringToPriority(value: String): com.example.timeapp.data.Priority = com.example.timeapp.data.Priority.valueOf(value)

    @TypeConverter fun entryStatusToString(value: EntryStatus): String = value.name
    @TypeConverter fun stringToEntryStatus(value: String): EntryStatus = EntryStatus.valueOf(value)

    @TypeConverter fun walletKindToString(value: WalletKind): String = value.name
    @TypeConverter fun stringToWalletKind(value: String): WalletKind = WalletKind.valueOf(value)

    @TypeConverter fun directionToString(value: Direction): String = value.name
    @TypeConverter fun stringToDirection(value: String): Direction = Direction.valueOf(value)

    @TypeConverter fun planStatusToString(value: PlanStatus?): String? = value?.name
    @TypeConverter fun stringToPlanStatus(value: String?): PlanStatus? = value?.let(PlanStatus::valueOf)

    @TypeConverter fun collectionKindToString(value: CollectionKind): String = value.name
    @TypeConverter fun stringToCollectionKind(value: String): CollectionKind = CollectionKind.valueOf(value)

    @TypeConverter fun workdaySegmentKindToString(value: WorkdaySegmentKind): String = value.name
    @TypeConverter fun stringToWorkdaySegmentKind(value: String): WorkdaySegmentKind = WorkdaySegmentKind.valueOf(value)

    @TypeConverter fun reviewPeriodToString(value: ReviewPeriod): String = value.name
    @TypeConverter fun stringToReviewPeriod(value: String): ReviewPeriod = ReviewPeriod.valueOf(value)

    @TypeConverter fun recurrenceToString(value: com.example.timeapp.data.Recurrence?): String? = value?.serialize()
    @TypeConverter fun stringToRecurrence(value: String?): com.example.timeapp.data.Recurrence? =
        com.example.timeapp.data.Recurrence.parse(value)

    @TypeConverter fun habitMarkToString(value: com.example.timeapp.data.HabitMark): String = value.name
    @TypeConverter fun stringToHabitMark(value: String): com.example.timeapp.data.HabitMark = com.example.timeapp.data.HabitMark.valueOf(value)

    @TypeConverter fun sessionModeToString(value: com.example.timeapp.data.SessionMode): String = value.name
    @TypeConverter fun stringToSessionMode(value: String): com.example.timeapp.data.SessionMode = com.example.timeapp.data.SessionMode.valueOf(value)

    @TypeConverter fun focusModeToString(value: com.example.timeapp.data.FocusMode): String = value.name
    @TypeConverter fun stringToFocusMode(value: String): com.example.timeapp.data.FocusMode = com.example.timeapp.data.FocusMode.valueOf(value)

    @TypeConverter fun focusPhaseToString(value: com.example.timeapp.data.FocusPhase): String = value.name
    @TypeConverter fun stringToFocusPhase(value: String): com.example.timeapp.data.FocusPhase = com.example.timeapp.data.FocusPhase.valueOf(value)

    @TypeConverter fun reminderOwnerKindToString(value: ReminderOwnerKind): String = value.name
    @TypeConverter fun stringToReminderOwnerKind(value: String): ReminderOwnerKind = ReminderOwnerKind.valueOf(value)
}
