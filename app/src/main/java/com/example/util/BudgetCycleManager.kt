package com.example.util

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Representa un período o ciclo de presupuesto determinado por el día de corte / inicio seleccionado.
 */
data class BudgetCycleRange(
    val year: Int,
    val month: Int, // 0-indexed (0 = Enero, 11 = Diciembre)
    val startDayOfMonth: Int,
    val startDateMillis: Long,
    val endDateMillis: Long,
    val cycleName: String, // e.g. "Noviembre 2026"
    val dateRangeLabel: String, // e.g. "30 Oct - 29 Nov"
    val totalDaysInCycle: Int,
    val elapsedDays: Int,
    val isCurrentCycle: Boolean
) {
    val isCustomCycle: Boolean
        get() = startDayOfMonth > 1

    /**
     * Título completo para encabezados de reportes.
     * Ejemplo: "Noviembre 2026 (30 Oct - 29 Nov)" o "Noviembre 2026" si es mes calendario.
     */
    val displayTitle: String
        get() = if (isCustomCycle) "$cycleName ($dateRangeLabel)" else cycleName
}

/**
 * Gestor centralizado del ciclo de presupuesto financiero.
 * Permite configurar el día de corte o inicio del mes contable (ej. día 30, 15, 25 o 1)
 * para armonizar los ingresos quincenales o de fin de mes con los gastos que realmente cubren,
 * evitando desfases y falsos déficits intermensuales.
 */
object BudgetCycleManager {
    private const val PREFS_NAME = "budget_cycle_prefs"
    private const val KEY_START_DAY = "key_cycle_start_day"
    const val DEFAULT_START_DAY = 1

    private val monthNamesSpanish = listOf(
        "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
        "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"
    )
    private val monthAbbrSpanish = listOf(
        "Ene", "Feb", "Mar", "Abr", "May", "Jun",
        "Jul", "Ago", "Sep", "Oct", "Nov", "Dic"
    )

    private const val KEY_PREDICTED_INCOME = "key_cycle_predicted_income"

    private val _startDayFlow = MutableStateFlow(DEFAULT_START_DAY)
    val startDayFlow: StateFlow<Int> = _startDayFlow.asStateFlow()

    private val _predictedIncomeFlow = MutableStateFlow<Double?>(null)
    val predictedIncomeFlow: StateFlow<Double?> = _predictedIncomeFlow.asStateFlow()

    /**
     * Inicializa las preferencias del ciclo de presupuesto desde SharedPreferences.
     */
    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedDay = prefs.getInt(KEY_START_DAY, DEFAULT_START_DAY).coerceIn(1, 31)
        _startDayFlow.value = savedDay

        val savedIncome = prefs.getFloat(KEY_PREDICTED_INCOME, -1f).toDouble()
        if (savedIncome > 0.0) {
            _predictedIncomeFlow.value = savedIncome
        }
    }

    /**
     * Retorna el día de inicio del ciclo actualmente configurado (1 a 31).
     */
    fun getStartDay(): Int = _startDayFlow.value

    /**
     * Actualiza y persiste el día de corte o inicio de presupuesto.
     */
    fun setStartDay(context: Context, day: Int) {
        val validDay = day.coerceIn(1, 31)
        _startDayFlow.value = validDay
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_START_DAY, validDay).apply()
    }

    /**
     * Retorna el ingreso previsto configurado manualmente, o null si debe calcularse automáticamente.
     */
    fun getPredictedIncome(): Double? = _predictedIncomeFlow.value

    /**
     * Actualiza y persiste el ingreso previsto para el ciclo financiero.
     * Si [amount] es null o <= 0, se remueve para cálculo automático inteligente.
     */
    fun setPredictedIncome(context: Context, amount: Double?) {
        val validAmount = if (amount != null && amount > 0.0) amount else null
        _predictedIncomeFlow.value = validAmount
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (validAmount != null) {
            prefs.edit().putFloat(KEY_PREDICTED_INCOME, validAmount.toFloat()).apply()
        } else {
            prefs.edit().remove(KEY_PREDICTED_INCOME).apply()
        }
    }

    /**
     * Calcula el rango de fechas exacto para un mes y año objetivo respetando el día de inicio configurado.
     *
     * @param year Año calendario objetivo
     * @param month Mes calendario objetivo (0 = Enero, 11 = Diciembre)
     * @param startDay Día de corte (1 a 31)
     * @param nowMillis Timestamp actual para determinar días transcurridos
     */
    fun getCycleRange(
        year: Int,
        month: Int,
        startDay: Int = getStartDay(),
        nowMillis: Long = System.currentTimeMillis()
    ): BudgetCycleRange {
        val startCal = Calendar.getInstance()
        val endCal = Calendar.getInstance()

        val cycleMonthName = "${monthNamesSpanish[month]} $year"

        if (startDay == 1) {
            // Mes calendario estándar: 1ro del mes al último día del mes
            startCal.apply {
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month)
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            val daysInMonth = startCal.getActualMaximum(Calendar.DAY_OF_MONTH)

            endCal.apply {
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month)
                set(Calendar.DAY_OF_MONTH, daysInMonth)
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }

            val startDateMillis = startCal.timeInMillis
            val endDateMillis = endCal.timeInMillis
            val isCurrentCycle = nowMillis in startDateMillis..endDateMillis

            val elapsedDays = if (isCurrentCycle) {
                val nowCal = Calendar.getInstance().apply { timeInMillis = nowMillis }
                nowCal.get(Calendar.DAY_OF_MONTH).coerceIn(1, daysInMonth)
            } else if (nowMillis > endDateMillis) {
                daysInMonth
            } else {
                0
            }

            val dateRangeLabel = "1 ${monthAbbrSpanish[month]} - $daysInMonth ${monthAbbrSpanish[month]}"

            return BudgetCycleRange(
                year = year,
                month = month,
                startDayOfMonth = 1,
                startDateMillis = startDateMillis,
                endDateMillis = endDateMillis,
                cycleName = cycleMonthName,
                dateRangeLabel = dateRangeLabel,
                totalDaysInCycle = daysInMonth,
                elapsedDays = elapsedDays,
                isCurrentCycle = isCurrentCycle
            )
        } else {
            // Ciclo personalizado: Inicia en el mes anterior en el día de corte
            // y culmina en el mes objetivo el día (startDay - 1)
            val prevMonthCal = Calendar.getInstance().apply {
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month)
                add(Calendar.MONTH, -1)
            }
            val prevYear = prevMonthCal.get(Calendar.YEAR)
            val prevMonth = prevMonthCal.get(Calendar.MONTH)
            val prevMaxDays = prevMonthCal.getActualMaximum(Calendar.DAY_OF_MONTH)
            val actualStartDay = startDay.coerceIn(1, prevMaxDays)

            startCal.apply {
                set(Calendar.YEAR, prevYear)
                set(Calendar.MONTH, prevMonth)
                set(Calendar.DAY_OF_MONTH, actualStartDay)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            val targetMonthCal = Calendar.getInstance().apply {
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month)
            }
            val targetMaxDays = targetMonthCal.getActualMaximum(Calendar.DAY_OF_MONTH)
            val actualEndDay = (startDay - 1).coerceIn(1, targetMaxDays)

            endCal.apply {
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month)
                set(Calendar.DAY_OF_MONTH, actualEndDay)
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }

            val startDateMillis = startCal.timeInMillis
            val endDateMillis = endCal.timeInMillis
            val isCurrentCycle = nowMillis in startDateMillis..endDateMillis

            // Cálculo preciso de días totales en el ciclo
            val diffMillis = (endDateMillis - startDateMillis).coerceAtLeast(0)
            val totalDays = (diffMillis / (24L * 60 * 60 * 1000) + 1).toInt().coerceAtLeast(1)

            val elapsedDays = if (isCurrentCycle) {
                val elapsedMillis = (nowMillis - startDateMillis).coerceAtLeast(0)
                ((elapsedMillis / (24L * 60 * 60 * 1000)) + 1).toInt().coerceIn(1, totalDays)
            } else if (nowMillis > endDateMillis) {
                totalDays
            } else {
                0
            }

            val dateRangeLabel = "$actualStartDay ${monthAbbrSpanish[prevMonth]} - $actualEndDay ${monthAbbrSpanish[month]}"

            return BudgetCycleRange(
                year = year,
                month = month,
                startDayOfMonth = startDay,
                startDateMillis = startDateMillis,
                endDateMillis = endDateMillis,
                cycleName = cycleMonthName,
                dateRangeLabel = dateRangeLabel,
                totalDaysInCycle = totalDays,
                elapsedDays = elapsedDays,
                isCurrentCycle = isCurrentCycle
            )
        }
    }

    /**
     * Retorna el ciclo financiero actualmente en curso según la fecha actual del sistema.
     */
    fun getCurrentCycleRange(
        startDay: Int = getStartDay(),
        nowMillis: Long = System.currentTimeMillis()
    ): BudgetCycleRange {
        val nowCal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val curDay = nowCal.get(Calendar.DAY_OF_MONTH)
        val curYear = nowCal.get(Calendar.YEAR)
        val curMonth = nowCal.get(Calendar.MONTH)

        val targetYear: Int
        val targetMonth: Int

        if (startDay == 1) {
            targetYear = curYear
            targetMonth = curMonth
        } else {
            if (curDay >= startDay) {
                // Ya ocurrió el corte; los ingresos y gastos se atribuyen al mes siguiente
                if (curMonth == 11) {
                    targetYear = curYear + 1
                    targetMonth = 0
                } else {
                    targetYear = curYear
                    targetMonth = curMonth + 1
                }
            } else {
                // Aún no llega al día de corte; pertenece al ciclo del mes actual
                targetYear = curYear
                targetMonth = curMonth
            }
        }

        return getCycleRange(targetYear, targetMonth, startDay, nowMillis)
    }

    /**
     * Retorna el ciclo inmediatamente anterior al rango provisto.
     */
    fun getPreviousCycleRange(
        reference: BudgetCycleRange,
        startDay: Int = getStartDay()
    ): BudgetCycleRange {
        val prevYear: Int
        val prevMonth: Int
        if (reference.month == 0) {
            prevYear = reference.year - 1
            prevMonth = 11
        } else {
            prevYear = reference.year
            prevMonth = reference.month - 1
        }
        return getCycleRange(prevYear, prevMonth, startDay)
    }

    /**
     * Genera una lista cronológica de los últimos [count] ciclos para análisis históricos y gráficos.
     */
    fun getRecentCycleRanges(
        count: Int = 6,
        startDay: Int = getStartDay(),
        nowMillis: Long = System.currentTimeMillis()
    ): List<BudgetCycleRange> {
        val current = getCurrentCycleRange(startDay, nowMillis)
        val result = mutableListOf<BudgetCycleRange>()
        var cursor = current
        result.add(cursor)

        for (i in 1 until count) {
            cursor = getPreviousCycleRange(cursor, startDay)
            result.add(cursor)
        }

        return result.reversed() // Orden ascendente cronológico
    }
}
