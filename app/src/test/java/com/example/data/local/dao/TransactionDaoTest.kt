package com.example.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TransactionDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var transactionDao: TransactionDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        transactionDao = database.transactionDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertExpenseAndQueryById() = runBlocking {
        val expense = TransactionEntity(
            amount = 150.0,
            type = "EXPENSE",
            categoryId = 1L,
            description = "Supermercado",
            date = 1000L,
            accountId = 1L
        )

        val id = transactionDao.insertTransaction(expense)
        val fetched = transactionDao.getTransactionById(id)

        assertNotNull(fetched)
        assertEquals("EXPENSE", fetched?.type)
        assertEquals(150.0, fetched?.amount ?: 0.0, 0.01)
        assertEquals("Supermercado", fetched?.description)
    }

    @Test
    fun deleteTransactionById() = runBlocking {
        val expense = TransactionEntity(
            amount = 50.0,
            type = "EXPENSE",
            categoryId = 1L,
            description = "Café",
            date = 2000L,
            accountId = 1L
        )

        val id = transactionDao.insertTransaction(expense)
        transactionDao.deleteTransactionById(id)

        val fetched = transactionDao.getTransactionById(id)
        assertNull(fetched)
    }

    @Test
    fun queryByDateRangeAndExpensesByDateRange() = runBlocking {
        val t1 = TransactionEntity(amount = 10.0, type = "EXPENSE", categoryId = 1L, description = "T1", date = 1000L)
        val t2 = TransactionEntity(amount = 20.0, type = "INCOME", categoryId = 1L, description = "T2", date = 2000L)
        val t3 = TransactionEntity(amount = 30.0, type = "EXPENSE", categoryId = 1L, description = "T3", date = 3000L)
        val t4 = TransactionEntity(amount = 40.0, type = "EXPENSE", categoryId = 1L, description = "T4", date = 5000L)

        transactionDao.insertTransaction(t1)
        transactionDao.insertTransaction(t2)
        transactionDao.insertTransaction(t3)
        transactionDao.insertTransaction(t4)

        // Date range between 1500 and 4000
        val inRange = transactionDao.getTransactionsByDateRangeSync(1500L, 4000L)
        assertEquals(2, inRange.size) // t2 and t3

        // Expenses in date range between 1000 and 5000
        val expenses = transactionDao.getExpensesByDateRange(1000L, 5000L).first()
        assertEquals(3, expenses.size) // t1, t3, t4
    }

    @Test
    fun aggregateMonthlySpendingDirectlyFromRoom() = runBlocking {
        // Date timestamps for Jan 2026 and Feb 2026
        // Jan 15, 2026 ~ 1768435200000L
        // Feb 10, 2026 ~ 1770681600000L
        val tJanExpense1 = TransactionEntity(amount = 120.0, type = "EXPENSE", categoryId = 1L, description = "Super Jan", date = 1768435200000L)
        val tJanExpense2 = TransactionEntity(amount = 80.0, type = "EXPENSE", categoryId = 2L, description = "Farmacia Jan", date = 1768445200000L)
        val tJanIncome = TransactionEntity(amount = 500.0, type = "INCOME", categoryId = 3L, description = "Salario Jan", date = 1768455200000L)
        val tFebExpense = TransactionEntity(amount = 250.0, type = "EXPENSE", categoryId = 1L, description = "Ropa Feb", date = 1770681600000L)

        transactionDao.insertTransaction(tJanExpense1)
        transactionDao.insertTransaction(tJanExpense2)
        transactionDao.insertTransaction(tJanIncome)
        transactionDao.insertTransaction(tFebExpense)

        val aggregates = transactionDao.getMonthlySpendingAggregation(1760000000000L).first()
        assertEquals(2, aggregates.size)

        val janAgg = aggregates.first()
        assertEquals(200.0, janAgg.totalExpense, 0.01)
        assertEquals(500.0, janAgg.totalIncome, 0.01)
        assertEquals(3, janAgg.transactionCount)

        val febAgg = aggregates.last()
        assertEquals(250.0, febAgg.totalExpense, 0.01)
        assertEquals(0.0, febAgg.totalIncome, 0.01)
        assertEquals(1, febAgg.transactionCount)
    }

    @Test
    fun queryByAccountIdSyncAndVerifyDeltas() = runBlocking {
        val tAccount1A = TransactionEntity(amount = 100.0, type = "INCOME", categoryId = 1L, description = "Depósito", date = 1000L, accountId = 1L)
        val tAccount1B = TransactionEntity(amount = 40.0, type = "EXPENSE", categoryId = 2L, description = "Comida", date = 2000L, accountId = 1L)
        val tAccount2 = TransactionEntity(amount = 300.0, type = "INCOME", categoryId = 1L, description = "Otro banco", date = 3000L, accountId = 2L)

        transactionDao.insertTransaction(tAccount1A)
        transactionDao.insertTransaction(tAccount1B)
        transactionDao.insertTransaction(tAccount2)

        val txsAccount1 = transactionDao.getTransactionsByAccountIdSync(1L)
        assertEquals(2, txsAccount1.size)
        assertEquals(2000L, txsAccount1[0].date) // Sorted by date DESC
        assertEquals(1000L, txsAccount1[1].date)

        val txsAccount2 = transactionDao.getTransactionsByAccountIdSync(2L)
        assertEquals(1, txsAccount2.size)
        assertEquals(300.0, txsAccount2[0].amount, 0.01)
    }

    @Test
    fun correlatedTransfersQueryAndDeletion() = runBlocking {
        val corrId = "corr_abc123"
        val outTx = TransactionEntity(amount = 75.0, type = "TRANSFER", categoryId = 10L, description = "A cuenta 2", date = 1000L, accountId = 1L, tags = "TRANSFER,OUT,CORR:$corrId")
        val inTx = TransactionEntity(amount = 75.0, type = "TRANSFER", categoryId = 10L, description = "De cuenta 1", date = 1000L, accountId = 2L, tags = "TRANSFER,IN,CORR:$corrId")
        val regularTx = TransactionEntity(amount = 20.0, type = "EXPENSE", categoryId = 1L, description = "Café", date = 2000L, accountId = 1L)

        transactionDao.insertTransaction(outTx)
        transactionDao.insertTransaction(inTx)
        transactionDao.insertTransaction(regularTx)

        val found = transactionDao.getTransactionsByCorrelationTag(corrId)
        assertEquals(2, found.size)

        val deletedCount = transactionDao.deleteTransactionsByCorrelationTag(corrId)
        assertEquals(2, deletedCount)

        val remaining = transactionDao.getTransactionsByCorrelationTag(corrId)
        assertEquals(0, remaining.size)

        val allRemaining = transactionDao.getAllTransactions().first()
        assertEquals(1, allRemaining.size)
        assertEquals(regularTx.description, allRemaining[0].description)
    }
}

