package com.callcontrol.domain

import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * One time window of the schedule, `[start, end)` (start inclusive, end exclusive), on the
 * given [days].
 *
 * If `start > end` the window crosses midnight (e.g. 22:00 -> 06:00). The part after midnight
 * belongs to the day the window *started* on: a Friday 22:00 -> 06:00 window is still active on
 * Saturday at 03:00. An `end` of 00:00 means "until midnight" (22:00 -> 00:00 covers the evening).
 * `start == end` is an empty window and never matches.
 */
data class ScheduleEntry(
    val days: Set<DayOfWeek>,
    val start: LocalTime,
    val end: LocalTime,
    val mode: Mode,
    val enabled: Boolean = true,
) {
    fun matches(now: LocalDateTime): Boolean {
        if (!enabled || start == end) return false
        val time = now.toLocalTime()
        val today = now.dayOfWeek
        return if (start < end) {
            today in days && time >= start && time < end
        } else {
            val yesterday = today.minus(1)
            (today in days && time >= start) || (yesterday in days && time < end)
        }
    }
}

/**
 * Automatic changes of the *global* mode. The schedule only replaces the global mode; it never
 * overrides per-number, unregistered or hidden rules (those have higher priority).
 *
 * @param entries evaluated in list order; the first matching entry wins when windows overlap.
 * @param defaultMode mode for times not covered by any entry ("the rest of the day").
 *   `null` means: outside the windows, use the manually chosen global mode.
 */
data class Schedule(
    val enabled: Boolean,
    val entries: List<ScheduleEntry>,
    val defaultMode: Mode? = null,
) {
    companion object {
        val DISABLED = Schedule(enabled = false, entries = emptyList(), defaultMode = null)
    }
}

enum class ModeSource { MANUAL, SCHEDULE_ENTRY, SCHEDULE_DEFAULT }

data class ResolvedMode(val mode: Mode, val source: ModeSource)

/**
 * Computes the effective global mode at a given moment.
 *
 * Deliberately stateless: the mode is derived from the stored schedule at the moment a call
 * arrives. No alarm or background service is needed, so Doze / battery optimisation cannot make the
 * schedule "miss" a switch, and nothing has to be re-armed after a reboot.
 *
 * [now] is local wall-clock time (the caller passes the device's time zone), so daylight-saving
 * changes follow the clock the user sees.
 */
object ScheduleResolver {
    fun resolve(schedule: Schedule, now: LocalDateTime, manualMode: Mode): ResolvedMode {
        if (!schedule.enabled) return ResolvedMode(manualMode, ModeSource.MANUAL)

        val hit = schedule.entries.firstOrNull { it.matches(now) }
        if (hit != null) return ResolvedMode(hit.mode, ModeSource.SCHEDULE_ENTRY)

        val fallback = schedule.defaultMode
        return if (fallback != null) ResolvedMode(fallback, ModeSource.SCHEDULE_DEFAULT)
        else ResolvedMode(manualMode, ModeSource.MANUAL)
    }
}
