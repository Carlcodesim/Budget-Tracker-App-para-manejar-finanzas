package com.example.domain.repository

import com.example.domain.model.SavingsGoal
import kotlinx.coroutines.flow.Flow

/**
 * Interfaz de repositorio para operaciones con metas de ahorro.
 */
interface SavingsGoalRepository {
    fun getAllSavingsGoals(): Flow<List<SavingsGoal>>
    suspend fun getSavingsGoalById(id: Long): SavingsGoal?
    suspend fun insertSavingsGoal(savingsGoal: SavingsGoal): Long
    suspend fun updateSavingsGoal(savingsGoal: SavingsGoal)
    suspend fun deleteSavingsGoal(savingsGoal: SavingsGoal)
}
