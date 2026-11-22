package com.callcontrol.app

import android.app.Application
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.callcontrol.domain.Mode
import com.callcontrol.domain.RuleConfig
import com.callcontrol.domain.ScheduleEntry
import com.callcontrol.storage.AppSettings
import com.callcontrol.storage.CallControlRepository
import com.callcontrol.storage.CallLogEntry
import com.callcontrol.storage.RepoResult
import com.callcontrol.storage.StoredNumberRule
import com.callcontrol.storage.StoredScheduleEntry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The only bridge between the screens and storage. Every action goes through [CallControlRepository]
 * (validation, normalization, error handling live there). `null` in a state flow means "still loading".
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo: CallControlRepository = (app as CallControlApp).repository

    // ---- navigation (a plain back stack that survives configuration changes) ----

    val backStack = mutableStateListOf<Screen>(Screen.Home)

    val current: Screen get() = backStack.last()

    fun navigate(screen: Screen) {
        backStack.add(screen)
    }

    /** Pops one screen. Returns false when already at the root. */
    fun back(): Boolean {
        if (backStack.size <= 1) return false
        backStack.removeAt(backStack.lastIndex)
        return true
    }

    // ---- observed data ----

    private val retryTick = MutableStateFlow(0)

    /** Re-subscribes every data flow (used by the "retry" button after a storage failure). */
    fun retry() {
        retryTick.value += 1
    }

    val settings: StateFlow<RepoResult<AppSettings>?> =
        retryTick.flatMapLatest { repo.observeSettings() }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val rules: StateFlow<RepoResult<List<StoredNumberRule>>?> =
        retryTick.flatMapLatest { repo.observeNumberRules() }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val entries: StateFlow<RepoResult<List<StoredScheduleEntry>>?> =
        retryTick.flatMapLatest { repo.observeScheduleEntries() }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val log: StateFlow<RepoResult<List<CallLogEntry>>?> =
        retryTick.flatMapLatest { repo.observeCallLog(100) }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // ---- one-off messages shown as a snackbar ----

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages

    private fun say(text: String) {
        _messages.tryEmit(text)
    }

    // ---- actions ----

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            val r = repo.updateSettings(transform)
            if (r is RepoResult.Failure) say(r.error.toMessage())
        }
    }

    fun completeOnboarding() = updateSettings { it.copy(onboardingCompleted = true) }

    fun addRule(
        rawNumber: String, mode: Mode, name: String?, notes: String?, enabled: Boolean,
        onDone: (RepoResult<StoredNumberRule>) -> Unit,
    ) {
        viewModelScope.launch { onDone(repo.addNumberRule(rawNumber, mode, name, notes, enabled)) }
    }

    fun updateRule(
        id: Long, rawNumber: String, mode: Mode, enabled: Boolean, name: String?, notes: String?,
        onDone: (RepoResult<StoredNumberRule>) -> Unit,
    ) {
        viewModelScope.launch { onDone(repo.updateNumberRule(id, rawNumber, mode, enabled, name, notes)) }
    }

    fun setRuleEnabled(id: Long, enabled: Boolean) {
        viewModelScope.launch {
            val r = repo.setNumberRuleEnabled(id, enabled)
            if (r is RepoResult.Failure) say(r.error.toMessage())
        }
    }

    fun deleteRule(id: Long, onDone: (RepoResult<Unit>) -> Unit) {
        viewModelScope.launch { onDone(repo.deleteNumberRule(id)) }
    }

    fun addEntry(entry: ScheduleEntry, onDone: (RepoResult<StoredScheduleEntry>) -> Unit) {
        viewModelScope.launch { onDone(repo.addScheduleEntry(entry)) }
    }

    fun updateEntry(id: Long, entry: ScheduleEntry, onDone: (RepoResult<StoredScheduleEntry>) -> Unit) {
        viewModelScope.launch { onDone(repo.updateScheduleEntry(id, entry)) }
    }

    fun setEntryEnabled(stored: StoredScheduleEntry, enabled: Boolean) {
        viewModelScope.launch {
            val r = repo.updateScheduleEntry(stored.id, stored.entry.copy(enabled = enabled))
            if (r is RepoResult.Failure) say(r.error.toMessage())
        }
    }

    fun deleteEntry(id: Long, onDone: (RepoResult<Unit>) -> Unit) {
        viewModelScope.launch { onDone(repo.deleteScheduleEntry(id)) }
    }

    /** Moves the entry at [index] by [delta] positions (-1 = earlier = higher priority). */
    fun moveEntry(current: List<StoredScheduleEntry>, index: Int, delta: Int) {
        val target = index + delta
        if (index !in current.indices || target !in current.indices) return
        val ids = current.map { it.id }.toMutableList()
        ids.add(target, ids.removeAt(index))
        viewModelScope.launch {
            val r = repo.reorderScheduleEntries(ids)
            if (r is RepoResult.Failure) say(r.error.toMessage())
        }
    }

    fun clearLog() {
        viewModelScope.launch {
            when (val r = repo.clearCallLog()) {
                is RepoResult.Success -> say("تم مسح السجل.")
                is RepoResult.Failure -> say(r.error.toMessage())
            }
        }
    }

    /** Reads everything the screening service reads, for the diagnostics screen. */
    suspend fun checkStorage(): RepoResult<RuleConfig> = repo.loadRuleConfig()
}
