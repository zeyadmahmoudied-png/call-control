package com.callcontrol.storage.android.room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * Room entities. Implementation details of :storage-android; the rest of the app only sees the
 * ports from :storage-core.
 *
 * Modes are stored as their enum NAME (text), not their ordinal, so reordering the enum can never
 * silently change what a stored rule means.
 */

@Entity(
    tableName = "number_rules",
    indices = [Index(value = ["number_key"], unique = true)],
)
data class NumberRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "number_key") val numberKey: String,
    @ColumnInfo(name = "mode") val mode: String,
    @ColumnInfo(name = "enabled") val enabled: Boolean,
    @ColumnInfo(name = "name") val name: String?,
    @ColumnInfo(name = "notes") val notes: String?,
    @ColumnInfo(name = "created_at") val createdAtMillis: Long,
    @ColumnInfo(name = "updated_at") val updatedAtMillis: Long,
)

@Entity(tableName = "schedule_entries")
data class ScheduleEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Evaluation order (first match wins). */
    @ColumnInfo(name = "position") val position: Int,
    /** Bit (DayOfWeek.value - 1) is set for each selected day: Monday = bit 0 ... Sunday = bit 6. */
    @ColumnInfo(name = "days_mask") val daysMask: Int,
    @ColumnInfo(name = "start_second") val startSecondOfDay: Int,
    @ColumnInfo(name = "end_second") val endSecondOfDay: Int,
    @ColumnInfo(name = "mode") val mode: String,
    @ColumnInfo(name = "enabled") val enabled: Boolean,
)

@Entity(tableName = "call_log")
data class CallLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "timestamp") val timestampMillis: Long,
    /** Null for hidden callers. */
    @ColumnInfo(name = "number_key") val numberKey: String?,
    @ColumnInfo(name = "hidden") val hidden: Boolean,
    @ColumnInfo(name = "contact_status") val contactStatus: String,
    @ColumnInfo(name = "mode") val mode: String,
    @ColumnInfo(name = "reason") val reason: String,
    /** Exception class name only; never a message. */
    @ColumnInfo(name = "error_type") val errorType: String?,
)
