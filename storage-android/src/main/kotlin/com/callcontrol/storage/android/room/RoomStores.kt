package com.callcontrol.storage.android.room

import android.database.sqlite.SQLiteConstraintException
import com.callcontrol.domain.ScheduleEntry
import com.callcontrol.storage.CallLogEntry
import com.callcontrol.storage.CallLogStore
import com.callcontrol.storage.NumberRuleStore
import com.callcontrol.storage.ScheduleEntryStore
import com.callcontrol.storage.StoredNumberRule
import com.callcontrol.storage.StoredScheduleEntry
import com.callcontrol.storage.WriteOutcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Room implementation of [NumberRuleStore]. Exceptions are NOT caught here: the Repository turns them
 * into RepoResult.Failure. The one exception is the unique-number clash, which is a normal outcome.
 */
class RoomNumberRuleStore(private val dao: NumberRuleDao) : NumberRuleStore {

    override fun observeAll(): Flow<List<StoredNumberRule>> =
        dao.observeAll().map { rows -> rows.mapNotNull { it.toStored() } }

    override suspend fun getAll(): List<StoredNumberRule> = dao.getAll().mapNotNull { it.toStored() }

    override suspend fun get(id: Long): StoredNumberRule? = dao.getById(id)?.toStored()

    override suspend fun insert(rule: StoredNumberRule): Long? {
        val rowId = dao.insert(rule.copy(id = 0).toEntity())
        return if (rowId == -1L) null else rowId
    }

    override suspend fun update(rule: StoredNumberRule): WriteOutcome =
        try {
            if (dao.update(rule.toEntity()) == 0) WriteOutcome.NOT_FOUND else WriteOutcome.OK
        } catch (e: SQLiteConstraintException) {
            // The only constraint that can fire on update is the unique number_key index.
            // ABORT means the row was left exactly as it was.
            WriteOutcome.DUPLICATE
        }

    override suspend fun delete(id: Long): Boolean = dao.deleteById(id) > 0
}

class RoomScheduleEntryStore(private val dao: ScheduleEntryDao) : ScheduleEntryStore {

    override fun observeAll(): Flow<List<StoredScheduleEntry>> =
        dao.observeAll().map { rows -> rows.mapNotNull { it.toStored() } }

    override suspend fun getAll(): List<StoredScheduleEntry> = dao.getAll().mapNotNull { it.toStored() }

    override suspend fun append(entry: ScheduleEntry): Long = dao.append(entry.toNewEntity())

    override suspend fun update(id: Long, entry: ScheduleEntry): Boolean {
        val e = entry.toNewEntity()
        return dao.updateContent(id, e.daysMask, e.startSecondOfDay, e.endSecondOfDay, e.mode, e.enabled) > 0
    }

    override suspend fun delete(id: Long): Boolean = dao.deleteById(id) > 0

    override suspend fun reorder(orderedIds: List<Long>): Boolean = dao.reorder(orderedIds)
}

class RoomCallLogStore(private val dao: CallLogDao) : CallLogStore {

    override fun observeRecent(limit: Int): Flow<List<CallLogEntry>> =
        dao.observeRecent(limit).map { rows -> rows.mapNotNull { it.toStored() } }

    override suspend fun getRecent(limit: Int): List<CallLogEntry> = dao.getRecent(limit).mapNotNull { it.toStored() }

    override suspend fun append(entry: CallLogEntry, keepLast: Int) = dao.appendAndTrim(entry.toEntity(), keepLast)

    override suspend fun clear() {
        dao.clear()
    }
}
