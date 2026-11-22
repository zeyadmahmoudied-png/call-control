package com.callcontrol.storage.android.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.callcontrol.domain.LevelRule
import com.callcontrol.domain.Mode
import com.callcontrol.storage.AppSettings
import com.callcontrol.storage.SettingsStore
import com.callcontrol.storage.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Preferences-DataStore implementation of [SettingsStore] (simple settings only; rules and schedule
 * windows are in Room).
 *
 * Behaviour worth knowing:
 *  - [update] uses DataStore's `edit`, which is atomic: if the transform throws, nothing is written.
 *  - A missing key means "default". A stored value this version cannot parse (e.g. an unknown mode name)
 *    also falls back to that field's default, field by field, so one bad value cannot hide the rest.
 *  - Read errors (IOException, CorruptionException) are NOT turned into empty settings. They propagate,
 *    the Repository reports a storage failure, and the stored file is left untouched. Showing defaults
 *    while a real file exists would be misleading and a following write could overwrite good data.
 *    (No ReplaceFileCorruptionHandler is installed for the same reason.)
 */
class DataStoreSettingsStore(private val dataStore: DataStore<Preferences>) : SettingsStore {

    override fun observe(): Flow<AppSettings> = dataStore.data.map { it.toSettings() }

    override suspend fun read(): AppSettings = dataStore.data.first().toSettings()

    override suspend fun update(transform: (AppSettings) -> AppSettings): AppSettings {
        var stored = AppSettings()
        dataStore.edit { prefs ->
            val updated = transform(prefs.toSettings())
            prefs.write(updated)
            stored = updated
        }
        return stored
    }

    object Keys {
        val CONTROL_ENABLED = booleanPreferencesKey("control_enabled")
        val GLOBAL_MODE = stringPreferencesKey("global_mode")
        val UNREGISTERED_ENABLED = booleanPreferencesKey("unregistered_enabled")
        val UNREGISTERED_MODE = stringPreferencesKey("unregistered_mode")
        val HIDDEN_ENABLED = booleanPreferencesKey("hidden_enabled")
        val HIDDEN_MODE = stringPreferencesKey("hidden_mode")
        val SCHEDULE_ENABLED = booleanPreferencesKey("schedule_enabled")
        val SCHEDULE_DEFAULT_MODE = stringPreferencesKey("schedule_default_mode")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val LOG_ENABLED = booleanPreferencesKey("log_enabled")
    }

    private fun Preferences.toSettings(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            controlEnabled = this[Keys.CONTROL_ENABLED] ?: d.controlEnabled,
            globalMode = modeOrNull(this[Keys.GLOBAL_MODE]) ?: d.globalMode,
            unregistered = LevelRule(
                enabled = this[Keys.UNREGISTERED_ENABLED] ?: d.unregistered.enabled,
                mode = modeOrNull(this[Keys.UNREGISTERED_MODE]) ?: d.unregistered.mode,
            ),
            hidden = LevelRule(
                enabled = this[Keys.HIDDEN_ENABLED] ?: d.hidden.enabled,
                mode = modeOrNull(this[Keys.HIDDEN_MODE]) ?: d.hidden.mode,
            ),
            scheduleEnabled = this[Keys.SCHEDULE_ENABLED] ?: d.scheduleEnabled,
            scheduleDefaultMode = modeOrNull(this[Keys.SCHEDULE_DEFAULT_MODE]),
            themeMode = ThemeMode.values().firstOrNull { it.name == this[Keys.THEME_MODE] } ?: d.themeMode,
            onboardingCompleted = this[Keys.ONBOARDING_COMPLETED] ?: d.onboardingCompleted,
            logEnabled = this[Keys.LOG_ENABLED] ?: d.logEnabled,
        )
    }

    private fun MutablePreferences.write(s: AppSettings) {
        this[Keys.CONTROL_ENABLED] = s.controlEnabled
        this[Keys.GLOBAL_MODE] = s.globalMode.name
        this[Keys.UNREGISTERED_ENABLED] = s.unregistered.enabled
        this[Keys.UNREGISTERED_MODE] = s.unregistered.mode.name
        this[Keys.HIDDEN_ENABLED] = s.hidden.enabled
        this[Keys.HIDDEN_MODE] = s.hidden.mode.name
        this[Keys.SCHEDULE_ENABLED] = s.scheduleEnabled
        val scheduleDefault = s.scheduleDefaultMode
        if (scheduleDefault != null) this[Keys.SCHEDULE_DEFAULT_MODE] = scheduleDefault.name
        else remove(Keys.SCHEDULE_DEFAULT_MODE)
        this[Keys.THEME_MODE] = s.themeMode.name
        this[Keys.ONBOARDING_COMPLETED] = s.onboardingCompleted
        this[Keys.LOG_ENABLED] = s.logEnabled
    }

    private fun modeOrNull(name: String?): Mode? = Mode.values().firstOrNull { it.name == name }

    companion object {
        const val FILE_NAME = "call_control_settings"

        /**
         * Creates the DataStore for the settings file. DataStore allows exactly ONE instance per file
         * per process: call this once (see [com.callcontrol.storage.android.CallControlStorage]).
         */
        fun createDataStore(
            context: Context,
            scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
            fileName: String = FILE_NAME,
        ): DataStore<Preferences> =
            PreferenceDataStoreFactory.create(
                scope = scope,
                produceFile = { context.applicationContext.preferencesDataStoreFile(fileName) },
            )
    }
}
