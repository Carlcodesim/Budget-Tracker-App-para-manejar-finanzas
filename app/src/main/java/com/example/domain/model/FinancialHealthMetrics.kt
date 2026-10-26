package com.example.domain.model

/**
 * Representa el diagnóstico reactivo de salud financiera del usuario para el mes en curso.
 * Incluye un puntaje global (0-100), ritmo de gasto proyectado y sugerencia de gasto diario seguro.
 */
data class FinancialHealthMetrics(
    val score: Int,
    val statusLabel: String,
    val projectedMonthEndExpense: Double,
    val daysLeftInMonth: Int,
    val dailyRecommendedBudget: Double,
    val currentDailyPace: Double,
    val savingsRate: Float,
    val healthAdvice: String
)
