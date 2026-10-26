package com.example.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.Category
import com.example.domain.model.Transaction
import com.example.domain.repository.CategoryRepository
import com.example.domain.usecase.category.AddCategoryUseCase
import com.example.domain.usecase.category.GetCategoriesUseCase
import com.example.domain.usecase.transaction.GetTransactionsUseCase
import com.example.domain.model.TransactionType
import java.util.Calendar
import com.example.util.BudgetCycleManager
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel para gestionar la configuración de categorías personalizadas de la aplicación y copias de seguridad.
 */
class SettingsViewModel(
    private val getCategoriesUseCase: GetCategoriesUseCase,
    private val addCategoryUseCase: AddCategoryUseCase,
    private val categoryRepository: CategoryRepository,
    private val getTransactionsUseCase: GetTransactionsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadCategories()
        loadMonthlyData()
    }

    private fun loadCategories() {
        viewModelScope.launch {
            getCategoriesUseCase().collect { list ->
                val totalBudget = list.mapNotNull { it.maxMonthlyBudget }.sum()
                _uiState.update { 
                    it.copy(
                        categories = list,
                        totalMonthlyBudget = if (totalBudget > 0) totalBudget else 2500.0
                    ) 
                }
            }
        }
    }

    private fun loadMonthlyData() {
        viewModelScope.launch {
            combine(
                getTransactionsUseCase(),
                BudgetCycleManager.startDayFlow
            ) { transactions, startDay ->
                val currentCycle = BudgetCycleManager.getCurrentCycleRange(startDay)
                transactions.filter { tx ->
                    tx.type == TransactionType.EXPENSE && tx.date.time in currentCycle.startDateMillis..currentCycle.endDateMillis
                }.sumOf { it.amount }
            }.collect { monthlyExpenses ->
                _uiState.update {
                    it.copy(totalMonthlySpent = monthlyExpenses)
                }
            }
        }
    }

    fun onNameChanged(name: String) {
        _uiState.update { it.copy(name = name, nameError = null, successMessage = null, errorMessage = null) }
    }

    fun onIconKeyChanged(iconKey: String) {
        _uiState.update { it.copy(selectedIconKey = iconKey, successMessage = null, errorMessage = null) }
    }

    fun onColorHexChanged(colorHex: String) {
        _uiState.update { it.copy(selectedColorHex = colorHex, successMessage = null, errorMessage = null) }
    }

    fun onTypeChanged(type: String) {
        _uiState.update { it.copy(selectedType = type, successMessage = null, errorMessage = null) }
    }

    fun onBudgetLimitChanged(budget: String) {
        if (budget.isEmpty() || budget.all { it.isDigit() || it == '.' }) {
            _uiState.update { it.copy(budgetLimit = budget, successMessage = null, errorMessage = null) }
        }
    }

    fun updateCategory(updatedCategory: Category) {
        if (updatedCategory.name.isBlank()) {
            _uiState.update { it.copy(errorMessage = "El nombre de la categoría no puede estar vacío") }
            return
        }

        viewModelScope.launch {
            try {
                categoryRepository.insertCategory(updatedCategory)
                _uiState.update { it.copy(successMessage = "¡Categoría '${updatedCategory.name}' actualizada con éxito!") }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Error al actualizar categoría: ${e.localizedMessage}") }
            }
        }
    }

    fun updateCategoryBudget(category: Category, budget: Double?) {
        viewModelScope.launch {
            try {
                val updated = category.copy(maxMonthlyBudget = budget)
                categoryRepository.insertCategory(updated)
                _uiState.update { it.copy(successMessage = "¡Presupuesto de '${category.name}' actualizado!") }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Error al actualizar presupuesto: ${e.localizedMessage}") }
            }
        }
    }

    fun addCategory() {
        val state = _uiState.value
        if (state.name.isBlank()) {
            _uiState.update { it.copy(nameError = "El nombre de la categoría no puede estar vacío") }
            return
        }

        // Verificar si ya existe una categoría con ese nombre
        val exists = state.categories.any { it.name.equals(state.name.trim(), ignoreCase = true) }
        if (exists) {
            _uiState.update { it.copy(nameError = "Ya existe una categoría con este nombre") }
            return
        }

        val budgetVal = state.budgetLimit.toDoubleOrNull()

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                val newCategory = Category(
                    name = state.name.trim(),
                    iconKey = state.selectedIconKey,
                    isDefault = false,
                    maxMonthlyBudget = budgetVal,
                    colorHex = state.selectedColorHex,
                    type = state.selectedType
                )
                addCategoryUseCase(newCategory)
                _uiState.update {
                    it.copy(
                        name = "",
                        selectedIconKey = "attach_money",
                        selectedColorHex = "#3B82F6",
                        selectedType = "EXPENSE",
                        budgetLimit = "",
                        isSaving = false,
                        successMessage = "¡Categoría creada con éxito!"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = "Error al crear la categoría: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    fun deleteCategory(category: Category) {
        if (category.isDefault) {
            _uiState.update { it.copy(errorMessage = "No se pueden eliminar las categorías por defecto") }
            return
        }

        viewModelScope.launch {
            try {
                categoryRepository.deleteCategory(category)
                _uiState.update { it.copy(successMessage = "Categoría eliminada con éxito") }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Error al eliminar la categoría: ${e.localizedMessage}") }
            }
        }
    }

    suspend fun getTransactionsForExport(): List<Transaction> {
        return getTransactionsUseCase().first()
    }

    fun setSuccessMessage(msg: String) {
        _uiState.update { it.copy(successMessage = msg) }
    }

    fun setErrorMessage(msg: String) {
        _uiState.update { it.copy(errorMessage = msg) }
    }

    fun clearMessages() {
        _uiState.update { it.copy(successMessage = null, errorMessage = null) }
    }
}
