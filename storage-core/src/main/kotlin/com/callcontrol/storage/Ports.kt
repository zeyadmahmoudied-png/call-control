package com.callcontrol.storage

import com.callcontrol.domain.ScheduleEntry
import kotlinx.coroutines.flow.Flow

/*
 * Storage ports. The Repository only talks to these interfaces; Room and DataStore implement them
 * in the Android module. Contract notes that every implementation must respect:
 *
 *  - Operations may throw on I/O failure. The Repository converts exceptions into RepoResult.Failure.
 *  - A failed write must leave previously stored data unchanged (single atomic transaction / edit).
 *  - Nothing here may log phone numbers or other personal data.
 */

enum class WriteOutcome { OK, NOT_FOUND, DUPLICATE }

interface NumberRuleStore {
    /** Emits the full list (oldest first) now and after every change. */
    fun observeAll(): Flow<List<StoredNumberRule>>

    suspend fun getAll(): List<StoredNumberRule>

    suspend fun get(id: Long): StoredNumberRule?

    /** Inserts [rule] (its `id` is ignored) and returns the new id, or null if the number already exists. */
    suspend fun insert(rule: StoredNumberRule): Long?

    /** Replaces the rule with `rule.id`. [WriteOutcome.DUPLICATE] if its number now clashes with another rule. */
    suspend fun update(rule: StoredNumberRule): WriteOutcome

    /** Deletes only this rule. Returns false if it did not exist. */
    suspend fun delete(id: Long): Boolean
}

interface ScheduleEntryStore {
    /** Emits the entries in evaluation order now and after every change. */
    fun observeAll(): Flow<List<StoredScheduleEntry>>

    suspend fun getAll(): List<StoredScheduleEntry>

    /** Adds [entry] at the end of the order and returns its id. */
    suspend fun append(entry: ScheduleEntry): Long

    /** Returns false if no entry has this id. Keeps the entry's position. */
    suspend fun update(id: Long, entry: ScheduleEntry): Boolean

    suspend fun delete(id: Long): Boolean

    /**
     * Atomically sets the order. Must return false and change nothing unless [orderedIds] contains
     * exactly the ids that currently exist, each once.
     */
    suspend fun reorder(orderedIds: List<Long>): Boolean
}

interface SettingsStore {
    fun observe(): Flow<AppSettings>

    suspend fun read(): AppSettings

    /**
     * Atomically applies [transform] to the current settings and stores the result.
     * If [transform] throws, nothing is stored. Returns the stored settings.
     */
    suspend fun update(transform: (AppSettings) -> AppSettings): AppSettings
}
