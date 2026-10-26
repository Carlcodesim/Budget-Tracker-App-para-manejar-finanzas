package com.example.presentation.goals.list

import com.example.domain.model.SavingsGoal

/**
 * Estados de la interfaz de usuario para la pantalla de metas de ahorro.
 */
sealed interface GoalsUiState {
    data object Loading : GoalsUiState
    data class Success(
        val goals: List<com.example.domain.model.SavingsGoal>,
        val accounts: List<com.example.domain.model.Account> = emptyList()
    ) : GoalsUiState
    data class Error(val message: String) : GoalsUiState
}
