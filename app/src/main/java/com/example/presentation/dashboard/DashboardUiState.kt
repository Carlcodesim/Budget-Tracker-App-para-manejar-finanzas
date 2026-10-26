package com.example.presentation.dashboard

import com.example.domain.model.BalanceSummary
import com.example.domain.model.Transaction
import com.example.domain.model.Account
import com.example.domain.model.Category
import com.example.domain.model.FinancialHealthMetrics

data class BudgetStatus(
    val categoryId: Long,
    val categoryName: String,
    val iconKey: String,
    val limit: Double,
    val spent: Double,
    val isExceeded: Boolean = spent > limit,
    val percentage: Float = if (limit > 0) (spent / limit).toFloat() else 0f
)

/**
 * Estados de la interfaz de usuario para la pantalla de Dashboard.
 */
sealed interface DashboardUiState {
    data object Loading : DashboardUiState
    data class Success(
        val summary: BalanceSummary,
        val recentTransactions: List<Transaction>,
        val categoryExpenseMap: Map<String, Double> = emptyMap(),
        val monthlyCategoryExpenseMap: Map<String, Double> = emptyMap(),
        val allTimeCategoryExpenseMap: Map<String, Double> = emptyMap(),
        val budgetStatuses: List<BudgetStatus> = emptyList(),
        val accounts: List<Account> = emptyList(),
        val categories: List<Category> = emptyList(),
        val financialHealth: FinancialHealthMetrics? = null,
        val cyclePrediction: com.example.domain.model.CustomCycleBalancePrediction? = null
    ) : DashboardUiState
    data class Error(val message: String) : DashboardUiState
}
