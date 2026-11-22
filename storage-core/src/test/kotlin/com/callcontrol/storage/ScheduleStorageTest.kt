package com.callcontrol.storage

import com.callcontrol.domain.DecisionReason
import com.callcontrol.domain.Mode
import com.callcontrol.domain.ModeSource
import com.callcontrol.domain.RuleEngine
import com.callcontrol.domain.ScheduleEntry
import com.callcontrol.domain.ScheduleResolver
import com.callcontrol.domain.CallContext
import com.callcontrol.domain.ContactStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime

class ScheduleStorageTest {

    private val allDays = DayOfWeek.values().toSet()
    private fun t(h: Int, m: Int = 0) = LocalTime.of(h, m)

    private val morning = ScheduleEntry(setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY), t(8), t(14), Mode.BUSY)
    private val evening = ScheduleEntry(allDays, t(14), t(22), Mode.AVAILABLE)
    private val overnight = ScheduleEntry(setOf(DayOfWeek.FRIDAY), t(22), t(6), Mode.SILENT)

    private fun errorOf(r: RepoResult<*>): RepoError {
        assertTrue("expected Failure but was $r", r is RepoResult.Failure)
        return (r as RepoResult.Failure).error
    }

    private fun ids(f: Fixture): List<Long> = runBlocking { f.schedule.getAll().map { it.id } }

    // ---- save and restore ----

    @Test
    fun savedEntryKeepsDaysStartEndModeAndEnabledFlag() = runBlocking<Unit> {
        val f = Fixture()
        val entry = morning.copy(enabled = false)
        f.repository().addScheduleEntry(entry).getOrThrow()

        val restored = f.repository().loadRuleConfig().getOrThrow().schedule.entries.single()

        assertEquals(setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY), restored.days)
        assertEquals(t(8), restored.start)
        assertEquals(t(14), restored.end)
        assertEquals(Mode.BUSY, restored.mode)
        assertEquals(false, restored.enabled)
    }

    @Test
    fun entriesKeepTheirOrder() = runBlocking<Unit> {
        val f = Fixture()
        val repo = f.repository()
        repo.addScheduleEntry(evening).getOrThrow()
        repo.addScheduleEntry(morning).getOrThrow()
        repo.addScheduleEntry(overnight).getOrThrow()

        val order = repo.loadRuleConfig().getOrThrow().schedule.entries
        assertEquals(listOf(evening, morning, overnight), order)
    }

    // ---- update / delete ----

    @Test
    fun updateChangesTheEntryAndKeepsItsPosition() = runBlocking<Unit> {
        val f = Fixture()
        val repo = f.repository()
        val a = repo.addScheduleEntry(morning).getOrThrow()
        repo.addScheduleEntry(evening).getOrThrow()

        val changed = morning.copy(start = t(9), end = t(15), mode = Mode.SILENT, days = setOf(DayOfWeek.MONDAY))
        repo.updateScheduleEntry(a.id, changed).getOrThrow()

        assertEquals(listOf(changed, evening), f.schedule.getAll().map { it.entry })
    }

    @Test
    fun updateOrDeleteOfMissingEntryIsNotFound() = runBlocking<Unit> {
        val repo = Fixture().repository()
        assertEquals(RepoError.NotFound, errorOf(repo.updateScheduleEntry(42, morning)))
        assertEquals(RepoError.NotFound, errorOf(repo.deleteScheduleEntry(42)))
    }

    @Test
    fun deleteRemovesOnlyThatEntryAndKeepsTheOrderOfTheRest() = runBlocking<Unit> {
        val f = Fixture()
        val repo = f.repository()
        repo.addScheduleEntry(morning).getOrThrow()
        val middle = repo.addScheduleEntry(evening).getOrThrow()
        repo.addScheduleEntry(overnight).getOrThrow()

        repo.deleteScheduleEntry(middle.id).getOrThrow()

        assertEquals(listOf(morning, overnight), f.schedule.getAll().map { it.entry })
    }

    // ---- validation happens before any write ----

    @Test
    fun invalidEntriesAreRejectedWithoutTouchingStorage() = runBlocking<Unit> {
        val f = Fixture()
        val repo = f.repository()
        val saved = repo.addScheduleEntry(morning).getOrThrow()
        val writesBefore = f.schedule.writeCount

        assertEquals(
            RepoError.InvalidSchedule(ScheduleProblem.NO_DAYS),
            errorOf(repo.addScheduleEntry(morning.copy(days = emptySet()))),
        )
        assertEquals(
            RepoError.InvalidSchedule(ScheduleProblem.EMPTY_INTERVAL),
            errorOf(repo.addScheduleEntry(morning.copy(end = morning.start))),
        )
        assertEquals(
            RepoError.InvalidSchedule(ScheduleProblem.UNSUPPORTED_TIME_PRECISION),
            errorOf(repo.addScheduleEntry(morning.copy(start = LocalTime.of(8, 0, 0, 1)))),
        )
        assertEquals(
            RepoError.InvalidSchedule(ScheduleProblem.NO_DAYS),
            errorOf(repo.updateScheduleEntry(saved.id, morning.copy(days = emptySet()))),
        )

        assertEquals(writesBefore, f.schedule.writeCount)
        assertEquals(listOf(morning), f.schedule.getAll().map { it.entry })
    }

    // ---- reorder ----

    @Test
    fun reorderChangesTheEvaluationOrder() = runBlocking<Unit> {
        val f = Fixture()
        val repo = f.repository()
        repo.addScheduleEntry(morning).getOrThrow()
        repo.addScheduleEntry(evening).getOrThrow()
        repo.addScheduleEntry(overnight).getOrThrow()

        repo.reorderScheduleEntries(ids(f).reversed()).getOrThrow()

        assertEquals(listOf(overnight, evening, morning), f.schedule.getAll().map { it.entry })
    }

    @Test
    fun invalidReorderIsRejectedAndKeepsTheCurrentOrder() = runBlocking<Unit> {
        val f = Fixture()
        val repo = f.repository()
        repo.addScheduleEntry(morning).getOrThrow()
        repo.addScheduleEntry(evening).getOrThrow()
        val original = ids(f)

        assertEquals(RepoError.InvalidOrder, errorOf(repo.reorderScheduleEntries(listOf(original[0]))))                  // missing id
        assertEquals(RepoError.InvalidOrder, errorOf(repo.reorderScheduleEntries(original + 99L)))                       // unknown id
        assertEquals(RepoError.InvalidOrder, errorOf(repo.reorderScheduleEntries(listOf(original[0], original[0]))))     // duplicate id
        assertEquals(RepoError.InvalidOrder, errorOf(repo.reorderScheduleEntries(emptyList())))

        assertEquals(original, ids(f))
    }

    // ---- integration with the Phase 1 ScheduleResolver ----

    @Test
    fun overnightEntryRoundTripsAndStillResolvesAcrossMidnight() = runBlocking<Unit> {
        val f = Fixture()
        f.repository().addScheduleEntry(overnight).getOrThrow()
        f.repository().updateSettings { it.copy(scheduleEnabled = true) }.getOrThrow()

        val schedule = f.repository().loadRuleConfig().getOrThrow().schedule
        val saturday3am = LocalDateTime.of(2026, 11, 21, 3, 0) // belongs to Friday's window
        val saturday11pm = LocalDateTime.of(2026, 11, 21, 23, 0)

        assertEquals(Mode.SILENT, ScheduleResolver.resolve(schedule, saturday3am, Mode.AVAILABLE).mode)
        assertEquals(ModeSource.MANUAL, ScheduleResolver.resolve(schedule, saturday11pm, Mode.AVAILABLE).source)
    }

    @Test
    fun specExampleScheduleSurvivesARestartAndDrivesTheEngine() = runBlocking<Unit> {
        val f = Fixture()
        val repo = f.repository()
        repo.addScheduleEntry(ScheduleEntry(allDays, t(8), t(14), Mode.BUSY)).getOrThrow()
        repo.addScheduleEntry(ScheduleEntry(allDays, t(14), t(22), Mode.AVAILABLE)).getOrThrow()
        repo.updateSettings { it.copy(scheduleEnabled = true, scheduleDefaultMode = Mode.SILENT) }.getOrThrow()

        val config = f.repository().loadRuleConfig().getOrThrow() // "restart": a brand-new repository
        val call = CallContext("+201112345678", isHidden = false, contactStatus = ContactStatus.IN_CONTACTS)
        fun modeAt(h: Int) = RuleEngine.decide(call, config, LocalDateTime.of(2026, 11, 21, h, 0))

        assertEquals(Mode.BUSY, modeAt(9).mode)
        assertEquals(Mode.AVAILABLE, modeAt(15).mode)
        assertEquals(Mode.SILENT, modeAt(23).mode)
        assertEquals(DecisionReason.GLOBAL_SCHEDULE, modeAt(23).reason)
    }

    // ---- failures ----

    @Test
    fun failedScheduleWritesLeaveStoredDataUnchanged() = runBlocking<Unit> {
        val f = Fixture()
        val repo = f.repository()
        val a = repo.addScheduleEntry(morning).getOrThrow()
        repo.addScheduleEntry(evening).getOrThrow()
        val before = f.schedule.getAll()

        f.failEverything(IOException("disk full"))
        assertEquals(RepoError.Storage("IOException"), errorOf(repo.addScheduleEntry(overnight)))
        assertTrue(repo.updateScheduleEntry(a.id, overnight) is RepoResult.Failure)
        assertTrue(repo.deleteScheduleEntry(a.id) is RepoResult.Failure)
        assertTrue(repo.reorderScheduleEntries(before.map { it.id }.reversed()) is RepoResult.Failure)

        f.failEverything(null)
        assertEquals(before, f.schedule.getAll())
    }
}
