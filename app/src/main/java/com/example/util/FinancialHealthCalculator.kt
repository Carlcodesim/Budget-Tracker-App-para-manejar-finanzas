package com.example.util

import com.example.domain.model.FinancialHealthMetrics
import java.util.Calendar

/**
 * Calculador puro y optimizado del índice de salud financiera mensual, ritmo de gasto y margen diario.
 */
object FinancialHealthCalculator {

    fun calculate(
        currentMonthIncome: Double,
        currentMonthExpense: Double,
        totalBudgetLimit: Double,
        currentBalance: Double,
        calendar: Calendar = Calendar.getInstance()
    ): FinancialHealthMetrics {
        val cycle = BudgetCycleManager.getCurrentCycleRange()
        val currentDay = cycle.elapsedDays
        val maxDays = cycle.totalDaysInCycle
        val daysLeft = (maxDays - currentDay).coerceAtLeast(1)

        val currentDailyPace = if (currentDay > 0) currentMonthExpense / currentDay else 0.0
        val projectedMonthEndExpense = currentMonthExpense + (currentDailyPace * (daysLeft - 1))

        // Margen disponible para el resto del mes basado en presupuestos o ingresos
        val budgetReference = if (totalBudgetLimit > 0) totalBudgetLimit else currentMonthIncome
        val remainingBudget = if (budgetReference > 0) {
            (budgetReference - currentMonthExpense).coerceAtLeast(0.0)
        } else {
            (currentBalance).coerceAtLeast(0.0)
        }
        val dailyRecommendedBudget = remainingBudget / daysLeft

        val savingsRate = if (currentMonthIncome > 0) {
            ((currentMonthIncome - currentMonthExpense) / currentMonthIncome).toFloat()
        } else {
            0f
        }

        // Cálculo del puntaje integral (0 - 100)
        var score = 70 // Base neutral

        // Factor 1: Relación Gasto vs Ingreso
        if (currentMonthIncome > 0) {
            val expenseRatio = currentMonthExpense / currentMonthIncome
            when {
                expenseRatio <= 0.50 -> score += 20 // Excelente tasa de ahorro > 50%
                expenseRatio <= 0.70 -> score += 10 // Buen ahorro (30%-50%)
                expenseRatio <= 0.90 -> score += 0  // Aceptable
                expenseRatio <= 1.00 -> score -= 15 // Límite de equilibrio
                else -> score -= 30                 // Déficit
            }
        }

        // Factor 2: Proyección y respeto del presupuesto
        if (totalBudgetLimit > 0) {
            val budgetRatio = projectedMonthEndExpense / totalBudgetLimit
            val currentBudgetUsedRatio = currentMonthExpense / totalBudgetLimit

            if (currentMonthExpense <= totalBudgetLimit) {
                when {
                    budgetRatio <= 1.00 || currentBudgetUsedRatio <= 0.60 -> score += 10
                    budgetRatio <= 1.15 -> score += 5
                    else -> score += 0 // En los primeros días del mes, gastos fijos no restan si el gasto total está dentro del presupuesto
                }
            } else {
                when {
                    budgetRatio <= 1.15 -> score -= 15
                    else -> score -= 25
                }
            }
        }

        // Factor 3: Liquidez
        if (currentBalance < 0) {
            score -= 20
        } else if (currentBalance > currentMonthExpense * 2) {
            score += 10
        }

        val clampedScore = score.coerceIn(0, 100)

        val (statusLabel, advice) = when {
            clampedScore >= 85 -> Pair(
                "Excelente",
                "Tus finanzas están en óptimo equilibrio. Mantienes una gran tasa de ahorro y ritmo controlado."
            )
            clampedScore >= 70 -> Pair(
                "Saludable",
                "Tus gastos avanzan dentro del rango esperado. Mantén el ritmo para cerrar el mes con superávit."
            )
            clampedScore >= 50 -> Pair(
                "Moderado",
                "El ritmo de gasto está cercano al límite presupuestario. Modera gastos discrecionales."
            )
            else -> Pair(
                "Atención Requerida",
                "El ritmo proyectado supera tus ingresos o presupuestos. Prioriza solo necesidades básicas."
            )
        }

        return FinancialHealthMetrics(
            score = clampedScore,
            statusLabel = statusLabel,
            projectedMonthEndExpense = projectedMonthEndExpense,
            daysLeftInMonth = daysLeft,
            dailyRecommendedBudget = dailyRecommendedBudget,
            currentDailyPace = currentDailyPace,
            savingsRate = savingsRate,
            healthAdvice = advice
        )
    }
}
