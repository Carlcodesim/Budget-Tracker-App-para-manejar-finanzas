package com.example.presentation.transactions.form

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.Category
import com.example.domain.model.Account
import com.example.domain.model.Transaction
import com.example.domain.model.TransactionType
import com.example.domain.repository.TransactionRepository
import com.example.domain.usecase.category.GetCategoriesUseCase
import com.example.domain.usecase.account.GetAccountsUseCase
import com.example.domain.usecase.receipt.ParseReceiptUseCase
import com.example.domain.usecase.transaction.AddTransactionUseCase
import com.example.domain.usecase.transaction.GetTransactionsUseCase
import com.example.domain.usecase.transaction.UpdateTransactionUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Date

/**
 * ViewModel para el formulario de agregar/editar transacciones.
 * Implementa validaciones inline, cálculo dinámico de presupuesto por categoría con alertas,
 * e integración con Gemini AI para escaneo inteligente de recibos/facturas.
 */
class TransactionFormViewModel(
    private val addTransactionUseCase: AddTransactionUseCase,
    private val updateTransactionUseCase: UpdateTransactionUseCase,
    private val getCategoriesUseCase: GetCategoriesUseCase,
    private val getAccountsUseCase: GetAccountsUseCase,
    private val transactionRepository: TransactionRepository,
    private val getTransactionsUseCase: GetTransactionsUseCase,
    private val parseReceiptUseCase: ParseReceiptUseCase,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _uiState = MutableStateFlow(TransactionFormUiState())
    val uiState: StateFlow<TransactionFormUiState> = _uiState.asStateFlow()

    private var allTransactions: List<Transaction> = emptyList()

    init {
        // Cargar las categorías de base de datos
        viewModelScope.launch {
            getCategoriesUseCase().collect { categories ->
                _uiState.update { state ->
                    val selectedCat = state.selectedCategory?.let { current ->
                        categories.find { it.id == current.id }
                    } ?: state.selectedCategory ?: categories.firstOrNull()

                    state.copy(
                        categories = categories,
                        selectedCategory = selectedCat
                    )
                }
                recalculateCategoryBudget()
            }
        }

        // Cargar las cuentas de base de datos
        viewModelScope.launch {
            getAccountsUseCase().collect { accounts ->
                _uiState.update { state ->
                    val selectedAcc = if (state.selectedAccount != null) {
                        accounts.find { it.id == state.selectedAccount?.id } ?: state.selectedAccount
                    } else {
                        accounts.firstOrNull()
                    }
                    state.copy(
                        accounts = accounts,
                        selectedAccount = selectedAcc
                    )
                }
            }
        }

        // Cargar transacciones para calcular el gasto mensual por categoría en tiempo real
        viewModelScope.launch {
            getTransactionsUseCase().collect { transactions ->
                allTransactions = transactions
                recalculateCategoryBudget()
            }
        }
    }

    fun loadTransaction(id: Long) {
        if (id <= 0) {
            _uiState.update { it.copy(isLoadFinished = true) }
            recalculateCategoryBudget()
            return
        }
        viewModelScope.launch {
            val transaction = transactionRepository.getTransactionById(id)
            if (transaction != null) {
                _uiState.update { state ->
                    val foundAccount = state.accounts.find { it.id == transaction.accountId }
                    state.copy(
                        id = transaction.id,
                        amount = if (transaction.amount % 1.0 == 0.0) transaction.amount.toLong().toString() else transaction.amount.toString(),
                        type = transaction.type.name,
                        selectedCategory = transaction.category,
                        selectedAccount = foundAccount ?: state.selectedAccount ?: Account(id = transaction.accountId, name = "Cuenta ${transaction.accountId}", type = "CASH", initialBalance = 0.0),
                        description = transaction.description ?: "",
                        dateEpoch = transaction.date.time,
                        tags = transaction.tags ?: "",
                        isLoadFinished = true
                    )
                }
                recalculateCategoryBudget()
            } else {
                _uiState.update {
                    it.copy(
                        isLoadFinished = true,
                        errorMessage = "Movimiento no encontrado"
                    )
                }
            }
        }
    }

    fun onTagsChanged(tags: String) {
        _uiState.update { it.copy(tags = tags) }
    }

    fun onAmountChanged(amount: String) {
        _uiState.update {
            it.copy(
                amount = amount,
                amountError = if (amount.isNotBlank() && amount.toDoubleOrNull() == null) {
                    "Por favor ingresa un formato numérico válido"
                } else null
            )
        }
        recalculateCategoryBudget()
    }

    fun onTypeChanged(type: String) {
        _uiState.update { it.copy(type = type) }
        recalculateCategoryBudget()
    }

    fun onCategoryChanged(category: Category) {
        _uiState.update { it.copy(selectedCategory = category, categoryError = null) }
        recalculateCategoryBudget()
    }

    fun onAccountChanged(account: Account) {
        _uiState.update { it.copy(selectedAccount = account, accountError = null) }
    }

    fun onDescriptionChanged(description: String) {
        _uiState.update { it.copy(description = description) }
    }

    fun onDateChanged(epoch: Long) {
        _uiState.update { it.copy(dateEpoch = epoch) }
        recalculateCategoryBudget()
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, receiptScanSuccessMessage = null) }
    }

    fun clearScannedReceipt() {
        _uiState.update {
            it.copy(
                scannedReceiptBitmap = null,
                scannedReceiptDetails = null,
                receiptScanSuccessMessage = null
            )
        }
    }

    /**
     * Escanea y procesa una imagen de recibo utilizando Gemini AI.
     */
    fun scanReceipt(bitmap: Bitmap) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isScanningReceipt = true,
                    errorMessage = null,
                    receiptScanSuccessMessage = null,
                    scannedReceiptBitmap = bitmap
                )
            }
            try {
                val availableCategoryNames = _uiState.value.categories.map { it.name }
                val parsed = withContext(ioDispatcher) {
                    parseReceiptUseCase(bitmap, availableCategoryNames)
                }

                _uiState.update { state ->
                    var updatedState = state.copy(
                        type = "EXPENSE",
                        scannedReceiptDetails = parsed
                    )

                    // 1. Monto extraído
                    parsed.amount?.let { amt ->
                        if (amt > 0) {
                            updatedState = updatedState.copy(
                                amount = if (amt % 1.0 == 0.0) amt.toLong().toString() else String.format(java.util.Locale.US, "%.2f", amt),
                                amountError = null
                            )
                        }
                    }

                    // 2. Descripción / Comercio / Nota
                    val descParts = listOfNotNull(parsed.merchant, parsed.note).filter { it.isNotBlank() }
                    if (descParts.isNotEmpty()) {
                        updatedState = updatedState.copy(description = descParts.joinToString(" - "))
                    } else if (!parsed.merchant.isNullOrBlank()) {
                        updatedState = updatedState.copy(description = parsed.merchant)
                    }

                    // 3. Fecha extraída
                    parsed.dateEpoch?.let { dateMillis ->
                        if (dateMillis > 0) {
                            updatedState = updatedState.copy(dateEpoch = dateMillis)
                        }
                    }

                    // 4. Categoría sugerida
                    if (!parsed.suggestedCategory.isNullOrBlank()) {
                        val matchedCategory = state.categories.find { cat ->
                            cat.name.equals(parsed.suggestedCategory, ignoreCase = true) ||
                            cat.name.contains(parsed.suggestedCategory, ignoreCase = true) ||
                            parsed.suggestedCategory.contains(cat.name, ignoreCase = true)
                        }
                        if (matchedCategory != null) {
                            updatedState = updatedState.copy(
                                selectedCategory = matchedCategory,
                                categoryError = null
                            )
                        }
                    }

                    // 5. Etiquetas sugeridas
                    if (!parsed.suggestedTags.isNullOrBlank()) {
                        updatedState = updatedState.copy(tags = parsed.suggestedTags)
                    }

                    updatedState.copy(
                        isScanningReceipt = false,
                        receiptScanSuccessMessage = "¡Recibo escaneado e interpretado con Gemini OCR!"
                    )
                }
                recalculateCategoryBudget()
            } catch (e: Exception) {
                val errorMsg = if (e.message?.contains("API_KEY") == true || e.cause?.message?.contains("API_KEY") == true) {
                    "Clave de Gemini no configurada. Por favor agrega tu GEMINI_API_KEY en el panel de Secrets de AI Studio para escanear recibos."
                } else {
                    "Error al analizar comprobante con Gemini: ${e.localizedMessage ?: "Verifica tu conexión a internet"}"
                }
                _uiState.update {
                    it.copy(
                        isScanningReceipt = false,
                        errorMessage = errorMsg
                    )
                }
            }
        }
    }

    private fun recalculateCategoryBudget() {
        val state = _uiState.value
        val category = state.selectedCategory
        val isExpense = state.type == "EXPENSE"

        if (category == null || !isExpense) {
            _uiState.update {
                it.copy(
                    categoryCurrentSpentThisMonth = 0.0,
                    categoryMonthlyLimit = null,
                    isOverBudgetWarning = false
                )
            }
            return
        }

        val targetCal = Calendar.getInstance().apply { timeInMillis = state.dateEpoch }
        val targetMonth = targetCal.get(Calendar.MONTH)
        val targetYear = targetCal.get(Calendar.YEAR)

        val spentInMonth = allTransactions
            .filter { tx ->
                if (tx.type != TransactionType.EXPENSE) return@filter false
                if (tx.category.id != category.id) return@filter false
                if (tx.id == state.id && state.id > 0L) return@filter false // Excluir la transacción que estamos editando
                val txCal = Calendar.getInstance().apply { time = tx.date }
                txCal.get(Calendar.MONTH) == targetMonth && txCal.get(Calendar.YEAR) == targetYear
            }
            .sumOf { it.amount }

        val budgetLimit = category.maxMonthlyBudget?.takeIf { it > 0 }
        val enteredAmount = state.amount.toDoubleOrNull() ?: 0.0
        val isOver = budgetLimit != null && (spentInMonth + enteredAmount) > budgetLimit

        _uiState.update {
            it.copy(
                categoryCurrentSpentThisMonth = spentInMonth,
                categoryMonthlyLimit = budgetLimit,
                isOverBudgetWarning = isOver
            )
        }
    }

    fun validateAndSave(): Boolean {
        val state = _uiState.value
        val amountDouble = state.amount.toDoubleOrNull()
        var hasError = false

        if (state.amount.isBlank()) {
            _uiState.update { it.copy(amountError = "El monto no puede estar vacío") }
            hasError = true
        } else if (amountDouble == null || amountDouble <= 0) {
            _uiState.update { it.copy(amountError = "El monto debe ser numérico y mayor a 0") }
            hasError = true
        } else {
            _uiState.update { it.copy(amountError = null) }
        }

        if (state.selectedCategory == null) {
            _uiState.update { it.copy(categoryError = "Debes seleccionar una categoría") }
            hasError = true
        } else {
            _uiState.update { it.copy(categoryError = null) }
        }

        if (state.selectedAccount == null) {
            _uiState.update { it.copy(accountError = "Debes seleccionar una cuenta") }
            hasError = true
        } else {
            _uiState.update { it.copy(accountError = null) }
        }

        if (hasError) return false

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val transaction = Transaction(
                id = state.id,
                amount = amountDouble!!,
                type = TransactionType.valueOf(state.type),
                category = state.selectedCategory!!,
                description = state.description.trim(),
                date = Date(state.dateEpoch),
                accountId = state.selectedAccount?.id ?: 1L,
                tags = state.tags.trim()
            )

            if (transaction.id == 0L) {
                addTransactionUseCase(transaction)
            } else {
                updateTransactionUseCase(transaction)
            }
            _uiState.update { it.copy(isSaving = false, isSaveFinished = true) }
        }
        return true
    }
}
