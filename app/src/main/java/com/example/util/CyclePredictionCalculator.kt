package com.example.util

import com.example.domain.model.CustomCycleBalancePrediction
import com.example.domain.model.CycleDayDataPoint
import com.example.domain.model.Transaction
import com.example.domain.model.TransactionType
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.max

/**
 * Calculador de alta precisión para proyectar y comparar el balance real vs el ingreso previsto
 * durante el ciclo de presupuesto personalizado activo.
 */
object CyclePredictionCalculator {

    private val salaryKeywords = listOf(
        "sueldo", "salario", "nomina", "nómina", "quincena", "honorario", "pago", "remuneracion"
    )

    fun calculate(
        transactions: List<Transaction>,
        currentCycle: BudgetCycleRange,
        customPredictedIncome: Double?,
        totalNetWorth: Double,
        nowMillis: Long = System.currentTimeMillis()
    ): CustomCycleBalancePrediction {
        val startMillis = currentCycle.startDateMillis
        val endMillis = currentCycle.endDateMillis

        // Transacciones exclusivas del ciclo activo
        val cycleTxs = transactions.filter { it.date.time in startMillis..endMillis }

        val actualIncome = cycleTxs
            .filter { it.type == TransactionType.INCOME }
            .sumOf { it.amount }

        val actualExpense = cycleTxs
            .filter { it.type == TransactionType.EXPENSE }
            .sumOf { it.amount }

        val actualBalance = actualIncome - actualExpense

        // Determinar el ingreso previsto (manual o inferido inteligentemente)
        val isAutoEstimated = customPredictedIncome == null || customPredictedIncome <= 0.0
        val predictedIncome: Double = if (!isAutoEstimated) {
            customPredictedIncome!!
        } else {
            estimatePredictedIncome(
                allTransactions = transactions,
                cycleTransactions = cycleTxs,
                currentCycle = currentCycle,
                actualIncome = actualIncome,
                actualExpense = actualExpense
            )
        }

        // Generar puntos de datos día a día para Canvas estilo Recharts / D3
        val timelineDataPoints = generateTimelinePoints(
            cycleTxs = cycleTxs,
            currentCycle = currentCycle,
            predictedIncome = predictedIncome,
            nowMillis = nowMillis
        )

        val totalDays = currentCycle.totalDaysInCycle
        val elapsedDays = currentCycle.elapsedDays.coerceIn(0, totalDays)
        val daysRemaining = (totalDays - elapsedDays).coerceAtLeast(0)

        val progressPercent = if (predictedIncome > 0.0) {
            (actualIncome / predictedIncome).toFloat().coerceAtLeast(0f)
        } else {
            0f
        }

        val balanceVsPredictedRatio = if (predictedIncome > 0.0) {
            (actualBalance / predictedIncome).toFloat()
        } else {
            0f
        }

        val remainingPredictedIncome = max(0.0, predictedIncome - actualIncome)

        return CustomCycleBalancePrediction(
            cycleRange = currentCycle,
            actualBalance = actualBalance,
            actualNetWorth = totalNetWorth,
            actualIncome = actualIncome,
            actualExpense = actualExpense,
            predictedIncome = predictedIncome,
            incomeProgressPercent = progressPercent,
            balanceVsPredictedRatio = balanceVsPredictedRatio,
            remainingPredictedIncome = remainingPredictedIncome,
            daysTotal = totalDays,
            daysElapsed = elapsedDays,
            daysRemaining = daysRemaining,
            timelineDataPoints = timelineDataPoints,
            isCustomCycle = currentCycle.isCustomCycle,
            isAutoEstimated = isAutoEstimated
        )
    }

    /**
     * Infiere el ingreso previsto para el ciclo a partir de los patrones de nómina (ej. quincenas 15 y 30),
     * historial de ciclos previos o el monto acumulado actual.
     */
    private fun estimatePredictedIncome(
        allTransactions: List<Transaction>,
        cycleTransactions: List<Transaction>,
        currentCycle: BudgetCycleRange,
        actualIncome: Double,
        actualExpense: Double
    ): Double {
        // 1. Detectar transacciones de nómina / sueldo en el ciclo actual o histórico
        val salaryTxs = cycleTransactions.filter { tx ->
            tx.type == TransactionType.INCOME && isSalaryTransaction(tx)
        }

        if (salaryTxs.isNotEmpty()) {
            val totalSalaryInCycle = salaryTxs.sumOf { it.amount }
            // Si el ciclo es quincenal / corte día 15 o 30 y solo se ha registrado 1 pago en la 1ra mitad
            if (salaryTxs.size == 1 && currentCycle.elapsedDays <= currentCycle.totalDaysInCycle / 2) {
                // Se espera la 2da quincena
                return totalSalaryInCycle * 2.0
            } else if (totalSalaryInCycle > 0.0) {
                return totalSalaryInCycle
            }
        }

        // 2. Revisar promedio de ingresos en los últimos 3 ciclos completados
        val pastCycles = BudgetCycleManager.getRecentCycleRanges(
            count = 4,
            startDay = currentCycle.startDayOfMonth
        ).filter { it.endDateMillis < currentCycle.startDateMillis }

        if (pastCycles.isNotEmpty()) {
            val pastIncomes = pastCycles.map { pastCycle ->
                allTransactions
                    .filter { it.type == TransactionType.INCOME && it.date.time in pastCycle.startDateMillis..pastCycle.endDateMillis }
                    .sumOf { it.amount }
            }.filter { it > 0.0 }

            if (pastIncomes.isNotEmpty()) {
                val avg = pastIncomes.average()
                return max(avg, actualIncome)
            }
        }

        // 3. Si ya hay ingresos registrados en el ciclo activo
        if (actualIncome > 0.0) {
            // Si estamos a mitad de ciclo y corte > 1, proyectar 2 quincenas si solo hay 1
            return if (currentCycle.elapsedDays <= currentCycle.totalDaysInCycle / 2 && currentCycle.isCustomCycle) {
                actualIncome * 2.0
            } else {
                actualIncome
            }
        }

        // 4. Si hay gastos presupuestados o registrados, usar un estimado con margen de ahorro
        if (actualExpense > 0.0) {
            return max(actualExpense * 1.3, 2000.0)
        }

        // 5. Valor por defecto saludable para presupuestos estándar
        return 3000.0
    }

    private fun isSalaryTransaction(tx: Transaction): Boolean {
        val desc = tx.description?.lowercase().orEmpty()
        val cat = tx.category.name.lowercase()
        return salaryKeywords.any { desc.contains(it) || cat.contains(it) }
    }

    /**
     * Genera la lista de puntos de datos para cada día del ciclo.
     */
    private fun generateTimelinePoints(
        cycleTxs: List<Transaction>,
        currentCycle: BudgetCycleRange,
        predictedIncome: Double,
        nowMillis: Long
    ): List<CycleDayDataPoint> {
        val points = mutableListOf<CycleDayDataPoint>()
        val totalDays = currentCycle.totalDaysInCycle
        val dateFormat = SimpleDateFormat("d MMM", Locale.forLanguageTag("es-ES"))

        val cal = Calendar.getInstance().apply {
            timeInMillis = currentCycle.startDateMillis
        }

        var runningIncome = 0.0
        var runningExpense = 0.0

        for (dayIndex in 1..totalDays) {
            val dayStart = Calendar.getInstance().apply {
                time = cal.time
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            val dayEnd = Calendar.getInstance().apply {
                time = cal.time
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }.timeInMillis

            val isPastOrToday = dayStart <= nowMillis
            val dayLabel = dateFormat.format(cal.time)
            val dayOfMonth = cal.get(Calendar.DAY_OF_MONTH)

            if (isPastOrToday) {
                val dayTxs = cycleTxs.filter { it.date.time in dayStart..dayEnd }
                runningIncome += dayTxs.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
                runningExpense += dayTxs.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
            }

            val projectedTrajectory = predictedIncome * (dayIndex.toDouble() / totalDays.toDouble())

            points.add(
                CycleDayDataPoint(
                    dayIndex = dayIndex,
                    dayOfMonth = dayOfMonth,
                    timestamp = dayStart,
                    dateLabel = dayLabel,
                    accumulatedBalance = runningIncome - runningExpense,
                    accumulatedIncome = runningIncome,
                    accumulatedExpense = runningExpense,
                    predictedIncomeBaseline = predictedIncome,
                    projectedTrajectory = projectedTrajectory,
                    isPastOrToday = isPastOrToday
                )
            )

            cal.add(Calendar.DAY_OF_MONTH, 1)
        }

        return points
    }
}
