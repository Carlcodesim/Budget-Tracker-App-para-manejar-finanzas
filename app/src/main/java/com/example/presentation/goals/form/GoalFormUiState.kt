package com.example.presentation.goals.form

/**
 * Estado de la interfaz de usuario para el formulario de metas de ahorro (Agregar/Editar).
 * Implementa validación inline para garantizar que los campos requeridos estén completos.
 */
data class GoalFormUiState(
    val id: Long = 0L,
    val name: String = "",
    val targetAmount: String = "",
    val currentAmount: String = "0.0",
    val deadlineEpoch: Long? = null,
    val description: String = "",
    val priority: String = "Media",
    val category: String = "Otros",
    val isSaving: Boolean = false,
    val isLoadFinished: Boolean = false,
    val isSaveFinished: Boolean = false,

    // Mensajes de error para validaciones inline
    val nameError: String? = null,
    val targetAmountError: String? = null,
    val errorMessage: String? = null
) {
    val isFormValid: Boolean
        get() = name.isNotBlank() &&
                targetAmount.isNotBlank() &&
                targetAmount.toDoubleOrNull() != null &&
                (targetAmount.toDoubleOrNull() ?: 0.0) > 0.0
}
