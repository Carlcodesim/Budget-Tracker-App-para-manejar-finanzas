package com.example.domain.usecase

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.entity.AccountEntity
import com.example.data.local.entity.CategoryEntity
import com.example.data.repository.AccountRepositoryImpl
import com.example.data.repository.CategoryRepositoryImpl
import com.example.data.repository.TransactionRepositoryImpl
import com.example.domain.usecase.account.TransferFundsUseCase
import com.example.domain.usecase.account.TransferResult
import com.example.domain.usecase.transaction.DeleteTransactionUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TransferAndSyncLogicTest {

    private lateinit var database: AppDatabase
    private lateinit var accountRepo: AccountRepositoryImpl
    private lateinit var transactionRepo: TransactionRepositoryImpl
    private lateinit var categoryRepo: CategoryRepositoryImpl
    private lateinit var transferFundsUseCase: TransferFundsUseCase
    private lateinit var deleteTransactionUseCase: DeleteTransactionUseCase

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        accountRepo = AccountRepositoryImpl(database.accountDao(), database.transactionDao())
        transactionRepo = TransactionRepositoryImpl(database.transactionDao(), database.categoryDao())
        categoryRepo = CategoryRepositoryImpl(database.categoryDao())

        transferFundsUseCase = TransferFundsUseCase(accountRepo, transactionRepo, categoryRepo)
        deleteTransactionUseCase = DeleteTransactionUseCase(transactionRepo)

        runBlocking {
            // Inicializar datos base
            database.categoryDao().insertCategory(
                CategoryEntity(id = 10L, name = "Transferencia", iconKey = "swap_horiz", isDefault = true)
            )
            database.accountDao().insertAccount(
                AccountEntity(id = 1L, name = "Efectivo", type = "CASH", initialBalance = 1000.0)
            )
            database.accountDao().insertAccount(
                AccountEntity(id = 2L, name = "Banco", type = "BANK", initialBalance = 500.0)
            )
        }
    }


    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun executeTransferUpdatesBothAccountBalancesAccurately() = runBlocking {
        val result = transferFundsUseCase(
            sourceAccountId = 1L,
            targetAccountId = 2L,
            amount = 250.0,
            note = "Ahorro mensual"
        )

        assertTrue(result is TransferResult.Success)

        val accounts = accountRepo.getAllAccounts().first()
        val acc1 = accounts.find { it.id == 1L }!!
        val acc2 = accounts.find { it.id == 2L }!!

        // Efectivo: 1000 - 250 = 750
        assertEquals(750.0, acc1.currentBalance, 0.01)
        // Banco: 500 + 250 = 750
        assertEquals(750.0, acc2.currentBalance, 0.01)

        val txs = transactionRepo.getAllTransactions().first()
        assertEquals(2, txs.size)
        assertTrue(txs.all { it.isTransfer })
    }

    @Test
    fun failedIncomingTransferRollsBackOutgoingTransfer() = runBlocking {
        // Simular un fallo al insertar el movimiento de entrada.
        database.openHelper.writableDatabase.execSQL("""
            CREATE TRIGGER fail_transfer_in
            BEFORE INSERT ON transactions
            WHEN NEW.tags LIKE '%TRANSFER,IN,%'
            BEGIN
                SELECT RAISE(ABORT, 'simulated incoming transfer failure');
            END
        """.trimIndent())

        try {
            transferFundsUseCase(
                sourceAccountId = 1L,
                targetAccountId = 2L,
                amount = 100.0
            )
        } catch (_: Exception) {
            // Esperado: el trigger rechaza el segundo movimiento.
        }

        // La transacción SQL debe revertir también el primer movimiento.
        val txs = transactionRepo.getAllTransactions().first()
        assertEquals(0, txs.size)

        val accounts = accountRepo.getAllAccounts().first()
        assertEquals(1000.0, accounts.find { it.id == 1L }!!.currentBalance, 0.01)
        assertEquals(500.0, accounts.find { it.id == 2L }!!.currentBalance, 0.01)
    }

    @Test
    fun deleteCorrelatedTransferCleansBothSidesAndRestoresBalances() = runBlocking {
        val transferResult = transferFundsUseCase(
            sourceAccountId = 1L,
            targetAccountId = 2L,
            amount = 200.0
        ) as TransferResult.Success

        val txsBefore = transactionRepo.getAllTransactions().first()
        assertEquals(2, txsBefore.size)

        // Borrar una sola de las ramas de la transferencia
        val oneLeg = txsBefore.first()
        deleteTransactionUseCase(oneLeg, deleteLinkedTransfer = true)

        val txsAfter = transactionRepo.getAllTransactions().first()
        assertEquals(0, txsAfter.size)

        val accounts = accountRepo.getAllAccounts().first()
        val acc1 = accounts.find { it.id == 1L }!!
        val acc2 = accounts.find { it.id == 2L }!!

        // Ambos balances deben haber regresado íntegramente a sus valores iniciales
        assertEquals(1000.0, acc1.currentBalance, 0.01)
        assertEquals(500.0, acc2.currentBalance, 0.01)
    }
}
