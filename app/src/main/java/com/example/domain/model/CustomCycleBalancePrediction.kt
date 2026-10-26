package com.example.domain.model

import com.example.util.BudgetCycleRange

/**
 * Modelo de predicción y análisis del ciclo de presupuesto personalizado actual.
 * Proporciona el balance real versus el ingreso previsto con telemetría día a día estilo Recharts/D3.
 */
data class CustomCycleBalancePrediction(
    val cycleRange: BudgetCycleRange,
    val actualBalance: Double,             // Balance neto del período (Ingresos del ciclo - Gastos del ciclo)
    val actualNetWorth: Double,            // Balance global en todas las cuentas (liquidez total)
    val actualIncome: Double,              // Ingresos reales registrados en el ciclo activo
    val actualExpense: Double,             // Gastos reales registrados en el ciclo activo
    val predictedIncome: Double,           // Ingreso previsto/estimado para el período
    val incomeProgressPercent: Float,      // % del ingreso previsto ya recibido (ej. 0.50f = 50%)
    val balanceVsPredictedRatio: Float,    // Ratio de balance neto sobre ingreso previsto
    val remainingPredictedIncome: Double,  // Monto de ingreso previsto pendiente por recibir
    val daysTotal: Int,                    // Total de días del ciclo
    val daysElapsed: Int,                  // Días transcurridos
    val daysRemaining: Int,                // Días restantes para el corte
    val timelineDataPoints: List<CycleDayDataPoint>, // Trayectoria día a día para Recharts/D3 Canvas
    val isCustomCycle: Boolean,            // true si el corte es diferente del día 1
    val isAutoEstimated: Boolean           // true si el ingreso previsto fue inferido automáticamente
)

/**
 * Punto de datos diario dentro del ciclo de presupuesto para renderizado en Canvas estilo Recharts / D3.
 */
data class CycleDayDataPoint(
    val dayIndex: Int,                 // 1..totalDays
    val dayOfMonth: Int,               // Día del mes calendario (ej. 30, 31, 1, 2...)
    val timestamp: Long,               // Epoch millis del día
    val dateLabel: String,             // ej. "30 Oct", "15 Nov"
    val accumulatedBalance: Double,    // Balance neto acumulado (ingresos acumulados - gastos acumulados)
    val accumulatedIncome: Double,     // Ingreso acumulado hasta este día
    val accumulatedExpense: Double,    // Gasto acumulado hasta este día
    val predictedIncomeBaseline: Double, // Línea de referencia constante de ingreso previsto
    val projectedTrajectory: Double,   // Trayectoria proyectada de avance (D3 interpolation)
    val isPastOrToday: Boolean         // true si la fecha ya ocurrió o es hoy
)
