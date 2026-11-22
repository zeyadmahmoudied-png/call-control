package com.callcontrol.storage.android.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

/**
 * The app's Room database (number rules and schedule windows only; simple settings live in DataStore).
 *
 * DATA SAFETY RULES (do not weaken):
 *  - There is deliberately NO `fallbackToDestructiveMigration()`. If the stored schema version has no
 *    migration path, opening the database throws and the repository reports a storage failure.
 *    The user's file is left untouched; the call screening service fails open (allows calls).
 *  - Every schema change must bump `version`, add a [Migration] to [MIGRATIONS], keep the exported
 *    schema JSON (schemas/ folder, committed), and come with a MigrationTestHelper test.
 *  - Never delete or replace the database file from code.
 */
@Database(
    entities = [NumberRuleEntity::class, ScheduleEntryEntity::class, CallLogEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class CallControlDatabase : RoomDatabase() {
    abstract fun numberRuleDao(): NumberRuleDao
    abstract fun scheduleEntryDao(): ScheduleEntryDao
    abstract fun callLogDao(): CallLogDao

    companion object {
        const val FILE_NAME = "call_control.db"

        /**
         * Version 1 is the first schema (the call_log table was added BEFORE any release, so no migration exists),
         * so there is nothing to migrate yet.
         */
        val MIGRATIONS: Array<Migration> = emptyArray()

        fun build(context: Context, fileName: String = FILE_NAME): CallControlDatabase =
            Room.databaseBuilder(context.applicationContext, CallControlDatabase::class.java, fileName)
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
