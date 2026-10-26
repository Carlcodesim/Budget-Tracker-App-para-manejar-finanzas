package com.example.domain.model

/**
 * Representa un recordatorio financiero en la capa de dominio.
 */
data class Reminder(
    val id: Long = 0,
    val type: String, // "REGISTRO_GASTOS", "PAGO_UNIVERSIDAD", "PAGO_SERVICIOS", "META_AHORRO", "PAGO_TARJETA", "OTRO"
    val title: String,
    val description: String,
    val isEnabled: Boolean = true,
    val amount: Double? = null,
    val dueDateDay: Int? = null, // Día del mes para vencimiento (ej. 15)
    val time: String? = null, // Hora para registro de gastos (ej. "20:00")
    val notifyDaysBefore: Int = 1, // Días antes del vencimiento para notificar (ej. 1 = 1 día antes, 0 = mismo día)
    val notifyTime: String = "09:00", // Hora de notificación (ej. "09:00")
    val associatedGoalId: Long? = null, // Meta de ahorro asociada si aplica
    val isCompletedThisMonth: Boolean = false // Si el pago de este mes ya se realizó
)
