package com.example.domain.usecase.recurring

import com.example.domain.model.ProcessRecurringResult
import com.example.domain.repository.RecurringExpenseRepository

/**
 * Caso de uso para verificar y procesar automáticamente los gastos recurrentes
 * que corresponden al mes actual según su día de corte o ejecución programada.
 */
class ProcessRecurringExpensesUseCase(
    private val repository: RecurringExpenseRepository
) {
    suspend operator fun invoke(
        targetYearMonth: String? = null,
        forceAllDue: Boolean = false
    ): ProcessRecurringResult {
        return repository.processRecurringExpenses(targetYearMonth, forceAllDue)
    }

    suspend fun executeNow(recurringExpenseId: Long): Boolean {
        return repository.executeSingleRecurringExpenseNow(recurringExpenseId)
    }

    suspend fun toggleEnabled(recurringExpenseId: Long, isEnabled: Boolean) {
        repository.setEnabled(recurringExpenseId, isEnabled)
    }
}
