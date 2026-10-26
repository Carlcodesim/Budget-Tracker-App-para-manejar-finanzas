package com.example.presentation.reminders

import com.example.domain.model.Reminder
import com.example.domain.model.SavingsGoal
import com.example.domain.model.Category

/**
 * Estados de la UI para la pantalla de Recordatorios.
 */
sealed interface RemindersUiState {
    data object Loading : RemindersUiState

    data class Success(
        val reminders: List<Reminder>,
        val savingsGoals: List<SavingsGoal> = emptyList(),
        val categories: List<Category> = emptyList(),
        val successMessage: String? = null,
        val errorMessage: String? = null,
        val simulatedNotification: SimulatedNotification? = null
    ) : RemindersUiState
}

/**
 * Modelo para representar una notificación simulada en pantalla.
 */
data class SimulatedNotification(
    val title: String,
    val message: String,
    val type: String, // "REGISTRO_GASTOS", "PAGO_UNIVERSIDAD", "PAGO_SERVICIOS", "META_AHORRO"
    val timestamp: Long = System.currentTimeMillis()
)
