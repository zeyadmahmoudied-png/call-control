package com.callcontrol.storage

import com.callcontrol.domain.LevelRule
import com.callcontrol.domain.Mode
import com.callcontrol.domain.ScheduleEntry
import com.callcontrol.domain.SpecialRule

/**
 * A per-number rule as stored. The Phase 1 domain type [SpecialRule] has no id, name or notes, so
 * storage wraps it instead of changing the domain.
 *
 * @param id surrogate key (0 for a rule that has not been stored yet). Using an id (not the number) as
 *   the key lets the user edit the number of an existing rule.
 * @param numberKey normalized E.164 number; unique across all rules.
 */
data class StoredNumberRule(
    val id: Long,
    val numberKey: String,
    val mode: Mode,
    val enabled: Boolean,
    val name: String?,
    val notes: String?,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
) {
    fun toDomain(): SpecialRule = SpecialRule(numberKey = numberKey, mode = mode, enabled = enabled)
}

/** A schedule window with its storage id. Position in the list is the evaluation order (first match wins). */
data class StoredScheduleEntry(
    val id: Long,
    val entry: ScheduleEntry,
)

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * All simple settings (kept in Preferences DataStore on Android, never in Room).
 * Defaults describe a fresh install: control on, everything available, no special levels, no schedule.
 */
data class AppSettings(
    val controlEnabled: Boolean = true,
    val globalMode: Mode = Mode.AVAILABLE,
    val unregistered: LevelRule = LevelRule(enabled = false, mode = Mode.AVAILABLE),
    val hidden: LevelRule = LevelRule(enabled = false, mode = Mode.AVAILABLE),
    val scheduleEnabled: Boolean = false,
    /** Mode for times outside every schedule window; null = keep the manual [globalMode]. */
    val scheduleDefaultMode: Mode? = null,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val onboardingCompleted: Boolean = false,
    /** Keep a local log of handled calls (can be cleared at any time). */
    val logEnabled: Boolean = true,
)
