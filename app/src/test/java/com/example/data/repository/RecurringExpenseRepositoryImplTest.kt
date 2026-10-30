package com.example.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.entity.RecurringExpenseEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RecurringExpenseRepositoryImplTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: RecurringExpenseRepositoryImpl

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RecurringExpenseRepositoryImpl(
            database = database,
            recurringExpenseDao = database.recurringExpenseDao(),
            categoryDao = database.categoryDao(),
            accountDao = database.accountDao(),
            transactionDao = database.transactionDao()
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun executingSameRecurringExpenseTwiceInOneMonthCreatesOnlyOneTransaction() = runBlocking {
        val expenseId = database.recurringExpenseDao().insertRecurringExpense(
            RecurringExpenseEntity(
                title = "Internet",
                amount = 2200.0,
                categoryId = 7L,
                accountId = 1L,
                dayOfMonth = 15,
                isEnabled = true
            )
        )

        // El plan es RD$2,200, pero el usuario confirma que pagó RD$1,750.
        assertTrue(repository.executeSingleRecurringExpenseNow(expenseId, 1750.0))
        assertFalse(repository.executeSingleRecurringExpenseNow(expenseId, 2200.0))

        database.openHelper.readableDatabase.query(
            "SELECT COUNT(*) FROM transactions"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }

        database.openHelper.readableDatabase.query(
            "SELECT amount FROM transactions LIMIT 1"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1750.0, cursor.getDouble(0), 0.0)
        }

        val linkedTransactions = database.transactionDao()
            .getTransactionsByCorrelationTag("RECURRING_EXPENSE_ID=$expenseId;")
        assertEquals(1, linkedTransactions.size)
        assertEquals(1750.0, linkedTransactions.single().amount, 0.0)

        val displayedExpense = repository.getAllRecurringExpenses().first().single()
        assertEquals(1750.0, displayedExpense.lastExecutedAmount!!, 0.0)

        val savedExpense = database.recurringExpenseDao().getRecurringExpenseById(expenseId)
        val expectedMonth = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US)
            .format(java.util.Date())
        assertEquals(expectedMonth, savedExpense?.lastExecutedMonth)
    }


    @Test
    fun forceAllDueDoesNotCreateTransactionWhenAutoCreationIsDisabled() = runBlocking {
        database.recurringExpenseDao().insertRecurringExpense(
            RecurringExpenseEntity(
                title = "Internet manual",
                amount = 2200.0,
                categoryId = 7L,
                accountId = 1L,
                dayOfMonth = 1,
                isEnabled = true,
                autoCreate = false
            )
        )

        val result = repository.processRecurringExpenses(forceAllDue = true)

        assertEquals(0, result.executedCount)
        assertEquals(0.0, result.totalAmount, 0.0)
        database.openHelper.readableDatabase.query(
            "SELECT COUNT(*) FROM transactions"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
    }

}
