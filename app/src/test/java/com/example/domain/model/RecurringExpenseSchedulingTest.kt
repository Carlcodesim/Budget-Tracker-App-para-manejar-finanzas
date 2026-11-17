package com.example.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class RecurringExpenseSchedulingTest {

    private fun expense(dayOfMonth: Int, autoCreate: Boolean = true) = RecurringExpense(
        title = "Servicio de prueba",
        amount = 100.0,
        category = Category(name = "Servicios", iconKey = "receipt", type = "EXPENSE"),
        dayOfMonth = dayOfMonth,
        autoCreate = autoCreate
    )

    @Test
    fun day31IsAdjustedToLastDayOfThirtyDayMonth() {
        assertEquals(30, expense(31).effectiveDayInMonth(30))
    }

    @Test
    fun day31IsAdjustedToLastDayOfFebruary() {
        assertEquals(28, expense(31).effectiveDayInMonth(28))
    }

    @Test
    fun day31UsesFebruary29InLeapYear() {
        assertEquals(29, expense(31).effectiveDayInMonth(29))
    }

    @Test
    fun adjustedDueDateCanBeReachedInShortMonth() {
        assertTrue(expense(31).isDueForExecution(28, "2026-02", 28))
    }

    @Test
    fun manualRecurringExpenseIsNeverDueForAutomaticExecution() {
        assertFalse(expense(15, autoCreate = false).isDueForExecution(30, "2026-10", 31))
    }
    private fun date(year: Int, month: Int, day: Int): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month - 1, day, 8, 0, 0)
        }.timeInMillis

    @Test
    fun weeklyFrequencyAdvancesSevenDays() {
        val start = date(2026, 10, 1)
        val next = calculateNextDueDate(start, RecurrenceFrequency.WEEKLY, start)
        assertEquals(8, Calendar.getInstance().apply { timeInMillis = next }.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun biweeklyFrequencyMeansEveryFifteenDays() {
        val start = date(2026, 10, 1)
        val next = calculateNextDueDate(start, RecurrenceFrequency.BIWEEKLY, start)
        assertEquals(16, Calendar.getInstance().apply { timeInMillis = next }.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun monthlyFrequencyKeepsDay31AfterFebruary() {
        val anchor = date(2026, 1, 31)
        val february = calculateNextDueDate(anchor, RecurrenceFrequency.MONTHLY, anchor)
        val march = calculateNextDueDate(february, RecurrenceFrequency.MONTHLY, anchor)
        assertEquals(28, Calendar.getInstance().apply { timeInMillis = february }.get(Calendar.DAY_OF_MONTH))
        assertEquals(31, Calendar.getInstance().apply { timeInMillis = march }.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun yearlyFrequencyPreservesLeapDayAnchor() {
        val anchor = date(2024, 2, 29)
        val next = calculateNextDueDate(anchor, RecurrenceFrequency.YEARLY, anchor)
        val calendar = Calendar.getInstance().apply { timeInMillis = next }
        assertEquals(2, calendar.get(Calendar.MONTH) + 1)
        assertEquals(28, calendar.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun frequenciesExposeMonthlyEquivalentAmounts() {
        val category = Category(name = "Servicios", iconKey = "receipt", type = "EXPENSE")
        val weekly = RecurringExpense(
            title = "Pago semanal",
            amount = 100.0,
            category = category,
            dayOfMonth = 1,
            frequency = RecurrenceFrequency.WEEKLY
        )
        val annual = weekly.copy(frequency = RecurrenceFrequency.YEARLY)
        assertEquals(100.0 * 52.0 / 12.0, weekly.estimatedMonthlyAmount, 0.001)
        assertEquals(100.0 / 12.0, annual.estimatedMonthlyAmount, 0.001)
    }

}
