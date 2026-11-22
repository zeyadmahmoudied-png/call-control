package com.callcontrol.app

import androidx.test.platform.app.InstrumentationRegistry
import com.callcontrol.storage.AppSettings
import com.callcontrol.storage.CallControlRepository
import com.callcontrol.storage.RepoResult
import com.callcontrol.storage.android.CallControlStorage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

object TestSupport {
    fun repo(): CallControlRepository =
        CallControlStorage.repository(InstrumentationRegistry.getInstrumentation().targetContext)

    /** Empties every table and puts settings back to defaults (onboarding already completed). */
    fun reset() = runBlocking {
        val r = repo()
        (r.observeNumberRules().first() as? RepoResult.Success)?.value.orEmpty().forEach { r.deleteNumberRule(it.id) }
        (r.observeScheduleEntries().first() as? RepoResult.Success)?.value.orEmpty().forEach { r.deleteScheduleEntry(it.id) }
        r.clearCallLog()
        r.updateSettings { AppSettings(onboardingCompleted = true) }
    }
}
