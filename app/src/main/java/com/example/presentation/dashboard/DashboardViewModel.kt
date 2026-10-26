package com.example.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.Account
import com.example.domain.model.Category
import com.example.domain.model.Transaction
import com.example.domain.model.TransactionType
import com.example.domain.usecase.category.GetCategoriesUseCase
import com.example.domain.usecase.transaction.AddTransactionUseCase
import com.example.domain.usecase.transaction.GetBalanceSummaryUseCase
import com.example.domain.usecase.transaction.GetTransactionsUseCase
import com.example.domain.usecase.account.GetAccountsUseCase
import com.example.util.BudgetCycleManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date

/**
 * ViewModel para el Dashboard. Combina de manera reactiva el balance financiero general,
 * el historial y las categorías para derivar las métricas de gasto mensuales y alertas de presupuestos,
 * adaptándose al ciclo de corte financiero configurado.
 */
class DashboardViewModel(
    private val getBalanceSummaryUseCase: GetBalanceSummaryUseCase,
    private val getTransactionsUseCase: GetTransactionsUseCase,
    private val getCategoriesUseCase: GetCategoriesUseCase,
    private val getAccountsUseCase: GetAccountsUseCase,
    private val addTransactionUseCase: AddTransactionUseCase? = null,
    private val deleteTransactionUseCase: com.example.domain.usecase.transaction.DeleteTransactionUseCase? = null
) : ViewModel() {

    private val cycleSettingsFlow = combine(
        BudgetCycleManager.startDayFlow,
        BudgetCycleManager.predictedIncomeFlow
    ) { startDay, predictedIncome ->
        Pair(startDay, predictedIncome)
    }

    val uiState: StateFlow<DashboardUiState> = combine(
        getBalanceSummaryUseCase(),
        getTransactionsUseCase(),
        getCategoriesUseCase(),
        getAccountsUseCase(),
        cycleSettingsFlow
    ) { summary, transactions, categories, accounts, cycleSettings ->
        val (startDay, customPredictedIncome) = cycleSettings
        // Calcular límites de tiempo del ciclo financiero activo
        val currentCycle = BudgetCycleManager.getCurrentCycleRange(startDay)
        val startOfCycleMillis = currentCycle.startDateMillis
        val endOfCycleMillis = currentCycle.endDateMillis

        val monthlyCategoryExpenseMap = mutableMapOf<String, Double>()
        val allTimeCategoryExpenseMap = mutableMapOf<String, Double>()
        val spentMap = mutableMapOf<Long, Double>()

        for (tx in transactions) {
            if (tx.type == TransactionType.EXPENSE) {
                // Acumulación histórica por categoría
                allTimeCategoryExpenseMap[tx.category.name] = (allTimeCategoryExpenseMap[tx.category.name] ?: 0.0) + tx.amount

                // Acumulación del ciclo activo por categoría y para presupuestos
                if (tx.date.time in startOfCycleMillis..endOfCycleMillis) {
                    monthlyCategoryExpenseMap[tx.category.name] = (monthlyCategoryExpenseMap[tx.category.name] ?: 0.0) + tx.amount
                    spentMap[tx.category.id] = (spentMap[tx.category.id] ?: 0.0) + tx.amount
                }
            }
        }

        val budgetStatuses = categories.filter { it.maxMonthlyBudget != null }.map { category ->
            val spentVal = spentMap[category.id] ?: 0.0
            BudgetStatus(
                categoryId = category.id,
                categoryName = category.name,
                iconKey = category.iconKey,
                limit = category.maxMonthlyBudget!!,
                spent = spentVal
            )
        }

        // Si hay gastos en el mes actual, priorizamos mostrar el mes actual en categoryExpenseMap
        val primaryExpenseMap = if (monthlyCategoryExpenseMap.isNotEmpty()) {
            monthlyCategoryExpenseMap
        } else {
            allTimeCategoryExpenseMap
        }

        val totalBudgetLimit = budgetStatuses.sumOf { it.limit }
        val financialHealth = com.example.util.FinancialHealthCalculator.calculate(
            currentMonthIncome = summary.monthlyIncome,
            currentMonthExpense = summary.monthlyExpense,
            totalBudgetLimit = totalBudgetLimit,
            currentBalance = summary.currentBalance
        )

        val cyclePrediction = com.example.util.CyclePredictionCalculator.calculate(
            transactions = transactions,
            currentCycle = currentCycle,
            customPredictedIncome = customPredictedIncome,
            totalNetWorth = summary.currentBalance
        )

        DashboardUiState.Success(
            summary = summary,
            recentTransactions = transactions.take(5), // Tomamos los 5 movimientos más recientes
            categoryExpenseMap = primaryExpenseMap,
            monthlyCategoryExpenseMap = monthlyCategoryExpenseMap,
            allTimeCategoryExpenseMap = allTimeCategoryExpenseMap,
            budgetStatuses = budgetStatuses,
            accounts = accounts,
            categories = categories,
            financialHealth = financialHealth,
            cyclePrediction = cyclePrediction
        )
    }
    .flowOn(Dispatchers.Default)
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState.Loading
    )

    fun addRapidExpense(
        amount: Double,
        category: Category,
        account: Account,
        date: Date,
        note: String,
        onSuccess: ((Transaction) -> Unit)? = null
    ) {
        viewModelScope.launch {
            val expense = Transaction(
                amount = amount,
                type = TransactionType.EXPENSE,
                category = category,
                description = if (note.isBlank()) "Gasto Rápido" else note,
                date = date,
                accountId = account.id
            )
            val newId = addTransactionUseCase?.invoke(expense) ?: 0L
            val createdTx = expense.copy(id = newId)
            onSuccess?.invoke(createdTx)
        }
    }

    fun undoTransaction(transaction: Transaction) {
        viewModelScope.launch {
            deleteTransactionUseCase?.invoke(transaction)
        }
    }

    fun updatePredictedIncome(context: android.content.Context, amount: Double?) {
        BudgetCycleManager.setPredictedIncome(context, amount)
    }
}
