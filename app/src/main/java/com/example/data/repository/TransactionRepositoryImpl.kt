package com.example.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.example.data.local.dao.CategoryDao
import com.example.data.local.dao.TransactionDao
import com.example.data.mapper.toDomain
import com.example.data.mapper.toEntity
import com.example.domain.model.Category
import com.example.domain.model.MonthlySpendingAggregate
import com.example.domain.model.Transaction
import com.example.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.util.Calendar

/**
 * Implementación del repositorio de transacciones utilizando Room.
 * Combina reactivamente transacciones y categorías para construir modelos de dominio ricos.
 */
class TransactionRepositoryImpl(
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao
) : TransactionRepository {

    override fun getMonthlySpendingAggregation(monthsCount: Int): Flow<List<MonthlySpendingAggregate>> {
        val monthNames = listOf("Ene", "Feb", "Mar", "Abr", "May", "Jun", "Jul", "Ago", "Sep", "Oct", "Nov", "Dic")
        val count = monthsCount.coerceIn(1, 24)

        // Calcular timestamp de inicio para los últimos N meses
        val startCal = Calendar.getInstance().apply {
            add(Calendar.MONTH, -(count - 1))
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startDateMillis = startCal.timeInMillis

        return transactionDao.getMonthlySpendingAggregation(startDateMillis).map { dbSummaries ->
            val summaryByYearMonth = dbSummaries.associateBy { it.yearMonth }

            // Generar la secuencia completa de meses del rango
            (count - 1 downTo 0).map { offset ->
                val cal = Calendar.getInstance().apply {
                    add(Calendar.MONTH, -offset)
                }
                val year = cal.get(Calendar.YEAR)
                val monthIdx = cal.get(Calendar.MONTH) // 0-based
                val yearMonthKey = String.format("%04d-%02d", year, monthIdx + 1)

                val summary = summaryByYearMonth[yearMonthKey]
                MonthlySpendingAggregate(
                    monthIndex = monthIdx,
                    monthName = monthNames[monthIdx],
                    year = year,
                    totalExpense = summary?.totalExpense ?: 0.0,
                    totalIncome = summary?.totalIncome ?: 0.0,
                    transactionCount = summary?.transactionCount ?: 0
                )
            }
        }
    }

    override fun getAllTransactions(): Flow<List<Transaction>> {
        return combine(
            transactionDao.getAllTransactions(),
            categoryDao.getAllCategories()
        ) { transactionEntities, categoryEntities ->
            val categoryMap = categoryEntities.associateBy { it.id }
            transactionEntities.map { entity ->
                val categoryDomain = categoryMap[entity.categoryId]?.toDomain() ?: Category(
                    id = entity.categoryId,
                    name = "Otros",
                    iconKey = "category",
                    isDefault = true
                )
                entity.toDomain(categoryDomain)
            }
        }
    }

    override fun getPagedTransactions(): Flow<PagingData<Transaction>> {
        return combine(
            Pager(
                config = PagingConfig(
                    pageSize = 20,
                    enablePlaceholders = false
                )
            ) {
                transactionDao.getPagedTransactions()
            }.flow,
            categoryDao.getAllCategories()
        ) { pagingData, categoryEntities ->
            val categoryMap = categoryEntities.associateBy { it.id }
            pagingData.map { entity ->
                val categoryDomain = categoryMap[entity.categoryId]?.toDomain() ?: Category(
                    id = entity.categoryId,
                    name = "Otros",
                    iconKey = "category",
                    isDefault = true
                )
                entity.toDomain(categoryDomain)
            }
        }
    }

    override suspend fun getTransactionById(id: Long): Transaction? {
        val entity = transactionDao.getTransactionById(id) ?: return null
        val categoryEntity = categoryDao.getCategoryById(entity.categoryId)
        val categoryDomain = categoryEntity?.toDomain() ?: Category(
            id = entity.categoryId,
            name = "Otros",
            iconKey = "category",
            isDefault = true
        )
        return entity.toDomain(categoryDomain)
    }

    override suspend fun getTransactionsByAccountId(accountId: Long): List<Transaction> {
        val entities = transactionDao.getTransactionsByAccountIdSync(accountId)
        val categories = categoryDao.getAllCategoriesSync().associateBy { it.id }
        return entities.map { entity ->
            val catDomain = categories[entity.categoryId]?.toDomain() ?: Category(
                id = entity.categoryId,
                name = "Otros",
                iconKey = "category",
                isDefault = true
            )
            entity.toDomain(catDomain)
        }
    }

    override suspend fun getTransactionsByCorrelationId(correlationId: String): List<Transaction> {
        val entities = transactionDao.getTransactionsByCorrelationTag(correlationId)
        val categories = categoryDao.getAllCategoriesSync().associateBy { it.id }
        return entities.map { entity ->
            val catDomain = categories[entity.categoryId]?.toDomain() ?: Category(
                id = entity.categoryId,
                name = "Otros",
                iconKey = "category",
                isDefault = true
            )
            entity.toDomain(catDomain)
        }
    }

    override suspend fun deleteTransactionsByCorrelationId(correlationId: String): Int {
        return transactionDao.deleteTransactionsByCorrelationTag(correlationId)
    }


    override suspend fun insertTransaction(transaction: Transaction): Long {
        return transactionDao.insertTransaction(transaction.toEntity())
    }

    override suspend fun updateTransaction(transaction: Transaction) {
        transactionDao.updateTransaction(transaction.toEntity())
    }

    override suspend fun deleteTransaction(transaction: Transaction) {
        transactionDao.deleteTransaction(transaction.toEntity())
    }

    override suspend fun deleteTransactions(transactions: List<Transaction>) {
        transactionDao.deleteTransactions(transactions.map { it.toEntity() })
    }

    override suspend fun deleteTransactionById(id: Long) {
        transactionDao.deleteTransactionById(id)
    }

    override fun getTransactionsByDateRange(startDate: Long, endDate: Long): Flow<List<Transaction>> {
        return combine(
            transactionDao.getTransactionsByDateRange(startDate, endDate),
            categoryDao.getAllCategories()
        ) { transactionEntities, categoryEntities ->
            val categoryMap = categoryEntities.associateBy { it.id }
            transactionEntities.map { entity ->
                val categoryDomain = categoryMap[entity.categoryId]?.toDomain() ?: Category(
                    id = entity.categoryId,
                    name = "Otros",
                    iconKey = "category",
                    isDefault = true
                )
                entity.toDomain(categoryDomain)
            }
        }
    }

    override fun getTotalExpenseByDateRange(startDate: Long, endDate: Long): Flow<Double?> {
        return transactionDao.getTotalExpenseByDateRange(startDate, endDate)
    }

    override fun getTotalIncomeByDateRange(startDate: Long, endDate: Long): Flow<Double?> {
        return transactionDao.getTotalIncomeByDateRange(startDate, endDate)
    }
}
