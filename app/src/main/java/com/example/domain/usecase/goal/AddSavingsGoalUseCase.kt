package com.example.domain.usecase.goal

import com.example.domain.model.SavingsGoal
import com.example.domain.repository.SavingsGoalRepository

/**
 * Caso de uso para agregar una nueva meta de ahorro.
 */
class AddSavingsGoalUseCase(
    private val savingsGoalRepository: SavingsGoalRepository
) {
    suspend operator fun invoke(savingsGoal: SavingsGoal): Long {
        return savingsGoalRepository.insertSavingsGoal(savingsGoal)
    }
}
