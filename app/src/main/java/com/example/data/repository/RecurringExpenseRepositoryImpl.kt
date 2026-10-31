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
import com.example.domain.model.RecurrenceFrequency
import com.example.domain.model.calculateNextDueDate
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
            Triple(expenses, categories.associateBy { it.id }, accounts.associateBy { it.id })
        }.combine(transactionDao.getRecurringExpenseTransactions()) { source, recurringTransactions ->
            val (expenses, categoryMap, accountMap) = source
            val now = Calendar.getInstance()
            val monthStart = (now.clone() as Calendar).apply {
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val nextMonthStart = (now.clone() as Calendar).apply {
                set(Calendar.DAY_OF_MONTH, 1)
                add(Calendar.MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            expenses.map { entity ->
                val categoryDomain = categoryMap[entity.categoryId]?.toDomain() ?: Category(
                    id = entity.categoryId,
                    name = "Otros",
                    iconKey = "category",
                    isDefault = true
                )
                val accountDomain = accountMap[entity.accountId]?.toDomain()
                val linkedTransactions = recurringTransactions.filter {
                    it.tags?.contains(recurringTransactionTag(entity.id)) == true
                }
                val paymentsThisMonth = linkedTransactions.filter {
                    it.date >= monthStart && it.date < nextMonthStart
                }
                entity.toDomain(categoryDomain, accountDomain).copy(
                    lastExecutedAmount = linkedTransactions.firstOrNull()?.amount,
                    amountPaidThisMonth = paymentsThisMonth.sumOf { it.amount },
                    paymentCountThisMonth = paymentsThisMonth.size
                )
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
        val nowMillis = System.currentTimeMillis()
        val now = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val currentYearMonth = String.format(
            Locale.US,
            "%04d-%02d",
            now.get(Calendar.YEAR),
            now.get(Calendar.MONTH) + 1
        )
        val activeExpenses = recurringExpenseDao.getActiveRecurringExpensesSync()
        val categories = categoryDao.getAllCategoriesSync().associateBy { it.id }
        val createdTransactions = mutableListOf<Transaction>()
        var totalAmount = 0.0

        for (expense in activeExpenses) {
            if (!expense.isEnabled || !expense.autoCreate) continue

            // The bulk action never bypasses the due date or the user's automation preference.
            val dueDate = resolveNextDueDate(expense, nowMillis)
            if (dueDate > nowMillis) continue
            val nextDueDate = nextFutureDueDate(expense, dueDate, nowMillis)

            val descriptionText = buildString {
                append("[Gasto Recurrente] ")
                append(expense.title)
                if (!expense.description.isNullOrBlank()) append(" - ${expense.description}")
            }
            val transactionEntity = TransactionEntity(
                amount = expense.amount,
                type = "EXPENSE",
                categoryId = expense.categoryId,
                accountId = expense.accountId,
                description = descriptionText,
                date = dueDate,
                tags = "RECURRING,AUTO_GENERATED,${recurringTransactionTag(expense.id)}"
            )
            val newId = insertRecurringTransactionOnce(
                expenseId = expense.id,
                expectedStoredNextDueDate = expense.nextDueDate,
                yearMonth = currentYearMonth,
                nextDueDate = nextDueDate,
                transaction = transactionEntity
            ) ?: continue

            val category = categories[expense.categoryId]?.toDomain() ?: Category(
                id = expense.categoryId,
                name = "Otros",
                iconKey = "category",
                isDefault = true
            )
            createdTransactions.add(
                Transaction(
                    id = newId,
                    amount = expense.amount,
                    type = TransactionType.EXPENSE,
                    category = category,
                    description = descriptionText,
                    date = Date(dueDate),
                    accountId = expense.accountId,
                    tags = "RECURRING,AUTO_GENERATED"
                )
            )
            totalAmount += expense.amount
        }

        return ProcessRecurringResult(
            executedCount = createdTransactions.size,
            totalAmount = totalAmount,
            createdTransactions = createdTransactions
        )
    }

    override suspend fun executeSingleRecurringExpenseNow(id: Long, paidAmount: Double): Boolean {
        require(paidAmount > 0.0 && paidAmount.isFinite()) {
            "El importe pagado debe ser un número válido mayor que cero."
        }
        val expense = recurringExpenseDao.getRecurringExpenseById(id) ?: return false
        if (!expense.isEnabled) return false

        val nowMillis = System.currentTimeMillis()
        val dueDate = resolveNextDueDate(expense, nowMillis)
        // Manual registration applies only to the current occurrence once its date arrives.
        if (dueDate > nowMillis) return false

        val now = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val currentYearMonth = String.format(
            Locale.US,
            "%04d-%02d",
            now.get(Calendar.YEAR),
            now.get(Calendar.MONTH) + 1
        )
        val nextDueDate = nextFutureDueDate(expense, dueDate, nowMillis)
        val descriptionText = buildString {
            append("[Gasto Recurrente] ")
            append(expense.title)
            if (!expense.description.isNullOrBlank()) append(" - ${expense.description}")
        }
        val transactionEntity = TransactionEntity(
            amount = paidAmount,
            type = "EXPENSE",
            categoryId = expense.categoryId,
            accountId = expense.accountId,
            description = descriptionText,
            date = nowMillis,
            tags = "RECURRING,MANUAL_TRIGGER,${recurringTransactionTag(expense.id)}"
        )
        return insertRecurringTransactionOnce(
            expenseId = expense.id,
            expectedStoredNextDueDate = expense.nextDueDate,
            yearMonth = currentYearMonth,
            nextDueDate = nextDueDate,
            transaction = transactionEntity
        ) != null
    }

    private fun recurringTransactionTag(expenseId: Long): String =
        "RECURRING_EXPENSE_ID=$expenseId;"

    private fun resolveNextDueDate(
        expense: com.example.data.local.entity.RecurringExpenseEntity,
        nowMillis: Long
    ): Long {
        if (expense.nextDueDate > 0L) return expense.nextDueDate

        val now = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val currentYearMonth = String.format(
            Locale.US,
            "%04d-%02d",
            now.get(Calendar.YEAR),
            now.get(Calendar.MONTH) + 1
        )
        val due = (now.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 8)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            set(Calendar.DAY_OF_MONTH, expense.dayOfMonth.coerceIn(1, getActualMaximum(Calendar.DAY_OF_MONTH)))
        }
        val todayStart = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (expense.lastExecutedMonth == currentYearMonth || due.before(todayStart)) {
            due.add(Calendar.MONTH, 1)
            due.set(Calendar.DAY_OF_MONTH, expense.dayOfMonth.coerceIn(1, due.getActualMaximum(Calendar.DAY_OF_MONTH)))
        }
        return due.timeInMillis
    }

    private fun nextFutureDueDate(
        expense: com.example.data.local.entity.RecurringExpenseEntity,
        currentDueDate: Long,
        nowMillis: Long
    ): Long {
        val frequency = RecurrenceFrequency.fromStorage(expense.frequency)
        val anchorDate = expense.anchorDate.takeIf { it > 0L } ?: currentDueDate
        var nextDueDate = calculateNextDueDate(currentDueDate, frequency, anchorDate)
        var safetyCounter = 0
        while (nextDueDate <= nowMillis && safetyCounter < 10000) {
            nextDueDate = calculateNextDueDate(nextDueDate, frequency, anchorDate)
            safetyCounter++
        }
        return nextDueDate
    }

    private suspend fun insertRecurringTransactionOnce(
        expenseId: Long,
        expectedStoredNextDueDate: Long,
        yearMonth: String,
        nextDueDate: Long,
        transaction: TransactionEntity
    ): Long? = database.withTransaction {
        val currentExpense = recurringExpenseDao.getRecurringExpenseById(expenseId)
            ?: return@withTransaction null

        if (currentExpense.nextDueDate != expectedStoredNextDueDate) {
            return@withTransaction null
        }
        if (expectedStoredNextDueDate == 0L && currentExpense.lastExecutedMonth == yearMonth) {
            return@withTransaction null
        }

        val transactionId = transactionDao.insertTransaction(transaction)
        recurringExpenseDao.updateLastExecution(expenseId, yearMonth, nextDueDate)
        transactionId
    }
}
