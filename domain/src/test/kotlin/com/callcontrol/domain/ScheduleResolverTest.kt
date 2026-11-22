package com.callcontrol.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime

class ScheduleResolverTest {

    private val allDays = DayOfWeek.values().toSet()

    // 2026-11-20 = Friday, 2026-11-21 = Saturday, 2026-11-22 = Sunday
    private fun fri(h: Int, m: Int = 0) = LocalDateTime.of(2026, 11, 20, h, m)
    private fun sat(h: Int, m: Int = 0) = LocalDateTime.of(2026, 11, 21, h, m)
    private fun sun(h: Int, m: Int = 0) = LocalDateTime.of(2026, 11, 22, h, m)

    private fun t(h: Int, m: Int = 0) = LocalTime.of(h, m)

    /** The example from the spec: 08-14 busy, 14-22 available, rest of the day silent. */
    private val specExample = Schedule(
        enabled = true,
        entries = listOf(
            ScheduleEntry(allDays, t(8), t(14), Mode.BUSY),
            ScheduleEntry(allDays, t(14), t(22), Mode.AVAILABLE),
        ),
        defaultMode = Mode.SILENT,
    )

    private fun mode(s: Schedule, now: LocalDateTime, manual: Mode = Mode.UNAVAILABLE) =
        ScheduleResolver.resolve(s, now, manual).mode

    @Test
    fun specExampleThroughTheDay() {
        assertEquals(Mode.SILENT, mode(specExample, sat(0, 0)))
        assertEquals(Mode.SILENT, mode(specExample, sat(7, 59)))
        assertEquals(Mode.BUSY, mode(specExample, sat(8, 0)))
        assertEquals(Mode.BUSY, mode(specExample, sat(13, 59)))
        assertEquals(Mode.AVAILABLE, mode(specExample, sat(14, 0)))
        assertEquals(Mode.AVAILABLE, mode(specExample, sat(21, 59)))
        assertEquals(Mode.SILENT, mode(specExample, sat(22, 0)))
        assertEquals(Mode.SILENT, mode(specExample, sat(23, 59)))
    }

    @Test
    fun sourceIsReported() {
        assertEquals(ModeSource.SCHEDULE_ENTRY, ScheduleResolver.resolve(specExample, sat(9), Mode.AVAILABLE).source)
        assertEquals(ModeSource.SCHEDULE_DEFAULT, ScheduleResolver.resolve(specExample, sat(23), Mode.AVAILABLE).source)
        assertEquals(ModeSource.MANUAL, ScheduleResolver.resolve(Schedule.DISABLED, sat(9), Mode.AVAILABLE).source)
    }

    @Test
    fun disabledScheduleUsesManualMode() {
        val off = specExample.copy(enabled = false)
        assertEquals(Mode.UNAVAILABLE, mode(off, sat(9), manual = Mode.UNAVAILABLE))
    }

    @Test
    fun noDefaultModeFallsBackToManualOutsideWindows() {
        val s = specExample.copy(defaultMode = null)
        assertEquals(Mode.UNAVAILABLE, mode(s, sat(23), manual = Mode.UNAVAILABLE))
        assertEquals(Mode.BUSY, mode(s, sat(9), manual = Mode.UNAVAILABLE))
    }

    @Test
    fun disabledEntryIsIgnored() {
        val s = Schedule(
            enabled = true,
            entries = listOf(ScheduleEntry(allDays, t(8), t(14), Mode.BUSY, enabled = false)),
            defaultMode = null,
        )
        assertEquals(Mode.AVAILABLE, mode(s, sat(9), manual = Mode.AVAILABLE))
    }

    @Test
    fun dayFilterIsRespected() {
        val s = Schedule(true, listOf(ScheduleEntry(setOf(DayOfWeek.SATURDAY), t(8), t(14), Mode.BUSY)))
        assertEquals(Mode.BUSY, mode(s, sat(9)))
        assertEquals(Mode.UNAVAILABLE, mode(s, sun(9), manual = Mode.UNAVAILABLE))
    }

    // ---- crossing midnight ----

    @Test
    fun overnightWindowEveryDay() {
        val s = Schedule(true, listOf(ScheduleEntry(allDays, t(22), t(6), Mode.BUSY)))
        assertEquals(Mode.BUSY, mode(s, sat(23)))
        assertEquals(Mode.BUSY, mode(s, sun(3)))
        assertEquals(Mode.AVAILABLE, mode(s, sun(6), manual = Mode.AVAILABLE))     // end is exclusive
        assertEquals(Mode.AVAILABLE, mode(s, sat(21, 59), manual = Mode.AVAILABLE))
    }

    @Test
    fun overnightWindowBelongsToTheDayItStarted() {
        val s = Schedule(true, listOf(ScheduleEntry(setOf(DayOfWeek.FRIDAY), t(22), t(6), Mode.BUSY)))
        assertEquals(Mode.BUSY, mode(s, fri(23)))
        assertEquals(Mode.BUSY, mode(s, sat(3)))                                    // after midnight, still Friday's window
        assertEquals(Mode.AVAILABLE, mode(s, sat(23), manual = Mode.AVAILABLE))     // Saturday is not selected
        assertEquals(Mode.AVAILABLE, mode(s, fri(3), manual = Mode.AVAILABLE))     // Thursday night is not selected
    }

    @Test
    fun weekWrapSundayToMonday() {
        val s = Schedule(true, listOf(ScheduleEntry(setOf(DayOfWeek.SUNDAY), t(22), t(6), Mode.SILENT)))
        val mondayEarly = LocalDateTime.of(2026, 11, 23, 2, 0)
        assertEquals(Mode.SILENT, mode(s, mondayEarly))
    }

    @Test
    fun endOfMidnightMeansUntilTheEndOfTheDay() {
        val s = Schedule(true, listOf(ScheduleEntry(allDays, t(22), t(0), Mode.BUSY)))
        assertEquals(Mode.BUSY, mode(s, sat(23, 59)))
        assertEquals(Mode.AVAILABLE, mode(s, sun(0, 0), manual = Mode.AVAILABLE))
    }

    @Test
    fun emptyWindowNeverMatches() {
        val s = Schedule(true, listOf(ScheduleEntry(allDays, t(8), t(8), Mode.BUSY)))
        assertEquals(Mode.AVAILABLE, mode(s, sat(8), manual = Mode.AVAILABLE))
    }

    @Test
    fun overlappingWindowsFirstInListWins() {
        val s = Schedule(
            true,
            listOf(
                ScheduleEntry(allDays, t(8), t(12), Mode.SILENT),
                ScheduleEntry(allDays, t(10), t(14), Mode.BUSY),
            ),
        )
        assertEquals(Mode.SILENT, mode(s, sat(11)))
        assertEquals(Mode.BUSY, mode(s, sat(13)))
    }
}
