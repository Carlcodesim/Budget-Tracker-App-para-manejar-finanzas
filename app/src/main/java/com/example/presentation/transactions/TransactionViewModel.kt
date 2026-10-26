package com.example.presentation.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.Transaction
import com.example.domain.model.TransactionType
import com.example.domain.repository.TransactionRepository
import com.example.presentation.transactions.list.TransactionSortOption
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Estado inmutable de la UI para el listado de transacciones.
 */
data class TransactionListUiState(
    val isLoading: Boolean = true,
    val transactions: List<Transaction> = emptyList(),
    val filteredTransactions: List<Transaction> = emptyList(),
    val selectedTypeFilter: String = "ALL", // "ALL", "INCOME", "EXPENSE"
    val selectedSortOption: TransactionSortOption = TransactionSortOption.DATE_DESC,
    val isSortMenuExpanded: Boolean = false
)

/**
 * ViewModel que interactúa con [TransactionRepository] para exponer flujos observables de transacciones del dominio.
 * Separa y encapsula el estado de la UI mediante StateFlow reactivo.
 */
class TransactionViewModel(
    private val transactionRepository: TransactionRepository
) : ViewModel() {

    private val _selectedTypeFilter = MutableStateFlow("ALL")
    private val _selectedSortOption = MutableStateFlow(TransactionSortOption.DATE_DESC)
    private val _isSortMenuExpanded = MutableStateFlow(false)

    /**
     * Flujo observable de todas las transacciones ordenadas por fecha de forma descendente.
     */
    val transactionsFlow: Flow<List<Transaction>> = transactionRepository.getAllTransactions()

    /**
     * Estado observable retenido para compatibilidad.
     */
    val transactions: StateFlow<List<Transaction>> = transactionRepository.getAllTransactions()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /**
     * Estado unificado de la UI expuesto como StateFlow reactivo.
     */
    val uiState: StateFlow<TransactionListUiState> = combine(
        transactionRepository.getAllTransactions(),
        _selectedTypeFilter,
        _selectedSortOption,
        _isSortMenuExpanded
    ) { transactions, typeFilter, sortOption, menuExpanded ->
        val filtered = transactions
            .filter { transaction ->
                when (typeFilter) {
                    "INCOME" -> transaction.type == TransactionType.INCOME
                    "EXPENSE" -> transaction.type == TransactionType.EXPENSE
                    else -> true
                }
            }
            .sortedWith { t1, t2 ->
                when (sortOption) {
                    TransactionSortOption.DATE_DESC -> t2.date.compareTo(t1.date)
                    TransactionSortOption.DATE_ASC -> t1.date.compareTo(t2.date)
                    TransactionSortOption.AMOUNT_DESC -> t2.amount.compareTo(t1.amount)
                    TransactionSortOption.AMOUNT_ASC -> t1.amount.compareTo(t2.amount)
                    TransactionSortOption.CATEGORY_ASC -> (t1.category.name).compareTo(t2.category.name)
                    TransactionSortOption.CATEGORY_DESC -> (t2.category.name).compareTo(t1.category.name)
                }
            }
        TransactionListUiState(
            isLoading = false,
            transactions = transactions,
            filteredTransactions = filtered,
            selectedTypeFilter = typeFilter,
            selectedSortOption = sortOption,
            isSortMenuExpanded = menuExpanded
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TransactionListUiState(isLoading = true)
    )

    fun setTypeFilter(type: String) {
        _selectedTypeFilter.value = type
    }

    fun setSortOption(sortOption: TransactionSortOption) {
        _selectedSortOption.value = sortOption
    }

    fun setSortMenuExpanded(expanded: Boolean) {
        _isSortMenuExpanded.value = expanded
    }

    /**
     * Obtiene un flujo de transacciones filtrado por tipo (INCOME, EXPENSE, TRANSFER).
     */
    fun getTransactionsByType(type: TransactionType): Flow<List<Transaction>> {
        return transactionRepository.getAllTransactions().map { list ->
            list.filter { it.type == type }
        }
    }

    /**
     * Inserta o actualiza una transacción en el repositorio.
     */
    fun addTransaction(transaction: Transaction) {
        viewModelScope.launch {
            transactionRepository.insertTransaction(transaction)
        }
    }

    /**
     * Actualiza una transacción existente.
     */
    fun updateTransaction(transaction: Transaction) {
        viewModelScope.launch {
            transactionRepository.updateTransaction(transaction)
        }
    }

    /**
     * Elimina una transacción del repositorio.
     */
    fun deleteTransaction(transaction: Transaction) {
        viewModelScope.launch {
            transactionRepository.deleteTransaction(transaction)
        }
    }

    /**
     * Elimina una transacción por su ID.
     */
    fun deleteTransactionById(id: Long) {
        viewModelScope.launch {
            transactionRepository.deleteTransactionById(id)
        }
    }
}

