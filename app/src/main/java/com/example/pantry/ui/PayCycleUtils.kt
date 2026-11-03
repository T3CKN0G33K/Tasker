package com.example.pantry.ui

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

data class PayCycleInfo(
    val cycleId: String,          // e.g. "PP_2026-09-21_2026-10-04"
    val startDateStr: String,     // e.g. "2026-09-21"
    val endDateStr: String,       // e.g. "2026-10-04"
    val displayRange: String      // e.g. "Sep 21, 2026 – Oct 04, 2026"
)

object PayCycleUtils {
    fun getCurrentPayCycle(nowDate: LocalDate, anchorPayday: LocalDate): PayCycleInfo {
        val daysUntilPayday = ChronoUnit.DAYS.between(nowDate, anchorPayday)
        val cycleOffset = Math.floorDiv(daysUntilPayday, 14L)

        val targetPayday = anchorPayday.minusDays(cycleOffset * 14L)
        val periodEnd = targetPayday.minusDays(5L)  // Sunday (5 days before payday Friday)
        val periodStart = periodEnd.minusDays(13L)  // Monday (14 days total)

        val isoFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US)
        val startDateIso = periodStart.format(isoFormatter)
        val endDateIso = periodEnd.format(isoFormatter)

        val displayFormatter = DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.US)
        val startDisplay = periodStart.format(displayFormatter)
        val endDisplay = periodEnd.format(displayFormatter)

        val cycleId = "PP_${startDateIso}_${endDateIso}"
        val displayRange = "$startDisplay – $endDisplay"

        return PayCycleInfo(
            cycleId = cycleId,
            startDateStr = startDateIso,
            endDateStr = endDateIso,
            displayRange = displayRange
        )
    }

    fun getPayCycleForDate(date: LocalDate, anchorPayday: LocalDate): PayCycleInfo =
        getCurrentPayCycle(date, anchorPayday)
}
