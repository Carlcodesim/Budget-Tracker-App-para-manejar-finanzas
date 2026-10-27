package com.example.domain.repository

import androidx.paging.PagingData
import com.example.domain.model.MonthlySpendingAggregate
import com.example.domain.model.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Interfaz de repositorio para operaciones con transacciones.
 */
interface TransactionRepository {
    fun getAllTransactions(): Flow<List<Transaction>>
    fun getPagedTransactions(): Flow<PagingData<Transaction>>
    fun getMonthlySpendingAggregation(monthsCount: Int = 6): Flow<List<MonthlySpendingAggregate>>
    fun getTransactionsByDateRange(startDate: Long, endDate: Long): Flow<List<Transaction>>
    fun getTotalExpenseByDateRange(startDate: Long, endDate: Long): Flow<Double?>
    fun getTotalIncomeByDateRange(startDate: Long, endDate: Long): Flow<Double?>
    suspend fun getTransactionById(id: Long): Transaction?
    suspend fun getTransactionsByAccountId(accountId: Long): List<Transaction>
    suspend fun getTransactionsByCorrelationId(correlationId: String): List<Transaction>
    suspend fun insertTransaction(transaction: Transaction): Long
    suspend fun insertTransferPair(outgoing: Transaction, incoming: Transaction): Pair<Long, Long>
    suspend fun updateTransaction(transaction: Transaction)
    suspend fun deleteTransaction(transaction: Transaction)
    suspend fun deleteTransactions(transactions: List<Transaction>)
    suspend fun deleteTransactionById(id: Long)
    suspend fun deleteTransactionsByCorrelationId(correlationId: String): Int
}
