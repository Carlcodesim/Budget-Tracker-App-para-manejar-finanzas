package com.example.presentation.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.Category
import com.example.domain.usecase.category.AddCategoryUseCase
import com.example.domain.usecase.category.DeleteCategoryUseCase
import com.example.domain.usecase.category.GetCategoriesUseCase
import com.example.domain.usecase.category.UpdateCategoryUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

import com.example.domain.model.TransactionType
import com.example.domain.usecase.transaction.GetTransactionsUseCase
import com.example.util.BudgetCycleManager
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import java.util.Calendar

/**
 * ViewModel encargado de la lógica de negocio para la gestión completa de categorías de gastos e ingresos,
 * incluyendo control de presupuesto en tiempo real y alertas de gasto mensual adaptadas al ciclo de corte financiero.
 */
class CategoriesViewModel(
    private val getCategoriesUseCase: GetCategoriesUseCase,
    private val addCategoryUseCase: AddCategoryUseCase,
    private val updateCategoryUseCase: UpdateCategoryUseCase,
    private val deleteCategoryUseCase: DeleteCategoryUseCase,
    private val getTransactionsUseCase: GetTransactionsUseCase? = null
) : ViewModel() {

    private val _searchQueryState = MutableStateFlow("")
    private val _filterTypeState = MutableStateFlow("ALL")
    private val _uiState = MutableStateFlow(CategoriesUiState(isLoading = true))
    val uiState: StateFlow<CategoriesUiState> = _uiState.asStateFlow()

    init {
        loadCategories()
    }

    private fun loadCategories() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val txFlow = getTransactionsUseCase?.invoke() ?: flowOf(emptyList())

            combine(
                getCategoriesUseCase(),
                txFlow,
                _searchQueryState,
                _filterTypeState,
                BudgetCycleManager.startDayFlow
            ) { categoriesList, transactions, query, filterType, startDay ->
                // Calcular gastos del ciclo financiero activo por categoría
                val currentCycle = BudgetCycleManager.getCurrentCycleRange(startDay)
                val startOfCycle = currentCycle.startDateMillis
                val endOfCycle = currentCycle.endDateMillis

                val spentMap = mutableMapOf<Long, Double>()
                for (tx in transactions) {
                    if (tx.type == TransactionType.EXPENSE && tx.date.time in startOfCycle..endOfCycle) {
                        spentMap[tx.category.id] = (spentMap[tx.category.id] ?: 0.0) + tx.amount
                    }
                }

                val budgetsMap = categoriesList.associate { cat ->
                    val spent = spentMap[cat.id] ?: 0.0
                    val limit = cat.maxMonthlyBudget
                    val pct = if (limit != null && limit > 0) (spent / limit).toFloat() else 0f
                    val remaining = if (limit != null) (limit - spent) else null
                    val isOver = limit != null && limit > 0 && spent > limit
                    cat.id to CategoryBudgetInfo(
                        categoryId = cat.id,
                        spentThisMonth = spent,
                        budgetLimit = limit,
                        percentageUsed = pct,
                        remainingBudget = remaining,
                        isOverBudget = isOver
                    )
                }

                val filtered = filterCategories(
                    list = categoriesList,
                    query = query,
                    filterType = filterType,
                    budgetsMap = budgetsMap
                )

                _uiState.value.copy(
                    isLoading = false,
                    categories = categoriesList,
                    filteredCategories = filtered,
                    categoryBudgets = budgetsMap,
                    searchQuery = query,
                    selectedFilterType = filterType
                )
            }.catch { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Error al cargar categorías: ${error.localizedMessage}"
                    )
                }
            }.collect { newState ->
                _uiState.value = newState
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQueryState.value = query
    }

    fun onFilterTypeSelected(filterType: String) {
        _filterTypeState.value = filterType
    }

    private fun filterCategories(
        list: List<Category>,
        query: String,
        filterType: String,
        budgetsMap: Map<Long, CategoryBudgetInfo> = emptyMap()
    ): List<Category> {
        return list.filter { category ->
            val matchesType = when (filterType) {
                "EXPENSE" -> category.type == "EXPENSE"
                "INCOME" -> category.type == "INCOME"
                "WITH_BUDGET" -> category.maxMonthlyBudget != null && category.maxMonthlyBudget > 0.0
                "OVER_BUDGET" -> budgetsMap[category.id]?.isOverBudget == true
                else -> true
            }
            val matchesQuery = query.isBlank() || category.name.contains(query.trim(), ignoreCase = true)
            matchesType && matchesQuery
        }
    }

    fun openQuickBudgetDialog(category: Category) {
        _uiState.update { it.copy(categoryForQuickBudget = category) }
    }

    fun dismissQuickBudgetDialog() {
        _uiState.update { it.copy(categoryForQuickBudget = null) }
    }

    fun saveQuickBudget(category: Category, newBudget: Double?) {
        viewModelScope.launch {
            try {
                val updated = category.copy(maxMonthlyBudget = newBudget)
                updateCategoryUseCase(updated)
                _uiState.update {
                    it.copy(
                        categoryForQuickBudget = null,
                        successMessage = "Presupuesto actualizado para \"${category.name}\""
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        errorMessage = "Error al actualizar presupuesto: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    fun startCreatingCategory(initialType: String = "EXPENSE") {
        _uiState.update {
            it.copy(
                isAddEditBottomSheetOpen = true,
                categoryBeingEdited = null,
                formName = "",
                formIconKey = if (initialType == "INCOME") "attach_money" else "restaurant",
                formColorHex = if (initialType == "INCOME") "#10B981" else "#EF4444",
                formType = initialType,
                formMonthlyBudget = "",
                errorMessage = null
            )
        }
    }

    fun startEditingCategory(category: Category) {
        _uiState.update {
            it.copy(
                isAddEditBottomSheetOpen = true,
                categoryBeingEdited = category,
                formName = category.name,
                formIconKey = category.iconKey,
                formColorHex = category.colorHex,
                formType = category.type,
                formMonthlyBudget = category.maxMonthlyBudget?.toString() ?: "",
                errorMessage = null
            )
        }
    }

    fun onFormNameChanged(name: String) {
        _uiState.update { it.copy(formName = name) }
    }

    fun onFormIconKeyChanged(iconKey: String) {
        _uiState.update { it.copy(formIconKey = iconKey) }
    }

    fun onFormColorHexChanged(colorHex: String) {
        _uiState.update { it.copy(formColorHex = colorHex) }
    }

    fun onFormTypeChanged(type: String) {
        _uiState.update { it.copy(formType = type) }
    }

    fun onFormMonthlyBudgetChanged(budget: String) {
        // Filtrar caracteres no numéricos excepto punto decimal
        val filtered = budget.filter { it.isDigit() || it == '.' }
        if (filtered.count { it == '.' } <= 1) {
            _uiState.update { it.copy(formMonthlyBudget = filtered) }
        }
    }

    fun dismissAddEditBottomSheet() {
        _uiState.update {
            it.copy(
                isAddEditBottomSheetOpen = false,
                categoryBeingEdited = null,
                isSaving = false
            )
        }
    }

    fun saveCategory() {
        val currentState = _uiState.value
        val name = currentState.formName.trim()
        if (name.isBlank()) {
            _uiState.update { it.copy(errorMessage = "El nombre de la categoría no puede estar vacío.") }
            return
        }

        val budget = currentState.formMonthlyBudget.toDoubleOrNull()

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            try {
                val editingCategory = currentState.categoryBeingEdited
                if (editingCategory != null) {
                    val updatedCategory = editingCategory.copy(
                        name = name,
                        iconKey = currentState.formIconKey,
                        colorHex = currentState.formColorHex,
                        type = currentState.formType,
                        maxMonthlyBudget = budget
                    )
                    updateCategoryUseCase(updatedCategory)
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            isAddEditBottomSheetOpen = false,
                            categoryBeingEdited = null,
                            successMessage = "Categoría \"$name\" actualizada con éxito."
                        )
                    }
                } else {
                    val newCategory = Category(
                        name = name,
                        iconKey = currentState.formIconKey,
                        colorHex = currentState.formColorHex,
                        type = currentState.formType,
                        isDefault = false,
                        maxMonthlyBudget = budget
                    )
                    addCategoryUseCase(newCategory)
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            isAddEditBottomSheetOpen = false,
                            successMessage = "Categoría \"$name\" agregada con éxito."
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = "Error al guardar la categoría: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    fun requestDeleteCategory(category: Category) {
        _uiState.update { it.copy(categoryToDelete = category) }
    }

    fun dismissDeleteDialog() {
        _uiState.update { it.copy(categoryToDelete = null, isDeleting = false) }
    }

    fun confirmDeleteCategory() {
        val category = _uiState.value.categoryToDelete ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isDeleting = true) }
            try {
                deleteCategoryUseCase(category)
                _uiState.update {
                    it.copy(
                        categoryToDelete = null,
                        isDeleting = false,
                        successMessage = "Categoría \"${category.name}\" eliminada correctamente."
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isDeleting = false,
                        categoryToDelete = null,
                        errorMessage = "Error al eliminar categoría: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }
}
