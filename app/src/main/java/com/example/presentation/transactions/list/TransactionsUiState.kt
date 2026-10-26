package com.example.presentation.transactions.list

import com.example.domain.model.Category
import com.example.domain.model.Transaction

/**
 * Opciones de ordenamiento para las transacciones.
 */
enum class TransactionSortOption(val displayName: String) {
    DATE_DESC("Fecha: Más reciente"),
    DATE_ASC("Fecha: Más antigua"),
    AMOUNT_DESC("Monto: Mayor a menor"),
    AMOUNT_ASC("Monto: Menor a mayor"),
    CATEGORY_ASC("Categoría: A-Z"),
    CATEGORY_DESC("Categoría: Z-A")
}

/**
 * Presets de filtro temporal rápido para consultar movimientos.
 */
enum class TimeRangePreset(val label: String) {
    ALL("Todo"),
    THIS_MONTH("Este Mes"),
    LAST_MONTH("Mes Anterior"),
    LAST_7_DAYS("7 Días"),
    THIS_YEAR("Este Año")
}

/**
 * Agrupación de transacciones por fecha con totales precalculados para máximo rendimiento al hacer scroll.
 */
data class DailyGroupedTransactions(
    val dateHeader: String,
    val transactions: List<Transaction>,
    val totalIncome: Double,
    val totalExpense: Double,
    val netBalance: Double
)

/**
 * Representa el estado de UI para la pantalla de historial de movimientos,
 * incluyendo filtros seleccionados para categoría, tipo, tiempo y selección múltiple.
 */
data class TransactionsUiState(
    val isLoading: Boolean = false,
    val transactions: List<Transaction> = emptyList(),
    val filteredTransactions: List<Transaction> = emptyList(),
    val groupedTransactions: List<DailyGroupedTransactions> = emptyList(),
    val categories: List<Category> = emptyList(),
    val selectedTypeFilter: String = "ALL", // "ALL", "INCOME", "EXPENSE", "TRANSFER"
    val selectedCategoryIdFilter: Long = -1L, // -1L significa sin filtro de categoría
    val selectedSortOption: TransactionSortOption = TransactionSortOption.DATE_DESC,
    val selectedTimePreset: TimeRangePreset = TimeRangePreset.ALL,
    val selectedTransactionIds: Set<Long> = emptySet(),
    val searchQuery: String = "",
    val startDate: Long? = null,
    val endDate: Long? = null,
    val minAmount: Double? = null,
    val maxAmount: Double? = null,
    val errorMessage: String? = null
) {
    val isSelectionMode: Boolean get() = selectedTransactionIds.isNotEmpty()
}

