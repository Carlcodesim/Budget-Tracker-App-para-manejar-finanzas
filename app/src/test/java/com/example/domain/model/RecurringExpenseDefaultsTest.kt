package com.example.domain.model

import com.example.data.local.entity.RecurringExpenseEntity
import org.junit.Assert.assertFalse
import org.junit.Test

class RecurringExpenseDefaultsTest {

    @Test
    fun newDomainRecurringExpenseDoesNotAutoCreateByDefault() {
        val expense = RecurringExpense(
            title = "Internet",
            amount = 1200.0,
            category = Category(name = "Servicios", iconKey = "category"),
            dayOfMonth = 15
        )

        assertFalse(expense.autoCreate)
    }

    @Test
    fun newDatabaseEntityDoesNotAutoCreateByDefault() {
        val expense = RecurringExpenseEntity(
            title = "Internet",
            amount = 1200.0,
            categoryId = 1L,
            dayOfMonth = 15
        )

        assertFalse(expense.autoCreate)
    }
}
