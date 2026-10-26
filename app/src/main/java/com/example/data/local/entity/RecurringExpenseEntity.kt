package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entidad de base de datos Room para la gestión de gastos y suscripciones recurrentes.
 * Permite la generación automática de transacciones en fechas específicas de cada mes.
 */
@Entity(
    tableName = "recurring_expenses",
    indices = [
        Index(value = ["categoryId"]),
        Index(value = ["accountId"]),
        Index(value = ["dayOfMonth"]),
        Index(value = ["isEnabled"])
    ]
)
data class RecurringExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val amount: Double,
    val categoryId: Long,
    val accountId: Long = 1L,
    val dayOfMonth: Int, // Día del mes en que se ejecuta el gasto (1..31)
    val description: String? = null,
    val tags: String? = null,
    val isEnabled: Boolean = true,
    val lastExecutedMonth: String? = null, // Formato "yyyy-MM" para evitar cobros duplicados en el mismo mes
    val autoCreate: Boolean = true, // Si es true, el sistema crea la transacción automáticamente en la fecha
    val createdAt: Long = System.currentTimeMillis()
)
