package com.example.presentation.common.util

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.di.AppContainer
import com.example.presentation.dashboard.DashboardViewModel
import com.example.presentation.goals.form.GoalFormViewModel
import com.example.presentation.goals.list.GoalsViewModel
import com.example.presentation.transactions.TransactionViewModel
import com.example.presentation.transactions.form.TransactionFormViewModel
import com.example.presentation.transactions.list.TransactionsViewModel
import com.example.presentation.accounts.AccountsViewModel
import com.example.presentation.assistant.AssistantViewModel

/**
 * Fábrica personalizada de ViewModels para proveer las dependencias requeridas sin necesidad de Hilt.
 * Cumple con el principio de inyección de dependencia por constructor de forma directa y segura.
 */
class ViewModelFactory(
    private val appContainer: AppContainer
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(DashboardViewModel::class.java) -> {
                DashboardViewModel(
                    getBalanceSummaryUseCase = appContainer.getBalanceSummaryUseCase,
                    getTransactionsUseCase = appContainer.getTransactionsUseCase,
                    getCategoriesUseCase = appContainer.getCategoriesUseCase,
                    getAccountsUseCase = appContainer.getAccountsUseCase,
                    addTransactionUseCase = appContainer.addTransactionUseCase,
                    deleteTransactionUseCase = appContainer.deleteTransactionUseCase
                ) as T
            }
            modelClass.isAssignableFrom(TransactionViewModel::class.java) -> {
                TransactionViewModel(
                    transactionRepository = appContainer.transactionRepository
                ) as T
            }
            modelClass.isAssignableFrom(TransactionsViewModel::class.java) -> {
                TransactionsViewModel(
                    getTransactionsUseCase = appContainer.getTransactionsUseCase,
                    getPagedTransactionsUseCase = appContainer.getPagedTransactionsUseCase,
                    getCategoriesUseCase = appContainer.getCategoriesUseCase,
                    deleteTransactionUseCase = appContainer.deleteTransactionUseCase,
                    addTransactionUseCase = appContainer.addTransactionUseCase
                ) as T
            }
            modelClass.isAssignableFrom(TransactionFormViewModel::class.java) -> {
                TransactionFormViewModel(
                    addTransactionUseCase = appContainer.addTransactionUseCase,
                    updateTransactionUseCase = appContainer.updateTransactionUseCase,
                    getCategoriesUseCase = appContainer.getCategoriesUseCase,
                    getAccountsUseCase = appContainer.getAccountsUseCase,
                    transactionRepository = appContainer.transactionRepository,
                    getTransactionsUseCase = appContainer.getTransactionsUseCase,
                    parseReceiptUseCase = appContainer.parseReceiptUseCase
                ) as T
            }
            modelClass.isAssignableFrom(GoalsViewModel::class.java) -> {
                GoalsViewModel(
                    getSavingsGoalsUseCase = appContainer.getSavingsGoalsUseCase,
                    deleteSavingsGoalUseCase = appContainer.deleteSavingsGoalUseCase,
                    contributeToGoalUseCase = appContainer.contributeToGoalUseCase,
                    addSavingsGoalUseCase = appContainer.addSavingsGoalUseCase,
                    getAccountsUseCase = appContainer.getAccountsUseCase,
                    getCategoriesUseCase = appContainer.getCategoriesUseCase,
                    addTransactionUseCase = appContainer.addTransactionUseCase
                ) as T
            }
            modelClass.isAssignableFrom(GoalFormViewModel::class.java) -> {
                GoalFormViewModel(
                    addSavingsGoalUseCase = appContainer.addSavingsGoalUseCase,
                    updateSavingsGoalUseCase = appContainer.updateSavingsGoalUseCase,
                    savingsGoalRepository = appContainer.savingsGoalRepository
                ) as T
            }
            modelClass.isAssignableFrom(com.example.presentation.categories.CategoriesViewModel::class.java) -> {
                com.example.presentation.categories.CategoriesViewModel(
                    getCategoriesUseCase = appContainer.getCategoriesUseCase,
                    addCategoryUseCase = appContainer.addCategoryUseCase,
                    updateCategoryUseCase = appContainer.updateCategoryUseCase,
                    deleteCategoryUseCase = appContainer.deleteCategoryUseCase,
                    getTransactionsUseCase = appContainer.getTransactionsUseCase
                ) as T
            }
            modelClass.isAssignableFrom(com.example.presentation.settings.SettingsViewModel::class.java) -> {
                com.example.presentation.settings.SettingsViewModel(
                    getCategoriesUseCase = appContainer.getCategoriesUseCase,
                    addCategoryUseCase = appContainer.addCategoryUseCase,
                    categoryRepository = appContainer.categoryRepository,
                    getTransactionsUseCase = appContainer.getTransactionsUseCase
                ) as T
            }
            modelClass.isAssignableFrom(com.example.presentation.analysis.AnalysisViewModel::class.java) -> {
                com.example.presentation.analysis.AnalysisViewModel(
                    getTransactionsUseCase = appContainer.getTransactionsUseCase,
                    getCategoriesUseCase = appContainer.getCategoriesUseCase
                ) as T
            }
            modelClass.isAssignableFrom(com.example.presentation.reminders.RemindersViewModel::class.java) -> {
                com.example.presentation.reminders.RemindersViewModel(
                    reminderRepository = appContainer.reminderRepository,
                    getSavingsGoalsUseCase = appContainer.getSavingsGoalsUseCase,
                    getCategoriesUseCase = appContainer.getCategoriesUseCase,
                    addTransactionUseCase = appContainer.addTransactionUseCase,
                    contributeToGoalUseCase = appContainer.contributeToGoalUseCase
                ) as T
            }
            modelClass.isAssignableFrom(AccountsViewModel::class.java) -> {
                AccountsViewModel(
                    getAccountsUseCase = appContainer.getAccountsUseCase,
                    updateAccountUseCase = appContainer.updateAccountUseCase,
                    addAccountUseCase = appContainer.addAccountUseCase,
                    deleteAccountUseCase = appContainer.deleteAccountUseCase,
                    getTransactionsUseCase = appContainer.getTransactionsUseCase,
                    transferFundsUseCase = appContainer.transferFundsUseCase
                ) as T
            }
            modelClass.isAssignableFrom(AssistantViewModel::class.java) -> {
                AssistantViewModel(
                    geminiRepository = appContainer.geminiRepository,
                    getTransactionsUseCase = appContainer.getTransactionsUseCase,
                    getAccountsUseCase = appContainer.getAccountsUseCase,
                    getSavingsGoalsUseCase = appContainer.getSavingsGoalsUseCase,
                    getCategoriesUseCase = appContainer.getCategoriesUseCase,
                    contributeToGoalUseCase = appContainer.contributeToGoalUseCase,
                    addTransactionUseCase = appContainer.addTransactionUseCase
                ) as T
            }
            modelClass.isAssignableFrom(com.example.presentation.store.StoreViewModel::class.java) -> {
                com.example.presentation.store.StoreViewModel(
                    getTransactionsUseCase = appContainer.getTransactionsUseCase,
                    addTransactionUseCase = appContainer.addTransactionUseCase,
                    updateTransactionUseCase = appContainer.updateTransactionUseCase,
                    deleteTransactionUseCase = appContainer.deleteTransactionUseCase,
                    getCategoriesUseCase = appContainer.getCategoriesUseCase,
                    getAccountsUseCase = appContainer.getAccountsUseCase,
                    storeRepository = appContainer.storeRepository,
                    businessRepository = appContainer.businessRepository
                ) as T
            }
            modelClass.isAssignableFrom(com.example.presentation.monthlysummary.MonthlySummaryViewModel::class.java) -> {
                com.example.presentation.monthlysummary.MonthlySummaryViewModel(
                    transactionRepository = appContainer.transactionRepository
                ) as T
            }
            modelClass.isAssignableFrom(com.example.presentation.calculator.CalculatorViewModel::class.java) -> {
                com.example.presentation.calculator.CalculatorViewModel(
                    getTransactionsUseCase = appContainer.getTransactionsUseCase,
                    getAccountsUseCase = appContainer.getAccountsUseCase
                ) as T
            }
            modelClass.isAssignableFrom(com.example.presentation.recurring.RecurringExpensesViewModel::class.java) -> {
                com.example.presentation.recurring.RecurringExpensesViewModel(
                    getRecurringExpensesUseCase = appContainer.getRecurringExpensesUseCase,
                    addRecurringExpenseUseCase = appContainer.addRecurringExpenseUseCase,
                    updateRecurringExpenseUseCase = appContainer.updateRecurringExpenseUseCase,
                    deleteRecurringExpenseUseCase = appContainer.deleteRecurringExpenseUseCase,
                    processRecurringExpensesUseCase = appContainer.processRecurringExpensesUseCase,
                    getCategoriesUseCase = appContainer.getCategoriesUseCase,
                    getAccountsUseCase = appContainer.getAccountsUseCase
                ) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
