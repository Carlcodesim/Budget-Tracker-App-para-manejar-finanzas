package com.example.presentation.recurring

import com.example.domain.model.Account
import com.example.domain.model.Category
import com.example.domain.model.RecurringExpense

enum class RecurringFilter {
    ALL,
    PENDING_THIS_MONTH,
    EXECUTED_THIS_MONTH,
    PAUSED
}

/**
 * Estado UI reactivo para la pantalla del módulo de Gastos Recurrentes.
 */
data class RecurringExpensesUiState(
    val isLoading: Boolean = true,
    val recurringExpenses: List<RecurringExpense> = emptyList(),
    val filteredExpenses: List<RecurringExpense> = emptyList(),
    val categories: List<Category> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val totalMonthlyCommitted: Double = 0.0,
    val totalPaidThisMonth: Double = 0.0,
    val totalPendingThisMonth: Double = 0.0,
    val executedCountThisMonth: Int = 0,
    val pendingCountThisMonth: Int = 0,
    val nextDueExpense: RecurringExpense? = null,
    val daysUntilNextDue: Int? = null,
    val currentYearMonth: String = "",
    val currentDayOfMonth: Int = 1,
    val selectedFilter: RecurringFilter = RecurringFilter.ALL,
    val isAutoProcessing: Boolean = false,
    val snackbarMessage: String? = null,
    val editingExpense: RecurringExpense? = null,
    val isFormOpen: Boolean = false
)
