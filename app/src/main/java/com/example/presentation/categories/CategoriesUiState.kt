package com.example.presentation.categories

import com.example.domain.model.Category

/**
 * Información de presupuesto en tiempo real para una categoría durante el mes actual.
 */
data class CategoryBudgetInfo(
    val categoryId: Long,
    val spentThisMonth: Double,
    val budgetLimit: Double?,
    val percentageUsed: Float,
    val remainingBudget: Double?,
    val isOverBudget: Boolean
)

/**
 * Estado de la interfaz de usuario para la pantalla de gestión de categorías.
 */
data class CategoriesUiState(
    val categories: List<Category> = emptyList(),
    val filteredCategories: List<Category> = emptyList(),
    val categoryBudgets: Map<Long, CategoryBudgetInfo> = emptyMap(),
    val searchQuery: String = "",
    val selectedFilterType: String = "ALL", // "ALL", "EXPENSE", "INCOME", "WITH_BUDGET", "OVER_BUDGET"
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,

    // Formulario de Agregar / Editar
    val isAddEditBottomSheetOpen: Boolean = false,
    val categoryBeingEdited: Category? = null,
    val formName: String = "",
    val formIconKey: String = "restaurant",
    val formColorHex: String = "#EF4444",
    val formType: String = "EXPENSE", // "EXPENSE" o "INCOME"
    val formMonthlyBudget: String = "",
    val isSaving: Boolean = false,

    // Diálogo de ajuste rápido de presupuesto
    val categoryForQuickBudget: Category? = null,

    // Diálogo de confirmación de eliminación
    val categoryToDelete: Category? = null,
    val isDeleting: Boolean = false
) {
    val totalCount: Int get() = categories.size
    val expenseCount: Int get() = categories.count { it.type == "EXPENSE" }
    val incomeCount: Int get() = categories.count { it.type == "INCOME" }
    val withBudgetCount: Int get() = categories.count { it.maxMonthlyBudget != null && it.maxMonthlyBudget > 0.0 }
    val overBudgetCount: Int get() = categoryBudgets.values.count { it.isOverBudget }
    val isFormValid: Boolean get() = formName.trim().isNotBlank()
    val isEditing: Boolean get() = categoryBeingEdited != null
}
