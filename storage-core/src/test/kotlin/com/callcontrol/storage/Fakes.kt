package com.callcontrol.storage

import com.callcontrol.domain.ScheduleEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/*
 * In-memory implementations of the storage ports, used to test the Repository on the JVM.
 *
 * IMPORTANT: these are test doubles. They prove the Repository's own logic (validation, normalization,
 * error handling, no state of its own). They do NOT prove that Room or DataStore persist data; that is
 * covered by the instrumented tests in :storage-android, which must be run on an emulator/device.
 *
 * Fault injection: set [failure] and every operation (reads, writes, flows) throws it BEFORE touching
 * state, like a database transaction that rolls back.
 */

class InMemoryNumberRuleStore : NumberRuleStore {
    private val state = MutableStateFlow<List<StoredNumberRule>>(emptyList())
    private var nextId = 1L

    var failure: Exception? = null
    var writeCount = 0
        private set

    private fun check() {
        failure?.let { throw it }
    }

    override fun observeAll(): Flow<List<StoredNumberRule>> = flow {
        check()
        emitAll(state)
    }

    override suspend fun getAll(): List<StoredNumberRule> {
        check()
        return state.value
    }

    override suspend fun get(id: Long): StoredNumberRule? {
        check()
        return state.value.firstOrNull { it.id == id }
    }

    override suspend fun insert(rule: StoredNumberRule): Long? {
        check()
        if (state.value.any { it.numberKey == rule.numberKey }) return null
        val id = nextId++
        state.value = state.value + rule.copy(id = id)
        writeCount++
        return id
    }

    override suspend fun update(rule: StoredNumberRule): WriteOutcome {
        check()
        val current = state.value
        if (current.none { it.id == rule.id }) return WriteOutcome.NOT_FOUND
        if (current.any { it.id != rule.id && it.numberKey == rule.numberKey }) return WriteOutcome.DUPLICATE
        state.value = current.map { if (it.id == rule.id) rule else it }
        writeCount++
        return WriteOutcome.OK
    }

    override suspend fun delete(id: Long): Boolean {
        check()
        val current = state.value
        if (current.none { it.id == id }) return false
        state.value = current.filterNot { it.id == id }
        writeCount++
        return true
    }
}

class InMemoryScheduleEntryStore : ScheduleEntryStore {
    private val state = MutableStateFlow<List<StoredScheduleEntry>>(emptyList())
    private var nextId = 1L

    var failure: Exception? = null
    var writeCount = 0
        private set

    private fun check() {
        failure?.let { throw it }
    }

    override fun observeAll(): Flow<List<StoredScheduleEntry>> = flow {
        check()
        emitAll(state)
    }

    override suspend fun getAll(): List<StoredScheduleEntry> {
        check()
        return state.value
    }

    override suspend fun append(entry: ScheduleEntry): Long {
        check()
        val id = nextId++
        state.value = state.value + StoredScheduleEntry(id, entry)
        writeCount++
        return id
    }

    override suspend fun update(id: Long, entry: ScheduleEntry): Boolean {
        check()
        val current = state.value
        if (current.none { it.id == id }) return false
        state.value = current.map { if (it.id == id) StoredScheduleEntry(id, entry) else it }
        writeCount++
        return true
    }

    override suspend fun delete(id: Long): Boolean {
        check()
        val current = state.value
        if (current.none { it.id == id }) return false
        state.value = current.filterNot { it.id == id }
        writeCount++
        return true
    }

    override suspend fun reorder(orderedIds: List<Long>): Boolean {
        check()
        val current = state.value
        val byId = current.associateBy { it.id }
        if (orderedIds.size != current.size || orderedIds.toSet() != byId.keys) return false
        state.value = orderedIds.map { byId.getValue(it) }
        writeCount++
        return true
    }
}

class InMemorySettingsStore : SettingsStore {
    private val state = MutableStateFlow(AppSettings())

    var failure: Exception? = null
    var writeCount = 0
        private set

    private fun check() {
        failure?.let { throw it }
    }

    override fun observe(): Flow<AppSettings> = flow {
        check()
        emitAll(state)
    }

    override suspend fun read(): AppSettings {
        check()
        return state.value
    }

    override suspend fun update(transform: (AppSettings) -> AppSettings): AppSettings {
        check()
        val updated = transform(state.value) // may throw: nothing is committed in that case
        state.value = updated
        writeCount++
        return updated
    }
}

class InMemoryCallLogStore : CallLogStore {
    private val state = MutableStateFlow<List<CallLogEntry>>(emptyList()) // newest first
    private var nextId = 1L

    var failure: Exception? = null

    private fun check() {
        failure?.let { throw it }
    }

    override fun observeRecent(limit: Int): Flow<List<CallLogEntry>> = flow {
        check()
        emitAll(state.map { it.take(limit) })
    }

    override suspend fun getRecent(limit: Int): List<CallLogEntry> {
        check()
        return state.value.take(limit)
    }

    override suspend fun append(entry: CallLogEntry, keepLast: Int) {
        check()
        state.value = (listOf(entry.copy(id = nextId++)) + state.value).take(keepLast)
    }

    override suspend fun clear() {
        check()
        state.value = emptyList()
    }
}

/** The stores that make up "the database + DataStore". A new repository over the same Fixture = an app restart. */
class Fixture {
    val rules = InMemoryNumberRuleStore()
    val schedule = InMemoryScheduleEntryStore()
    val settings = InMemorySettingsStore()
    val log = InMemoryCallLogStore()
    var nowMillis = 1_000L

    fun repository() = CallControlRepository(rules, schedule, settings, log) { nowMillis }

    fun failEverything(e: Exception?) {
        rules.failure = e
        schedule.failure = e
        settings.failure = e
        log.failure = e
    }
}
