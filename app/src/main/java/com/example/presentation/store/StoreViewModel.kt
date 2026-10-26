package com.example.presentation.store

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.Account
import com.example.domain.model.Business
import com.example.domain.model.Category
import com.example.domain.model.Transaction
import com.example.domain.model.TransactionType
import com.example.domain.repository.BusinessRepository
import com.example.domain.repository.StoreRepository
import com.example.domain.usecase.account.GetAccountsUseCase
import com.example.domain.usecase.category.GetCategoriesUseCase
import com.example.domain.usecase.transaction.AddTransactionUseCase
import com.example.domain.usecase.transaction.DeleteTransactionUseCase
import com.example.domain.usecase.transaction.GetTransactionsUseCase
import com.example.domain.usecase.transaction.UpdateTransactionUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class StoreFilterType(val label: String) {
    ALL("Todos"),
    SALES("Ventas"),
    EXPENSES("Gastos")
}

enum class StorePeriodFilter(val label: String) {
    ALL_TIME("Histórico"),
    THIS_MONTH("Este Mes"),
    LAST_30_DAYS("30 Días"),
    THIS_WEEK("Esta Semana")
}

data class StoreTransactionItem(
    val id: Long,
    val description: String,
    val amount: Double,
    val isSale: Boolean,
    val date: Long,
    val categoryName: String,
    val accountName: String,
    val accountId: Long,
    val businessId: Long,
    val rawTransaction: Transaction
)

data class StoreDailyTrend(
    val dayLabel: String,
    val sales: Double,
    val expenses: Double
)

data class BusinessFinancialSummary(
    val business: Business,
    val sales: Double,
    val expenses: Double,
    val netProfit: Double,
    val profitMarginPercentage: Double,
    val salesCount: Int,
    val expensesCount: Int,
    val inventoryValue: Double
)

data class StoreUiState(
    val businesses: List<Business> = emptyList(),
    val selectedBusinessId: Long? = null, // null = Consolidado / Todos los Negocios
    val selectedBusiness: Business? = null,
    val businessBreakdown: List<BusinessFinancialSummary> = emptyList(),
    val cashAvailable: Double = 0.0,
    val inventoryValue: Double = 0.0,
    val totalSales: Double = 0.0,
    val totalExpenses: Double = 0.0,
    val netProfit: Double = 0.0,
    val profitMarginPercentage: Double = 0.0,
    val averageTicket: Double = 0.0,
    val salesCount: Int = 0,
    val expensesCount: Int = 0,
    val periodSales: Double = 0.0,
    val periodExpenses: Double = 0.0,
    val periodProfit: Double = 0.0,
    val weeklyTrends: List<StoreDailyTrend> = emptyList(),
    val transactions: List<StoreTransactionItem> = emptyList(),
    val filteredTransactions: List<StoreTransactionItem> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val currentFilter: StoreFilterType = StoreFilterType.ALL,
    val currentPeriod: StorePeriodFilter = StorePeriodFilter.ALL_TIME,
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val message: String? = null
)

class StoreViewModel(
    private val getTransactionsUseCase: GetTransactionsUseCase,
    private val addTransactionUseCase: AddTransactionUseCase,
    private val updateTransactionUseCase: UpdateTransactionUseCase,
    private val deleteTransactionUseCase: DeleteTransactionUseCase,
    private val getCategoriesUseCase: GetCategoriesUseCase,
    private val getAccountsUseCase: GetAccountsUseCase,
    private val storeRepository: StoreRepository,
    private val businessRepository: BusinessRepository
) : ViewModel() {

    private val _selectedBusinessId = MutableStateFlow<Long?>(null) // null = Todos los negocios
    private val _currentFilter = MutableStateFlow(StoreFilterType.ALL)
    private val _currentPeriod = MutableStateFlow(StorePeriodFilter.ALL_TIME)
    private val _searchQuery = MutableStateFlow("")

    private var lastDeletedTx: Transaction? = null

    init {
        viewModelScope.launch {
            businessRepository.getAllBusinesses().firstOrNull()?.let { list ->
                if (list.isEmpty()) {
                    val defaultBiz = Business(
                        name = "INSERC Store",
                        type = "Tecnología y Comercio",
                        description = "Negocio comercial principal",
                        colorHex = "#2563EB",
                        iconKey = "storefront",
                        inventoryValue = 0.0
                    )
                    businessRepository.insertBusiness(defaultBiz)
                }
            }
        }
    }

    // Helper para determinar a qué negocio pertenece una transacción
    private fun resolveBusinessId(tx: Transaction, businesses: List<Business>): Long {
        // 1. Tag explícito BIZ_<id>
        businesses.forEach { biz ->
            if (tx.tags?.contains("BIZ_${biz.id}", ignoreCase = true) == true) {
                return biz.id
            }
        }

        // 2. Coincidencia por nombre de negocio en tags o descripción
        businesses.forEach { biz ->
            if (biz.name.isNotBlank() && (
                tx.tags?.contains(biz.name, ignoreCase = true) == true ||
                tx.description?.contains(biz.name, ignoreCase = true) == true
            )) {
                return biz.id
            }
        }

        // 3. Casos históricos / retrocompatibilidad con INSERC
        if (tx.tags?.contains("INSERC", ignoreCase = true) == true ||
            tx.description?.contains("INSERC", ignoreCase = true) == true
        ) {
            val insercBiz = businesses.find { it.name.contains("INSERC", ignoreCase = true) }
            return insercBiz?.id ?: businesses.firstOrNull()?.id ?: 1L
        }

        // 4. Fallback: primer negocio registrado
        return businesses.firstOrNull()?.id ?: 1L
    }

    private data class StoreRawData(
        val allTransactions: List<Transaction>,
        val accounts: List<Account>,
        val categories: List<Category>,
        val businesses: List<Business>
    )

    private val storeRawFlow = combine(
        getTransactionsUseCase(),
        getAccountsUseCase(),
        getCategoriesUseCase(),
        businessRepository.getAllBusinesses()
    ) { allTransactions, accounts, categories, businesses ->
        StoreRawData(allTransactions, accounts, categories, businesses)
    }

    val uiState: StateFlow<StoreUiState> = combine(
        storeRawFlow,
        _selectedBusinessId,
        _currentFilter,
        _currentPeriod,
        _searchQuery
    ) { raw, selectedBizId, filter, period, query ->
        val businesses = raw.businesses
        val selectedBiz = businesses.find { it.id == selectedBizId }
        val accountMap = raw.accounts.associateBy { it.id }

        // Identificar todas las transacciones vinculadas a negocios
        val allBusinessTxs = raw.allTransactions.filter { tx ->
            val hasBizTag = tx.tags?.contains("BIZ_", ignoreCase = true) == true ||
                            tx.tags?.contains("INSERC", ignoreCase = true) == true ||
                            tx.tags?.contains("Negocio", ignoreCase = true) == true ||
                            tx.tags?.contains("Ventas", ignoreCase = true) == true ||
                            tx.category.name.contains("Negocio", ignoreCase = true) ||
                            tx.category.name.contains("Ventas", ignoreCase = true) ||
                            tx.description?.contains("INSERC", ignoreCase = true) == true ||
                            tx.description?.contains("Tienda", ignoreCase = true) == true ||
                            tx.description?.contains("Negocio", ignoreCase = true) == true ||
                            businesses.any { b -> tx.description?.contains(b.name, ignoreCase = true) == true }
            hasBizTag
        }

        // Convertir transacciones a StoreTransactionItem mapeando cada una a su negocio
        val allStoreItems = allBusinessTxs.sortedByDescending { it.date }.map { tx ->
            val acctName = accountMap[tx.accountId]?.name ?: "Cuenta No Asignada"
            val bizId = resolveBusinessId(tx, businesses)
            StoreTransactionItem(
                id = tx.id,
                description = tx.description?.takeIf { it.isNotBlank() } ?: tx.category.name,
                amount = tx.amount,
                isSale = tx.type == TransactionType.INCOME,
                date = tx.date.time,
                categoryName = tx.category.name,
                accountName = acctName,
                accountId = tx.accountId,
                businessId = bizId,
                rawTransaction = tx
            )
        }

        // Calcular resumen financiero desglosado para cada negocio
        val businessBreakdown = businesses.map { biz ->
            val bizItems = allStoreItems.filter { it.businessId == biz.id }
            val bSalesList = bizItems.filter { it.isSale }
            val bExpensesList = bizItems.filter { !it.isSale }
            val bSales = bSalesList.sumOf { it.amount }
            val bExpenses = bExpensesList.sumOf { it.amount }
            val bNet = bSales - bExpenses
            val bMargin = if (bSales > 0.0) ((bSales - bExpenses) / bSales) * 100.0 else 0.0

            BusinessFinancialSummary(
                business = biz,
                sales = bSales,
                expenses = bExpenses,
                netProfit = bNet,
                profitMarginPercentage = bMargin,
                salesCount = bSalesList.size,
                expensesCount = bExpensesList.size,
                inventoryValue = biz.inventoryValue
            )
        }

        // Filtrar transacciones para la vista actual (Consolidada o Negocio Específico)
        val activeBusinessItems = if (selectedBizId == null) {
            allStoreItems
        } else {
            allStoreItems.filter { it.businessId == selectedBizId }
        }

        // Cálculos generales para la vista activa
        val salesList = activeBusinessItems.filter { it.isSale }
        val expensesList = activeBusinessItems.filter { !it.isSale }
        val totalSales = salesList.sumOf { it.amount }
        val totalExpenses = expensesList.sumOf { it.amount }
        val netCash = totalSales - totalExpenses
        val profitMargin = if (totalSales > 0.0) ((totalSales - totalExpenses) / totalSales) * 100.0 else 0.0
        val avgTicket = if (salesList.isNotEmpty()) totalSales / salesList.size else 0.0

        val totalInventory = if (selectedBiz != null) {
            selectedBiz.inventoryValue
        } else {
            businesses.sumOf { it.inventoryValue }
        }

        // Tendencias de los últimos 7 días para la vista activa
        val now = Calendar.getInstance()
        val trends = mutableListOf<StoreDailyTrend>()
        for (i in 6 downTo 0) {
            val dayCal = Calendar.getInstance().apply {
                time = now.time
                add(Calendar.DAY_OF_YEAR, -i)
            }
            val startOfDay = (dayCal.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val endOfDay = (dayCal.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }.timeInMillis

            val dayTxs = activeBusinessItems.filter { it.date in startOfDay..endOfDay }
            val daySales = dayTxs.filter { it.isSale }.sumOf { it.amount }
            val dayExpenses = dayTxs.filter { !it.isSale }.sumOf { it.amount }

            val dayName = when (dayCal.get(Calendar.DAY_OF_WEEK)) {
                Calendar.MONDAY -> "Lun"
                Calendar.TUESDAY -> "Mar"
                Calendar.WEDNESDAY -> "Mié"
                Calendar.THURSDAY -> "Jue"
                Calendar.FRIDAY -> "Vie"
                Calendar.SATURDAY -> "Sáb"
                Calendar.SUNDAY -> "Dom"
                else -> ""
            }
            trends.add(StoreDailyTrend(dayName, daySales, dayExpenses))
        }

        // Filtro por Período
        val periodStartDate: Long? = when (period) {
            StorePeriodFilter.ALL_TIME -> null
            StorePeriodFilter.THIS_MONTH -> {
                (now.clone() as Calendar).apply {
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
            }
            StorePeriodFilter.LAST_30_DAYS -> {
                (now.clone() as Calendar).apply {
                    add(Calendar.DAY_OF_YEAR, -30)
                }.timeInMillis
            }
            StorePeriodFilter.THIS_WEEK -> {
                (now.clone() as Calendar).apply {
                    set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
            }
        }

        val inPeriodTransactions = if (periodStartDate != null) {
            activeBusinessItems.filter { it.date >= periodStartDate }
        } else {
            activeBusinessItems
        }

        val pSales = inPeriodTransactions.filter { it.isSale }.sumOf { it.amount }
        val pExpenses = inPeriodTransactions.filter { !it.isSale }.sumOf { it.amount }
        val pProfit = pSales - pExpenses

        // Filtro por Tipo y Búsqueda
        val filtered = inPeriodTransactions.filter { item ->
            val matchesFilter = when (filter) {
                StoreFilterType.ALL -> true
                StoreFilterType.SALES -> item.isSale
                StoreFilterType.EXPENSES -> !item.isSale
            }
            val matchesQuery = if (query.isBlank()) {
                true
            } else {
                item.description.contains(query, ignoreCase = true) ||
                item.accountName.contains(query, ignoreCase = true) ||
                item.categoryName.contains(query, ignoreCase = true)
            }
            matchesFilter && matchesQuery
        }

        StoreUiState(
            businesses = businesses,
            selectedBusinessId = selectedBizId,
            selectedBusiness = selectedBiz,
            businessBreakdown = businessBreakdown,
            cashAvailable = netCash,
            inventoryValue = totalInventory,
            totalSales = totalSales,
            totalExpenses = totalExpenses,
            netProfit = netCash,
            profitMarginPercentage = profitMargin,
            averageTicket = avgTicket,
            salesCount = salesList.size,
            expensesCount = expensesList.size,
            periodSales = pSales,
            periodExpenses = pExpenses,
            periodProfit = pProfit,
            weeklyTrends = trends,
            transactions = activeBusinessItems,
            filteredTransactions = filtered,
            accounts = raw.accounts,
            categories = raw.categories,
            currentFilter = filter,
            currentPeriod = period,
            searchQuery = query,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = StoreUiState(isLoading = true)
    )

    // Selección de negocio
    fun selectBusiness(businessId: Long?) {
        _selectedBusinessId.value = businessId
    }

    // Creación de un nuevo negocio
    fun createBusiness(
        name: String,
        type: String,
        description: String,
        colorHex: String,
        inventoryValue: Double
    ) {
        viewModelScope.launch {
            val newBiz = Business(
                name = name.trim(),
                type = type.trim().ifBlank { "Comercio / Ventas" },
                description = description.trim(),
                colorHex = colorHex.ifBlank { "#2563EB" },
                iconKey = "storefront",
                inventoryValue = inventoryValue.coerceAtLeast(0.0)
            )
            val newId = businessRepository.insertBusiness(newBiz)
            _selectedBusinessId.value = newId
        }
    }

    // Actualización de un negocio existente
    fun updateBusiness(business: Business) {
        viewModelScope.launch {
            businessRepository.updateBusiness(business)
        }
    }

    // Eliminación de un negocio
    fun deleteBusiness(businessId: Long) {
        viewModelScope.launch {
            val biz = businessRepository.getBusinessById(businessId)
            if (biz != null) {
                businessRepository.deleteBusiness(biz)
                if (_selectedBusinessId.value == businessId) {
                    _selectedBusinessId.value = null
                }
            }
        }
    }

    fun setFilter(filter: StoreFilterType) {
        _currentFilter.value = filter
    }

    fun setPeriod(period: StorePeriodFilter) {
        _currentPeriod.value = period
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    // Actualiza el valor del inventario para el negocio actualmente activo
    fun updateInventoryValue(value: Double) {
        val currentBizId = _selectedBusinessId.value ?: uiState.value.businesses.firstOrNull()?.id ?: 1L
        viewModelScope.launch {
            businessRepository.updateInventoryValue(currentBizId, value)
            storeRepository.saveInventoryValue(value)
        }
    }

    fun addTransaction(
        description: String,
        amount: Double,
        isSale: Boolean,
        accountId: Long,
        date: Date = Date(),
        categoryId: Long? = null,
        targetBusinessId: Long? = null
    ) {
        viewModelScope.launch {
            val currentState = uiState.value
            val categories = currentState.categories
            val selectedCategory = categories.find { it.id == categoryId }
                ?: categories.find { it.name.contains("Negocio", ignoreCase = true) }
                ?: categories.find { it.name.contains("Otros", ignoreCase = true) }
                ?: Category(id = 9L, name = "Negocio", iconKey = "storefront", isDefault = true)

            val effectiveBizId = targetBusinessId
                ?: _selectedBusinessId.value
                ?: currentState.businesses.firstOrNull()?.id
                ?: 1L

            val bizName = currentState.businesses.find { it.id == effectiveBizId }?.name ?: "Negocio"
            val fallbackDesc = if (isSale) "Venta $bizName" else "Gasto $bizName"

            val newTransaction = Transaction(
                amount = amount,
                type = if (isSale) TransactionType.INCOME else TransactionType.EXPENSE,
                category = selectedCategory,
                description = description.ifBlank { fallbackDesc },
                date = date,
                accountId = accountId,
                tags = "BIZ_${effectiveBizId},Negocio"
            )

            addTransactionUseCase(newTransaction)
        }
    }

    fun addQuickSale(
        amount: Double,
        description: String,
        accountId: Long,
        targetBusinessId: Long? = null
    ) {
        addTransaction(
            description = description.ifBlank { "Venta Express" },
            amount = amount,
            isSale = true,
            accountId = accountId,
            date = Date(),
            targetBusinessId = targetBusinessId
        )
    }

    fun updateTransaction(
        item: StoreTransactionItem,
        description: String,
        amount: Double,
        isSale: Boolean,
        accountId: Long,
        date: Date = Date(item.date),
        targetBusinessId: Long? = null
    ) {
        viewModelScope.launch {
            val bizId = targetBusinessId ?: item.businessId
            val updated = item.rawTransaction.copy(
                description = description.ifBlank { if (isSale) "Venta Comercial" else "Gasto Comercial" },
                amount = amount,
                type = if (isSale) TransactionType.INCOME else TransactionType.EXPENSE,
                accountId = accountId,
                date = date,
                tags = "BIZ_${bizId},Negocio"
            )
            updateTransactionUseCase(updated)
        }
    }

    fun deleteTransaction(item: StoreTransactionItem) {
        lastDeletedTx = item.rawTransaction
        viewModelScope.launch {
            deleteTransactionUseCase(item.rawTransaction)
        }
    }

    fun undoDelete() {
        val tx = lastDeletedTx ?: return
        viewModelScope.launch {
            addTransactionUseCase(tx)
            lastDeletedTx = null
        }
    }

    fun exportStoreCsv(): String {
        val items = uiState.value.filteredTransactions
        val currentBiz = uiState.value.selectedBusiness
        val builder = java.lang.StringBuilder()
        builder.append("Reporte Comercial - ${currentBiz?.name ?: "Consolidado de Negocios"}\n")
        builder.append("ID,Fecha,Tipo,Concepto,Monto,Cuenta,Categoría,NegocioID\n")
        for (it in items) {
            val dateStr = com.example.presentation.common.util.DateFormatter.format(Date(it.date))
            val typeStr = if (it.isSale) "Venta" else "Gasto/Compra"
            val desc = it.description.replace("\"", "\"\"")
            val amt = String.format(Locale.US, "%.2f", it.amount)
            val acct = it.accountName.replace("\"", "\"\"")
            val cat = it.categoryName.replace("\"", "\"\"")
            builder.append("${it.id},\"$dateStr\",$typeStr,\"$desc\",$amt,\"$acct\",\"$cat\",${it.businessId}\n")
        }
        return builder.toString()
    }

    fun getTodayCloseout(): StoreDailyCloseout {
        val today = Calendar.getInstance()
        val todayItems = uiState.value.transactions.filter {
            val cal = Calendar.getInstance().apply { timeInMillis = it.date }
            cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
            cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
        }
        val sales = todayItems.filter { it.isSale }
        val expenses = todayItems.filter { !it.isSale }
        val salesTotal = sales.sumOf { it.amount }
        val expensesTotal = expenses.sumOf { it.amount }
        return StoreDailyCloseout(
            date = today.time,
            salesCount = sales.size,
            salesTotal = salesTotal,
            expensesCount = expenses.size,
            expensesTotal = expensesTotal,
            netBalance = salesTotal - expensesTotal,
            todayTransactions = todayItems
        )
    }

    fun generateReceiptText(
        item: StoreTransactionItem,
        customerName: String,
        paidWith: Double? = null,
        change: Double? = null
    ): String {
        val bizName = uiState.value.selectedBusiness?.name ?: "COMPROBANTE COMERCIAL"
        val dateStr = com.example.presentation.common.util.DateFormatter.format(Date(item.date))
        val currency = com.example.presentation.common.util.CurrencyFormatter.format(item.amount)
        val client = customerName.ifBlank { "Consumidor Final" }
        val sb = StringBuilder()
        sb.append("================================\n")
        sb.append("      $bizName\n")
        sb.append("     COMPROBANTE DE VENTA\n")
        sb.append("================================\n")
        sb.append("Fecha: $dateStr\n")
        sb.append("Cliente: $client\n")
        sb.append("Transacción: #${item.id}\n")
        sb.append("Cuenta: ${item.accountName}\n")
        sb.append("--------------------------------\n")
        sb.append("CONCEPTO: ${item.description}\n")
        sb.append("TOTAL: $currency\n")
        if (paidWith != null && paidWith >= item.amount) {
            sb.append("PAGÓ CON: ${com.example.presentation.common.util.CurrencyFormatter.format(paidWith)}\n")
            if (change != null) {
                sb.append("CAMBIO: ${com.example.presentation.common.util.CurrencyFormatter.format(change)}\n")
            }
        }
        sb.append("--------------------------------\n")
        sb.append(" ¡Gracias por su compra!\n")
        sb.append("================================\n")
        return sb.toString()
    }

    fun generateCloseoutShareText(closeout: StoreDailyCloseout): String {
        val bizName = uiState.value.selectedBusiness?.name ?: "Consolidado de Negocios"
        val dateStr = com.example.presentation.common.util.DateFormatter.format(closeout.date)
        val sb = StringBuilder()
        sb.append("================================\n")
        sb.append("  CORTE DE CAJA - $bizName\n")
        sb.append("================================\n")
        sb.append("Fecha: $dateStr\n")
        sb.append("--------------------------------\n")
        sb.append("Ventas del día (${closeout.salesCount}): ${com.example.presentation.common.util.CurrencyFormatter.format(closeout.salesTotal)}\n")
        sb.append("Gastos del día (${closeout.expensesCount}): ${com.example.presentation.common.util.CurrencyFormatter.format(closeout.expensesTotal)}\n")
        sb.append("--------------------------------\n")
        sb.append("BALANCE NETO: ${com.example.presentation.common.util.CurrencyFormatter.format(closeout.netBalance)}\n")
        sb.append("--------------------------------\n")
        sb.append("Detalle de operaciones:\n")
        if (closeout.todayTransactions.isEmpty()) {
            sb.append("  (Sin operaciones registradas hoy)\n")
        } else {
            closeout.todayTransactions.forEach {
                val sign = if (it.isSale) "(+) Venta: " else "(-) Gasto: "
                sb.append("• $sign${it.description} - ${com.example.presentation.common.util.CurrencyFormatter.format(it.amount)} [${it.accountName}]\n")
            }
        }
        sb.append("================================\n")
        return sb.toString()
    }

    fun generateDailyCloseoutReportText(closeout: StoreDailyCloseout): String = generateCloseoutShareText(closeout)
}

data class StoreDailyCloseout(
    val date: Date,
    val salesCount: Int,
    val salesTotal: Double,
    val expensesCount: Int,
    val expensesTotal: Double,
    val netBalance: Double,
    val todayTransactions: List<StoreTransactionItem>
)
