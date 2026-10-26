package com.example.presentation.monthlysummary

import com.example.domain.model.Transaction

/**
 * Estado de la velocidad o ritmo de gasto en el mes.
 */
enum class SpendingVelocityStatus {
    OPTIMAL,   // Ritmo bajo / controlado
    MODERATE,  // Ritmo normal
    ACCELERATED // Ritmo alto
}

/**
 * Representa la participación y agregación de gastos por categoría en el mes.
 */
data class CategoryMonthlyShare(
    val categoryId: Long,
    val categoryName: String,
    val iconKey: String,
    val totalAmount: Double,
    val transactionCount: Int,
    val percentageOfTotal: Float,
    val dailyAverageForCategory: Double
)

/**
 * Representa el gasto de un día específico del mes para el gráfico diario.
 */
data class DailySpendingItem(
    val day: Int,
    val dayOfWeek: String,
    val amount: Double,
    val transactionCount: Int,
    val isPeakDay: Boolean
)

/**
 * Representa la agregación semanal dentro del mes.
 */
data class WeeklySpendingItem(
    val weekNumber: Int,
    val weekLabel: String,
    val dateRangeLabel: String,
    val totalAmount: Double,
    val transactionCount: Int,
    val percentageOfTotal: Float
)

/**
 * Representa el desglose quincenal dentro del ciclo de presupuesto (armonización de nóminas de día 15 y día 30).
 */
data class FortnightSpendingItem(
    val fortnightNumber: Int, // 1 o 2
    val title: String, // ej. "1ra Quincena" o "2da Quincena"
    val dateRangeLabel: String, // ej. "30 Oct - 14 Nov" o "15 Nov - 29 Nov"
    val incomeAmount: Double,
    val expenseAmount: Double,
    val netBalance: Double,
    val transactionCount: Int,
    val expenseRatio: Float = if (incomeAmount > 0) (expenseAmount / incomeAmount).toFloat().coerceIn(0f, 1f) else 0f
)

/**
 * Resumen consolidado y agregado de gastos mensuales extraídos de Room Database.
 */
data class MonthlySpendingSummary(
    val year: Int,
    val month: Int, // 0-indexed (0 = Enero, 11 = Diciembre)
    val monthName: String,
    val totalExpense: Double,
    val totalIncome: Double,
    val netSavings: Double,
    val transactionCount: Int,
    val dailyAverage: Double,
    val averagePerTransaction: Double = 0.0,
    val savingsRatePercentage: Double = 0.0,
    val daysInMonth: Int,
    val elapsedDays: Int,
    val projectedMonthEndExpense: Double,
    val peakExpenseDay: Int?,
    val peakExpenseAmount: Double,
    val previousMonthExpense: Double,
    val expenseDeltaPercentage: Double,
    val isExpenseHigherThanPrevious: Boolean,
    val categoryBreakdown: List<CategoryMonthlyShare>,
    val dailySpendingHistory: List<DailySpendingItem>,
    val weeklySpendingHistory: List<WeeklySpendingItem> = emptyList(),
    val fortnights: List<FortnightSpendingItem> = emptyList(),
    val topExpenses: List<Transaction>,
    val allMonthExpenses: List<Transaction> = emptyList(),
    val spendingVelocity: SpendingVelocityStatus,
    val hasExpenses: Boolean,
    val dateRangeLabel: String = "",
    val isCustomCycle: Boolean = false
)

/**
 * Estados de la pantalla de Resumen Mensual de Gastos.
 */
sealed interface MonthlySummaryUiState {
    data object Loading : MonthlySummaryUiState

    data class Success(
        val summary: MonthlySpendingSummary,
        val selectedYear: Int,
        val selectedMonth: Int,
        val availableYears: List<Int> = listOf(2024, 2025, 2026, 2027)
    ) : MonthlySummaryUiState

    data class Error(val message: String) : MonthlySummaryUiState
}

