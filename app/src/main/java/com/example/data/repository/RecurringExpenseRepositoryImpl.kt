package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.local.AppDatabase
import com.example.data.local.dao.AccountDao
import com.example.data.local.dao.CategoryDao
import com.example.data.local.dao.RecurringExpenseDao
import com.example.data.local.dao.TransactionDao
import com.example.data.local.entity.TransactionEntity
import com.example.data.mapper.toDomain
import com.example.data.mapper.toEntity
import com.example.domain.model.Account
import com.example.domain.model.Category
import com.example.domain.model.ProcessRecurringResult
import com.example.domain.model.RecurringExpense
import com.example.domain.model.Transaction
import com.example.domain.model.TransactionType
import com.example.domain.repository.RecurringExpenseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Implementación del repositorio de gastos recurrentes.
 * Maneja la persistencia y la generación automática de transacciones programadas mensuales.
 */
class RecurringExpenseRepositoryImpl(
    private val database: AppDatabase,
    private val recurringExpenseDao: RecurringExpenseDao,
    private val categoryDao: CategoryDao,
    private val accountDao: AccountDao,
    private val transactionDao: TransactionDao
) : RecurringExpenseRepository {

    override fun getAllRecurringExpenses(): Flow<List<RecurringExpense>> {
        return combine(
            recurringExpenseDao.getAllRecurringExpenses(),
            categoryDao.getAllCategories(),
            accountDao.getAllAccounts()
        ) { expenses, categories, accounts ->
            val categoryMap = categories.associateBy { it.id }
            val accountMap = accounts.associateBy { it.id }

            expenses.map { entity ->
                val categoryDomain = categoryMap[entity.categoryId]?.toDomain() ?: Category(
                    id = entity.categoryId,
                    name = "Otros",
                    iconKey = "category",
                    isDefault = true
                )
                val accountDomain = accountMap[entity.accountId]?.toDomain()
                entity.toDomain(categoryDomain, accountDomain)
            }
        }
    }

    override suspend fun getRecurringExpenseById(id: Long): RecurringExpense? {
        val entity = recurringExpenseDao.getRecurringExpenseById(id) ?: return null
        val category = categoryDao.getCategoryById(entity.categoryId)?.toDomain() ?: Category(
            id = entity.categoryId,
            name = "Otros",
            iconKey = "category",
            isDefault = true
        )
        val account = accountDao.getAccountById(entity.accountId)?.toDomain()
        return entity.toDomain(category, account)
    }

    override suspend fun insertRecurringExpense(recurringExpense: RecurringExpense): Long {
        return recurringExpenseDao.insertRecurringExpense(recurringExpense.toEntity())
    }

    override suspend fun updateRecurringExpense(recurringExpense: RecurringExpense) {
        recurringExpenseDao.updateRecurringExpense(recurringExpense.toEntity())
    }

    override suspend fun deleteRecurringExpense(id: Long) {
        recurringExpenseDao.deleteRecurringExpenseById(id)
    }

    override suspend fun setEnabled(id: Long, isEnabled: Boolean) {
        recurringExpenseDao.updateEnabledStatus(id, isEnabled)
    }

    override suspend fun processRecurringExpenses(
        targetYearMonth: String?,
        forceAllDue: Boolean
    ): ProcessRecurringResult {
        val nowCal = Calendar.getInstance()
        val currentYear = nowCal.get(Calendar.YEAR)
        val currentMonth = nowCal.get(Calendar.MONTH) + 1 // 1-based (1..12)
        val currentDay = nowCal.get(Calendar.DAY_OF_MONTH)

        val yearMonthKey = targetYearMonth ?: String.format(Locale.US, "%04d-%02d", currentYear, currentMonth)

        val activeExpenses = recurringExpenseDao.getActiveRecurringExpensesSync()
        val categories = categoryDao.getAllCategoriesSync().associateBy { it.id }
        val createdTransactions = mutableListOf<Transaction>()
        var totalAmount = 0.0

        for (expense in activeExpenses) {
            // Verificar si aplica auto-creación y si no ha sido ejecutado ya este mes
            if (!expense.isEnabled) continue
            if (expense.lastExecutedMonth == yearMonthKey) continue

            val isDue = forceAllDue || (expense.autoCreate && currentDay >= expense.dayOfMonth)

            if (isDue) {
                // Calcular fecha programada
                val maxDayInMonth = nowCal.getActualMaximum(Calendar.DAY_OF_MONTH)
                val targetDay = expense.dayOfMonth.coerceIn(1, maxDayInMonth)

                val scheduledCal = Calendar.getInstance().apply {
                    set(Calendar.YEAR, currentYear)
                    set(Calendar.MONTH, currentMonth - 1)
                    set(Calendar.DAY_OF_MONTH, targetDay)
                    set(Calendar.HOUR_OF_DAY, 8)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }

                val descriptionText = buildString {
                    append("[Gasto Recurrente] ")
                    append(expense.title)
                    if (!expense.description.isNullOrBlank()) {
                        append(" - ")
                        append(expense.description)
                    }
                }

                val transactionEntity = TransactionEntity(
                    amount = expense.amount,
                    type = "EXPENSE",
                    categoryId = expense.categoryId,
                    accountId = expense.accountId,
                    description = descriptionText,
                    date = scheduledCal.timeInMillis,
                    tags = "RECURRING,AUTO_GENERATED"
                )

                // Insertar la transacción y marcar el mes en una única operación atómica.
                val newId = insertRecurringTransactionOnce(expense.id, yearMonthKey, transactionEntity)
                    ?: continue

                val catDomain = categories[expense.categoryId]?.toDomain() ?: Category(
                    id = expense.categoryId,
                    name = "Otros",
                    iconKey = "category",
                    isDefault = true
                )

                val createdTx = Transaction(
                    id = newId,
                    amount = expense.amount,
                    type = TransactionType.EXPENSE,
                    category = catDomain,
                    description = descriptionText,
                    date = Date(scheduledCal.timeInMillis),
                    accountId = expense.accountId,
                    tags = "RECURRING,AUTO_GENERATED"
                )

                createdTransactions.add(createdTx)
                totalAmount += expense.amount
            }
        }

        return ProcessRecurringResult(
            executedCount = createdTransactions.size,
            totalAmount = totalAmount,
            createdTransactions = createdTransactions
        )
    }

    override suspend fun executeSingleRecurringExpenseNow(id: Long): Boolean {
        val expense = recurringExpenseDao.getRecurringExpenseById(id) ?: return false
        val nowCal = Calendar.getInstance()
        val currentYear = nowCal.get(Calendar.YEAR)
        val currentMonth = nowCal.get(Calendar.MONTH) + 1
        val yearMonthKey = String.format(Locale.US, "%04d-%02d", currentYear, currentMonth)

        val descriptionText = buildString {
            append("[Gasto Recurrente] ")
            append(expense.title)
            if (!expense.description.isNullOrBlank()) {
                append(" - ")
                append(expense.description)
            }
        }

        val transactionEntity = TransactionEntity(
            amount = expense.amount,
            type = "EXPENSE",
            categoryId = expense.categoryId,
            accountId = expense.accountId,
            description = descriptionText,
            date = System.currentTimeMillis(),
            tags = "RECURRING,MANUAL_TRIGGER"
        )

        // Releer el estado y escribir ambos cambios dentro de la misma transacción SQL.
        return insertRecurringTransactionOnce(expense.id, yearMonthKey, transactionEntity) != null
    }

    private suspend fun insertRecurringTransactionOnce(
        expenseId: Long,
        yearMonth: String,
        transaction: TransactionEntity
    ): Long? = database.withTransaction {
        val currentExpense = recurringExpenseDao.getRecurringExpenseById(expenseId)
            ?: return@withTransaction null

        if (!currentExpense.isEnabled || currentExpense.lastExecutedMonth == yearMonth) {
            return@withTransaction null
        }

        val transactionId = transactionDao.insertTransaction(transaction)
        recurringExpenseDao.updateLastExecutedMonth(expenseId, yearMonth)
        transactionId
    }
}
