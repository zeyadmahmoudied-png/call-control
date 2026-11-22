package com.callcontrol.storage.android.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NumberRuleDao {
    @Query("SELECT * FROM number_rules ORDER BY created_at ASC, id ASC")
    fun observeAll(): Flow<List<NumberRuleEntity>>

    @Query("SELECT * FROM number_rules ORDER BY created_at ASC, id ASC")
    suspend fun getAll(): List<NumberRuleEntity>

    @Query("SELECT * FROM number_rules WHERE id = :id")
    suspend fun getById(id: Long): NumberRuleEntity?

    /** Returns the new row id, or -1 if the unique number_key index rejected the row (IGNORE). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: NumberRuleEntity): Long

    /** Returns the number of updated rows. Throws SQLiteConstraintException if number_key clashes (ABORT: nothing changes). */
    @Update(onConflict = OnConflictStrategy.ABORT)
    suspend fun update(entity: NumberRuleEntity): Int

    /** Deletes exactly one rule row. Contacts are never touched: this table is the app's own data. */
    @Query("DELETE FROM number_rules WHERE id = :id")
    suspend fun deleteById(id: Long): Int
}

@Dao
abstract class ScheduleEntryDao {
    @Query("SELECT * FROM schedule_entries ORDER BY position ASC, id ASC")
    abstract fun observeAll(): Flow<List<ScheduleEntryEntity>>

    @Query("SELECT * FROM schedule_entries ORDER BY position ASC, id ASC")
    abstract suspend fun getAll(): List<ScheduleEntryEntity>

    @Query("SELECT id FROM schedule_entries ORDER BY position ASC, id ASC")
    abstract suspend fun getIds(): List<Long>

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM schedule_entries")
    abstract suspend fun nextPosition(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertRow(entity: ScheduleEntryEntity): Long

    /** Keeps the row's position; only the content changes. */
    @Query(
        "UPDATE schedule_entries SET days_mask = :daysMask, start_second = :startSecond, " +
            "end_second = :endSecond, mode = :mode, enabled = :enabled WHERE id = :id",
    )
    abstract suspend fun updateContent(
        id: Long,
        daysMask: Int,
        startSecond: Int,
        endSecond: Int,
        mode: String,
        enabled: Boolean,
    ): Int

    @Query("DELETE FROM schedule_entries WHERE id = :id")
    abstract suspend fun deleteById(id: Long): Int

    @Query("UPDATE schedule_entries SET position = :position WHERE id = :id")
    abstract suspend fun setPosition(id: Long, position: Int): Int

    /** Position lookup and insert run in one transaction, so two appends can never share a position. */
    @Transaction
    open suspend fun append(entity: ScheduleEntryEntity): Long =
        insertRow(entity.copy(id = 0, position = nextPosition()))

    /**
     * One transaction: either every position is rewritten or none is.
     * Returns false (and changes nothing) unless [orderedIds] is exactly the set of existing ids.
     */
    @Transaction
    open suspend fun reorder(orderedIds: List<Long>): Boolean {
        val existing = getIds()
        if (orderedIds.size != existing.size || orderedIds.toSet() != existing.toSet()) return false
        orderedIds.forEachIndexed { index, id -> setPosition(id, index) }
        return true
    }
}

@Dao
abstract class CallLogDao {
    @Query("SELECT * FROM call_log ORDER BY timestamp DESC, id DESC LIMIT :limit")
    abstract fun observeRecent(limit: Int): Flow<List<CallLogEntity>>

    @Query("SELECT * FROM call_log ORDER BY timestamp DESC, id DESC LIMIT :limit")
    abstract suspend fun getRecent(limit: Int): List<CallLogEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertRow(entity: CallLogEntity): Long

    @Query(
        "DELETE FROM call_log WHERE id NOT IN " +
            "(SELECT id FROM call_log ORDER BY timestamp DESC, id DESC LIMIT :keep)",
    )
    abstract suspend fun trimTo(keep: Int): Int

    @Query("DELETE FROM call_log")
    abstract suspend fun clear(): Int

    /** Insert and trim in ONE transaction, so the log can never be left over the limit. */
    @Transaction
    open suspend fun appendAndTrim(entity: CallLogEntity, keep: Int) {
        insertRow(entity.copy(id = 0))
        trimTo(keep)
    }
}
