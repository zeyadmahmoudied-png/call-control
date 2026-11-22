package com.callcontrol.storage.android.room

import com.callcontrol.domain.ContactStatus
import com.callcontrol.domain.DecisionReason
import com.callcontrol.domain.Mode
import com.callcontrol.domain.ScheduleEntry
import com.callcontrol.storage.CallLogEntry
import com.callcontrol.storage.StoredNumberRule
import com.callcontrol.storage.StoredScheduleEntry
import java.time.DayOfWeek
import java.time.LocalTime

/** Parses a stored mode name. Unknown names (e.g. written by a newer app version) give null. */
internal fun parseMode(name: String): Mode? = Mode.values().firstOrNull { it.name == name }

internal fun daysToMask(days: Set<DayOfWeek>): Int =
    days.fold(0) { mask, day -> mask or (1 shl (day.value - 1)) }

internal fun maskToDays(mask: Int): Set<DayOfWeek> =
    DayOfWeek.values().filter { mask and (1 shl (it.value - 1)) != 0 }.toSet()

/**
 * Rows this version cannot understand (unknown mode) map to null and are skipped by the stores.
 * They are never deleted or rewritten, so a newer app version can still read them later.
 */
internal fun NumberRuleEntity.toStored(): StoredNumberRule? {
    val parsed = parseMode(mode) ?: return null
    return StoredNumberRule(id, numberKey, parsed, enabled, name, notes, createdAtMillis, updatedAtMillis)
}

internal fun StoredNumberRule.toEntity(): NumberRuleEntity =
    NumberRuleEntity(id, numberKey, mode.name, enabled, name, notes, createdAtMillis, updatedAtMillis)

internal fun ScheduleEntryEntity.toStored(): StoredScheduleEntry? {
    val parsed = parseMode(mode) ?: return null
    if (startSecondOfDay !in 0 until SECONDS_PER_DAY || endSecondOfDay !in 0 until SECONDS_PER_DAY) return null
    val entry = ScheduleEntry(
        days = maskToDays(daysMask),
        start = LocalTime.ofSecondOfDay(startSecondOfDay.toLong()),
        end = LocalTime.ofSecondOfDay(endSecondOfDay.toLong()),
        mode = parsed,
        enabled = enabled,
    )
    return StoredScheduleEntry(id, entry)
}

/** Position 0 is a placeholder: [ScheduleEntryDao.append] assigns the real position inside its transaction. */
internal fun ScheduleEntry.toNewEntity(): ScheduleEntryEntity =
    ScheduleEntryEntity(
        id = 0,
        position = 0,
        daysMask = daysToMask(days),
        startSecondOfDay = start.toSecondOfDay(),
        endSecondOfDay = end.toSecondOfDay(),
        mode = mode.name,
        enabled = enabled,
    )

internal fun CallLogEntity.toStored(): CallLogEntry? {
    val parsedMode = parseMode(mode) ?: return null
    val parsedReason = DecisionReason.values().firstOrNull { it.name == reason } ?: return null
    val parsedContact = ContactStatus.values().firstOrNull { it.name == contactStatus } ?: return null
    return CallLogEntry(id, timestampMillis, numberKey, hidden, parsedContact, parsedMode, parsedReason, errorType)
}

internal fun CallLogEntry.toEntity(): CallLogEntity =
    CallLogEntity(0, timestampMillis, numberKey, hidden, contactStatus.name, mode.name, reason.name, errorType)

private const val SECONDS_PER_DAY = 24 * 60 * 60
