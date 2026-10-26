package com.example.data.repository

import com.example.data.local.dao.SavingsGoalDao
import com.example.data.mapper.toDomain
import com.example.data.mapper.toEntity
import com.example.domain.model.SavingsGoal
import com.example.domain.repository.SavingsGoalRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Implementación del repositorio de metas de ahorro utilizando Room.
 */
class SavingsGoalRepositoryImpl(
    private val savingsGoalDao: SavingsGoalDao
) : SavingsGoalRepository {
    override fun getAllSavingsGoals(): Flow<List<SavingsGoal>> {
        return savingsGoalDao.getAllSavingsGoals().map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun getSavingsGoalById(id: Long): SavingsGoal? {
        return savingsGoalDao.getSavingsGoalById(id)?.toDomain()
    }

    override suspend fun insertSavingsGoal(savingsGoal: SavingsGoal): Long {
        return savingsGoalDao.insertSavingsGoal(savingsGoal.toEntity())
    }

    override suspend fun updateSavingsGoal(savingsGoal: SavingsGoal) {
        savingsGoalDao.updateSavingsGoal(savingsGoal.toEntity())
    }

    override suspend fun deleteSavingsGoal(savingsGoal: SavingsGoal) {
        savingsGoalDao.deleteSavingsGoal(savingsGoal.toEntity())
    }
}
