package com.example.domain.usecase.goal

import com.example.domain.model.SavingsGoal
import com.example.domain.repository.SavingsGoalRepository
import kotlinx.coroutines.flow.Flow

/**
 * Caso de uso para obtener todas las metas de ahorro de forma reactiva.
 */
class GetSavingsGoalsUseCase(
    private val savingsGoalRepository: SavingsGoalRepository
) {
    operator fun invoke(): Flow<List<SavingsGoal>> {
        return savingsGoalRepository.getAllSavingsGoals()
    }
}
