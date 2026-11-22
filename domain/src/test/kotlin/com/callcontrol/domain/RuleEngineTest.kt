package com.callcontrol.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime

class RuleEngineTest {

    private val now = LocalDateTime.of(2026, 11, 21, 10, 0) // Saturday 10:00

    private val friend = "+201012345678"
    private val stranger = "+201112345678"

    private fun config(
        global: Mode = Mode.AVAILABLE,
        unregistered: LevelRule = LevelRule(false, Mode.AVAILABLE),
        hidden: LevelRule = LevelRule(false, Mode.AVAILABLE),
        schedule: Schedule = Schedule.DISABLED,
        rules: List<SpecialRule> = emptyList(),
        enabled: Boolean = true,
    ) = RuleConfig(
        controlEnabled = enabled,
        globalMode = global,
        unregistered = unregistered,
        hidden = hidden,
        schedule = schedule,
        numberRules = rules.associateBy { it.numberKey },
    )

    private fun call(
        number: String? = friend,
        contact: ContactStatus = ContactStatus.IN_CONTACTS,
        hidden: Boolean = false,
    ) = CallContext(number, hidden, contact)

    private fun decide(c: CallContext, cfg: RuleConfig, at: LocalDateTime = now) = RuleEngine.decide(c, cfg, at)

    // ---- mode -> action mapping ----

    @Test
    fun modesMapToTheExpectedDeviceActions() {
        assertEquals(CallAction.ALLOW, Mode.AVAILABLE.action)
        assertEquals(CallAction.REJECT, Mode.BUSY.action)
        assertEquals(CallAction.REJECT, Mode.UNAVAILABLE.action) // same device action as BUSY: the network decides what the caller hears
        assertEquals(CallAction.SILENCE, Mode.SILENT.action)
    }

    // ---- per-number rules ----

    @Test
    fun numberRuleAvailableBeatsBusyGlobal() {
        val d = decide(call(), config(global = Mode.BUSY, rules = listOf(SpecialRule(friend, Mode.AVAILABLE))))
        assertEquals(Mode.AVAILABLE, d.mode)
        assertEquals(CallAction.ALLOW, d.action)
        assertEquals(DecisionReason.NUMBER_RULE, d.reason)
        assertEquals(friend, d.matchedNumber)
    }

    @Test
    fun numberRuleBusyBeatsAvailableGlobal() {
        val d = decide(call(), config(global = Mode.AVAILABLE, rules = listOf(SpecialRule(friend, Mode.BUSY))))
        assertEquals(CallAction.REJECT, d.action)
        assertEquals(DecisionReason.NUMBER_RULE, d.reason)
    }

    @Test
    fun numberRuleSilentSilencesWithoutRejecting() {
        val d = decide(call(), config(rules = listOf(SpecialRule(friend, Mode.SILENT))))
        assertEquals(CallAction.SILENCE, d.action)
    }

    @Test
    fun numberWithoutSpecialRuleUsesGlobalMode() {
        val d = decide(call(stranger), config(global = Mode.SILENT, rules = listOf(SpecialRule(friend, Mode.BUSY))))
        assertEquals(Mode.SILENT, d.mode)
        assertEquals(DecisionReason.GLOBAL_MODE, d.reason)
        assertNull(d.matchedNumber)
    }

    @Test
    fun disabledNumberRuleFallsThrough() {
        val d = decide(call(), config(global = Mode.SILENT, rules = listOf(SpecialRule(friend, Mode.BUSY, enabled = false))))
        assertEquals(Mode.SILENT, d.mode)
        assertEquals(DecisionReason.GLOBAL_MODE, d.reason)
    }

    @Test
    fun ruleStoredFromAnyFormatMatchesTheIncomingNumber() {
        val stored = (PhoneNormalizer.normalize("010 1234 5678") as NormalizeResult.Valid).e164
        val cfg = config(rules = listOf(SpecialRule(stored, Mode.BUSY)))
        val incoming = PhoneNormalizer.matchKey("+201012345678")
        assertEquals(CallAction.REJECT, decide(call(incoming), cfg).action)
        val incomingLocal = PhoneNormalizer.matchKey("01012345678")
        assertEquals(CallAction.REJECT, decide(call(incomingLocal), cfg).action)
    }

    // ---- unregistered ----

    @Test
    fun unregisteredRuleAppliesToNumbersNotInContacts() {
        val cfg = config(global = Mode.AVAILABLE, unregistered = LevelRule(true, Mode.BUSY))
        val d = decide(call(stranger, ContactStatus.NOT_IN_CONTACTS), cfg)
        assertEquals(CallAction.REJECT, d.action)
        assertEquals(DecisionReason.UNREGISTERED_RULE, d.reason)
    }

    @Test
    fun unregisteredRuleDoesNotAffectContacts() {
        val cfg = config(global = Mode.AVAILABLE, unregistered = LevelRule(true, Mode.BUSY))
        val d = decide(call(friend, ContactStatus.IN_CONTACTS), cfg)
        assertEquals(CallAction.ALLOW, d.action)
        assertEquals(DecisionReason.GLOBAL_MODE, d.reason)
    }

    @Test
    fun disabledUnregisteredRuleFallsThroughToGlobal() {
        val cfg = config(global = Mode.SILENT, unregistered = LevelRule(false, Mode.BUSY))
        val d = decide(call(stranger, ContactStatus.NOT_IN_CONTACTS), cfg)
        assertEquals(Mode.SILENT, d.mode)
        assertEquals(DecisionReason.GLOBAL_MODE, d.reason)
    }

    @Test
    fun unknownContactStatusNeverCountsAsUnregistered() {
        // e.g. READ_CONTACTS denied: we must not treat every caller as a stranger.
        val cfg = config(global = Mode.AVAILABLE, unregistered = LevelRule(true, Mode.BUSY))
        val d = decide(call(stranger, ContactStatus.UNKNOWN), cfg)
        assertEquals(CallAction.ALLOW, d.action)
        assertEquals(DecisionReason.GLOBAL_MODE, d.reason)
    }

    // ---- hidden ----

    @Test
    fun hiddenRuleAppliesToHiddenCalls() {
        val cfg = config(hidden = LevelRule(true, Mode.BUSY))
        val d = decide(call(number = null, contact = ContactStatus.UNKNOWN, hidden = true), cfg)
        assertEquals(CallAction.REJECT, d.action)
        assertEquals(DecisionReason.HIDDEN_RULE, d.reason)
    }

    @Test
    fun hiddenCallIsNotTreatedAsUnregistered() {
        // Both levels enabled with different modes: a hidden call must use the HIDDEN one.
        val cfg = config(
            unregistered = LevelRule(true, Mode.BUSY),
            hidden = LevelRule(true, Mode.SILENT),
        )
        val d = decide(call(number = null, contact = ContactStatus.NOT_IN_CONTACTS, hidden = true), cfg)
        assertEquals(Mode.SILENT, d.mode)
        assertEquals(DecisionReason.HIDDEN_RULE, d.reason)
    }

    @Test
    fun hiddenCallWithHiddenRuleDisabledGoesToGlobalNotUnregistered() {
        val cfg = config(
            global = Mode.AVAILABLE,
            unregistered = LevelRule(true, Mode.BUSY),
            hidden = LevelRule(false, Mode.BUSY),
        )
        val d = decide(call(number = null, contact = ContactStatus.NOT_IN_CONTACTS, hidden = true), cfg)
        assertEquals(Mode.AVAILABLE, d.mode)
        assertEquals(DecisionReason.GLOBAL_MODE, d.reason)
    }

    @Test
    fun blankNumberIsTreatedAsHiddenEvenWithoutTheFlag() {
        val cfg = config(hidden = LevelRule(true, Mode.BUSY))
        assertEquals(DecisionReason.HIDDEN_RULE, decide(call(number = null, hidden = false), cfg).reason)
        assertEquals(DecisionReason.HIDDEN_RULE, decide(call(number = "  ", hidden = false), cfg).reason)
    }

    @Test
    fun nonHiddenCallIgnoresHiddenRule() {
        val cfg = config(global = Mode.AVAILABLE, hidden = LevelRule(true, Mode.BUSY))
        assertEquals(DecisionReason.GLOBAL_MODE, decide(call(friend), cfg).reason)
    }

    // ---- priority conflicts ----

    @Test
    fun numberRuleBeatsUnregisteredRule() {
        val cfg = config(
            unregistered = LevelRule(true, Mode.BUSY),
            rules = listOf(SpecialRule(stranger, Mode.AVAILABLE)),
        )
        val d = decide(call(stranger, ContactStatus.NOT_IN_CONTACTS), cfg)
        assertEquals(Mode.AVAILABLE, d.mode)
        assertEquals(DecisionReason.NUMBER_RULE, d.reason)
    }

    @Test
    fun unregisteredRuleBeatsGlobalMode() {
        val cfg = config(global = Mode.BUSY, unregistered = LevelRule(true, Mode.AVAILABLE))
        val d = decide(call(stranger, ContactStatus.NOT_IN_CONTACTS), cfg)
        assertEquals(Mode.AVAILABLE, d.mode)
        assertEquals(DecisionReason.UNREGISTERED_RULE, d.reason)
    }

    @Test
    fun hiddenRuleBeatsGlobalMode() {
        val cfg = config(global = Mode.AVAILABLE, hidden = LevelRule(true, Mode.UNAVAILABLE))
        val d = decide(call(number = null, hidden = true), cfg)
        assertEquals(Mode.UNAVAILABLE, d.mode)
    }

    // ---- control switch and global mode changes ----

    @Test
    fun disablingControlAllowsEverythingButKeepsSettings() {
        val cfg = config(
            global = Mode.BUSY,
            unregistered = LevelRule(true, Mode.BUSY),
            hidden = LevelRule(true, Mode.BUSY),
            rules = listOf(SpecialRule(friend, Mode.BUSY)),
            enabled = false,
        )
        assertEquals(DecisionReason.CONTROL_DISABLED, decide(call(friend), cfg).reason)
        assertEquals(CallAction.ALLOW, decide(call(friend), cfg).action)
        assertEquals(CallAction.ALLOW, decide(call(null, ContactStatus.UNKNOWN, hidden = true), cfg).action)
        // nothing was thrown away: re-enabling restores the previous behaviour
        assertEquals(CallAction.REJECT, decide(call(friend), cfg.copy(controlEnabled = true)).action)
    }

    @Test
    fun changingGlobalModeChangesTheDecision() {
        val base = config(global = Mode.AVAILABLE)
        val c = call(stranger, ContactStatus.IN_CONTACTS)
        assertEquals(CallAction.ALLOW, decide(c, base).action)
        assertEquals(CallAction.REJECT, decide(c, base.copy(globalMode = Mode.BUSY)).action)
        assertEquals(CallAction.REJECT, decide(c, base.copy(globalMode = Mode.UNAVAILABLE)).action)
        assertEquals(CallAction.SILENCE, decide(c, base.copy(globalMode = Mode.SILENT)).action)
    }

    // ---- schedule integration ----

    private val workHours = Schedule(
        enabled = true,
        entries = listOf(ScheduleEntry(DayOfWeek.values().toSet(), LocalTime.of(8, 0), LocalTime.of(14, 0), Mode.BUSY)),
        defaultMode = Mode.SILENT,
    )

    @Test
    fun scheduleDrivesTheGlobalMode() {
        val cfg = config(global = Mode.AVAILABLE, schedule = workHours)
        val inside = decide(call(), cfg, LocalDateTime.of(2026, 11, 21, 9, 0))
        assertEquals(Mode.BUSY, inside.mode)
        assertEquals(DecisionReason.GLOBAL_SCHEDULE, inside.reason)
        val outside = decide(call(), cfg, LocalDateTime.of(2026, 11, 21, 20, 0))
        assertEquals(Mode.SILENT, outside.mode)
        assertEquals(DecisionReason.GLOBAL_SCHEDULE, outside.reason)
    }

    @Test
    fun scheduleNeverOverridesNumberUnregisteredOrHiddenRules() {
        val at = LocalDateTime.of(2026, 11, 21, 9, 0) // schedule says BUSY
        val cfg = config(
            schedule = workHours,
            unregistered = LevelRule(true, Mode.AVAILABLE),
            hidden = LevelRule(true, Mode.AVAILABLE),
            rules = listOf(SpecialRule(friend, Mode.AVAILABLE)),
        )
        assertEquals(DecisionReason.NUMBER_RULE, decide(call(friend), cfg, at).reason)
        assertEquals(DecisionReason.UNREGISTERED_RULE, decide(call(stranger, ContactStatus.NOT_IN_CONTACTS), cfg, at).reason)
        assertEquals(DecisionReason.HIDDEN_RULE, decide(call(null, ContactStatus.UNKNOWN, hidden = true), cfg, at).reason)
    }

    @Test
    fun disabledScheduleUsesTheManualGlobalMode() {
        val cfg = config(global = Mode.UNAVAILABLE, schedule = workHours.copy(enabled = false))
        val d = decide(call(), cfg, LocalDateTime.of(2026, 11, 21, 9, 0))
        assertEquals(Mode.UNAVAILABLE, d.mode)
        assertEquals(DecisionReason.GLOBAL_MODE, d.reason)
    }

    // ---- failures (fail-open) ----

    @Test
    fun databaseFailureAllowsTheCall() {
        val d = RuleEngine.decideSafely(call(), now) { throw IllegalStateException("database is corrupted") }
        assertEquals(CallAction.ALLOW, d.action)
        assertEquals(DecisionReason.INTERNAL_ERROR, d.reason)
        assertNotNull(d.errorMessage)
        assertEquals(true, d.errorMessage!!.contains("database is corrupted"))
    }

    @Test
    fun failureInsideDecisionLogicAlsoAllowsTheCall() {
        // a provider that returns normally but a null-unsafe platform value would be a bug elsewhere;
        // here we verify any RuntimeException from the provider path is contained.
        val d = RuleEngine.decideSafely(call(), now) { throw UnsupportedOperationException("migration missing") }
        assertEquals(CallAction.ALLOW, d.action)
    }

    @Test
    fun decideSafelyBehavesLikeDecideWhenNothingFails() {
        val cfg = config(rules = listOf(SpecialRule(friend, Mode.BUSY)))
        assertEquals(decide(call(), cfg), RuleEngine.decideSafely(call(), now) { cfg })
    }

    // ---- consecutive calls ----

    @Test
    fun twoConsecutiveCallsAreDecidedIndependently() {
        val cfg = config(global = Mode.AVAILABLE, rules = listOf(SpecialRule(friend, Mode.BUSY)))
        val first = decide(call(friend), cfg)
        val second = decide(call(stranger), cfg)
        assertEquals(CallAction.REJECT, first.action)
        assertEquals(CallAction.ALLOW, second.action)
        // and repeating the first call gives the identical answer (no hidden state)
        assertEquals(first, decide(call(friend), cfg))
    }

    @Test
    fun settingsChangedBetweenTwoCallsApplyToTheSecondCall() {
        val before = config(global = Mode.AVAILABLE)
        val after = before.copy(globalMode = Mode.BUSY)
        assertEquals(CallAction.ALLOW, decide(call(), before).action)
        assertEquals(CallAction.REJECT, decide(call(), after).action)
    }
}
