package com.callcontrol.storage

import com.callcontrol.domain.CallContext
import com.callcontrol.domain.ContactStatus
import com.callcontrol.domain.DecisionReason
import com.callcontrol.domain.InvalidReason
import com.callcontrol.domain.Mode
import com.callcontrol.domain.RuleEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.LocalDateTime

class RuleStorageTest {

    private val friend = "+201012345678"
    private val now = LocalDateTime.of(2026, 11, 21, 10, 0)

    private fun errorOf(r: RepoResult<*>): RepoError {
        assertTrue("expected Failure but was $r", r is RepoResult.Failure)
        return (r as RepoResult.Failure).error
    }

    // ---- add and retrieve ----

    @Test
    fun addedRuleIsStoredNormalizedAndRetrievable() = runBlocking<Unit> {
        val f = Fixture()
        val added = f.repository().addNumberRule("010 1234 5678", Mode.BUSY, name = "  Ahmed ", notes = " work ").getOrThrow()

        assertEquals(friend, added.numberKey)
        assertEquals(Mode.BUSY, added.mode)
        assertTrue(added.enabled)
        assertEquals("Ahmed", added.name)
        assertEquals("work", added.notes)
        assertEquals(1_000L, added.createdAtMillis)
        assertEquals(listOf(added), f.rules.getAll())

        val config = f.repository().loadRuleConfig().getOrThrow()
        assertEquals(Mode.BUSY, config.numberRules.getValue(friend).mode)
    }

    @Test
    fun blankNameAndNotesAreStoredAsNull() = runBlocking<Unit> {
        val added = Fixture().repository().addNumberRule(friend, Mode.SILENT, name = "   ", notes = "").getOrThrow()
        assertNull(added.name)
        assertNull(added.notes)
    }

    @Test
    fun sameNumberInDifferentFormatsIsRejectedAsDuplicate() = runBlocking<Unit> {
        val f = Fixture()
        val repo = f.repository()
        repo.addNumberRule("01012345678", Mode.BUSY).getOrThrow()

        for (variant in listOf("+201012345678", "00201012345678", "010-1234-5678", "٠١٠١٢٣٤٥٦٧٨")) {
            assertEquals(RepoError.DuplicateNumber, errorOf(repo.addNumberRule(variant, Mode.AVAILABLE)))
        }
        val all = f.rules.getAll()
        assertEquals(1, all.size)
        assertEquals(Mode.BUSY, all.single().mode) // the original was not overwritten
    }

    @Test
    fun invalidNumberIsRejectedWithoutWriting() = runBlocking<Unit> {
        val f = Fixture()
        val error = errorOf(f.repository().addNumberRule("abc", Mode.BUSY))
        assertEquals(RepoError.InvalidNumber(InvalidReason.ILLEGAL_CHARACTERS), error)
        assertEquals(0, f.rules.writeCount)
    }

    // ---- update ----

    @Test
    fun updateChangesFieldsKeepsCreationTimeAndBumpsUpdateTime() = runBlocking<Unit> {
        val f = Fixture()
        val repo = f.repository()
        val added = repo.addNumberRule(friend, Mode.BUSY, name = "A").getOrThrow()

        f.nowMillis = 2_000L
        val updated = repo.updateNumberRule(added.id, friend, Mode.SILENT, enabled = true, name = "B", notes = "n").getOrThrow()

        assertEquals(Mode.SILENT, updated.mode)
        assertEquals("B", updated.name)
        assertEquals(1_000L, updated.createdAtMillis)
        assertEquals(2_000L, updated.updatedAtMillis)
        assertEquals(updated, f.rules.get(added.id))
    }

    @Test
    fun updateCanChangeTheNumber() = runBlocking<Unit> {
        val f = Fixture()
        val repo = f.repository()
        val added = repo.addNumberRule(friend, Mode.BUSY).getOrThrow()

        repo.updateNumberRule(added.id, "01112345678", Mode.BUSY, true, null, null).getOrThrow()

        val keys = repo.loadRuleConfig().getOrThrow().numberRules.keys
        assertEquals(setOf("+201112345678"), keys)
    }

    @Test
    fun updateToAnExistingNumberIsRejectedAndBothRulesStayIntact() = runBlocking<Unit> {
        val f = Fixture()
        val repo = f.repository()
        val a = repo.addNumberRule("01012345678", Mode.BUSY).getOrThrow()
        val b = repo.addNumberRule("01112345678", Mode.SILENT).getOrThrow()

        val error = errorOf(repo.updateNumberRule(b.id, "01012345678", Mode.AVAILABLE, true, null, null))

        assertEquals(RepoError.DuplicateNumber, error)
        assertEquals(a, f.rules.get(a.id))
        assertEquals(b, f.rules.get(b.id))
    }

    @Test
    fun updateOfMissingRuleOrWithInvalidNumberChangesNothing() = runBlocking<Unit> {
        val f = Fixture()
        val repo = f.repository()
        val added = repo.addNumberRule(friend, Mode.BUSY).getOrThrow()

        assertEquals(RepoError.NotFound, errorOf(repo.updateNumberRule(999, friend, Mode.AVAILABLE, true, null, null)))
        assertEquals(
            RepoError.InvalidNumber(InvalidReason.INVALID_EGYPT_NUMBER),
            errorOf(repo.updateNumberRule(added.id, "010123", Mode.AVAILABLE, true, null, null)),
        )
        assertEquals(added, f.rules.get(added.id))
    }

    // ---- enable / disable ----

    @Test
    fun disablingKeepsTheRuleStoredButInactiveInTheEngine() = runBlocking<Unit> {
        val f = Fixture()
        val repo = f.repository()
        val added = repo.addNumberRule(friend, Mode.BUSY).getOrThrow()
        val call = CallContext(friend, isHidden = false, contactStatus = ContactStatus.IN_CONTACTS)

        repo.setNumberRuleEnabled(added.id, false).getOrThrow()
        val disabledConfig = repo.loadRuleConfig().getOrThrow()
        assertFalse(f.rules.get(added.id)!!.enabled)
        assertEquals(1, disabledConfig.numberRules.size) // still stored
        assertEquals(DecisionReason.GLOBAL_MODE, RuleEngine.decide(call, disabledConfig, now).reason)

        repo.setNumberRuleEnabled(added.id, true).getOrThrow()
        val enabledConfig = repo.loadRuleConfig().getOrThrow()
        assertEquals(DecisionReason.NUMBER_RULE, RuleEngine.decide(call, enabledConfig, now).reason)
    }

    @Test
    fun enablingAMissingRuleIsNotFound() = runBlocking<Unit> {
        assertEquals(RepoError.NotFound, errorOf(Fixture().repository().setNumberRuleEnabled(5, true)))
    }

    // ---- delete ----

    @Test
    fun deleteRemovesOnlyThatRule() = runBlocking<Unit> {
        val f = Fixture()
        val repo = f.repository()
        val a = repo.addNumberRule("01012345678", Mode.BUSY).getOrThrow()
        val b = repo.addNumberRule("01112345678", Mode.SILENT).getOrThrow()

        repo.deleteNumberRule(a.id).getOrThrow()

        assertEquals(listOf(b), f.rules.getAll())
        assertEquals(RepoError.NotFound, errorOf(repo.deleteNumberRule(a.id)))
    }

    @Test
    fun aDeletedNumberCanBeAddedAgain() = runBlocking<Unit> {
        val repo = Fixture().repository()
        val a = repo.addNumberRule(friend, Mode.BUSY).getOrThrow()
        repo.deleteNumberRule(a.id).getOrThrow()
        repo.addNumberRule(friend, Mode.SILENT).getOrThrow()
    }

    // ---- restart ----

    @Test
    fun aNewRepositoryOverTheSameStoresSeesAllRules() = runBlocking<Unit> {
        val f = Fixture()
        f.repository().addNumberRule("01012345678", Mode.BUSY, name = "A").getOrThrow()
        f.repository().addNumberRule("01112345678", Mode.SILENT, enabled = false).getOrThrow()

        val afterRestart = f.repository().loadRuleConfig().getOrThrow()

        assertEquals(Mode.BUSY, afterRestart.numberRules.getValue("+201012345678").mode)
        assertEquals(false, afterRestart.numberRules.getValue("+201112345678").enabled)
    }

    // ---- failures ----

    @Test
    fun writeFailureIsReportedWithoutLeakingTheNumberAndStoresNothing() = runBlocking<Unit> {
        val f = Fixture()
        val repo = f.repository()
        f.failEverything(IOException("disk error while saving +201012345678"))

        val error = errorOf(repo.addNumberRule(friend, Mode.BUSY))

        assertEquals(RepoError.Storage("IOException"), error)
        assertFalse("error must not contain the number", error.toString().contains("1012345678"))

        f.failEverything(null)
        assertEquals(emptyList<StoredNumberRule>(), f.rules.getAll())
        repo.addNumberRule(friend, Mode.BUSY).getOrThrow() // the repository recovers by itself
    }

    @Test
    fun failedUpdateAndDeleteLeaveExistingDataUnchanged() = runBlocking<Unit> {
        val f = Fixture()
        val repo = f.repository()
        val added = repo.addNumberRule(friend, Mode.BUSY, name = "A").getOrThrow()

        f.failEverything(IOException("boom"))
        assertTrue(repo.updateNumberRule(added.id, friend, Mode.SILENT, true, "B", null) is RepoResult.Failure)
        assertTrue(repo.setNumberRuleEnabled(added.id, false) is RepoResult.Failure)
        assertTrue(repo.deleteNumberRule(added.id) is RepoResult.Failure)

        f.failEverything(null)
        assertEquals(added, f.rules.get(added.id))
    }

    @Test
    fun readFailureMakesLoadRuleConfigFail() = runBlocking<Unit> {
        val f = Fixture()
        f.repository().addNumberRule(friend, Mode.BUSY).getOrThrow()
        f.rules.failure = IllegalStateException("database is corrupted")

        assertEquals(RepoError.Storage("IllegalStateException"), errorOf(f.repository().loadRuleConfig()))
    }
}
