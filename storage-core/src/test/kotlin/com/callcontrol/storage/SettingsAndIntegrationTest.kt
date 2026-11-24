package com.callcontrol.storage

import com.callcontrol.domain.CallAction
import com.callcontrol.domain.CallContext
import com.callcontrol.domain.ContactStatus
import com.callcontrol.domain.DecisionReason
import com.callcontrol.domain.LevelRule
import com.callcontrol.domain.Mode
import com.callcontrol.domain.RuleConfig
import com.callcontrol.domain.RuleEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.LocalDateTime

class SettingsAndIntegrationTest {

    private val now = LocalDateTime.of(2026, 11, 21, 10, 0)
    private val friend = "+201012345678"
    private val stranger = "+201112345678"

    private fun errorOf(r: RepoResult<*>): RepoError {
        assertTrue("expected Failure but was $r", r is RepoResult.Failure)
        return (r as RepoResult.Failure).error
    }

    // ---- settings ----

    @Test
    fun freshInstallHasSafeDefaults() = runBlocking<Unit> {
        val s = Fixture().repository().loadSettings().getOrThrow()
        assertEquals(AppSettings(), s)
        assertTrue(s.controlEnabled)
        assertEquals(Mode.BUSY, s.globalMode)
        assertFalse(s.unregistered.enabled)
        assertFalse(s.hidden.enabled)
        assertFalse(s.scheduleEnabled)
    }

    @Test
    fun savedSettingsAreRestoredByANewRepository() = runBlocking<Unit> {
        val f = Fixture()
        val wanted = AppSettings(
            controlEnabled = false,
            globalMode = Mode.BUSY,
            unregistered = LevelRule(true, Mode.SILENT),
            hidden = LevelRule(true, Mode.UNAVAILABLE),
            scheduleEnabled = true,
            scheduleDefaultMode = Mode.SILENT,
            themeMode = ThemeMode.DARK,
            onboardingCompleted = true,
        )
        f.repository().updateSettings { wanted }.getOrThrow()

        assertEquals(wanted, f.repository().loadSettings().getOrThrow())
    }

    @Test
    fun aThrowingTransformStoresNothing() = runBlocking<Unit> {
        val f = Fixture()
        val repo = f.repository()
        repo.updateSettings { it.copy(globalMode = Mode.BUSY) }.getOrThrow()

        val result = repo.updateSettings { throw IllegalStateException("bad transform") }

        assertEquals(RepoError.Storage("IllegalStateException"), errorOf(result))
        assertEquals(Mode.BUSY, repo.loadSettings().getOrThrow().globalMode)
    }

    @Test
    fun settingsStoreFailureIsReportedAndDataSurvives() = runBlocking<Unit> {
        val f = Fixture()
        val repo = f.repository()
        repo.updateSettings { it.copy(themeMode = ThemeMode.LIGHT) }.getOrThrow()

        f.settings.failure = IOException("datastore unavailable")
        assertEquals(RepoError.Storage("IOException"), errorOf(repo.updateSettings { it.copy(themeMode = ThemeMode.DARK) }))
        assertEquals(RepoError.Storage("IOException"), errorOf(repo.loadSettings()))

        f.settings.failure = null
        assertEquals(ThemeMode.LIGHT, repo.loadSettings().getOrThrow().themeMode)
    }

    @Test
    fun observeSettingsReflectsChanges() = runBlocking<Unit> {
        val repo = Fixture().repository()
        assertEquals(Mode.BUSY, repo.observeSettings().first().getOrThrow().globalMode)
        repo.updateSettings { it.copy(globalMode = Mode.SILENT) }.getOrThrow()
        assertEquals(Mode.SILENT, repo.observeSettings().first().getOrThrow().globalMode)
    }

    // ---- config assembly ----

    @Test
    fun buildRuleConfigMapsEveryField() {
        val rule = StoredNumberRule(7, friend, Mode.BUSY, enabled = false, name = "A", notes = null, createdAtMillis = 1, updatedAtMillis = 2)
        val settings = AppSettings(
            controlEnabled = false,
            globalMode = Mode.SILENT,
            unregistered = LevelRule(true, Mode.BUSY),
            hidden = LevelRule(true, Mode.UNAVAILABLE),
            scheduleEnabled = true,
            scheduleDefaultMode = Mode.BUSY,
        )

        val config = buildRuleConfig(listOf(rule), emptyList(), settings)

        assertFalse(config.controlEnabled)
        assertEquals(Mode.SILENT, config.globalMode)
        assertEquals(LevelRule(true, Mode.BUSY), config.unregistered)
        assertEquals(LevelRule(true, Mode.UNAVAILABLE), config.hidden)
        assertTrue(config.schedule.enabled)
        assertEquals(Mode.BUSY, config.schedule.defaultMode)
        assertEquals(setOf(friend), config.numberRules.keys)
        assertFalse(config.numberRules.getValue(friend).enabled)
        assertEquals(RuleConfig.DEFAULT, buildRuleConfig(emptyList(), emptyList(), AppSettings()).copy(schedule = RuleConfig.DEFAULT.schedule))
    }

    @Test
    fun observeRuleConfigEmitsAgainAfterEveryKindOfChange() = runBlocking<Unit> {
        val f = Fixture()
        val repo = f.repository()
        assertTrue(repo.observeRuleConfig().first().getOrThrow().numberRules.isEmpty())

        repo.addNumberRule(friend, Mode.BUSY).getOrThrow()
        assertEquals(setOf(friend), repo.observeRuleConfig().first().getOrThrow().numberRules.keys)

        repo.updateSettings { it.copy(globalMode = Mode.SILENT) }.getOrThrow()
        assertEquals(Mode.SILENT, repo.observeRuleConfig().first().getOrThrow().globalMode)
    }

    @Test
    fun observeRuleConfigReportsAStorageFailureInsteadOfThrowing() = runBlocking<Unit> {
        val f = Fixture()
        f.rules.failure = IllegalStateException("corrupt")
        assertEquals(RepoError.Storage("IllegalStateException"), errorOf(f.repository().observeRuleConfig().first()))
    }

    // ---- end to end with the Phase 1 engine ----

    @Test
    fun storedConfigProducesTheExpectedEngineDecisions() = runBlocking<Unit> {
        val f = Fixture()
        val repo = f.repository()
        repo.addNumberRule("01012345678", Mode.BUSY).getOrThrow()
        repo.updateSettings {
            it.copy(
                globalMode = Mode.AVAILABLE,
                unregistered = LevelRule(true, Mode.SILENT),
                hidden = LevelRule(true, Mode.UNAVAILABLE),
            )
        }.getOrThrow()

        val config = f.repository().loadRuleConfig().getOrThrow() // after a "restart"
        fun decide(c: CallContext) = RuleEngine.decide(c, config, now)

        val friendCall = CallContext(friend, false, ContactStatus.NOT_IN_CONTACTS)
        val strangerCall = CallContext(stranger, false, ContactStatus.NOT_IN_CONTACTS)
        val contactCall = CallContext(stranger, false, ContactStatus.IN_CONTACTS)
        val hiddenCall = CallContext(null, true, ContactStatus.UNKNOWN)

        assertEquals(DecisionReason.NUMBER_RULE, decide(friendCall).reason)
        assertEquals(CallAction.REJECT, decide(friendCall).action)
        assertEquals(DecisionReason.UNREGISTERED_RULE, decide(strangerCall).reason)
        assertEquals(CallAction.SILENCE, decide(strangerCall).action)
        assertEquals(DecisionReason.GLOBAL_MODE, decide(contactCall).reason)
        assertEquals(DecisionReason.HIDDEN_RULE, decide(hiddenCall).reason)
    }

    @Test
    fun disablingControlInSettingsAllowsEverythingEvenWithRulesStored() = runBlocking<Unit> {
        val f = Fixture()
        val repo = f.repository()
        repo.addNumberRule(friend, Mode.BUSY).getOrThrow()
        repo.updateSettings { it.copy(controlEnabled = false) }.getOrThrow()

        val d = RuleEngine.decide(CallContext(friend, false, ContactStatus.IN_CONTACTS), repo.loadRuleConfig().getOrThrow(), now)
        assertEquals(DecisionReason.CONTROL_DISABLED, d.reason)
        assertEquals(1, f.rules.getAll().size) // nothing was deleted
    }

    @Test
    fun storageFailureMakesTheEngineFailOpenWithoutLeakingTheNumber() = runBlocking<Unit> {
        val f = Fixture()
        val repo = f.repository()
        repo.addNumberRule(friend, Mode.BUSY).getOrThrow()
        f.failEverything(IOException("cannot read +201012345678"))

        val call = CallContext(friend, false, ContactStatus.IN_CONTACTS)
        val d = RuleEngine.decideSafely(call, now) { runBlocking { repo.loadRuleConfig() }.getOrThrow() }

        assertEquals(CallAction.ALLOW, d.action)
        assertEquals(DecisionReason.INTERNAL_ERROR, d.reason)
        assertFalse(d.errorMessage!!.contains("1012345678"))
    }

    // ---- cancellation ----

    @Test
    fun coroutineCancellationIsNeverSwallowed() = runBlocking<Unit> {
        val f = Fixture()
        f.rules.failure = CancellationException("cancelled")

        var rethrown = false
        try {
            f.repository().loadRuleConfig()
        } catch (e: CancellationException) {
            rethrown = true
        }
        assertTrue(rethrown)
    }
}
