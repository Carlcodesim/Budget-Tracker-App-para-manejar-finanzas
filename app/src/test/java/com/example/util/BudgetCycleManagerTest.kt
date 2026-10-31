package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class BudgetCycleManagerTest {

    private fun dateAtNoon(year: Int, month: Int, day: Int): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month, day, 12, 0, 0)
        }.timeInMillis

    private fun startOfDay(year: Int, month: Int, day: Int): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month, day, 0, 0, 0)
        }.timeInMillis

    private fun endOfDay(year: Int, month: Int, day: Int): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month, day, 23, 59, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis

    @Test
    fun day30CycleDoesNotOverflowWhenCurrentDateIsOctober31() {
        val now = dateAtNoon(2026, Calendar.OCTOBER, 31)

        val range = BudgetCycleManager.getCurrentCycleRange(
            startDay = 30,
            nowMillis = now
        )

        assertEquals("30 Oct - 29 Nov", range.dateRangeLabel)
        assertEquals(startOfDay(2026, Calendar.OCTOBER, 30), range.startDateMillis)
        assertEquals(endOfDay(2026, Calendar.NOVEMBER, 29), range.endDateMillis)
        assertEquals(31, range.totalDaysInCycle)
        assertTrue(range.isCurrentCycle)
    }

    @Test
    fun day30CycleChangesOnNovember30WithoutOverlappingDates() {
        val now = dateAtNoon(2026, Calendar.NOVEMBER, 30)

        val range = BudgetCycleManager.getCurrentCycleRange(
            startDay = 30,
            nowMillis = now
        )

        assertEquals("30 Nov - 29 Dic", range.dateRangeLabel)
        assertEquals(startOfDay(2026, Calendar.NOVEMBER, 30), range.startDateMillis)
        assertEquals(endOfDay(2026, Calendar.DECEMBER, 29), range.endDateMillis)
        assertTrue(range.isCurrentCycle)
    }

    @Test
    fun day30CycleUsesLastDayAsBoundaryInFebruary() {
        val beforeCutoff = BudgetCycleManager.getCurrentCycleRange(
            startDay = 30,
            nowMillis = dateAtNoon(2027, Calendar.FEBRUARY, 27)
        )
        val onCutoff = BudgetCycleManager.getCurrentCycleRange(
            startDay = 30,
            nowMillis = dateAtNoon(2027, Calendar.FEBRUARY, 28)
        )

        assertEquals("30 Ene - 27 Feb", beforeCutoff.dateRangeLabel)
        assertEquals("28 Feb - 29 Mar", onCutoff.dateRangeLabel)
    }

    @Test
    fun day30CycleUsesFebruary29InLeapYear() {
        val range = BudgetCycleManager.getCurrentCycleRange(
            startDay = 30,
            nowMillis = dateAtNoon(2028, Calendar.FEBRUARY, 29)
        )

        assertEquals("29 Feb - 29 Mar", range.dateRangeLabel)
        assertEquals(startOfDay(2028, Calendar.FEBRUARY, 29), range.startDateMillis)
    }

    @Test
    fun day31CycleUsesApril30AsEffectiveCutoff() {
        val range = BudgetCycleManager.getCurrentCycleRange(
            startDay = 31,
            nowMillis = dateAtNoon(2027, Calendar.APRIL, 30)
        )

        assertEquals("30 Abr - 30 May", range.dateRangeLabel)
        assertEquals(startOfDay(2027, Calendar.APRIL, 30), range.startDateMillis)
    }
}
