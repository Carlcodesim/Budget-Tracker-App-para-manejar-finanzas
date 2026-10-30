package com.example.domain.model

import java.util.Calendar

/**
 * Modelo de dominio para un gasto recurrente programado mensual.
 */
data class RecurringExpense(
    val id: Long = 0,
    val title: String,
    val amount: Double,
    val category: Category,
    val account: Account? = null,
    val accountId: Long = 1L,
    val dayOfMonth: Int, // Día del mes (1..31)
    val description: String? = null,
    val tags: String? = null,
    val isEnabled: Boolean = true,
    val lastExecutedMonth: String? = null, // "yyyy-MM"
    val autoCreate: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    /**
     * Determina si el gasto ya ha sido ejecutado o generado como transacción en el mes dado ("yyyy-MM").
     */
    fun isExecutedInMonth(yearMonth: String): Boolean {
        return lastExecutedMonth == yearMonth
    }

    /**
     * Calcula los días restantes para la fecha de cobro en el mes actual.
     * Si hoy es menor que el día de cobro, retorna la diferencia.
     * Si hoy es igual, retorna 0.
     * Si hoy es mayor, indica cuántos días han pasado o días para el próximo mes.
     */
    fun getDaysUntilDue(currentDay: Int): Int {
        return dayOfMonth - currentDay
    }

    /**
     * Verifica si el gasto está pendiente de ejecución automática para el día actual y mes dado.
     */
    fun effectiveDayInMonth(daysInMonth: Int): Int {
        require(daysInMonth in 1..31) { "Los días del mes deben estar entre 1 y 31." }
        return dayOfMonth.coerceIn(1, daysInMonth)
    }

    /**
     * Verifica si el gasto ya llegó a su fecha efectiva de ejecución en el mes actual.
     * Los días 29, 30 o 31 se ajustan al último día cuando el mes es más corto.
     */
    fun isDueForExecution(
        currentDay: Int,
        currentYearMonth: String,
        daysInMonth: Int = 31
    ): Boolean {
        return isEnabled &&
            autoCreate &&
            !isExecutedInMonth(currentYearMonth) &&
            currentDay >= effectiveDayInMonth(daysInMonth)
    }
}

/**
 * Resultado de la ejecución y procesamiento de gastos recurrentes automáticos.
 */
data class ProcessRecurringResult(
    val executedCount: Int,
    val totalAmount: Double,
    val createdTransactions: List<Transaction>
)
