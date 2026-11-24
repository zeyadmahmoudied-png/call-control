package com.callcontrol.storage

import com.callcontrol.domain.CallAction
import com.callcontrol.domain.ContactStatus
import com.callcontrol.domain.DecisionReason
import com.callcontrol.domain.LevelRule
import com.callcontrol.domain.Mode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.LocalDateTime

class ScreeningProcessorTest {

    private val at = LocalDateTime.of(2026, 11, 21, 10, 0)
    private val friend = "+201012345678"

    private fun processor(
        f: Fixture,
        contacts: ContactLookup = ContactLookup { ContactStatus.IN_CONTACTS },
        lookupTimeout: Long = 1_500,
        configTimeout: Long = 2_500,
    ) = ScreeningProcessor(f.repository(), contacts, { at }, { 5_000L }, lookupTimeout, configTimeout)

    private fun call(raw: String?, restricted: Boolean = false) = IncomingCall(raw, restricted)

    // ---- the four states through the full pipeline ----

    @Test
    fun numberRuleBusyRejects() = runBlocking<Unit> {
        val f = Fixture()
        f.repository().addNumberRule("01012345678", Mode.BUSY).getOrThrow()

        val r = processor(f).decide(call("+201012345678"))

        assertEquals(CallAction.REJECT, r.decision.action)
        assertEquals(DecisionReason.NUMBER_RULE, r.decision.reason)
        assertEquals(friend, r.numberKey)
    }

    @Test
    fun numberRuleMatchesTheNumberInAnyEgyptianFormat() = runBlocking<Unit> {
        val f = Fixture()
        f.repository().addNumberRule("+201012345678", Mode.BUSY).getOrThrow()
        val p = processor(f)
        for (incoming in listOf("01012345678", "+201012345678", "00201012345678", "010 1234 5678")) {
            assertEquals(incoming, DecisionReason.NUMBER_RULE, p.decide(call(incoming)).decision.reason)
        }
    }

    @Test
    fun numberRuleSilentSilences() = runBlocking<Unit> {
        val f = Fixture()
        f.repository().updateSettings { it.copy(globalMode = Mode.AVAILABLE) }.getOrThrow()
        f.repository().addNumberRule(friend, Mode.SILENT).getOrThrow()
        assertEquals(CallAction.SILENCE, processor(f).decide(call(friend)).decision.action)
    }

    @Test
    fun noRulesAndDefaultSettingsRejectTheCall() = runBlocking<Unit> {
        val r = processor(Fixture()).decide(call("+201112345678"))
        assertEquals(CallAction.REJECT, r.decision.action)
        assertEquals(DecisionReason.GLOBAL_MODE, r.decision.reason)
    }

    @Test
    fun globalUnavailableRejectsLikeBusy() = runBlocking<Unit> {
        val f = Fixture()
        f.repository().updateSettings { it.copy(globalMode = Mode.UNAVAILABLE) }.getOrThrow()
        assertEquals(CallAction.REJECT, processor(f).decide(call("+201112345678")).decision.action)
    }

    @Test
    fun controlSwitchedOffAllowsEverything() = runBlocking<Unit> {
        val f = Fixture()
        f.repository().addNumberRule(friend, Mode.BUSY).getOrThrow()
        f.repository().updateSettings { it.copy(controlEnabled = false) }.getOrThrow()
        val r = processor(f).decide(call(friend))
        assertEquals(CallAction.ALLOW, r.decision.action)
        assertEquals(DecisionReason.CONTROL_DISABLED, r.decision.reason)
    }

    // ---- unregistered vs hidden ----

    @Test
    fun unregisteredRuleAppliesOnlyWhenTheNumberIsNotInContacts() = runBlocking<Unit> {
        val f = Fixture()
        f.repository().updateSettings { it.copy(globalMode = Mode.AVAILABLE, unregistered = LevelRule(true, Mode.BUSY)) }.getOrThrow()

        val stranger = processor(f, ContactLookup { ContactStatus.NOT_IN_CONTACTS }).decide(call("+201112345678"))
        val contact = processor(f, ContactLookup { ContactStatus.IN_CONTACTS }).decide(call("+201112345678"))

        assertEquals(DecisionReason.UNREGISTERED_RULE, stranger.decision.reason)
        assertEquals(CallAction.REJECT, stranger.decision.action)
        assertEquals(DecisionReason.GLOBAL_MODE, contact.decision.reason)
    }

    @Test
    fun hiddenCallUsesTheHiddenRuleNeverTheUnregisteredOne() = runBlocking<Unit> {
        val f = Fixture()
        f.repository().updateSettings {
            it.copy(globalMode = Mode.AVAILABLE, unregistered = LevelRule(true, Mode.BUSY), hidden = LevelRule(true, Mode.SILENT))
        }.getOrThrow()
        var lookups = 0
        val p = processor(f, ContactLookup { lookups++; ContactStatus.NOT_IN_CONTACTS })

        val noNumber = p.decide(call(null))
        val restricted = p.decide(call("+201112345678", restricted = true))

        assertEquals(DecisionReason.HIDDEN_RULE, noNumber.decision.reason)
        assertEquals(CallAction.SILENCE, noNumber.decision.action)
        assertEquals(DecisionReason.HIDDEN_RULE, restricted.decision.reason)
        assertEquals("contacts must not even be queried for hidden callers", 0, lookups)
    }

    @Test
    fun restrictedCallIgnoresAPerNumberRuleForItsNumber() = runBlocking<Unit> {
        val f = Fixture()
        f.repository().addNumberRule(friend, Mode.BUSY).getOrThrow()
        val r = processor(f).decide(call(friend, restricted = true))
        assertTrue(r.hidden)
        assertEquals(DecisionReason.GLOBAL_MODE, r.decision.reason)
    }

    @Test
    fun blankNumberCountsAsHidden() = runBlocking<Unit> {
        val f = Fixture()
        f.repository().updateSettings { it.copy(globalMode = Mode.AVAILABLE, hidden = LevelRule(true, Mode.BUSY)) }.getOrThrow()
        assertEquals(DecisionReason.HIDDEN_RULE, processor(f).decide(call("")).decision.reason)
        assertEquals(DecisionReason.HIDDEN_RULE, processor(f).decide(call("   ")).decision.reason)
    }

    // ---- contacts failures never turn into "unregistered" ----

    @Test
    fun contactsPermissionDeniedDoesNotTriggerTheUnregisteredRule() = runBlocking<Unit> {
        val f = Fixture()
        f.repository().updateSettings { it.copy(globalMode = Mode.AVAILABLE, unregistered = LevelRule(true, Mode.BUSY)) }.getOrThrow()
        val r = processor(f, ContactLookup { throw SecurityException("READ_CONTACTS denied") }).decide(call("+201112345678"))
        assertEquals(ContactStatus.UNKNOWN, r.contactStatus)
        assertEquals(CallAction.ALLOW, r.decision.action)
    }

    @Test
    fun slowContactsLookupTimesOutAsUnknown() = runBlocking<Unit> {
        val f = Fixture()
        f.repository().updateSettings { it.copy(globalMode = Mode.AVAILABLE, unregistered = LevelRule(true, Mode.BUSY)) }.getOrThrow()
        val slow = ContactLookup { delay(5_000); ContactStatus.NOT_IN_CONTACTS }
        val started = System.currentTimeMillis()

        val r = processor(f, slow, lookupTimeout = 50).decide(call("+201112345678"))

        assertEquals(ContactStatus.UNKNOWN, r.contactStatus)
        assertEquals(CallAction.ALLOW, r.decision.action)
        assertTrue("must not wait for the slow lookup", System.currentTimeMillis() - started < 3_000)
    }

    // ---- storage failures fail open ----

    @Test
    fun databaseFailureAllowsTheCall() = runBlocking<Unit> {
        val f = Fixture()
        f.repository().addNumberRule(friend, Mode.BUSY).getOrThrow()
        f.rules.failure = IllegalStateException("database is corrupted")

        val r = processor(f).decide(call(friend))

        assertEquals(CallAction.ALLOW, r.decision.action)
        assertEquals(DecisionReason.INTERNAL_ERROR, r.decision.reason)
    }

    @Test
    fun settingsFailureAlsoAllowsTheCall() = runBlocking<Unit> {
        val f = Fixture()
        f.settings.failure = IOException("datastore unavailable")
        val r = processor(f).decide(call(friend))
        assertEquals(CallAction.ALLOW, r.decision.action)
        assertEquals(DecisionReason.INTERNAL_ERROR, r.decision.reason)
    }

    @Test
    fun hungStorageTimesOutAndAllowsTheCall() = runBlocking<Unit> {
        val f = Fixture()
        f.repository().addNumberRule(friend, Mode.BUSY).getOrThrow()
        val hung = object : NumberRuleStore by f.rules {
            override suspend fun getAll(): List<StoredNumberRule> {
                delay(10_000)
                return emptyList()
            }
        }
        val repo = CallControlRepository(hung, f.schedule, f.settings, f.log)
        val started = System.currentTimeMillis()

        val r = ScreeningProcessor(repo, ContactLookup { ContactStatus.IN_CONTACTS }, { at }, { 1L }, 1_500, 100).decide(call(friend))

        assertEquals(CallAction.ALLOW, r.decision.action)
        assertEquals(DecisionReason.INTERNAL_ERROR, r.decision.reason)
        assertTrue(System.currentTimeMillis() - started < 3_000)
    }

    @Test
    fun errorInformationNeverContainsThePhoneNumber() = runBlocking<Unit> {
        val f = Fixture()
        f.rules.failure = IOException("cannot read row for +201012345678")
        val r = processor(f).decide(call(friend))
        assertFalse(r.decision.errorMessage.orEmpty().contains("1012345678"))
        val logged = processor(f).also { f.rules.failure = null }.record(r)
        assertEquals(RepoResult.Success(true), logged)
        assertEquals("IllegalStateException", f.log.getRecent(1).single().errorType)
    }

    @Test
    fun cancellationIsPropagatedNotSwallowed() = runBlocking<Unit> {
        val f = Fixture()
        val p = processor(f, ContactLookup { throw CancellationException("cancelled") })
        var propagated = false
        try {
            p.decide(call("+201112345678"))
        } catch (e: CancellationException) {
            propagated = true
        }
        assertTrue(propagated)
    }

    // ---- two calls in a row / settings changing between calls ----

    @Test
    fun twoConsecutiveCallsAreDecidedIndependently() = runBlocking<Unit> {
        val f = Fixture()
        f.repository().updateSettings { it.copy(globalMode = Mode.AVAILABLE) }.getOrThrow()
        f.repository().addNumberRule("01012345678", Mode.BUSY).getOrThrow()
        f.repository().addNumberRule("01112345678", Mode.SILENT).getOrThrow()
        val p = processor(f)

        val first = p.decide(call("01012345678"))
        val second = p.decide(call("01112345678"))
        val third = p.decide(call("01212345678"))

        assertEquals(CallAction.REJECT, first.decision.action)
        assertEquals(CallAction.SILENCE, second.decision.action)
        assertEquals(CallAction.ALLOW, third.decision.action)
    }

    @Test
    fun concurrentCallsDoNotInterfere() = runBlocking<Unit> {
        val f = Fixture()
        f.repository().updateSettings { it.copy(globalMode = Mode.AVAILABLE) }.getOrThrow()
        f.repository().addNumberRule("01012345678", Mode.BUSY).getOrThrow()
        val p = processor(f)
        val results = (1..20).map { i ->
            async(Dispatchers.Default) {
                p.decide(call(if (i % 2 == 0) "01012345678" else "01112345678"))
            }
        }.awaitAll()
        results.forEachIndexed { index, r ->
            val expected = if ((index + 1) % 2 == 0) CallAction.REJECT else CallAction.ALLOW
            assertEquals(expected, r.decision.action)
        }
    }

    @Test
    fun aRuleChangeBetweenTwoCallsAppliesToTheSecondCall() = runBlocking<Unit> {
        val f = Fixture()
        val repo = f.repository()
        repo.updateSettings { it.copy(globalMode = Mode.AVAILABLE) }.getOrThrow()
        val p = processor(f)
        assertEquals(CallAction.ALLOW, p.decide(call(friend)).decision.action)

        val rule = repo.addNumberRule(friend, Mode.BUSY).getOrThrow()
        assertEquals(CallAction.REJECT, p.decide(call(friend)).decision.action)

        repo.setNumberRuleEnabled(rule.id, false).getOrThrow()
        assertEquals(CallAction.ALLOW, p.decide(call(friend)).decision.action)
    }

    @Test
    fun serviceDisabledMeansNothingIsDecidedByTheProcessorButFailOpenResultAllows() {
        val r = ScreeningProcessor.failOpen("OutOfMemoryError")
        assertEquals(CallAction.ALLOW, r.decision.action)
        assertEquals(DecisionReason.INTERNAL_ERROR, r.decision.reason)
    }

    // ---- logging ----

    @Test
    fun recordedEntryKeepsTheDecisionAndTheNumberForKnownCallers() = runBlocking<Unit> {
        val f = Fixture()
        f.repository().addNumberRule(friend, Mode.BUSY).getOrThrow()
        val p = processor(f)

        assertEquals(RepoResult.Success(true), p.record(p.decide(call(friend))))

        val e = f.log.getRecent(10).single()
        assertEquals(friend, e.numberKey)
        assertEquals(Mode.BUSY, e.mode)
        assertEquals(DecisionReason.NUMBER_RULE, e.reason)
        assertEquals(5_000L, e.timestampMillis)
        assertNull(e.errorType)
    }

    @Test
    fun hiddenCallersAreLoggedWithoutANumber() = runBlocking<Unit> {
        val f = Fixture()
        val p = processor(f)
        p.record(p.decide(call(friend, restricted = true)))
        val e = f.log.getRecent(1).single()
        assertNull(e.numberKey)
        assertTrue(e.hidden)
    }

    @Test
    fun deliveryErrorIsRecordedAsAClassNameOnly() = runBlocking<Unit> {
        val f = Fixture()
        val p = processor(f)
        p.record(p.decide(call(friend)), deliveryError = "IllegalStateException")
        assertEquals("IllegalStateException", f.log.getRecent(1).single().errorType)
    }

    @Test
    fun nothingIsLoggedWhenLoggingIsSwitchedOff() = runBlocking<Unit> {
        val f = Fixture()
        f.repository().updateSettings { it.copy(logEnabled = false) }.getOrThrow()
        val p = processor(f)
        assertEquals(RepoResult.Success(false), p.record(p.decide(call(friend))))
        assertEquals(0, f.log.getRecent(10).size)
    }

    @Test
    fun logFailureIsReportedNotThrown() = runBlocking<Unit> {
        val f = Fixture()
        val p = processor(f)
        val decided = p.decide(call(friend))
        f.log.failure = IOException("disk full")
        assertEquals(RepoResult.Failure(RepoError.Storage("IOException")), p.record(decided))
    }
}
