package com.callcontrol.app

import android.app.Application
import com.callcontrol.app.service.AndroidContactLookup
import com.callcontrol.storage.CallControlRepository
import com.callcontrol.storage.ScreeningProcessor
import com.callcontrol.storage.android.CallControlStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Holds the single storage stack and the screening processor for the whole process. */
class CallControlApp : Application() {

    val repository: CallControlRepository by lazy { CallControlStorage.repository(this) }

    val screeningProcessor: ScreeningProcessor by lazy { ScreeningProcessor(repository, AndroidContactLookup(this)) }

    /**
     * Process-wide scope for work that must outlive the screening service instance
     * (Android destroys the service right after the answer is sent, and the log write comes after that).
     */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
