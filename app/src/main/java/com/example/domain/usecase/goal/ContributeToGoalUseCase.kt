package com.example.domain.usecase.goal

import com.example.domain.model.SavingsGoal
import com.example.domain.repository.SavingsGoalRepository

/**
 * Caso de uso para realizar un aporte de dinero a una meta de ahorro específica.
 */
class ContributeToGoalUseCase(
    private val savingsGoalRepository: SavingsGoalRepository
) {
    suspend operator fun invoke(goalId: Long, amount: Double) {
        val goal = savingsGoalRepository.getSavingsGoalById(goalId) ?: return
        val updatedGoal = goal.copy(currentAmount = goal.currentAmount + amount)
        savingsGoalRepository.updateSavingsGoal(updatedGoal)
    }
}
