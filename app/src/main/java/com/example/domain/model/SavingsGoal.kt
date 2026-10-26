package com.example.domain.model

import java.util.Date

/**
 * Representa una meta de ahorro en la capa de dominio.
 */
data class SavingsGoal(
    val id: Long = 0,
    val name: String,
    val targetAmount: Double,
    val currentAmount: Double = 0.0,
    val deadline: Date?,
    val createdAt: Date,
    val description: String = "",
    val priority: String = "Media", // Alta, Media, Baja
    val category: String = "Otros"  // Viajes, Vehículo, Fondo de Emergencia, Hogar, Tecnología, Otros
) {
    val progressPercentage: Float
        get() = if (targetAmount > 0) {
            (currentAmount / targetAmount).toFloat().coerceIn(0f, 1f)
        } else {
            0f
        }

    val remainingAmount: Double
        get() = (targetAmount - currentAmount).coerceAtLeast(0.0)

    val daysRemaining: Long?
        get() = deadline?.let {
            val diff = it.time - System.currentTimeMillis()
            if (diff > 0) diff / (1000 * 60 * 60 * 24) else 0L
        }

    val recommendedMonthlySaving: Double?
        get() {
            val days = daysRemaining ?: return null
            if (days <= 0 || remainingAmount <= 0) return 0.0
            val months = (days / 30.4).coerceAtLeast(0.1)
            return remainingAmount / months
        }
}
