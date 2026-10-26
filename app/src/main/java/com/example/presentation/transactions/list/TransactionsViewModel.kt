package com.example.presentation.transactions.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.Category
import com.example.domain.model.Transaction
import com.example.domain.model.TransactionType
import com.example.domain.usecase.category.GetCategoriesUseCase
import com.example.domain.usecase.transaction.AddTransactionUseCase
import com.example.domain.usecase.transaction.DeleteTransactionUseCase
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.example.domain.usecase.transaction.GetPagedTransactionsUseCase
import com.example.domain.usecase.transaction.GetTransactionsUseCase
import java.util.Calendar
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce

/**
 * ViewModel para controlar la pantalla de listado de movimientos.
 * Maneja los estados de filtrado dinámico, presets temporales y operaciones por lote (selección múltiple).
 */
@OptIn(FlowPreview::class)
class TransactionsViewModel(
    private val getTransactionsUseCase: GetTransactionsUseCase,
    private val getPagedTransactionsUseCase: GetPagedTransactionsUseCase? = null,
    private val getCategoriesUseCase: GetCategoriesUseCase,
    private val deleteTransactionUseCase: DeleteTransactionUseCase,
    private val addTransactionUseCase: AddTransactionUseCase
) : ViewModel() {

    val pagedTransactions: Flow<PagingData<Transaction>> = (
        getPagedTransactionsUseCase?.invoke() ?: getTransactionsUseCase().map { PagingData.from(it) }
    ).cachedIn(viewModelScope)

    private val _filterState = MutableStateFlow(FilterParams())
    private val _searchQuery = MutableStateFlow("")
    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    private var lastDeletedTransaction: Transaction? = null
    private var lastDeletedBatch: List<Transaction> = emptyList()

    val uiState: StateFlow<TransactionsUiState> = combine(
        getTransactionsUseCase(),
        getCategoriesUseCase(),
        _filterState,
        _searchQuery.debounce(200),
        _selectedIds
    ) { transactions, categories, filters, debouncedQuery, selectedIds ->
        val effectiveFilters = filters.copy(searchQuery = debouncedQuery)
        val filtered = filterAndSortTransactions(transactions, effectiveFilters)

        // Precomputar grupos por día y totales off-main-thread para evitar lag durante el scroll
        val groupedList = filtered.groupBy { com.example.presentation.common.util.DateFormatter.formatGroupHeader(it.date) }
            .map { (dateHeader, txs) ->
                var inc = 0.0
                var exp = 0.0
                for (t in txs) {
                    when (t.type) {
                        TransactionType.INCOME -> inc += t.amount
                        TransactionType.EXPENSE -> exp += t.amount
                        TransactionType.TRANSFER -> {}
                    }
                }
                DailyGroupedTransactions(
                    dateHeader = dateHeader,
                    transactions = txs,
                    totalIncome = inc,
                    totalExpense = exp,
                    netBalance = inc - exp
                )
            }

        TransactionsUiState(
            isLoading = false,
            transactions = transactions,
            filteredTransactions = filtered,
            groupedTransactions = groupedList,
            categories = categories,
            selectedTypeFilter = filters.type,
            selectedCategoryIdFilter = filters.categoryId,
            selectedSortOption = filters.sortOption,
            selectedTimePreset = filters.timePreset,
            selectedTransactionIds = selectedIds,
            searchQuery = _searchQuery.value,
            startDate = filters.startDate,
            endDate = filters.endDate,
            minAmount = filters.minAmount,
            maxAmount = filters.maxAmount
        )
    }
    .flowOn(Dispatchers.Default)
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TransactionsUiState(isLoading = true)
    )

    private fun filterAndSortTransactions(
        transactions: List<Transaction>,
        filters: FilterParams
    ): List<Transaction> {
        val targetType = when (filters.type) {
            "INCOME" -> TransactionType.INCOME
            "EXPENSE" -> TransactionType.EXPENSE
            "TRANSFER" -> TransactionType.TRANSFER
            else -> null
        }
        val targetCatId = filters.categoryId
        val rawQuery = filters.searchQuery.trim()
        val hasQuery = rawQuery.isNotEmpty()
        val queryLower = if (hasQuery) rawQuery.lowercase() else ""
        val startDate = filters.startDate
        val endDate = filters.endDate
        val minAmt = filters.minAmount
        val maxAmt = filters.maxAmount

        val filtered = transactions.filter { tx ->
            if (targetType != null && tx.type != targetType) return@filter false
            if (targetCatId != -1L && tx.category.id != targetCatId) return@filter false

            val tTime = tx.date.time
            if (startDate != null && tTime < startDate) return@filter false
            if (endDate != null && tTime > endDate) return@filter false

            if (minAmt != null && tx.amount < minAmt) return@filter false
            if (maxAmt != null && tx.amount > maxAmt) return@filter false

            if (hasQuery) {
                val descMatch = tx.description?.contains(queryLower, ignoreCase = true) == true
                val catMatch = tx.category.name.contains(queryLower, ignoreCase = true)
                val tagsMatch = tx.tags?.contains(queryLower, ignoreCase = true) == true
                if (!descMatch && !catMatch && !tagsMatch) return@filter false
            }

            true
        }

        return when (filters.sortOption) {
            TransactionSortOption.DATE_DESC -> filtered.sortedByDescending { it.date.time }
            TransactionSortOption.DATE_ASC -> filtered.sortedBy { it.date.time }
            TransactionSortOption.AMOUNT_DESC -> filtered.sortedByDescending { it.amount }
            TransactionSortOption.AMOUNT_ASC -> filtered.sortedBy { it.amount }
            TransactionSortOption.CATEGORY_ASC -> filtered.sortedBy { it.category.name.lowercase() }
            TransactionSortOption.CATEGORY_DESC -> filtered.sortedByDescending { it.category.name.lowercase() }
        }
    }

    fun onTypeFilterChanged(type: String) {
        _filterState.update { it.copy(type = type) }
    }

    fun onCategoryFilterChanged(categoryId: Long) {
        _filterState.update { it.copy(categoryId = categoryId) }
    }

    fun onSortOptionChanged(sortOption: TransactionSortOption) {
        _filterState.update { it.copy(sortOption = sortOption) }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        _filterState.update { it.copy(searchQuery = query) }
    }

    fun onTimePresetSelected(preset: TimeRangePreset) {
        if (preset == TimeRangePreset.ALL) {
            _filterState.update { it.copy(timePreset = preset, startDate = null, endDate = null) }
            return
        }

        val cal = Calendar.getInstance()
        val startMillis: Long
        val endMillis: Long

        when (preset) {
            TimeRangePreset.THIS_MONTH -> {
                val currentCycle = com.example.util.BudgetCycleManager.getCurrentCycleRange()
                startMillis = currentCycle.startDateMillis
                endMillis = currentCycle.endDateMillis
            }
            TimeRangePreset.LAST_MONTH -> {
                val currentCycle = com.example.util.BudgetCycleManager.getCurrentCycleRange()
                val prevCycle = com.example.util.BudgetCycleManager.getPreviousCycleRange(currentCycle)
                startMillis = prevCycle.startDateMillis
                endMillis = prevCycle.endDateMillis
            }
            TimeRangePreset.LAST_7_DAYS -> {
                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                cal.set(Calendar.SECOND, 59)
                cal.set(Calendar.MILLISECOND, 999)
                endMillis = cal.timeInMillis

                cal.add(Calendar.DAY_OF_YEAR, -7)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                startMillis = cal.timeInMillis
            }
            TimeRangePreset.THIS_YEAR -> {
                cal.set(Calendar.DAY_OF_YEAR, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                startMillis = cal.timeInMillis

                cal.add(Calendar.YEAR, 1)
                cal.add(Calendar.MILLISECOND, -1)
                endMillis = cal.timeInMillis
            }
            TimeRangePreset.ALL -> {
                startMillis = 0L
                endMillis = Long.MAX_VALUE
            }
        }
        _filterState.update { it.copy(timePreset = preset, startDate = startMillis, endDate = endMillis) }
    }

    fun onDateRangeChanged(start: Long?, end: Long?) {
        _filterState.update { it.copy(startDate = start, endDate = end, timePreset = TimeRangePreset.ALL) }
    }

    fun onAmountRangeChanged(min: Double?, max: Double?) {
        _filterState.update { it.copy(minAmount = min, maxAmount = max) }
    }

    fun clearAllFilters() {
        _searchQuery.value = ""
        _filterState.update { FilterParams() }
    }

    fun toggleSelectTransaction(id: Long) {
        _selectedIds.update { current ->
            if (id in current) current - id else current + id
        }
    }

    fun selectAllFilteredTransactions(filtered: List<Transaction>) {
        _selectedIds.value = filtered.map { it.id }.toSet()
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
    }

    fun deleteSelectedTransactions(onComplete: ((Int) -> Unit)? = null) {
        val idsToDelete = _selectedIds.value
        if (idsToDelete.isEmpty()) return

        val allTx = uiState.value.transactions
        val toDelete = allTx.filter { it.id in idsToDelete }
        if (toDelete.isNotEmpty()) {
            lastDeletedBatch = toDelete
            viewModelScope.launch {
                deleteTransactionUseCase(toDelete)
                _selectedIds.value = emptySet()
                onComplete?.invoke(toDelete.size)
            }
        }
    }

    fun undoBatchDelete() {
        val toRestore = lastDeletedBatch
        if (toRestore.isNotEmpty()) {
            viewModelScope.launch {
                toRestore.forEach { addTransactionUseCase(it) }
                lastDeletedBatch = emptyList()
            }
        }
    }

    fun deleteTransaction(transaction: Transaction) {
        lastDeletedTransaction = transaction
        viewModelScope.launch {
            deleteTransactionUseCase(transaction)
        }
    }

    fun undoDelete() {
        val transaction = lastDeletedTransaction ?: return
        viewModelScope.launch {
            addTransactionUseCase(transaction)
            lastDeletedTransaction = null
        }
    }

    /**
     * Clona una transacción existente con fecha de hoy y la registra inmediatamente.
     */
    fun duplicateTransaction(transaction: Transaction, onSuccess: ((Transaction) -> Unit)? = null) {
        viewModelScope.launch {
            val duplicate = transaction.copy(
                id = 0L,
                date = Date()
            )
            val newId = addTransactionUseCase(duplicate)
            val created = duplicate.copy(id = newId)
            onSuccess?.invoke(created)
        }
    }

    fun addExpense(
        amount: Double,
        category: Category,
        description: String?,
        date: Date = Date(),
        accountId: Long = 1L
    ) {
        viewModelScope.launch {
            val expense = Transaction(
                amount = amount,
                type = TransactionType.EXPENSE,
                category = category,
                description = description?.ifBlank { null },
                date = date,
                accountId = accountId
            )
            addTransactionUseCase(expense)
        }
    }

    private data class FilterParams(
        val type: String = "ALL",
        val categoryId: Long = -1L,
        val sortOption: TransactionSortOption = TransactionSortOption.DATE_DESC,
        val timePreset: TimeRangePreset = TimeRangePreset.ALL,
        val searchQuery: String = "",
        val startDate: Long? = null,
        val endDate: Long? = null,
        val minAmount: Double? = null,
        val maxAmount: Double? = null
    )
}
