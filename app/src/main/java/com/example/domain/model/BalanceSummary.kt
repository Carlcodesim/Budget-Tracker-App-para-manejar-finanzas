package com.example.domain.model

/**
 * Representa el resumen financiero del balance actual, ingresos y gastos totales.
 */
data class BalanceSummary(
    val currentBalance: Double,
    val totalIncome: Double,
    val totalExpense: Double,
    val monthlyIncome: Double,
    val monthlyExpense: Double
)
