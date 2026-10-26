package com.example.domain.model

/**
 * Domain model representing aggregated monthly spending and income data.
 */
data class MonthlySpendingAggregate(
    val monthIndex: Int, // 0-11
    val monthName: String, // "Ene", "Feb", etc.
    val year: Int,
    val totalExpense: Double,
    val totalIncome: Double,
    val transactionCount: Int = 0
) {
    val netSavings: Double
        get() = totalIncome - totalExpense

    val savingsRatePercentage: Float
        get() = if (totalIncome > 0) ((netSavings / totalIncome) * 100f).toFloat().coerceIn(-100f, 100f) else 0f
}
