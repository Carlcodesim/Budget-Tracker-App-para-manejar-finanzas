package com.example.data.local.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) para la tabla de transacciones.
 * Permite realizar operaciones CRUD completas (inserción, actualización, borrado)
 * e incluye consultas especializadas por rango de fechas y por tipo (gastos/ingresos).
 */
@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY date DESC")
    fun getAllTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY date DESC")
    fun getPagedTransactions(): PagingSource<Int, TransactionEntity>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getTransactionById(id: Long): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE type = :type ORDER BY date DESC")
    fun getTransactionsByType(type: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE date BETWEEN :startDate AND :endDate ORDER BY date DESC")
    fun getTransactionsByDateRange(startDate: Long, endDate: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE date BETWEEN :startDate AND :endDate ORDER BY date DESC")
    suspend fun getTransactionsByDateRangeSync(startDate: Long, endDate: Long): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE accountId = :accountId ORDER BY date DESC")
    fun getTransactionsByAccountId(accountId: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE accountId = :accountId ORDER BY date DESC")
    suspend fun getTransactionsByAccountIdSync(accountId: Long): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE tags LIKE '%' || :correlationId || '%'")
    suspend fun getTransactionsByCorrelationTag(correlationId: String): List<TransactionEntity>

    @Query("DELETE FROM transactions WHERE tags LIKE '%' || :correlationId || '%'")
    suspend fun deleteTransactionsByCorrelationTag(correlationId: String): Int

    @Query("SELECT * FROM transactions WHERE type = 'EXPENSE' AND date BETWEEN :startDate AND :endDate ORDER BY date DESC")
    fun getExpensesByDateRange(startDate: Long, endDate: Long): Flow<List<TransactionEntity>>

    @Query("SELECT categoryId, SUM(amount) as totalAmount, COUNT(*) as transactionCount FROM transactions WHERE type = 'EXPENSE' AND date BETWEEN :startDate AND :endDate GROUP BY categoryId ORDER BY totalAmount DESC")
    fun getCategorySpendingByDateRange(startDate: Long, endDate: Long): Flow<List<CategorySpendingSummary>>

    @Query("SELECT SUM(amount) FROM transactions WHERE type = 'EXPENSE' AND date BETWEEN :startDate AND :endDate")
    fun getTotalExpenseByDateRange(startDate: Long, endDate: Long): Flow<Double?>

    @Query("SELECT SUM(amount) FROM transactions WHERE type = 'INCOME' AND date BETWEEN :startDate AND :endDate")
    fun getTotalIncomeByDateRange(startDate: Long, endDate: Long): Flow<Double?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllTransactions(transactions: List<TransactionEntity>)

    /**
     * Inserta ambos lados de una transferencia dentro de una única transacción SQL.
     * Si falla cualquiera de las inserciones, Room revierte las dos.
     */
    @Transaction
    suspend fun insertTransferPair(
        outgoing: TransactionEntity,
        incoming: TransactionEntity
    ): Pair<Long, Long> {
        val outgoingId = insertTransaction(outgoing)
        val incomingId = insertTransaction(incoming)
        return outgoingId to incomingId
    }

    @Update
    suspend fun updateTransaction(transaction: TransactionEntity)

    @Delete
    suspend fun deleteTransaction(transaction: TransactionEntity)

    @Delete
    suspend fun deleteTransactions(transactions: List<TransactionEntity>)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteTransactionById(id: Long)

    @Query("DELETE FROM transactions")
    suspend fun deleteAllTransactions()

    @Query("""
        SELECT 
            strftime('%Y-%m', datetime(date / 1000, 'unixepoch', 'localtime')) as yearMonth,
            SUM(CASE WHEN type = 'EXPENSE' THEN amount ELSE 0.0 END) as totalExpense,
            SUM(CASE WHEN type = 'INCOME' THEN amount ELSE 0.0 END) as totalIncome,
            COUNT(*) as transactionCount
        FROM transactions
        WHERE date >= :startDateMillis
        GROUP BY yearMonth
        ORDER BY yearMonth ASC
    """)
    fun getMonthlySpendingAggregation(startDateMillis: Long): Flow<List<MonthlySpendingDbSummary>>
}

/**
 * Agregación de gastos e ingresos mensuales calculada directamente en Room.
 */
data class MonthlySpendingDbSummary(
    val yearMonth: String,
    val totalExpense: Double,
    val totalIncome: Double,
    val transactionCount: Int
)

/**
 * Agregación de gastos por categoría devuelta por consultas Room.
 */
data class CategorySpendingSummary(
    val categoryId: Long,
    val totalAmount: Double,
    val transactionCount: Int
)

