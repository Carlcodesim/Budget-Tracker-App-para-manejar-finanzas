package com.example.domain.usecase.goal

import com.example.domain.model.SavingsGoal
import com.example.domain.repository.SavingsGoalRepository

/**
 * Caso de uso para actualizar una meta de ahorro existente.
 */
class UpdateSavingsGoalUseCase(
    private val savingsGoalRepository: SavingsGoalRepository
) {
    suspend operator fun invoke(savingsGoal: SavingsGoal) {
        savingsGoalRepository.updateSavingsGoal(savingsGoal)
    }
}
