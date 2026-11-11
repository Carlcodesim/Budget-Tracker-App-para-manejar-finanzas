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

    @Test
    fun consecutiveDay30CyclesHaveNoGapsOrOverlaps() {
        val november = BudgetCycleManager.getCycleRange(2026, Calendar.NOVEMBER, 30)
        val october = BudgetCycleManager.getPreviousCycleRange(november, 30)
        val december = BudgetCycleManager.getCycleRange(2026, Calendar.DECEMBER, 30)

        assertEquals("30 Sep - 29 Oct", october.dateRangeLabel)
        assertEquals("30 Oct - 29 Nov", november.dateRangeLabel)
        assertEquals("30 Nov - 29 Dic", december.dateRangeLabel)
        assertEquals(october.endDateMillis + 1L, november.startDateMillis)
        assertEquals(november.endDateMillis + 1L, december.startDateMillis)
    }

    @Test
    fun consecutiveDay30CyclesRemainContiguousAcrossYearBoundary() {
        val january = BudgetCycleManager.getCycleRange(2027, Calendar.JANUARY, 30)
        val december = BudgetCycleManager.getPreviousCycleRange(january, 30)

        assertEquals("30 Dic - 29 Ene", january.dateRangeLabel)
        assertEquals("30 Nov - 29 Dic", december.dateRangeLabel)
        assertEquals(december.endDateMillis + 1L, january.startDateMillis)
    }

    @Test
    fun consecutiveDay30CyclesRemainContiguousAcrossFebruaryInLeapYear() {
        val march = BudgetCycleManager.getCycleRange(2028, Calendar.MARCH, 30)
        val february = BudgetCycleManager.getPreviousCycleRange(march, 30)

        assertEquals("29 Feb - 29 Mar", march.dateRangeLabel)
        assertEquals("30 Ene - 28 Feb", february.dateRangeLabel)
        assertEquals(february.endDateMillis + 1L, march.startDateMillis)
    }

    @Test
    fun consecutiveDay31CyclesRemainContiguousAcrossThirtyDayMonth() {
        val may = BudgetCycleManager.getCycleRange(2027, Calendar.MAY, 31)
        val april = BudgetCycleManager.getPreviousCycleRange(may, 31)

        assertEquals("30 Abr - 30 May", may.dateRangeLabel)
        assertEquals("31 Mar - 29 Abr", april.dateRangeLabel)
        assertEquals(april.endDateMillis + 1L, may.startDateMillis)
    }

}
