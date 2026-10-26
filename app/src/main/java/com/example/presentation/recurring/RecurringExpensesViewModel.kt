package com.example.presentation.recurring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.Category
import com.example.domain.model.ProcessRecurringResult
import com.example.domain.model.RecurringExpense
import com.example.domain.usecase.account.GetAccountsUseCase
import com.example.domain.usecase.category.GetCategoriesUseCase
import com.example.domain.usecase.recurring.AddRecurringExpenseUseCase
import com.example.domain.usecase.recurring.DeleteRecurringExpenseUseCase
import com.example.domain.usecase.recurring.GetRecurringExpensesUseCase
import com.example.domain.usecase.recurring.ProcessRecurringExpensesUseCase
import com.example.domain.usecase.recurring.UpdateRecurringExpenseUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

/**
 * ViewModel para gestionar los gastos recurrentes mensuales y la automatización
 * de transacciones en fechas específicas.
 */
class RecurringExpensesViewModel(
    private val getRecurringExpensesUseCase: GetRecurringExpensesUseCase,
    private val addRecurringExpenseUseCase: AddRecurringExpenseUseCase,
    private val updateRecurringExpenseUseCase: UpdateRecurringExpenseUseCase,
    private val deleteRecurringExpenseUseCase: DeleteRecurringExpenseUseCase,
    private val processRecurringExpensesUseCase: ProcessRecurringExpensesUseCase,
    private val getCategoriesUseCase: GetCategoriesUseCase,
    private val getAccountsUseCase: GetAccountsUseCase
) : ViewModel() {

    private val _selectedFilter = MutableStateFlow(RecurringFilter.ALL)
    private val _snackbarMessage = MutableStateFlow<String?>(null)
    private val _isFormOpen = MutableStateFlow(false)
    private val _editingExpense = MutableStateFlow<RecurringExpense?>(null)
    private val _isAutoProcessing = MutableStateFlow(false)

    init {
        // En la inicialización, ejecutar automáticamente la verificación y creación de transacciones pendientes
        autoProcessOnLaunch()
    }

    private fun getCurrentYearMonth(): String {
        val now = Calendar.getInstance()
        val year = now.get(Calendar.YEAR)
        val month = now.get(Calendar.MONTH) + 1
        return String.format(Locale.US, "%04d-%02d", year, month)
    }

    private fun getCurrentDayOfMonth(): Int {
        return Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
    }

    val uiState: StateFlow<RecurringExpensesUiState> = combine(
        getRecurringExpensesUseCase(),
        getCategoriesUseCase(),
        getAccountsUseCase(),
        _selectedFilter,
        _snackbarMessage,
        _isFormOpen,
        _editingExpense,
        _isAutoProcessing
    ) { params: Array<Any?> ->
        @Suppress("UNCHECKED_CAST")
        val expenses = params[0] as List<RecurringExpense>
        @Suppress("UNCHECKED_CAST")
        val categories = params[1] as List<Category>
        @Suppress("UNCHECKED_CAST")
        val accounts = params[2] as List<com.example.domain.model.Account>
        val filter = params[3] as RecurringFilter
        val snackbar = params[4] as String?
        val isFormOpen = params[5] as Boolean
        val editing = params[6] as RecurringExpense?
        val isAutoProcessing = params[7] as Boolean

        val currentYearMonth = getCurrentYearMonth()
        val currentDay = getCurrentDayOfMonth()

        // Cálculos estadísticos
        val activeExpenses = expenses.filter { it.isEnabled }
        val totalMonthlyCommitted = activeExpenses.sumOf { it.amount }

        var totalPaid = 0.0
        var totalPending = 0.0
        var executedCount = 0
        var pendingCount = 0

        for (exp in activeExpenses) {
            if (exp.isExecutedInMonth(currentYearMonth)) {
                totalPaid += exp.amount
                executedCount++
            } else {
                totalPending += exp.amount
                pendingCount++
            }
        }

        // Próximo gasto a vencer
        val upcomingUnexecuted = activeExpenses
            .filter { !it.isExecutedInMonth(currentYearMonth) }
            .sortedBy {
                val diff = it.dayOfMonth - currentDay
                if (diff >= 0) diff else (diff + 31)
            }
        val nextDue = upcomingUnexecuted.firstOrNull()
        val daysUntilNext = nextDue?.let {
            val diff = it.dayOfMonth - currentDay
            if (diff >= 0) diff else (diff + 30)
        }

        // Filtrado
        val filtered = when (filter) {
            RecurringFilter.ALL -> expenses
            RecurringFilter.PENDING_THIS_MONTH -> expenses.filter { it.isEnabled && !it.isExecutedInMonth(currentYearMonth) }
            RecurringFilter.EXECUTED_THIS_MONTH -> expenses.filter { it.isExecutedInMonth(currentYearMonth) }
            RecurringFilter.PAUSED -> expenses.filter { !it.isEnabled }
        }

        RecurringExpensesUiState(
            isLoading = false,
            recurringExpenses = expenses,
            filteredExpenses = filtered,
            categories = categories,
            accounts = accounts,
            totalMonthlyCommitted = totalMonthlyCommitted,
            totalPaidThisMonth = totalPaid,
            totalPendingThisMonth = totalPending,
            executedCountThisMonth = executedCount,
            pendingCountThisMonth = pendingCount,
            nextDueExpense = nextDue,
            daysUntilNextDue = daysUntilNext,
            currentYearMonth = currentYearMonth,
            currentDayOfMonth = currentDay,
            selectedFilter = filter,
            isAutoProcessing = isAutoProcessing,
            snackbarMessage = snackbar,
            editingExpense = editing,
            isFormOpen = isFormOpen
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = RecurringExpensesUiState(
            currentYearMonth = getCurrentYearMonth(),
            currentDayOfMonth = getCurrentDayOfMonth()
        )
    )

    private fun autoProcessOnLaunch() {
        viewModelScope.launch {
            try {
                _isAutoProcessing.value = true
                val result = processRecurringExpensesUseCase()
                if (result.executedCount > 0) {
                    _snackbarMessage.value = "⚡ Se generaron automáticamente ${result.executedCount} transacciones de gastos recurrentes por un total de $${String.format(Locale.US, "%,.2f", result.totalAmount)}."
                }
            } catch (e: Exception) {
                // Silently handle startup sync error
            } finally {
                _isAutoProcessing.value = false
            }
        }
    }

    fun syncAndProcessNow(forceAllDue: Boolean = false) {
        viewModelScope.launch {
            try {
                _isAutoProcessing.value = true
                val result = processRecurringExpensesUseCase(forceAllDue = forceAllDue)
                if (result.executedCount > 0) {
                    _snackbarMessage.value = "✅ Se crearon ${result.executedCount} transacciones por $${String.format(Locale.US, "%,.2f", result.totalAmount)}."
                } else {
                    _snackbarMessage.value = "Al día de hoy no hay más gastos pendientes por crear en este mes."
                }
            } catch (e: Exception) {
                _snackbarMessage.value = "Error al procesar gastos: ${e.message}"
            } finally {
                _isAutoProcessing.value = false
            }
        }
    }

    fun executeSingleExpenseNow(expense: RecurringExpense) {
        viewModelScope.launch {
            try {
                val success = processRecurringExpensesUseCase.executeNow(expense.id)
                if (success) {
                    _snackbarMessage.value = "✅ Transacción generada con éxito para '${expense.title}' por $${String.format(Locale.US, "%,.2f", expense.amount)}."
                } else {
                    _snackbarMessage.value = "No se pudo generar la transacción."
                }
            } catch (e: Exception) {
                _snackbarMessage.value = "Error al generar transacción: ${e.message}"
            }
        }
    }

    fun toggleExpenseStatus(expense: RecurringExpense, isEnabled: Boolean) {
        viewModelScope.launch {
            try {
                processRecurringExpensesUseCase.toggleEnabled(expense.id, isEnabled)
                _snackbarMessage.value = if (isEnabled) {
                    "Gasto '${expense.title}' reactivado."
                } else {
                    "Gasto '${expense.title}' pausado."
                }
            } catch (e: Exception) {
                _snackbarMessage.value = "Error al cambiar estado: ${e.message}"
            }
        }
    }

    fun deleteExpense(expense: RecurringExpense) {
        viewModelScope.launch {
            try {
                deleteRecurringExpenseUseCase(expense.id)
                _snackbarMessage.value = "Gasto recurrente '${expense.title}' eliminado."
            } catch (e: Exception) {
                _snackbarMessage.value = "Error al eliminar: ${e.message}"
            }
        }
    }

    fun saveRecurringExpense(
        title: String,
        amount: Double,
        categoryId: Long,
        accountId: Long,
        dayOfMonth: Int,
        description: String?,
        autoCreate: Boolean,
        editingId: Long = 0
    ) {
        viewModelScope.launch {
            try {
                val category = uiState.value.categories.firstOrNull { it.id == categoryId } ?: Category(
                    id = categoryId,
                    name = "Otros",
                    iconKey = "category",
                    isDefault = true
                )
                val account = uiState.value.accounts.firstOrNull { it.id == accountId }

                val recurring = RecurringExpense(
                    id = editingId,
                    title = title.trim(),
                    amount = amount,
                    category = category,
                    account = account,
                    accountId = accountId,
                    dayOfMonth = dayOfMonth.coerceIn(1, 31),
                    description = description?.takeIf { it.isNotBlank() },
                    isEnabled = true,
                    autoCreate = autoCreate
                )

                if (editingId > 0) {
                    updateRecurringExpenseUseCase(recurring)
                    _snackbarMessage.value = "Gasto recurrente '${title}' actualizado correctamente."
                } else {
                    addRecurringExpenseUseCase(recurring)
                    _snackbarMessage.value = "Gasto recurrente '${title}' programado para el día $dayOfMonth de cada mes."
                }
                closeForm()
            } catch (e: Exception) {
                _snackbarMessage.value = "Error al guardar: ${e.message}"
            }
        }
    }

    fun openForm(expenseToEdit: RecurringExpense? = null) {
        _editingExpense.value = expenseToEdit
        _isFormOpen.value = true
    }

    fun closeForm() {
        _isFormOpen.value = false
        _editingExpense.value = null
    }

    fun setFilter(filter: RecurringFilter) {
        _selectedFilter.value = filter
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }
}
