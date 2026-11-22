package com.callcontrol.storage

import com.callcontrol.domain.Mode
import com.callcontrol.domain.NormalizeResult
import com.callcontrol.domain.PhoneNormalizer
import com.callcontrol.domain.RuleConfig
import com.callcontrol.domain.Schedule
import com.callcontrol.domain.ScheduleEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * The single entry point between the app and storage. It depends only on the storage ports and the
 * Phase 1 domain, never on Android, Room or DataStore.
 *
 * Rules it enforces:
 *  - Numbers are normalized with [PhoneNormalizer] before they are stored, so the same number cannot
 *    be saved twice under different spellings.
 *  - Input is validated BEFORE any write; a rejected input touches nothing.
 *  - Every storage exception becomes [RepoResult.Failure] with only the exception class name
 *    (no message, no number), so personal data cannot leak into logs through error handling.
 *  - Coroutine cancellation is never swallowed.
 *  - Deleting a rule deletes that rule only; this layer has no access to contacts at all.
 */
class CallControlRepository(
    private val rules: NumberRuleStore,
    private val schedule: ScheduleEntryStore,
    private val settings: SettingsStore,
    private val log: CallLogStore = NoCallLogStore,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {

    // ---------------------------------------------------------------- observing

    /** After a storage failure the flow emits one [RepoResult.Failure] and completes; re-collect to retry. */
    fun observeNumberRules(): Flow<RepoResult<List<StoredNumberRule>>> = rules.observeAll().asResults()

    fun observeScheduleEntries(): Flow<RepoResult<List<StoredScheduleEntry>>> = schedule.observeAll().asResults()

    fun observeSettings(): Flow<RepoResult<AppSettings>> = settings.observe().asResults()

    /** The complete engine input, re-emitted whenever rules, schedule windows or settings change. */
    fun observeRuleConfig(): Flow<RepoResult<RuleConfig>> =
        combine(rules.observeAll(), schedule.observeAll(), settings.observe()) { r, s, st ->
            buildRuleConfig(r, s, st)
        }.asResults()

    /**
     * One-shot read of the engine input (what the call screening service will use).
     * All-or-nothing: if any source fails the result is a Failure, and the caller must fail open
     * (allow the call) rather than guess with partial rules.
     */
    suspend fun loadRuleConfig(): RepoResult<RuleConfig> = guarded {
        RepoResult.Success(buildRuleConfig(rules.getAll(), schedule.getAll(), settings.read()))
    }

    fun observeCallLog(limit: Int = 100): Flow<RepoResult<List<CallLogEntry>>> = log.observeRecent(limit).asResults()

    suspend fun loadSettings(): RepoResult<AppSettings> = guarded { RepoResult.Success(settings.read()) }

    // ---------------------------------------------------------------- number rules

    suspend fun addNumberRule(
        rawNumber: String,
        mode: Mode,
        name: String? = null,
        notes: String? = null,
        enabled: Boolean = true,
    ): RepoResult<StoredNumberRule> = guarded {
        val key = when (val n = PhoneNormalizer.normalize(rawNumber)) {
            is NormalizeResult.Valid -> n.e164
            is NormalizeResult.Invalid -> return@guarded RepoResult.Failure(RepoError.InvalidNumber(n.reason))
        }
        val now = clock()
        val draft = StoredNumberRule(0, key, mode, enabled, name.cleaned(), notes.cleaned(), now, now)
        val id = rules.insert(draft) ?: return@guarded RepoResult.Failure(RepoError.DuplicateNumber)
        RepoResult.Success(draft.copy(id = id))
    }

    /** Edits every field of an existing rule, including its number. */
    suspend fun updateNumberRule(
        id: Long,
        rawNumber: String,
        mode: Mode,
        enabled: Boolean,
        name: String?,
        notes: String?,
    ): RepoResult<StoredNumberRule> = guarded {
        val key = when (val n = PhoneNormalizer.normalize(rawNumber)) {
            is NormalizeResult.Valid -> n.e164
            is NormalizeResult.Invalid -> return@guarded RepoResult.Failure(RepoError.InvalidNumber(n.reason))
        }
        val existing = rules.get(id) ?: return@guarded RepoResult.Failure(RepoError.NotFound)
        val updated = existing.copy(
            numberKey = key,
            mode = mode,
            enabled = enabled,
            name = name.cleaned(),
            notes = notes.cleaned(),
            updatedAtMillis = clock(),
        )
        when (rules.update(updated)) {
            WriteOutcome.OK -> RepoResult.Success(updated)
            WriteOutcome.NOT_FOUND -> RepoResult.Failure(RepoError.NotFound)
            WriteOutcome.DUPLICATE -> RepoResult.Failure(RepoError.DuplicateNumber)
        }
    }

    /** Disables or enables a rule without deleting it. */
    suspend fun setNumberRuleEnabled(id: Long, enabled: Boolean): RepoResult<StoredNumberRule> = guarded {
        val existing = rules.get(id) ?: return@guarded RepoResult.Failure(RepoError.NotFound)
        val updated = existing.copy(enabled = enabled, updatedAtMillis = clock())
        when (rules.update(updated)) {
            WriteOutcome.OK -> RepoResult.Success(updated)
            WriteOutcome.NOT_FOUND -> RepoResult.Failure(RepoError.NotFound)
            WriteOutcome.DUPLICATE -> RepoResult.Failure(RepoError.DuplicateNumber)
        }
    }

    suspend fun deleteNumberRule(id: Long): RepoResult<Unit> = guarded {
        if (rules.delete(id)) RepoResult.Success(Unit) else RepoResult.Failure(RepoError.NotFound)
    }

    // ---------------------------------------------------------------- schedule windows

    suspend fun addScheduleEntry(entry: ScheduleEntry): RepoResult<StoredScheduleEntry> = guarded {
        validate(entry)?.let { return@guarded RepoResult.Failure(RepoError.InvalidSchedule(it)) }
        RepoResult.Success(StoredScheduleEntry(schedule.append(entry), entry))
    }

    suspend fun updateScheduleEntry(id: Long, entry: ScheduleEntry): RepoResult<StoredScheduleEntry> = guarded {
        validate(entry)?.let { return@guarded RepoResult.Failure(RepoError.InvalidSchedule(it)) }
        if (schedule.update(id, entry)) RepoResult.Success(StoredScheduleEntry(id, entry))
        else RepoResult.Failure(RepoError.NotFound)
    }

    suspend fun deleteScheduleEntry(id: Long): RepoResult<Unit> = guarded {
        if (schedule.delete(id)) RepoResult.Success(Unit) else RepoResult.Failure(RepoError.NotFound)
    }

    /** [orderedIds] must be exactly the existing ids, each once; otherwise nothing changes. */
    suspend fun reorderScheduleEntries(orderedIds: List<Long>): RepoResult<Unit> = guarded {
        if (orderedIds.toSet().size != orderedIds.size) return@guarded RepoResult.Failure(RepoError.InvalidOrder)
        if (schedule.reorder(orderedIds)) RepoResult.Success(Unit) else RepoResult.Failure(RepoError.InvalidOrder)
    }

    // ---------------------------------------------------------------- settings

    suspend fun updateSettings(transform: (AppSettings) -> AppSettings): RepoResult<AppSettings> = guarded {
        RepoResult.Success(settings.update(transform))
    }

    // ---------------------------------------------------------------- call log

    /** Appends [entry] unless the user switched logging off (then Success(false)). Never throws. */
    suspend fun recordCall(entry: CallLogEntry): RepoResult<Boolean> = guarded {
        if (!settings.read().logEnabled) {
            RepoResult.Success(false)
        } else {
            log.append(entry.copy(id = 0), MAX_LOG_ENTRIES)
            RepoResult.Success(true)
        }
    }

    suspend fun clearCallLog(): RepoResult<Unit> = guarded {
        log.clear()
        RepoResult.Success(Unit)
    }

    // ---------------------------------------------------------------- helpers

    private fun validate(entry: ScheduleEntry): ScheduleProblem? = when {
        entry.days.isEmpty() -> ScheduleProblem.NO_DAYS
        entry.start == entry.end -> ScheduleProblem.EMPTY_INTERVAL
        entry.start.nano != 0 || entry.end.nano != 0 -> ScheduleProblem.UNSUPPORTED_TIME_PRECISION
        else -> null
    }

    private fun String?.cleaned(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

    private suspend inline fun <T> guarded(block: () -> RepoResult<T>): RepoResult<T> =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            RepoResult.Failure(RepoError.Storage(e::class.java.simpleName))
        }

    private fun <T> Flow<T>.asResults(): Flow<RepoResult<T>> =
        map<T, RepoResult<T>> { RepoResult.Success(it) }
            .catch { e -> emit(RepoResult.Failure(RepoError.Storage(e::class.java.simpleName))) }
}

/** Pure assembly of the Phase 1 engine input from stored data. Exposed for testing. */
fun buildRuleConfig(
    rules: List<StoredNumberRule>,
    entries: List<StoredScheduleEntry>,
    settings: AppSettings,
): RuleConfig = RuleConfig(
    controlEnabled = settings.controlEnabled,
    globalMode = settings.globalMode,
    unregistered = settings.unregistered,
    hidden = settings.hidden,
    schedule = Schedule(
        enabled = settings.scheduleEnabled,
        entries = entries.map { it.entry },
        defaultMode = settings.scheduleDefaultMode,
    ),
    numberRules = rules.associate { it.numberKey to it.toDomain() },
)
