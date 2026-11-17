package com.example.domain.model

import java.util.Calendar
import java.util.Locale

enum class RecurrenceFrequency(
    val label: String,
    val monthlyFactor: Double
) {
    WEEKLY("Semanal", 52.0 / 12.0),
    BIWEEKLY("Quincenal · cada 15 días", 365.2425 / 15.0 / 12.0),
    MONTHLY("Mensual", 1.0),
    YEARLY("Anual", 1.0 / 12.0);

    companion object {
        fun fromStorage(value: String?): RecurrenceFrequency =
            values().firstOrNull { it.name == value } ?: MONTHLY
    }
}

/**
 * Calcula la siguiente fecha del ciclo sin perder el día original en meses cortos.
 * La frecuencia quincenal se define explícitamente como cada 15 días.
 */
fun calculateNextDueDate(
    currentDueDate: Long,
    frequency: RecurrenceFrequency,
    anchorDate: Long
): Long {
    val current = Calendar.getInstance().apply { timeInMillis = currentDueDate }
    val anchor = Calendar.getInstance().apply {
        timeInMillis = anchorDate.takeIf { it > 0L } ?: currentDueDate
    }

    when (frequency) {
        RecurrenceFrequency.WEEKLY -> current.add(Calendar.DAY_OF_MONTH, 7)
        RecurrenceFrequency.BIWEEKLY -> current.add(Calendar.DAY_OF_MONTH, 15)
        RecurrenceFrequency.MONTHLY -> {
            current.set(Calendar.DAY_OF_MONTH, 1)
            current.add(Calendar.MONTH, 1)
            current.set(
                Calendar.DAY_OF_MONTH,
                anchor.get(Calendar.DAY_OF_MONTH).coerceAtMost(current.getActualMaximum(Calendar.DAY_OF_MONTH))
            )
        }
        RecurrenceFrequency.YEARLY -> {
            current.add(Calendar.YEAR, 1)
            current.set(Calendar.MONTH, anchor.get(Calendar.MONTH))
            current.set(Calendar.DAY_OF_MONTH, 1)
            current.set(
                Calendar.DAY_OF_MONTH,
                anchor.get(Calendar.DAY_OF_MONTH).coerceAtMost(current.getActualMaximum(Calendar.DAY_OF_MONTH))
            )
        }
    }

    current.set(Calendar.HOUR_OF_DAY, 0)
    current.set(Calendar.MINUTE, 0)
    current.set(Calendar.SECOND, 0)
    current.set(Calendar.MILLISECOND, 0)
    return current.timeInMillis
}

/**
 * Modelo de dominio para un gasto periódico.
 */
data class RecurringExpense(
    val id: Long = 0,
    val title: String,
    val amount: Double,
    val category: Category,
    val account: Account? = null,
    val accountId: Long = 0L,
    val dayOfMonth: Int,
    val description: String? = null,
    val tags: String? = null,
    val isEnabled: Boolean = true,
    val lastExecutedMonth: String? = null,
    val lastExecutedAmount: Double? = null,
    val amountPaidThisMonth: Double = 0.0,
    val paymentCountThisMonth: Int = 0,
    val autoCreate: Boolean = false,
    val frequency: RecurrenceFrequency = RecurrenceFrequency.MONTHLY,
    val anchorDate: Long = 0L,
    val nextDueDate: Long = 0L,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun isExecutedInMonth(yearMonth: String): Boolean =
        lastExecutedMonth == yearMonth

    /**
     * Los registros anteriores a la frecuencia configurable se mantienen mensuales.
     * Si su vencimiento de este mes ya pasó, se muestra el siguiente mes para no
     * provocar cargos retroactivos al actualizar la aplicación.
     */
    fun resolvedNextDueDate(nowMillis: Long = System.currentTimeMillis()): Long {
        if (nextDueDate > 0L) return nextDueDate

        val now = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val currentYearMonth = String.format(
            Locale.US,
            "%04d-%02d",
            now.get(Calendar.YEAR),
            now.get(Calendar.MONTH) + 1
        )
        val due = (now.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            set(Calendar.DAY_OF_MONTH, dayOfMonth.coerceIn(1, getActualMaximum(Calendar.DAY_OF_MONTH)))
        }
        val todayStart = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (lastExecutedMonth == currentYearMonth || due.before(todayStart)) {
            due.add(Calendar.MONTH, 1)
            due.set(Calendar.DAY_OF_MONTH, dayOfMonth.coerceIn(1, due.getActualMaximum(Calendar.DAY_OF_MONTH)))
        }
        return due.timeInMillis
    }

    fun effectiveDayInMonth(daysInMonth: Int): Int {
        require(daysInMonth in 1..31) { "Los días del mes deben estar entre 1 y 31." }
        return dayOfMonth.coerceIn(1, daysInMonth)
    }

    fun isDueForExecution(
        currentDay: Int,
        currentYearMonth: String,
        daysInMonth: Int = 31
    ): Boolean {
        return isEnabled && autoCreate && !isExecutedInMonth(currentYearMonth) &&
            currentDay >= effectiveDayInMonth(daysInMonth)
    }

    val estimatedMonthlyAmount: Double
        get() = amount * frequency.monthlyFactor
}

data class ProcessRecurringResult(
    val executedCount: Int,
    val totalAmount: Double,
    val createdTransactions: List<Transaction>
)
