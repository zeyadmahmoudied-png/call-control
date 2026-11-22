package com.callcontrol.storage

import com.callcontrol.domain.ScheduleEntry

/** Helpers for showing schedule problems to the user. The engine itself is unchanged (first match wins). */
object ScheduleAnalysis {
    private const val MINUTES_PER_DAY = 24 * 60
    private const val MINUTES_PER_WEEK = 7 * MINUTES_PER_DAY

    /**
     * Index pairs (i < j) of ENABLED entries that cover at least one common minute of the week.
     * Because the first matching entry wins, entry `i` hides the overlapping part of entry `j`.
     */
    fun findOverlaps(entries: List<ScheduleEntry>): List<Pair<Int, Int>> {
        val coverage = entries.map { if (it.enabled) coverage(it) else null }
        val result = ArrayList<Pair<Int, Int>>()
        for (i in entries.indices) {
            val a = coverage[i] ?: continue
            for (j in i + 1 until entries.size) {
                val b = coverage[j] ?: continue
                if (shareMinute(a, b)) result += i to j
            }
        }
        return result
    }

    private fun shareMinute(a: BooleanArray, b: BooleanArray): Boolean {
        for (k in 0 until MINUTES_PER_WEEK) if (a[k] && b[k]) return true
        return false
    }

    private fun coverage(e: ScheduleEntry): BooleanArray {
        val covered = BooleanArray(MINUTES_PER_WEEK)
        val start = e.start.toSecondOfDay() / 60
        val end = e.end.toSecondOfDay() / 60
        for (day in e.days) {
            val d = day.value - 1 // Monday = 0 ... Sunday = 6
            if (start < end) {
                for (m in start until end) covered[d * MINUTES_PER_DAY + m] = true
            } else if (start > end) {
                // crosses midnight: the part after midnight belongs to the next day
                for (m in start until MINUTES_PER_DAY) covered[d * MINUTES_PER_DAY + m] = true
                val next = (d + 1) % 7
                for (m in 0 until end) covered[next * MINUTES_PER_DAY + m] = true
            }
        }
        return covered
    }
}
