package com.example.domain.usecase.goal

import com.example.domain.model.SavingsGoal
import com.example.domain.repository.SavingsGoalRepository

/**
 * Caso de uso para eliminar una meta de ahorro existente.
 */
class DeleteSavingsGoalUseCase(
    private val savingsGoalRepository: SavingsGoalRepository
) {
    suspend operator fun invoke(savingsGoal: SavingsGoal) {
        savingsGoalRepository.deleteSavingsGoal(savingsGoal)
    }
}
