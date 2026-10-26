package com.example.domain.usecase.transaction

import com.example.domain.model.MonthlySpendingAggregate
import com.example.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow

/**
 * Use case to retrieve monthly aggregated spending and income data directly from Room Database.
 */
class GetMonthlySpendingAggregationUseCase(
    private val transactionRepository: TransactionRepository
) {
    operator fun invoke(monthsCount: Int = 6): Flow<List<MonthlySpendingAggregate>> {
        return transactionRepository.getMonthlySpendingAggregation(monthsCount)
    }
}
