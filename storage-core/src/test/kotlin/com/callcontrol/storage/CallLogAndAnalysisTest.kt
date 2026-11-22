package com.callcontrol.storage

import com.callcontrol.domain.ContactStatus
import com.callcontrol.domain.DecisionReason
import com.callcontrol.domain.Mode
import com.callcontrol.domain.ScheduleEntry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.DayOfWeek
import java.time.LocalTime

class CallLogAndAnalysisTest {

    private fun entry(n: Int) = CallLogEntry(
        id = 0, timestampMillis = n.toLong(), numberKey = "+2010000000$n", hidden = false,
        contactStatus = ContactStatus.IN_CONTACTS, mode = Mode.BUSY, reason = DecisionReason.NUMBER_RULE, errorType = null,
    )

    // ---- call log through the repository ----

    @Test
    fun loggedEntriesComeBackNewestFirst() = runBlocking<Unit> {
        val repo = Fixture().repository()
        repo.recordCall(entry(1)).getOrThrow()
        repo.recordCall(entry(2)).getOrThrow()
        val log = repo.observeCallLog(10).first().getOrThrow()
        assertEquals(listOf(2L, 1L), log.map { it.timestampMillis })
    }

    @Test
    fun logIsTrimmedToTheMaximumKeepingTheNewest() = runBlocking<Unit> {
        val f = Fixture()
        val repo = f.repository()
        for (n in 1..MAX_LOG_ENTRIES + 25) repo.recordCall(entry(n)).getOrThrow()
        val all = f.log.getRecent(10_000)
        assertEquals(MAX_LOG_ENTRIES, all.size)
        assertEquals((MAX_LOG_ENTRIES + 25).toLong(), all.first().timestampMillis)
        assertEquals(26L, all.last().timestampMillis)
    }

    @Test
    fun clearRemovesEveryEntryAndOnlyTheLog() = runBlocking<Unit> {
        val f = Fixture()
        val repo = f.repository()
        repo.addNumberRule("01012345678", Mode.BUSY).getOrThrow()
        repo.recordCall(entry(1)).getOrThrow()
        repo.clearCallLog().getOrThrow()
        assertEquals(0, f.log.getRecent(10).size)
        assertEquals(1, f.rules.getAll().size)
    }

    @Test
    fun logFailureOnClearIsReportedAndKeepsData() = runBlocking<Unit> {
        val f = Fixture()
        val repo = f.repository()
        repo.recordCall(entry(1)).getOrThrow()
        f.log.failure = IOException("boom")
        assertEquals(RepoResult.Failure(RepoError.Storage("IOException")), repo.clearCallLog())
        f.log.failure = null
        assertEquals(1, f.log.getRecent(10).size)
    }

    @Test
    fun withoutALogStoreNothingIsRecordedAndNothingFails() = runBlocking<Unit> {
        val f = Fixture()
        val repo = CallControlRepository(f.rules, f.schedule, f.settings)
        assertEquals(RepoResult.Success(true), repo.recordCall(entry(1)))
        assertEquals(emptyList<CallLogEntry>(), repo.observeCallLog().first().getOrThrow())
    }

    // ---- schedule overlap analysis ----

    private val allDays = DayOfWeek.values().toSet()
    private fun t(h: Int, m: Int = 0) = LocalTime.of(h, m)
    private fun e(days: Set<DayOfWeek>, s: LocalTime, en: LocalTime, enabled: Boolean = true) =
        ScheduleEntry(days, s, en, Mode.BUSY, enabled)

    @Test
    fun theSpecExampleHasNoOverlap() {
        val entries = listOf(e(allDays, t(8), t(14)), e(allDays, t(14), t(22)))
        assertEquals(emptyList<Pair<Int, Int>>(), ScheduleAnalysis.findOverlaps(entries))
    }

    @Test
    fun overlappingWindowsAreReported() {
        val entries = listOf(e(allDays, t(8), t(12)), e(allDays, t(11), t(15)), e(allDays, t(20), t(21)))
        assertEquals(listOf(0 to 1), ScheduleAnalysis.findOverlaps(entries))
    }

    @Test
    fun differentDaysDoNotOverlap() {
        val entries = listOf(e(setOf(DayOfWeek.MONDAY), t(8), t(12)), e(setOf(DayOfWeek.TUESDAY), t(8), t(12)))
        assertTrue(ScheduleAnalysis.findOverlaps(entries).isEmpty())
    }

    @Test
    fun overnightWindowOverlapsTheNextMorning() {
        val entries = listOf(e(setOf(DayOfWeek.FRIDAY), t(22), t(6)), e(setOf(DayOfWeek.SATURDAY), t(5), t(9)))
        assertEquals(listOf(0 to 1), ScheduleAnalysis.findOverlaps(entries))
    }

    @Test
    fun overnightWindowOnSundayWrapsToMonday() {
        val entries = listOf(e(setOf(DayOfWeek.SUNDAY), t(23), t(2)), e(setOf(DayOfWeek.MONDAY), t(1), t(3)))
        assertEquals(listOf(0 to 1), ScheduleAnalysis.findOverlaps(entries))
    }

    @Test
    fun touchingWindowsDoNotOverlapAndDisabledOnesAreIgnored() {
        assertTrue(ScheduleAnalysis.findOverlaps(listOf(e(allDays, t(8), t(10)), e(allDays, t(10), t(12)))).isEmpty())
        assertTrue(ScheduleAnalysis.findOverlaps(listOf(e(allDays, t(8), t(12)), e(allDays, t(9), t(10), enabled = false))).isEmpty())
    }

    @Test
    fun endAtMidnightCoversTheEveningOnly() {
        val entries = listOf(e(allDays, t(22), t(0)), e(allDays, t(0), t(6)))
        assertTrue(ScheduleAnalysis.findOverlaps(entries).isEmpty())
    }
}
