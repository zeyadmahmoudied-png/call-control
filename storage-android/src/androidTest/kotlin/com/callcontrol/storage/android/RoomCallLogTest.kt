package com.callcontrol.storage.android

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.callcontrol.domain.ContactStatus
import com.callcontrol.domain.DecisionReason
import com.callcontrol.domain.Mode
import com.callcontrol.storage.CallLogEntry
import com.callcontrol.storage.android.room.CallControlDatabase
import com.callcontrol.storage.android.room.CallLogEntity
import com.callcontrol.storage.android.room.RoomCallLogStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * INSTRUMENTED (emulator/device) tests of the real Room call-log table.
 * Each test uses its own temporary database file.
 */
@RunWith(AndroidJUnit4::class)
class RoomCallLogTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "log-test-${UUID.randomUUID()}.db"
    private val open = mutableListOf<CallControlDatabase>()

    private fun db() = CallControlDatabase.build(context, dbName).also { open += it }

    @Before
    fun clean() {
        context.deleteDatabase(dbName)
    }

    @After
    fun cleanUp() {
        open.forEach { it.close() }
        context.deleteDatabase(dbName)
    }

    private fun entry(n: Int, key: String? = "+2010000000$n") = CallLogEntry(
        id = 0, timestampMillis = n.toLong(), numberKey = key, hidden = key == null,
        contactStatus = ContactStatus.NOT_IN_CONTACTS, mode = Mode.SILENT, reason = DecisionReason.UNREGISTERED_RULE, errorType = null,
    )

    @Test
    fun entriesSurviveReopeningAndComeBackNewestFirst() = runBlocking<Unit> {
        val first = db()
        RoomCallLogStore(first.callLogDao()).apply { append(entry(1), 300); append(entry(2), 300) }
        first.close()
        open -= first

        val restored = RoomCallLogStore(db().callLogDao()).getRecent(10)

        assertEquals(listOf(2L, 1L), restored.map { it.timestampMillis })
        assertEquals(Mode.SILENT, restored.first().mode)
        assertEquals(DecisionReason.UNREGISTERED_RULE, restored.first().reason)
    }

    @Test
    fun hiddenCallerKeepsANullNumber() = runBlocking<Unit> {
        val store = RoomCallLogStore(db().callLogDao())
        store.append(entry(1, key = null), 300)
        val e = store.getRecent(1).single()
        assertEquals(null, e.numberKey)
        assertEquals(true, e.hidden)
    }

    @Test
    fun appendTrimsToTheNewestEntriesInOneTransaction() = runBlocking<Unit> {
        val store = RoomCallLogStore(db().callLogDao())
        for (n in 1..12) store.append(entry(n), 5)
        assertEquals(listOf(12L, 11L, 10L, 9L, 8L), store.getRecent(100).map { it.timestampMillis })
    }

    @Test
    fun clearRemovesEverything() = runBlocking<Unit> {
        val store = RoomCallLogStore(db().callLogDao())
        store.append(entry(1), 300)
        store.clear()
        assertEquals(0, store.getRecent(10).size)
    }

    @Test
    fun aRowWithAnUnknownReasonIsSkippedButKept() = runBlocking<Unit> {
        val database = db()
        database.callLogDao().insertRow(
            CallLogEntity(timestampMillis = 1, numberKey = null, hidden = true, contactStatus = "UNKNOWN", mode = "BUSY", reason = "FUTURE_REASON", errorType = null),
        )
        assertEquals(0, RoomCallLogStore(database.callLogDao()).getRecent(10).size)
        assertEquals(1, database.callLogDao().getRecent(10).size)
    }
}
