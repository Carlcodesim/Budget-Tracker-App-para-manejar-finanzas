package com.example.domain.usecase.recurring

import com.example.domain.model.RecurringExpense
import com.example.domain.repository.RecurringExpenseRepository

/**
 * Caso de uso para actualizar un gasto recurrente existente.
 */
class UpdateRecurringExpenseUseCase(
    private val repository: RecurringExpenseRepository
) {
    suspend operator fun invoke(recurringExpense: RecurringExpense) {
        require(recurringExpense.title.isNotBlank()) { "El título del gasto recurrente no puede estar vacío" }
        require(recurringExpense.amount > 0) { "El monto del gasto debe ser mayor que 0" }
        require(recurringExpense.dayOfMonth in 1..31) { "El día del mes debe estar entre 1 y 31" }
        repository.updateRecurringExpense(recurringExpense)
    }
}
