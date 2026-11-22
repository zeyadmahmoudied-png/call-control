package com.callcontrol.storage.android

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.callcontrol.domain.Mode
import com.callcontrol.domain.ScheduleEntry
import com.callcontrol.storage.RepoError
import com.callcontrol.storage.RepoResult
import com.callcontrol.storage.WriteOutcome
import com.callcontrol.storage.getOrThrow
import com.callcontrol.storage.android.room.CallControlDatabase
import com.callcontrol.storage.android.room.NumberRuleEntity
import com.callcontrol.storage.android.room.RoomNumberRuleStore
import com.callcontrol.storage.android.room.RoomScheduleEntryStore
import com.callcontrol.storage.CallControlRepository
import com.callcontrol.storage.android.datastore.DataStoreSettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.DayOfWeek
import java.time.LocalTime
import java.util.UUID

/**
 * INSTRUMENTED tests: they need an Android emulator or device (run with ./gradlew :storage-android:connectedAndroidTest).
 * They were written together with the code but NOT run when this phase was delivered (no Android SDK
 * in the build environment). Treat them as unverified until you run them.
 *
 * These use a real on-disk database file, closed and reopened, which is what "data survives an app
 * restart" means at the storage level.
 */
@RunWith(AndroidJUnit4::class)
class RoomPersistenceTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "test-${UUID.randomUUID()}.db"
    private val openDatabases = mutableListOf<CallControlDatabase>()

    private fun open(): CallControlDatabase =
        CallControlDatabase.build(context, dbName).also { openDatabases += it }

    private fun close(db: CallControlDatabase) {
        db.close()
        openDatabases -= db
    }

    @Before
    fun cleanStart() {
        context.deleteDatabase(dbName) // removes only this test's own temporary file
    }

    @After
    fun cleanUp() {
        openDatabases.forEach { it.close() }
        context.deleteDatabase(dbName)
    }

    private fun entity(key: String, mode: String = Mode.BUSY.name) =
        NumberRuleEntity(numberKey = key, mode = mode, enabled = true, name = null, notes = null, createdAtMillis = 1, updatedAtMillis = 1)

    @Test
    fun rulesSurviveClosingAndReopeningTheDatabaseFile() = runBlocking<Unit> {
        val first = open()
        val repo1 = repoOver(first)
        repo1.addNumberRule("01012345678", Mode.BUSY, name = "Ahmed").getOrThrow()
        repo1.addNumberRule("01112345678", Mode.SILENT, enabled = false).getOrThrow()
        close(first)

        val second = open()
        val config = repoOver(second).loadRuleConfig().getOrThrow()

        assertEquals(Mode.BUSY, config.numberRules.getValue("+201012345678").mode)
        assertEquals(false, config.numberRules.getValue("+201112345678").enabled)
    }

    @Test
    fun scheduleSurvivesReopenWithOrderDaysAndTimes() = runBlocking<Unit> {
        val a = ScheduleEntry(setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY), LocalTime.of(8, 0), LocalTime.of(14, 0), Mode.BUSY)
        val b = ScheduleEntry(setOf(DayOfWeek.FRIDAY), LocalTime.of(22, 0), LocalTime.of(6, 0), Mode.SILENT, enabled = false)
        val first = open()
        repoOver(first).addScheduleEntry(a).getOrThrow()
        repoOver(first).addScheduleEntry(b).getOrThrow()
        close(first)

        val restored = repoOver(open()).loadRuleConfig().getOrThrow().schedule.entries

        assertEquals(listOf(a, b), restored)
    }

    @Test
    fun uniqueIndexRejectsTheSameNumberTwice() = runBlocking<Unit> {
        val repo = repoOver(open())
        repo.addNumberRule("01012345678", Mode.BUSY).getOrThrow()
        val duplicate = repo.addNumberRule("+201012345678", Mode.AVAILABLE)
        assertEquals(RepoError.DuplicateNumber, (duplicate as RepoResult.Failure).error)
    }

    @Test
    fun updatingToAnExistingNumberIsADuplicateAndChangesNothing() = runBlocking<Unit> {
        val db = open()
        val store = RoomNumberRuleStore(db.numberRuleDao())
        val a = store.insert(storedRule("+201012345678"))!!
        val b = store.insert(storedRule("+201112345678"))!!

        val outcome = store.update(store.get(b)!!.copy(numberKey = "+201012345678"))

        assertEquals(WriteOutcome.DUPLICATE, outcome)
        assertEquals("+201012345678", store.get(a)!!.numberKey)
        assertEquals("+201112345678", store.get(b)!!.numberKey)
    }

    @Test
    fun deletingARuleRemovesOnlyThatRow() = runBlocking<Unit> {
        val store = RoomNumberRuleStore(open().numberRuleDao())
        val a = store.insert(storedRule("+201012345678"))!!
        store.insert(storedRule("+201112345678"))!!

        assertTrue(store.delete(a))

        assertEquals(listOf("+201112345678"), store.getAll().map { it.numberKey })
    }

    @Test
    fun scheduleReorderIsAllOrNothing() = runBlocking<Unit> {
        val store = RoomScheduleEntryStore(open().scheduleEntryDao())
        val e = ScheduleEntry(setOf(DayOfWeek.MONDAY), LocalTime.of(8, 0), LocalTime.of(9, 0), Mode.BUSY)
        val ids = listOf(store.append(e), store.append(e.copy(mode = Mode.SILENT)), store.append(e.copy(mode = Mode.AVAILABLE)))

        assertTrue(!store.reorder(listOf(ids[0], ids[1])))        // missing one
        assertTrue(!store.reorder(ids + 12345L))                   // unknown id
        assertEquals(ids, store.getAll().map { it.id })            // unchanged

        assertTrue(store.reorder(ids.reversed()))
        assertEquals(ids.reversed(), store.getAll().map { it.id })
    }

    @Test
    fun aRowWithAnUnknownModeIsSkippedButNotDeleted() = runBlocking<Unit> {
        val db = open()
        db.numberRuleDao().insert(entity("+201012345678", mode = "FUTURE_MODE"))
        val store = RoomNumberRuleStore(db.numberRuleDao())

        assertEquals(emptyList<String>(), store.getAll().map { it.numberKey }) // this version ignores it
        assertEquals(1, db.numberRuleDao().getAll().size)                      // but the data is still there
    }

    private fun storedRule(key: String) = com.callcontrol.storage.StoredNumberRule(
        id = 0, numberKey = key, mode = Mode.BUSY, enabled = true, name = null, notes = null, createdAtMillis = 1, updatedAtMillis = 1,
    )

    private fun repoOver(db: CallControlDatabase): CallControlRepository {
        // Settings are not under test here; a throwaway DataStore file keeps the Repository constructible.
        val file = File(context.cacheDir, "unused-${UUID.randomUUID()}.preferences_pb")
        val scope = kotlinx.coroutines.CoroutineScope(Dispatchers.IO + kotlinx.coroutines.SupervisorJob())
        val dataStore = androidx.datastore.preferences.core.PreferenceDataStoreFactory.create(scope = scope) { file }
        return CallControlRepository(
            RoomNumberRuleStore(db.numberRuleDao()),
            RoomScheduleEntryStore(db.scheduleEntryDao()),
            DataStoreSettingsStore(dataStore),
        )
    }
}
