package com.example.domain.usecase.recurring

import com.example.domain.repository.RecurringExpenseRepository

/**
 * Caso de uso para eliminar un gasto recurrente.
 */
class DeleteRecurringExpenseUseCase(
    private val repository: RecurringExpenseRepository
) {
    suspend operator fun invoke(id: Long) {
        repository.deleteRecurringExpense(id)
    }
}
