package com.example.presentation.goals.form

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.SavingsGoal
import com.example.domain.repository.SavingsGoalRepository
import com.example.domain.usecase.goal.AddSavingsGoalUseCase
import com.example.domain.usecase.goal.UpdateSavingsGoalUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Date

/**
 * ViewModel para el formulario de metas de ahorro (Agregar/Editar).
 * Asegura validación in-situ e interactúa con el repositorio y casos de uso del dominio.
 */
class GoalFormViewModel(
    private val addSavingsGoalUseCase: AddSavingsGoalUseCase,
    private val updateSavingsGoalUseCase: UpdateSavingsGoalUseCase,
    private val savingsGoalRepository: SavingsGoalRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(GoalFormUiState())
    val uiState: StateFlow<GoalFormUiState> = _uiState.asStateFlow()

    fun loadGoal(id: Long) {
        if (id <= 0) {
            _uiState.update { it.copy(isLoadFinished = true) }
            return
        }
        viewModelScope.launch {
            val goal = savingsGoalRepository.getSavingsGoalById(id)
            if (goal != null) {
                _uiState.update {
                    it.copy(
                        id = goal.id,
                        name = goal.name,
                        targetAmount = goal.targetAmount.toString(),
                        currentAmount = goal.currentAmount.toString(),
                        deadlineEpoch = goal.deadline?.time,
                        description = goal.description,
                        priority = goal.priority,
                        category = goal.category,
                        isLoadFinished = true
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isLoadFinished = true,
                        errorMessage = "Meta de ahorro no encontrada"
                    )
                }
            }
        }
    }

    fun onNameChanged(name: String) {
        _uiState.update { it.copy(name = name, nameError = null) }
    }

    fun onDescriptionChanged(description: String) {
        _uiState.update { it.copy(description = description) }
    }

    fun onPriorityChanged(priority: String) {
        _uiState.update { it.copy(priority = priority) }
    }

    fun onCategoryChanged(category: String) {
        _uiState.update { it.copy(category = category) }
    }

    fun onTargetAmountChanged(amount: String) {
        _uiState.update {
            it.copy(
                targetAmount = amount,
                targetAmountError = if (amount.isNotBlank() && amount.toDoubleOrNull() == null) {
                    "Por favor ingresa un formato numérico válido"
                } else null
            )
        }
    }

    fun onDeadlineChanged(epoch: Long?) {
        _uiState.update { it.copy(deadlineEpoch = epoch) }
    }

    fun validateAndSave(): Boolean {
        val state = _uiState.value
        val targetAmountDouble = state.targetAmount.toDoubleOrNull()
        var hasError = false

        if (state.name.isBlank()) {
            _uiState.update { it.copy(nameError = "El nombre de la meta no puede estar vacío") }
            hasError = true
        }

        if (state.targetAmount.isBlank()) {
            _uiState.update { it.copy(targetAmountError = "El monto objetivo no puede estar vacío") }
            hasError = true
        } else if (targetAmountDouble == null || targetAmountDouble <= 0) {
            _uiState.update { it.copy(targetAmountError = "El monto objetivo debe ser numérico y mayor a 0") }
            hasError = true
        }

        if (hasError) return false

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val goal = SavingsGoal(
                id = state.id,
                name = state.name.trim(),
                targetAmount = targetAmountDouble!!,
                currentAmount = state.currentAmount.toDoubleOrNull() ?: 0.0,
                deadline = state.deadlineEpoch?.let { Date(it) },
                createdAt = Date(),
                description = state.description.trim(),
                priority = state.priority,
                category = state.category
            )

            if (goal.id == 0L) {
                addSavingsGoalUseCase(goal)
            } else {
                updateSavingsGoalUseCase(goal)
            }
            _uiState.update { it.copy(isSaving = false, isSaveFinished = true) }
        }
        return true
    }
}
