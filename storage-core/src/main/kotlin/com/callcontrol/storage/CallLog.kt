package com.callcontrol.storage

import com.callcontrol.domain.ContactStatus
import com.callcontrol.domain.DecisionReason
import com.callcontrol.domain.Mode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Newest entries are kept; older ones are deleted automatically. */
const val MAX_LOG_ENTRIES = 300

/**
 * One handled call. Stored only on the device.
 *
 * Privacy: [numberKey] is null for hidden callers, and [errorType] is only an exception class name,
 * never a message. The user can switch logging off (AppSettings.logEnabled) and clear it at any time.
 */
data class CallLogEntry(
    val id: Long,
    val timestampMillis: Long,
    val numberKey: String?,
    val hidden: Boolean,
    val contactStatus: ContactStatus,
    val mode: Mode,
    val reason: DecisionReason,
    val errorType: String?,
)

interface CallLogStore {
    /** Newest first, at most [limit] entries, re-emitted after every change. */
    fun observeRecent(limit: Int): Flow<List<CallLogEntry>>

    suspend fun getRecent(limit: Int): List<CallLogEntry>

    /** Inserts [entry] (its id is ignored) and, in the same transaction, deletes everything older than the newest [keepLast]. */
    suspend fun append(entry: CallLogEntry, keepLast: Int)

    suspend fun clear()
}

/** Used when no log store is supplied: nothing is recorded. */
object NoCallLogStore : CallLogStore {
    override fun observeRecent(limit: Int): Flow<List<CallLogEntry>> = flowOf(emptyList())
    override suspend fun getRecent(limit: Int): List<CallLogEntry> = emptyList()
    override suspend fun append(entry: CallLogEntry, keepLast: Int) = Unit
    override suspend fun clear() = Unit
}
