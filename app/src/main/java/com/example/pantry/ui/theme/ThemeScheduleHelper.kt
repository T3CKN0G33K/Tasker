package com.example.pantry.ui.theme

import java.time.LocalDate
import java.time.Month

object ThemeScheduleHelper {

    /**
     * Checks today's calendar date and returns the appropriate holiday theme name.
     * Returns null if no holiday window is active.
     */
    fun getSeasonalThemeForDate(date: LocalDate = LocalDate.now()): String? {
        val month = date.month
        val day = date.dayOfMonth

        return when {
            // Halloween: All of October
            month == Month.OCTOBER -> "halloween"

            // Thanksgiving: November 15 - November 30
            month == Month.NOVEMBER && day >= 15 -> "thanksgiving"

            // Christmas: December 1 - December 26
            month == Month.DECEMBER && day in 1..26 -> "christmas"

            // New Year: December 27 - January 7
            (month == Month.DECEMBER && day >= 27) || (month == Month.JANUARY && day <= 7) -> "newyear"

            else -> null
        }
    }
}
