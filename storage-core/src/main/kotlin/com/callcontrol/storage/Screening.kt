package com.callcontrol.storage

import com.callcontrol.domain.CallContext
import com.callcontrol.domain.ContactStatus
import com.callcontrol.domain.Decision
import com.callcontrol.domain.DecisionReason
import com.callcontrol.domain.Mode
import com.callcontrol.domain.PhoneNormalizer
import com.callcontrol.domain.RuleConfig
import com.callcontrol.domain.RuleEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.time.LocalDateTime

/** Looks a number up in the user's contacts. May throw (e.g. SecurityException); the processor treats that as UNKNOWN. */
fun interface ContactLookup {
    suspend fun lookup(rawNumber: String): ContactStatus
}

/** What Android tells us about an incoming call, already reduced to plain values. */
data class IncomingCall(
    val rawNumber: String?,
    /** True when the caller ID is withheld (PRESENTATION_RESTRICTED). */
    val presentationRestricted: Boolean,
)

data class ScreeningResult(
    val decision: Decision,
    val numberKey: String?,
    val hidden: Boolean,
    val contactStatus: ContactStatus,
)

/**
 * Everything the call-screening service does except talking to Android: normalize, look the caller up,
 * load the rules, decide. Pure Kotlin so it is unit-tested on the JVM.
 *
 * SAFETY: [decide] never throws for a failure of its own (storage error, contacts error, timeout): it returns
 * a decision that ALLOWS the call (reason INTERNAL_ERROR). Only coroutine cancellation propagates.
 * The two timeouts keep the total well inside the system's ~5 s limit for a screening answer.
 */
class ScreeningProcessor(
    private val repository: CallControlRepository,
    private val contacts: ContactLookup,
    private val now: () -> LocalDateTime = { LocalDateTime.now() },
    private val epochMillis: () -> Long = { System.currentTimeMillis() },
    private val lookupTimeoutMillis: Long = 1_500,
    private val configTimeoutMillis: Long = 2_500,
) {

    suspend fun decide(call: IncomingCall): ScreeningResult {
        val key = PhoneNormalizer.matchKey(call.rawNumber)
        val hidden = call.presentationRestricted || key == null

        val raw = call.rawNumber
        val contactStatus = if (hidden || raw == null) ContactStatus.UNKNOWN else lookupContact(raw)

        val config = loadConfig()
        val decision = RuleEngine.decideSafely(CallContext(key, hidden, contactStatus), now()) {
            config ?: throw IllegalStateException("rule config unavailable")
        }
        return ScreeningResult(decision, key, hidden, contactStatus)
    }

    /**
     * Writes the log entry (best effort, after the answer was sent). [deliveryError] is the exception class name
     * if sending the answer to Android failed. Returns the repository result so the caller can see a logging failure.
     */
    suspend fun record(result: ScreeningResult, deliveryError: String? = null): RepoResult<Boolean> {
        val d = result.decision
        val entry = CallLogEntry(
            id = 0,
            timestampMillis = epochMillis(),
            numberKey = if (result.hidden) null else result.numberKey,
            hidden = result.hidden,
            contactStatus = result.contactStatus,
            mode = d.mode,
            reason = d.reason,
            errorType = deliveryError ?: d.errorMessage?.substringBefore(':'),
        )
        return repository.recordCall(entry)
    }

    private suspend fun lookupContact(raw: String): ContactStatus =
        try {
            withTimeout(lookupTimeoutMillis) { contacts.lookup(raw) }
        } catch (e: TimeoutCancellationException) {
            ContactStatus.UNKNOWN
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ContactStatus.UNKNOWN
        }

    private suspend fun loadConfig(): RuleConfig? =
        try {
            withTimeout(configTimeoutMillis) { repository.loadRuleConfig() }.getOrNull()
        } catch (e: TimeoutCancellationException) {
            null
        } catch (e: CancellationException) {
            throw e
        }

    companion object {
        /** Result used if something unexpected escapes [decide]: allow the call. */
        fun failOpen(errorType: String): ScreeningResult =
            ScreeningResult(
                decision = Decision(Mode.AVAILABLE, DecisionReason.INTERNAL_ERROR, errorMessage = errorType),
                numberKey = null,
                hidden = false,
                contactStatus = ContactStatus.UNKNOWN,
            )
    }
}
