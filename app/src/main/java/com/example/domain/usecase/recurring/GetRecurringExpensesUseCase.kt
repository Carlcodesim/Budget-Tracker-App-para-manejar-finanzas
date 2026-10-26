package com.example.domain.usecase.recurring

import com.example.domain.model.RecurringExpense
import com.example.domain.repository.RecurringExpenseRepository
import kotlinx.coroutines.flow.Flow

/**
 * Caso de uso para obtener la lista reactiva de gastos recurrentes.
 */
class GetRecurringExpensesUseCase(
    private val repository: RecurringExpenseRepository
) {
    operator fun invoke(): Flow<List<RecurringExpense>> {
        return repository.getAllRecurringExpenses()
    }
}
