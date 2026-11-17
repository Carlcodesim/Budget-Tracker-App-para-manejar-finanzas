package com.example.di

import android.content.Context
import com.example.data.local.AppDatabase
import com.example.data.repository.CategoryRepositoryImpl
import com.example.data.repository.SavingsGoalRepositoryImpl
import com.example.data.repository.TransactionRepositoryImpl
import com.example.data.repository.ReminderRepositoryImpl
import com.example.data.repository.AccountRepositoryImpl
import com.example.domain.repository.CategoryRepository
import com.example.domain.repository.SavingsGoalRepository
import com.example.domain.repository.TransactionRepository
import com.example.domain.repository.ReminderRepository
import com.example.domain.repository.AccountRepository
import com.example.domain.repository.RecurringExpenseRepository
import com.example.domain.repository.GeminiRepository
import com.example.data.repository.RecurringExpenseRepositoryImpl
import com.example.data.repository.GeminiRepositoryImpl
import com.example.domain.usecase.category.AddCategoryUseCase
import com.example.domain.usecase.category.GetCategoriesUseCase
import com.example.domain.usecase.category.UpdateCategoryUseCase
import com.example.domain.usecase.category.DeleteCategoryUseCase
import com.example.domain.usecase.account.GetAccountsUseCase
import com.example.domain.usecase.account.UpdateAccountUseCase
import com.example.domain.usecase.account.AddAccountUseCase
import com.example.domain.usecase.account.DeleteAccountUseCase
import com.example.domain.usecase.recurring.GetRecurringExpensesUseCase
import com.example.domain.usecase.recurring.AddRecurringExpenseUseCase
import com.example.domain.usecase.recurring.UpdateRecurringExpenseUseCase
import com.example.domain.usecase.recurring.DeleteRecurringExpenseUseCase
import com.example.domain.usecase.recurring.ProcessRecurringExpensesUseCase
import com.example.domain.usecase.goal.AddSavingsGoalUseCase
import com.example.domain.usecase.goal.ContributeToGoalUseCase
import com.example.domain.usecase.goal.DeleteSavingsGoalUseCase
import com.example.domain.usecase.goal.GetSavingsGoalsUseCase
import com.example.domain.usecase.goal.UpdateSavingsGoalUseCase
import com.example.domain.usecase.transaction.AddTransactionUseCase
import com.example.domain.usecase.transaction.DeleteTransactionUseCase
import com.example.domain.usecase.transaction.GetBalanceSummaryUseCase
import com.example.ui.theme.ThemeManager
import com.example.domain.usecase.transaction.GetTransactionsUseCase
import com.example.domain.usecase.transaction.GetPagedTransactionsUseCase
import com.example.domain.usecase.transaction.GetMonthlySpendingAggregationUseCase
import com.example.domain.usecase.transaction.UpdateTransactionUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Contenedor de dependencias para la inyección de dependencias manual (Constructor Injection).
 * Actúa como una fuente única de verdad para las instancias de base de datos, repositorios y casos de uso.
 */
class AppContainer(val context: Context) {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Gestor de Temas (Singleton)
    val themeManager: ThemeManager by lazy {
        ThemeManager(context)
    }

    // Base de datos (Singleton)
    val database: AppDatabase by lazy {
        AppDatabase.getDatabase(context, applicationScope)
    }

    // Repositorios
    val categoryRepository: CategoryRepository by lazy {
        CategoryRepositoryImpl(database.categoryDao())
    }

    val transactionRepository: TransactionRepository by lazy {
        TransactionRepositoryImpl(database.transactionDao(), database.categoryDao())
    }

    val savingsGoalRepository: SavingsGoalRepository by lazy {
        SavingsGoalRepositoryImpl(database.savingsGoalDao())
    }

    val reminderRepository: ReminderRepository by lazy {
        ReminderRepositoryImpl(database.reminderDao())
    }

    val accountRepository: AccountRepository by lazy {
        AccountRepositoryImpl(database.accountDao(), database.transactionDao())
    }

    val geminiRepository: GeminiRepository by lazy {
        GeminiRepositoryImpl()
    }

    val recurringExpenseRepository: RecurringExpenseRepository by lazy {
        RecurringExpenseRepositoryImpl(
            database = database,
            recurringExpenseDao = database.recurringExpenseDao(),
            categoryDao = database.categoryDao(),
            accountDao = database.accountDao(),
            transactionDao = database.transactionDao()
        )
    }

    val storeRepository: com.example.domain.repository.StoreRepository by lazy {
        com.example.data.repository.StoreRepositoryImpl(context)
    }

    val businessRepository: com.example.domain.repository.BusinessRepository by lazy {
        com.example.data.repository.BusinessRepositoryImpl(database.businessDao())
    }

    val parseReceiptUseCase by lazy { com.example.domain.usecase.receipt.ParseReceiptUseCase(geminiRepository) }

    // Casos de uso de gastos recurrentes
    val getRecurringExpensesUseCase by lazy { GetRecurringExpensesUseCase(recurringExpenseRepository) }
    val addRecurringExpenseUseCase by lazy { AddRecurringExpenseUseCase(recurringExpenseRepository) }
    val updateRecurringExpenseUseCase by lazy { UpdateRecurringExpenseUseCase(recurringExpenseRepository) }
    val deleteRecurringExpenseUseCase by lazy { DeleteRecurringExpenseUseCase(recurringExpenseRepository) }
    val processRecurringExpensesUseCase by lazy { ProcessRecurringExpensesUseCase(recurringExpenseRepository) }

    // Casos de uso de transacciones
    val getTransactionsUseCase by lazy { GetTransactionsUseCase(transactionRepository) }
    val getPagedTransactionsUseCase by lazy { GetPagedTransactionsUseCase(transactionRepository) }
    val getMonthlySpendingAggregationUseCase by lazy { GetMonthlySpendingAggregationUseCase(transactionRepository) }
    val addTransactionUseCase by lazy { AddTransactionUseCase(transactionRepository) }
    val updateTransactionUseCase by lazy { UpdateTransactionUseCase(transactionRepository) }
    val deleteTransactionUseCase by lazy { DeleteTransactionUseCase(transactionRepository) }
    val getBalanceSummaryUseCase by lazy { GetBalanceSummaryUseCase(transactionRepository, accountRepository) }

    // Casos de uso de categorías
    val getCategoriesUseCase by lazy { GetCategoriesUseCase(categoryRepository) }
    val addCategoryUseCase by lazy { AddCategoryUseCase(categoryRepository) }
    val updateCategoryUseCase by lazy { UpdateCategoryUseCase(categoryRepository) }
    val deleteCategoryUseCase by lazy { DeleteCategoryUseCase(categoryRepository) }

    // Casos de uso de cuentas
    val getAccountsUseCase by lazy { GetAccountsUseCase(accountRepository) }
    val updateAccountUseCase by lazy { UpdateAccountUseCase(accountRepository) }
    val addAccountUseCase by lazy { AddAccountUseCase(accountRepository) }
    val deleteAccountUseCase by lazy { DeleteAccountUseCase(accountRepository) }
    val transferFundsUseCase by lazy {
        com.example.domain.usecase.account.TransferFundsUseCase(
            accountRepository = accountRepository,
            transactionRepository = transactionRepository,
            categoryRepository = categoryRepository
        )
    }

    // Casos de uso de metas de ahorro
    val getSavingsGoalsUseCase by lazy { GetSavingsGoalsUseCase(savingsGoalRepository) }
    val addSavingsGoalUseCase by lazy { AddSavingsGoalUseCase(savingsGoalRepository) }
    val updateSavingsGoalUseCase by lazy { UpdateSavingsGoalUseCase(savingsGoalRepository) }
    val deleteSavingsGoalUseCase by lazy { DeleteSavingsGoalUseCase(savingsGoalRepository) }
    val contributeToGoalUseCase by lazy { ContributeToGoalUseCase(savingsGoalRepository) }
}

