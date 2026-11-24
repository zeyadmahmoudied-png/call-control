package com.callcontrol.domain

import java.time.LocalDateTime

/** Result of looking the caller up in the user's contacts. */
enum class ContactStatus {
    IN_CONTACTS,
    NOT_IN_CONTACTS,

    /** Lookup was not possible (permission denied, provider error). The unregistered rule must NOT apply. */
    UNKNOWN,
}

/**
 * Facts about one incoming call, already prepared by the caller (the screening service).
 *
 * @param numberKey the number as produced by [PhoneNormalizer.matchKey], or null if Android gave none.
 * @param isHidden true when the caller ID is withheld (e.g. PRESENTATION_RESTRICTED).
 */
data class CallContext(
    val numberKey: String?,
    val isHidden: Boolean,
    val contactStatus: ContactStatus,
) {
    /** A call with no usable number is treated as hidden even if the flag was not set. */
    val isEffectivelyHidden: Boolean get() = isHidden || numberKey.isNullOrBlank()
}

/** A per-number rule. [numberKey] must already be normalized. [enabled] = false keeps it stored but inactive. */
data class SpecialRule(
    val numberKey: String,
    val mode: Mode,
    val enabled: Boolean = true,
)

/** An independent level (unregistered / hidden). When disabled, evaluation falls through to the next level. */
data class LevelRule(
    val enabled: Boolean,
    val mode: Mode,
)

/**
 * Everything the engine needs, as one immutable snapshot.
 * [numberRules] is keyed by normalized number, so duplicate numbers are impossible by construction.
 */
data class RuleConfig(
    val controlEnabled: Boolean,
    val globalMode: Mode,
    val unregistered: LevelRule,
    val hidden: LevelRule,
    val schedule: Schedule = Schedule.DISABLED,
    val numberRules: Map<String, SpecialRule> = emptyMap(),
) {
    companion object {
        /** Fresh install: control on, reject calls by default; per-number exceptions may allow selected callers. */
        val DEFAULT = RuleConfig(
            controlEnabled = true,
            globalMode = Mode.BUSY,
            unregistered = LevelRule(enabled = false, mode = Mode.AVAILABLE),
            hidden = LevelRule(enabled = false, mode = Mode.AVAILABLE),
        )
    }
}

enum class DecisionReason {
    CONTROL_DISABLED,
    NUMBER_RULE,
    UNREGISTERED_RULE,
    HIDDEN_RULE,
    GLOBAL_SCHEDULE,
    GLOBAL_MODE,

    /** Something failed internally; the call was allowed (fail-open). */
    INTERNAL_ERROR,
}

data class Decision(
    val mode: Mode,
    val reason: DecisionReason,
    val matchedNumber: String? = null,
    val errorMessage: String? = null,
) {
    val action: CallAction get() = mode.action
}

/**
 * Pure, stateless decision logic. No Android, no database, no clock of its own.
 *
 * Priority (first match wins):
 *  1. Per-number rule (enabled; under a rejecting global mode, only ALLOW or REJECT is honored, never SILENT)
 *  2. A rejecting global mode, which blocks all other callers regardless of contact status or schedule
 *  3. Unregistered-number rule and hidden-number rule (when the global mode is not rejecting)
 *  4. Global mode (possibly replaced by the schedule)
 *
 * Unregistered and hidden are never mixed: a hidden call is not "unregistered", and a call whose
 * contact lookup failed ([ContactStatus.UNKNOWN]) is not "unregistered" either.
 * A hidden call has no number, so per-number rules are not consulted for it.
 */
object RuleEngine {

    fun decide(call: CallContext, config: RuleConfig, now: LocalDateTime): Decision {
        if (!config.controlEnabled) {
            return Decision(Mode.AVAILABLE, DecisionReason.CONTROL_DISABLED)
        }

        val hidden = call.isEffectivelyHidden
        val key = call.numberKey

        // 1. per-number rule
        if (!hidden && key != null) {
            val rule = config.numberRules[key]
            if (rule != null && rule.enabled) {
                // Under a global reject, only an explicit ALLOW rule is an exception.
                // A per-number SILENT rule must not let a caller through the master block.
                if (config.globalMode.action != CallAction.REJECT || rule.mode.action != CallAction.SILENCE) {
                    return Decision(rule.mode, DecisionReason.NUMBER_RULE, matchedNumber = key)
                }
            }
        }

        // A rejecting global mode is the master block: only an explicit per-number rule above
        // may create an exception. Broad categories and schedules cannot accidentally let calls through.
        if (config.globalMode.action == CallAction.REJECT) {
            return Decision(config.globalMode, DecisionReason.GLOBAL_MODE)
        }

        // 2. unregistered numbers (a number is present, lookup succeeded, and it is not a contact)
        if (!hidden && call.contactStatus == ContactStatus.NOT_IN_CONTACTS && config.unregistered.enabled) {
            return Decision(config.unregistered.mode, DecisionReason.UNREGISTERED_RULE)
        }

        // 3. hidden numbers
        if (hidden && config.hidden.enabled) {
            return Decision(config.hidden.mode, DecisionReason.HIDDEN_RULE)
        }

        // 4. global mode, possibly driven by the schedule
        val resolved = ScheduleResolver.resolve(config.schedule, now, config.globalMode)
        val reason = if (resolved.source == ModeSource.MANUAL) DecisionReason.GLOBAL_MODE else DecisionReason.GLOBAL_SCHEDULE
        return Decision(resolved.mode, reason)
    }

    /**
     * Fail-open wrapper: if loading the config or deciding throws, the call is ALLOWED and the error
     * is reported in [Decision.errorMessage] so the service can log it. A broken database must never
     * block calls by mistake.
     *
     * [configProvider] is a plain (non-suspending) function on purpose, so coroutine cancellation or a
     * timeout can never be swallowed here. The service enforces its own time limit around the call.
     */
    fun decideSafely(call: CallContext, now: LocalDateTime, configProvider: () -> RuleConfig): Decision =
        try {
            decide(call, configProvider(), now)
        } catch (e: Exception) {
            Decision(
                mode = Mode.AVAILABLE,
                reason = DecisionReason.INTERNAL_ERROR,
                errorMessage = "${e::class.java.simpleName}: ${e.message}",
            )
        }
}
