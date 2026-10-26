package com.example.presentation.settings

import com.example.domain.model.Category

/**
 * Estado de la interfaz de usuario para la pantalla de configuración de categorías.
 */
data class SettingsUiState(
    val categories: List<Category> = emptyList(),
    val name: String = "",
    val selectedIconKey: String = "attach_money",
    val selectedColorHex: String = "#3B82F6",
    val selectedType: String = "EXPENSE",
    val budgetLimit: String = "",
    val isSaving: Boolean = false,
    val nameError: String? = null,
    val successMessage: String? = null,
    val errorMessage: String? = null,
    val totalMonthlyBudget: Double = 0.0,
    val totalMonthlySpent: Double = 0.0
) {
    val isFormValid: Boolean
        get() = name.isNotBlank()
}
