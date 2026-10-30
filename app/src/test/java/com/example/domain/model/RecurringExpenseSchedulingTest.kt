package com.example.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
