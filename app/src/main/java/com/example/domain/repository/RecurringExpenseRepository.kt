package com.example.domain.repository

import com.example.domain.model.ProcessRecurringResult
import com.example.domain.model.RecurringExpense
import com.example.domain.model.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Contrato del repositorio para la gestión de gastos recurrentes.
 */
interface RecurringExpenseRepository {

    fun getAllRecurringExpenses(): Flow<List<RecurringExpense>>

    suspend fun getRecurringExpenseById(id: Long): RecurringExpense?

    suspend fun insertRecurringExpense(recurringExpense: RecurringExpense): Long

    suspend fun updateRecurringExpense(recurringExpense: RecurringExpense)

    suspend fun deleteRecurringExpense(id: Long)

    suspend fun setEnabled(id: Long, isEnabled: Boolean)

    /**
     * Procesa y genera automáticamente las transacciones pendientes para las fechas
     * programadas del mes actual o especificado.
     *
     * @param targetYearMonth Mes objetivo en formato "yyyy-MM" (por defecto el mes actual)
     * @param forceAllDue Si es true, procesa incluso aquellos cuya fecha aún no ha llegado en el mes actual
     */
    suspend fun processRecurringExpenses(
        targetYearMonth: String? = null,
        forceAllDue: Boolean = false
    ): ProcessRecurringResult

    /**
     * Fuerza la creación inmediata de la transacción correspondiente a un gasto recurrente específico.
     */
    suspend fun executeSingleRecurringExpenseNow(id: Long, paidAmount: Double): Boolean
}
