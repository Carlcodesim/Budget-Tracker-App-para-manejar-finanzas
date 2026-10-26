package com.example.presentation.analysis

import com.example.domain.model.MonthlySpendingAggregate
import com.example.domain.model.Transaction

/**
 * Representa la participación de una categoría en los gastos totales del mes.
 */
data class CategoryExpenseShare(
    val categoryId: Long,
    val categoryName: String,
    val iconKey: String,
    val amount: Double,
    val percentage: Float
)

/**
 * Datos comparativos de ahorro entre el mes actual y el mes anterior.
 */
data class SavingsTrendData(
    val currentMonthSavings: Double,
    val previousMonthSavings: Double,
    val savingsRateCurrent: Float,
    val savingsRatePrevious: Float,
    val savingsChangePercentage: Float,
    val isPositiveTrend: Boolean
)

/**
 * Representa los gastos e ingresos agrupados por mes para el gráfico de barras.
 */
data class MonthlyExpenseData(
    val monthName: String,
    val year: Int,
    val totalExpense: Double,
    val totalIncome: Double
)

/**
 * Velocidad y variación intermensual del gasto por categoría.
 */
data class CategorySpendingVelocity(
    val categoryId: Long,
    val categoryName: String,
    val iconKey: String,
    val currentMonthAmount: Double,
    val previousMonthAmount: Double,
    val percentageChange: Float,
    val isIncrease: Boolean
)

/**
 * Análisis del presupuesto basado en la regla 50/30/20 (Necesidades, Deseos, Ahorro).
 */
data class BudgetRule503020(
    val needsAmount: Double,
    val needsPercentage: Float,
    val wantsAmount: Double,
    val wantsPercentage: Float,
    val savingsAmount: Double,
    val savingsPercentage: Float,
    val statusSummary: String,
    val isBalanced: Boolean,
    val needsExceeded: Boolean,
    val wantsExceeded: Boolean,
    val savingsOnTrack: Boolean
)

/**
 * Estados de la interfaz de usuario para la pantalla de Análisis Financiero.
 */
sealed interface AnalysisUiState {
    data object Loading : AnalysisUiState
    
    data class Success(
        val categoryExpenses: List<CategoryExpenseShare>,
        val currentMonthIncome: Double,
        val currentMonthExpense: Double,
        val previousMonthIncome: Double,
        val previousMonthExpense: Double,
        val monthlyHistory: List<MonthlyExpenseData>,
        val monthlyAggregates: List<MonthlySpendingAggregate> = emptyList(),
        val savingsTrend: SavingsTrendData,
        val topExpenseCategory: CategoryExpenseShare?,
        val averageDailySpendingCurrent: Double,
        val averageDailySpendingPrevious: Double,
        val elapsedDaysCurrent: Int,
        val elapsedDaysPrevious: Int,
        val hasTransactions: Boolean,
        val categoryVelocities: List<CategorySpendingVelocity> = emptyList(),
        val budgetRule503020: BudgetRule503020? = null
    ) : AnalysisUiState
    
    data class Error(val message: String) : AnalysisUiState
}
