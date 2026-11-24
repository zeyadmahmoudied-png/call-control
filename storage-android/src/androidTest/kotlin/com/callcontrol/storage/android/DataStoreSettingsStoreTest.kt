package com.callcontrol.storage.android

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.callcontrol.domain.LevelRule
import com.callcontrol.domain.Mode
import com.callcontrol.storage.AppSettings
import com.callcontrol.storage.ThemeMode
import com.callcontrol.storage.android.datastore.DataStoreSettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/**
 * INSTRUMENTED tests (emulator/device). Written with the code, NOT run at delivery time.
 * Each test uses its own temporary preferences file inside the app cache directory.
 */
@RunWith(AndroidJUnit4::class)
class DataStoreSettingsStoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val file = File(context.cacheDir, "settings-test-${UUID.randomUUID()}.preferences_pb")
    private val jobs = mutableListOf<Job>()

    private fun openDataStore() = run {
        val job = Job().also { jobs += it }
        PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job)) { file }
    }

    @After
    fun cleanUp() = runBlocking<Unit> {
        jobs.forEach { it.cancelAndJoin() }
        file.delete() // this test's own temp file
    }

    @Test
    fun freshFileGivesDefaults() = runBlocking<Unit> {
        assertEquals(AppSettings(), DataStoreSettingsStore(openDataStore()).read())
    }

    @Test
    fun settingsSurviveReopeningTheDataStoreFile() = runBlocking<Unit> {
        val wanted = AppSettings(
            controlEnabled = false,
            globalMode = Mode.BUSY,
            unregistered = LevelRule(true, Mode.SILENT),
            hidden = LevelRule(true, Mode.UNAVAILABLE),
            scheduleEnabled = true,
            scheduleDefaultMode = Mode.SILENT,
            themeMode = ThemeMode.DARK,
            onboardingCompleted = true,
        )
        val firstJob = Job().also { jobs += it }
        val first = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + firstJob)) { file }
        DataStoreSettingsStore(first).update { wanted }
        firstJob.cancelAndJoin() // DataStore allows one live instance per file, so close the first one

        assertEquals(wanted, DataStoreSettingsStore(openDataStore()).read())
    }

    @Test
    fun clearingTheScheduleDefaultModeIsPersisted() = runBlocking<Unit> {
        val store = DataStoreSettingsStore(openDataStore())
        store.update { it.copy(scheduleDefaultMode = Mode.SILENT) }
        store.update { it.copy(scheduleDefaultMode = null) }
        assertEquals(null, store.read().scheduleDefaultMode)
    }

    @Test
    fun aThrowingTransformWritesNothing() = runBlocking<Unit> {
        val store = DataStoreSettingsStore(openDataStore())
        store.update { it.copy(globalMode = Mode.BUSY) }

        var thrown = false
        try {
            store.update { throw IllegalStateException("bad transform") }
        } catch (e: IllegalStateException) {
            thrown = true
        }

        assertTrue(thrown)
        assertEquals(Mode.BUSY, store.read().globalMode)
    }

    @Test
    fun oldAllowAllDefaultMigratesToRejectingGlobalModeOnce() = runBlocking<Unit> {
        val dataStore = openDataStore()
        dataStore.edit { it[DataStoreSettingsStore.Keys.GLOBAL_MODE] = Mode.AVAILABLE.name }
        val store = DataStoreSettingsStore(dataStore)

        assertEquals(Mode.BUSY, store.read().globalMode)

        // Once migrated, a deliberate user choice is preserved on later reads.
        store.update { it.copy(globalMode = Mode.AVAILABLE) }
        assertEquals(Mode.AVAILABLE, store.read().globalMode)
    }

    @Test
    fun anUnknownStoredValueFallsBackToThatFieldsDefaultOnly() = runBlocking<Unit> {
        val dataStore = openDataStore()
        val store = DataStoreSettingsStore(dataStore)
        store.update { it.copy(themeMode = ThemeMode.DARK, globalMode = Mode.BUSY) }
        dataStore.edit { it[DataStoreSettingsStore.Keys.GLOBAL_MODE] = "SOME_FUTURE_MODE" }

        val read = store.read()

        assertEquals(Mode.BUSY, read.globalMode) // bad value -> new default
        assertEquals(ThemeMode.DARK, read.themeMode)   // other fields untouched
    }
}
