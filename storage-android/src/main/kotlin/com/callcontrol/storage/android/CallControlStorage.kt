package com.callcontrol.storage.android

import android.content.Context
import com.callcontrol.storage.CallControlRepository
import com.callcontrol.storage.android.datastore.DataStoreSettingsStore
import com.callcontrol.storage.android.room.CallControlDatabase
import com.callcontrol.storage.android.room.RoomCallLogStore
import com.callcontrol.storage.android.room.RoomNumberRuleStore
import com.callcontrol.storage.android.room.RoomScheduleEntryStore

/**
 * Process-wide holder for the real storage stack (Room + DataStore + Repository).
 *
 * The database and the DataStore must each exist only once per process, so everything goes through
 * [repository]. Later phases (service, UI) call this instead of building their own instances.
 */
object CallControlStorage {
    @Volatile
    private var instance: CallControlRepository? = null

    fun repository(context: Context): CallControlRepository =
        instance ?: synchronized(this) {
            instance ?: build(context).also { instance = it }
        }

    private fun build(context: Context): CallControlRepository {
        val db = CallControlDatabase.build(context)
        val settings = DataStoreSettingsStore(DataStoreSettingsStore.createDataStore(context))
        return CallControlRepository(
            rules = RoomNumberRuleStore(db.numberRuleDao()),
            schedule = RoomScheduleEntryStore(db.scheduleEntryDao()),
            settings = settings,
            log = RoomCallLogStore(db.callLogDao()),
        )
    }
}
