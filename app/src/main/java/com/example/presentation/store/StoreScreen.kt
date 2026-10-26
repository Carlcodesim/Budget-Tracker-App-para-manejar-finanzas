package com.example.presentation.store

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.Account
import com.example.domain.model.Business
import com.example.presentation.common.components.ConfirmDeleteDialog
import com.example.presentation.common.components.EmptyState
import com.example.presentation.common.components.LoadingIndicator
import com.example.presentation.common.util.CurrencyFormatter
import com.example.presentation.common.util.DateFormatter
import com.example.presentation.store.components.BusinessConsolidatedComparisonCard
import com.example.presentation.store.components.BusinessSwitcherBar
import com.example.presentation.store.components.ConfirmDeleteBusinessDialog
import com.example.presentation.store.components.CreateOrEditBusinessDialog
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.IncomeGreen
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreScreen(
    viewModel: StoreViewModel,
    onNavigateBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // Dialog state
    var showAddDialog by remember { mutableStateOf(false) }
    var showQuickSaleDialog by remember { mutableStateOf(false) }
    var showEditInventoryDialog by remember { mutableStateOf(false) }
    var itemToEdit by remember { mutableStateOf<StoreTransactionItem?>(null) }
    var itemToDelete by remember { mutableStateOf<StoreTransactionItem?>(null) }
    var itemForDetail by remember { mutableStateOf<StoreTransactionItem?>(null) }
    var itemForReceipt by remember { mutableStateOf<StoreTransactionItem?>(null) }
    var receiptPaidWith by remember { mutableStateOf<Double?>(null) }
    var receiptChange by remember { mutableStateOf<Double?>(null) }
    var showCashRegisterSummary by remember { mutableStateOf(false) }
    var showCalculatorsDialog by remember { mutableStateOf(false) }

    // Estados de diálogo para gestión de múltiples negocios
    var showCreateBusinessDialog by remember { mutableStateOf(false) }
    var businessToEdit by remember { mutableStateOf<Business?>(null) }
    var businessToDelete by remember { mutableStateOf<Business?>(null) }

    // Obtenemos divisa reactiva
    val currentCurrencyOption by CurrencyFormatter.currencyOptionFlow.collectAsStateWithLifecycle()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            val selectedBiz = state.selectedBusiness
            val bizColor = remember(selectedBiz) {
                selectedBiz?.let {
                    try {
                        Color(android.graphics.Color.parseColor(it.colorHex))
                    } catch (e: Exception) {
                        null
                    }
                }
            }

            TopAppBar(
                title = {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (bizColor != null) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(bizColor)
                                )
                            }
                            Text(
                                text = selectedBiz?.name ?: "Consolidado de Negocios",
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = if (selectedBiz != null) {
                                "${selectedBiz.type} • Vista Individual"
                            } else {
                                "Multinegocio (${state.businesses.size} registrados) • Ventas e Inventario"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    if (onNavigateBack != null) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Volver"
                            )
                        }
                    }
                },
                actions = {
                    // Editar negocio activo (si hay uno seleccionado)
                    if (selectedBiz != null) {
                        IconButton(
                            onClick = { businessToEdit = selectedBiz }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Editar negocio activo",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else {
                        // Agregar nuevo negocio desde el appbar
                        IconButton(
                            onClick = { showCreateBusinessDialog = true }
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddBusiness,
                                contentDescription = "Nuevo negocio",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Corte de Caja del Día
                    IconButton(
                        onClick = { showCashRegisterSummary = true }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                            contentDescription = "Corte de Caja Diario",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Calculadora de Margen y Precio
                    IconButton(
                        onClick = { showCalculatorsDialog = true }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Calculate,
                            contentDescription = "Calculadora de Precios y Margen",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Exportar reporte CSV
                    IconButton(
                        onClick = {
                            val csv = viewModel.exportStoreCsv()
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, csv)
                                type = "text/csv"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Exportar Reporte Negocio (CSV)"))
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Exportar datos",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Botón Venta Express rápida
                SmallFloatingActionButton(
                    onClick = { showQuickSaleDialog = true },
                    containerColor = IncomeGreen,
                    contentColor = Color.White,
                    shape = CircleShape,
                    modifier = Modifier.testTag("quick_sale_fab")
                ) {
                    Icon(Icons.Default.FlashOn, contentDescription = "Venta Rápida Express")
                }

                // Botón FAB principal
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = CircleShape,
                    modifier = Modifier.testTag("add_store_transaction_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Añadir registro de negocio")
                }
            }
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        if (state.isLoading) {
            LoadingIndicator()
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.TopCenter
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 840.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Barra de Selección y Conmutación de Negocios
                    item {
                        BusinessSwitcherBar(
                            businesses = state.businesses,
                            selectedBusinessId = state.selectedBusinessId,
                            onSelectBusiness = { viewModel.selectBusiness(it) },
                            onAddNewBusiness = { showCreateBusinessDialog = true },
                            onEditSelectedBusiness = {
                                state.selectedBusiness?.let { businessToEdit = it }
                            },
                            onDeleteSelectedBusiness = {
                                state.selectedBusiness?.let { businessToDelete = it }
                            }
                        )
                    }

                    // Selector de Período Temporal
                    item {
                        ScrollableTabRow(
                            selectedTabIndex = state.currentPeriod.ordinal,
                            edgePadding = 0.dp,
                            divider = {},
                            containerColor = Color.Transparent,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            StorePeriodFilter.values().forEach { period ->
                                Tab(
                                    selected = state.currentPeriod == period,
                                    onClick = { viewModel.setPeriod(period) },
                                    text = {
                                        Text(
                                            text = period.label,
                                            fontWeight = if (state.currentPeriod == period) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                )
                            }
                        }
                    }

                    // Tarjetas Resumen del Negocio y KPIs
                    item {
                        StoreSummaryCards(
                            cashAvailable = state.cashAvailable,
                            inventoryValue = state.inventoryValue,
                            totalSales = state.totalSales,
                            totalExpenses = state.totalExpenses,
                            netProfit = state.netProfit,
                            profitMargin = state.profitMarginPercentage,
                            averageTicket = state.averageTicket,
                            salesCount = state.salesCount,
                            expensesCount = state.expensesCount,
                            periodSales = state.periodSales,
                            periodExpenses = state.periodExpenses,
                            periodProfit = state.periodProfit,
                            currentPeriod = state.currentPeriod,
                            onEditInventoryClick = { showEditInventoryDialog = true }
                        )
                    }

                    // Desglose Comparativo de Todos los Negocios (visible en vista Consolidada)
                    if (state.selectedBusinessId == null && state.businessBreakdown.isNotEmpty()) {
                        item {
                            BusinessConsolidatedComparisonCard(
                                summaries = state.businessBreakdown,
                                onSelectBusiness = { bizId -> viewModel.selectBusiness(bizId) }
                            )
                        }
                    }

                    // Gráfica de Rendimiento Semanal
                    if (state.weeklyTrends.isNotEmpty()) {
                        item {
                            StoreWeeklyPerformanceCard(trends = state.weeklyTrends)
                        }
                    }

                    // Buscador y Filtros
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = state.searchQuery,
                                onValueChange = { viewModel.setSearchQuery(it) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("store_search_input"),
                                placeholder = { Text("Buscar en ventas, gastos, clientes, cuentas...") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "Buscar",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                trailingIcon = {
                                    if (state.searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                            Icon(
                                                imageVector = Icons.Default.Clear,
                                                contentDescription = "Limpiar",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )

                            // Filtros de Tipo
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = state.currentFilter == StoreFilterType.ALL,
                                    onClick = { viewModel.setFilter(StoreFilterType.ALL) },
                                    label = { Text("Todos (${state.transactions.size})") },
                                    shape = RoundedCornerShape(10.dp)
                                )
                                FilterChip(
                                    selected = state.currentFilter == StoreFilterType.SALES,
                                    onClick = { viewModel.setFilter(StoreFilterType.SALES) },
                                    label = { Text("Ventas (${state.salesCount})") },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = IncomeGreen.copy(alpha = 0.2f),
                                        selectedLabelColor = IncomeGreen
                                    )
                                )
                                FilterChip(
                                    selected = state.currentFilter == StoreFilterType.EXPENSES,
                                    onClick = { viewModel.setFilter(StoreFilterType.EXPENSES) },
                                    label = { Text("Gastos (${state.expensesCount})") },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = ExpenseRed.copy(alpha = 0.2f),
                                        selectedLabelColor = ExpenseRed
                                    )
                                )
                            }
                        }
                    }

                    // Título de la lista
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Movimientos (${state.filteredTransactions.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Sincronizado con Finanzas",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    if (state.filteredTransactions.isEmpty()) {
                        item {
                            EmptyState(
                                icon = Icons.Default.Storefront,
                                title = if (state.searchQuery.isNotBlank()) "Sin resultados" else "Sin registros en este período",
                                subtitle = if (state.searchQuery.isNotBlank()) "No se encontraron operaciones con '${state.searchQuery}'" else "Registra ventas o compras de tu negocio para llevar el control integrado con tu presupuesto."
                            )
                        }
                    } else {
                        items(
                            items = state.filteredTransactions,
                            key = { it.id },
                            contentType = { "store_transaction" }
                        ) { item ->
                            val itemBiz = state.businesses.find { it.id == item.businessId }
                            StoreTransactionCard(
                                item = item,
                                business = itemBiz,
                                onClick = { itemForDetail = item },
                                onReceiptClick = { itemForReceipt = item },
                                onEditClick = { itemToEdit = item },
                                onDeleteClick = { itemToDelete = item }
                            )
                        }
                    }
                }
            }
        }
    }

    // Dialogo para Agregar Venta / Gasto de Negocio Detallado
    if (showAddDialog) {
        StoreTransactionDialog(
            accounts = state.accounts,
            businesses = state.businesses,
            initialBusinessId = state.selectedBusinessId ?: state.businesses.firstOrNull()?.id,
            currencySymbol = currentCurrencyOption.symbol,
            onDismiss = { showAddDialog = false },
            onSave = { description, amount, isSale, accountId, date, targetBizId ->
                viewModel.addTransaction(
                    description = description,
                    amount = amount,
                    isSale = isSale,
                    accountId = accountId,
                    date = date,
                    targetBusinessId = targetBizId
                )
                showAddDialog = false
                val targetBizName = state.businesses.find { it.id == targetBizId }?.name
                val bizSuffix = if (targetBizName != null) " en $targetBizName" else ""
                scope.launch {
                    val label = if (isSale) "Venta" else "Gasto"
                    snackbarHostState.showSnackbar("$label de ${CurrencyFormatter.format(amount)} registrado con éxito$bizSuffix")
                }
            }
        )
    }

    // Dialogo de Venta Express Rápida
    if (showQuickSaleDialog) {
        QuickSaleDialog(
            accounts = state.accounts,
            businesses = state.businesses,
            initialBusinessId = state.selectedBusinessId ?: state.businesses.firstOrNull()?.id,
            currencySymbol = currentCurrencyOption.symbol,
            onDismiss = { showQuickSaleDialog = false },
            onSave = { description, amount, accountId, paidWith, change, generateReceipt, targetBizId ->
                viewModel.addTransaction(
                    description = description,
                    amount = amount,
                    isSale = true,
                    accountId = accountId,
                    targetBusinessId = targetBizId
                )
                showQuickSaleDialog = false
                val targetBiz = state.businesses.find { it.id == targetBizId }
                val targetBizName = targetBiz?.name ?: "Negocio"
                scope.launch {
                    val changeInfo = if (change != null && change > 0) " | Cambio: ${CurrencyFormatter.format(change)}" else ""
                    snackbarHostState.showSnackbar("⚡ Venta express de ${CurrencyFormatter.format(amount)} registrada en $targetBizName$changeInfo")
                }
                if (generateReceipt) {
                    receiptPaidWith = paidWith
                    receiptChange = change
                    val tempAccountName = state.accounts.find { it.id == accountId }?.name ?: "Efectivo"
                    val tempCategory = state.categories.firstOrNull { it.name.contains("Ventas", ignoreCase = true) }
                        ?: state.categories.firstOrNull()
                        ?: com.example.domain.model.Category(1, "Ventas", "INCOME")
                    itemForReceipt = StoreTransactionItem(
                        id = System.currentTimeMillis() % 100000,
                        description = description,
                        amount = amount,
                        isSale = true,
                        date = System.currentTimeMillis(),
                        categoryName = tempCategory.name,
                        accountName = tempAccountName,
                        accountId = accountId,
                        businessId = targetBizId,
                        rawTransaction = com.example.domain.model.Transaction(
                            id = 0,
                            amount = amount,
                            type = com.example.domain.model.TransactionType.INCOME,
                            category = tempCategory,
                            description = description,
                            date = Date(),
                            accountId = accountId,
                            tags = "BIZ_${targetBizId},Negocio,$targetBizName"
                        )
                    )
                }
            }
        )
    }

    // Dialogo para Editar Venta / Gasto Existente
    itemToEdit?.let { currentItem ->
        EditStoreTransactionDialog(
            item = currentItem,
            accounts = state.accounts,
            businesses = state.businesses,
            currencySymbol = currentCurrencyOption.symbol,
            onDismiss = { itemToEdit = null },
            onSave = { description, amount, isSale, accountId, date, targetBizId ->
                viewModel.updateTransaction(
                    item = currentItem,
                    description = description,
                    amount = amount,
                    isSale = isSale,
                    accountId = accountId,
                    date = date,
                    targetBusinessId = targetBizId
                )
                itemToEdit = null
                scope.launch {
                    snackbarHostState.showSnackbar("Registro actualizado correctamente")
                }
            }
        )
    }

    // Dialogo para Editar Valor del Inventario del negocio activo
    if (showEditInventoryDialog) {
        val bizName = state.selectedBusiness?.name ?: "Negocio"
        EditInventoryDialog(
            currentValue = state.inventoryValue,
            currencySymbol = currentCurrencyOption.symbol,
            onDismiss = { showEditInventoryDialog = false },
            onSave = { newValue ->
                viewModel.updateInventoryValue(newValue)
                showEditInventoryDialog = false
                scope.launch {
                    snackbarHostState.showSnackbar("Inventario de $bizName actualizado a ${CurrencyFormatter.format(newValue)}")
                }
            }
        )
    }

    // Dialogo para Crear Nuevo Negocio
    if (showCreateBusinessDialog) {
        CreateOrEditBusinessDialog(
            businessToEdit = null,
            onDismiss = { showCreateBusinessDialog = false },
            onSave = { name, type, description, colorHex, inventoryVal ->
                viewModel.createBusiness(name, type, description, colorHex, inventoryVal)
                showCreateBusinessDialog = false
                scope.launch {
                    snackbarHostState.showSnackbar("Negocio '$name' creado con éxito")
                }
            }
        )
    }

    // Dialogo para Editar Negocio Seleccionado
    businessToEdit?.let { biz ->
        CreateOrEditBusinessDialog(
            businessToEdit = biz,
            onDismiss = { businessToEdit = null },
            onSave = { name, type, description, colorHex, inventoryVal ->
                viewModel.updateBusiness(
                    biz.copy(
                        name = name,
                        type = type,
                        description = description,
                        colorHex = colorHex,
                        inventoryValue = inventoryVal
                    )
                )
                businessToEdit = null
                scope.launch {
                    snackbarHostState.showSnackbar("Negocio '$name' actualizado con éxito")
                }
            },
            onDelete = {
                businessToDelete = biz
                businessToEdit = null
            }
        )
    }

    // Dialogo para Confirmar Eliminación de Negocio
    businessToDelete?.let { biz ->
        ConfirmDeleteBusinessDialog(
            business = biz,
            onConfirm = {
                val bizName = biz.name
                viewModel.deleteBusiness(biz.id)
                businessToDelete = null
                scope.launch {
                    snackbarHostState.showSnackbar("Negocio '$bizName' eliminado")
                }
            },
            onDismiss = { businessToDelete = null }
        )
    }

    // Dialogo Calculadora de Margen y Precios
    if (showCalculatorsDialog) {
        StoreCalculatorsDialog(
            currencySymbol = currentCurrencyOption.symbol,
            onDismiss = { showCalculatorsDialog = false }
        )
    }

    // Confirmación de eliminación de transacción
    itemToDelete?.let { item ->
        ConfirmDeleteDialog(
            title = "Eliminar registro del negocio",
            text = "¿Deseas eliminar '${item.description}' (${CurrencyFormatter.format(item.amount)})? Esta acción también actualizará los balances globales.",
            onConfirm = {
                viewModel.deleteTransaction(item)
                val deletedDesc = item.description
                itemToDelete = null
                scope.launch {
                    val result = snackbarHostState.showSnackbar(
                        message = "Registro '$deletedDesc' eliminado",
                        actionLabel = "DESHACER",
                        duration = SnackbarDuration.Short
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.undoDelete()
                    }
                }
            },
            onDismiss = { itemToDelete = null }
        )
    }

    // Detalle de movimiento al tocar la tarjeta
    itemForDetail?.let { item ->
        val biz = state.businesses.find { it.id == item.businessId }
        StoreTransactionDetailDialog(
            item = item,
            business = biz,
            onDismiss = { itemForDetail = null },
            onGenerateReceipt = {
                itemForDetail = null
                itemForReceipt = item
            },
            onEdit = {
                itemForDetail = null
                itemToEdit = item
            },
            onDelete = {
                itemForDetail = null
                itemToDelete = item
            }
        )
    }

    // Dialogo Comprobante / Recibo de Venta Digital
    itemForReceipt?.let { item ->
        val bizName = state.businesses.find { it.id == item.businessId }?.name
            ?: state.selectedBusiness?.name
            ?: "Comercio"
        StoreReceiptDialog(
            item = item,
            businessName = bizName,
            currencySymbol = currentCurrencyOption.symbol,
            initialPaidWith = receiptPaidWith,
            initialChange = receiptChange,
            onDismiss = {
                itemForReceipt = null
                receiptPaidWith = null
                receiptChange = null
            },
            onShare = { receiptText ->
                val sendIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, receiptText)
                    type = "text/plain"
                }
                context.startActivity(Intent.createChooser(sendIntent, "Compartir Comprobante de Venta"))
            },
            onCopy = { receiptText ->
                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("Comprobante $bizName", receiptText))
                scope.launch {
                    snackbarHostState.showSnackbar("Comprobante de $bizName copiado al portapapeles")
                }
            }
        )
    }

    // Dialogo Corte de Caja Diario
    if (showCashRegisterSummary) {
        val closeout = remember(state.transactions) { viewModel.getTodayCloseout() }
        val bizName = state.selectedBusiness?.name ?: "Consolidado de Negocios"
        DailyCashRegisterDialog(
            closeout = closeout,
            businessName = bizName,
            onDismiss = { showCashRegisterSummary = false },
            onShare = {
                val reportText = viewModel.generateDailyCloseoutReportText(closeout)
                val sendIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, reportText)
                    type = "text/plain"
                }
                context.startActivity(Intent.createChooser(sendIntent, "Compartir Corte de Caja Diario"))
            }
        )
    }
}

@Composable
fun StoreSummaryCards(
    cashAvailable: Double,
    inventoryValue: Double,
    totalSales: Double,
    totalExpenses: Double,
    netProfit: Double,
    profitMargin: Double,
    averageTicket: Double,
    salesCount: Int,
    expensesCount: Int,
    periodSales: Double,
    periodExpenses: Double,
    periodProfit: Double,
    currentPeriod: StorePeriodFilter,
    onEditInventoryClick: () -> Unit
) {
    val totalPatrimony = cashAvailable + inventoryValue
    val isProfitable = profitMargin >= 0.0
    var showAdvancedMetrics by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Tarjeta Principal Consolidada de Negocio
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("store_patrimony_card"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Fila Superior: Título + Badge de Margen
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Storefront,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Balance Comercial",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        if (currentPeriod != StorePeriodFilter.ALL_TIME) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = currentPeriod.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }

                    // Badge de Margen de Rentabilidad
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isProfitable) IncomeGreen.copy(alpha = 0.2f) else ExpenseRed.copy(alpha = 0.2f)
                            )
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "Margen ${String.format(java.util.Locale.US, "%.1f", profitMargin)}%",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isProfitable) IncomeGreen else ExpenseRed
                        )
                    }
                }

                // Fila de Patrimonio y Chip de Inventario
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Patrimonio Total",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                        )
                        Text(
                            text = CurrencyFormatter.format(totalPatrimony),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    // Botón Chip para Inventario
                    Surface(
                        onClick = onEditInventoryClick,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Default.Inventory,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Inv: ${CurrencyFormatter.format(inventoryValue)}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Editar",
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f),
                    thickness = 0.8.dp
                )

                // Fila compacta de 3 métricas del período
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Ventas
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Ventas ($salesCount)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                        )
                        Text(
                            text = CurrencyFormatter.format(periodSales),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = IncomeGreen
                        )
                    }

                    // Gastos
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Gastos ($expensesCount)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                        )
                        Text(
                            text = CurrencyFormatter.format(periodExpenses),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = ExpenseRed
                        )
                    }

                    // Utilidad
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Utilidad",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                        )
                        Text(
                            text = CurrencyFormatter.format(periodProfit),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (periodProfit >= 0) IncomeGreen else ExpenseRed
                        )
                    }
                }
            }
        }

        // Toggle para métricas secundarias (Caja Neta y Ticket Promedio)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showAdvancedMetrics = !showAdvancedMetrics }
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (showAdvancedMetrics) "Ocultar métricas secundarias" else "Ver caja neta y ticket promedio",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = if (showAdvancedMetrics) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
        }

        AnimatedVisibility(visible = showAdvancedMetrics) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Caja Neta en Efectivo", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = CurrencyFormatter.format(cashAvailable),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (cashAvailable >= 0) IncomeGreen else ExpenseRed
                        )
                    }
                }

                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Ticket Promedio", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = CurrencyFormatter.format(averageTicket),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StoreWeeklyPerformanceCard(trends: List<StoreDailyTrend>) {
    var isExpanded by remember { mutableStateOf(false) }
    val maxVal = trends.maxOfOrNull { maxOf(it.sales, it.expenses) }?.takeIf { it > 0 } ?: 1.0

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.BarChart,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Tendencia de los Últimos 7 Días",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(IncomeGreen))
                            Text("Ventas", style = MaterialTheme.typography.labelSmall)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(ExpenseRed))
                            Text("Gastos", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(75.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        trends.forEach { trend ->
                            val salesHeightFraction = (trend.sales / maxVal).coerceIn(0.04, 1.0).toFloat()
                            val expensesHeightFraction = (trend.expenses / maxVal).coerceIn(0.04, 1.0).toFloat()

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Bottom,
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    verticalAlignment = Alignment.Bottom
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(6.dp)
                                            .fillMaxHeight(fraction = salesHeightFraction)
                                            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                            .background(if (trend.sales > 0) IncomeGreen else Color.Transparent)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .width(6.dp)
                                            .fillMaxHeight(fraction = expensesHeightFraction)
                                            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                            .background(if (trend.expenses > 0) ExpenseRed else Color.Transparent)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = trend.dayLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StoreTransactionCard(
    item: StoreTransactionItem,
    business: Business? = null,
    onClick: () -> Unit = {},
    onReceiptClick: (() -> Unit)? = null,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val isSale = item.isSale
    val color = if (isSale) IncomeGreen else ExpenseRed
    val prefix = if (isSale) "+" else "-"
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icono compacto (34dp)
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isSale) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Descripción y Metadatos
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (business != null) {
                        val bizColor = remember(business.colorHex) {
                            try {
                                Color(android.graphics.Color.parseColor(business.colorHex))
                            } catch (e: Exception) {
                                Color(0xFF2563EB)
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = bizColor.copy(alpha = 0.15f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(bizColor)
                                )
                                Text(
                                    text = business.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = bizColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    maxLines = 1
                                )
                            }
                        }
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                    Text(
                        text = DateFormatter.format(Date(item.date)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Text(
                        text = item.accountName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Monto y tipo
            Column(
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = "$prefix${CurrencyFormatter.format(item.amount)}",
                    color = color,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    text = if (isSale) "Venta" else "Gasto",
                    style = MaterialTheme.typography.labelSmall,
                    color = color.copy(alpha = 0.85f),
                    fontWeight = FontWeight.Medium
                )
            }

            // Menú de opciones compacto (28dp)
            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Opciones",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Ver detalle") },
                        leadingIcon = {
                            Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        onClick = {
                            menuExpanded = false
                            onClick()
                        }
                    )
                    if (isSale && onReceiptClick != null) {
                        DropdownMenuItem(
                            text = { Text("Comprobante / Recibo") },
                            leadingIcon = {
                                Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null, tint = IncomeGreen, modifier = Modifier.size(18.dp))
                            },
                            onClick = {
                                menuExpanded = false
                                onReceiptClick()
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Editar") },
                        leadingIcon = {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        onClick = {
                            menuExpanded = false
                            onEditClick()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Eliminar", color = ExpenseRed) },
                        leadingIcon = {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = ExpenseRed, modifier = Modifier.size(18.dp))
                        },
                        onClick = {
                            menuExpanded = false
                            onDeleteClick()
                        }
                    )
                }
            }
        }
    }
}

// Dialogo de Venta Express Rápida (1-tap con montos sugeridos y calculadora de cambio)
@Composable
fun QuickSaleDialog(
    accounts: List<Account>,
    businesses: List<Business> = emptyList(),
    initialBusinessId: Long? = null,
    currencySymbol: String,
    onDismiss: () -> Unit,
    onSave: (description: String, amount: Double, accountId: Long, paidWith: Double?, change: Double?, generateReceipt: Boolean, targetBusinessId: Long) -> Unit
) {
    var amount by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedAccountId by remember { mutableStateOf(accounts.firstOrNull()?.id ?: 1L) }
    var selectedBusinessId by remember { mutableStateOf(initialBusinessId ?: businesses.firstOrNull()?.id ?: 1L) }
    var showChangeCalc by remember { mutableStateOf(false) }
    var customerPaid by remember { mutableStateOf("") }
    var generateReceipt by remember { mutableStateOf(false) }

    val quickAmounts = listOf(50.0, 100.0, 200.0, 500.0, 1000.0, 2000.0)
    val amountDouble = amount.toDoubleOrNull() ?: 0.0
    val paidDouble = customerPaid.toDoubleOrNull()
    val changeAmount = if (paidDouble != null && amountDouble > 0 && paidDouble >= amountDouble) {
        paidDouble - amountDouble
    } else null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.FlashOn, contentDescription = null, tint = IncomeGreen)
                Text("Venta Rápida Express", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Selector de negocio si hay más de 1
                if (businesses.size > 1) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "Negocio Destino",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(businesses, key = { it.id }) { biz ->
                                val isSelected = selectedBusinessId == biz.id
                                val bizColor = try {
                                    Color(android.graphics.Color.parseColor(biz.colorHex))
                                } catch (e: Exception) {
                                    MaterialTheme.colorScheme.primary
                                }
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedBusinessId = biz.id },
                                    label = { Text(biz.name, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                    leadingIcon = {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(bizColor)
                                        )
                                    }
                                )
                            }
                        }
                    }
                }

                Text(
                    "Registra una venta al contado al instante.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Montos rápidos sugeridos
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(quickAmounts) { qa ->
                        SuggestionChip(
                            onClick = { amount = String.format(Locale.US, "%.2f", qa) },
                            label = { Text("+$currencySymbol$qa") }
                        )
                    }
                }

                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Monto Cobrado ($currencySymbol)") },
                    placeholder = { Text("0.00") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Concepto o Producto (Opcional)") },
                    placeholder = { Text("Ej: Venta mostrador") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                // Botón / Toggle para calcular cambio en efectivo
                Surface(
                    onClick = { showChangeCalc = !showChangeCalc },
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Calculate, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                            Text("Calculadora de Cambio / Vuelto", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        }
                        Icon(
                            imageVector = if (showChangeCalc) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                AnimatedVisibility(visible = showChangeCalc) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = customerPaid,
                                onValueChange = { customerPaid = it },
                                label = { Text("Cliente paga con ($currencySymbol)") },
                                placeholder = { Text("Ej: 500, 1000") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            )

                            // Sugerencias dinámicas de billetes
                            if (amountDouble > 0) {
                                val billSuggestions = listOf(
                                    amountDouble,
                                    kotlin.math.ceil(amountDouble / 50.0) * 50.0,
                                    kotlin.math.ceil(amountDouble / 100.0) * 100.0,
                                    kotlin.math.ceil(amountDouble / 500.0) * 500.0
                                ).filter { it >= amountDouble }.distinct().take(4)

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    billSuggestions.forEach { bill ->
                                        SuggestionChip(
                                            onClick = { customerPaid = String.format(Locale.US, "%.2f", bill) },
                                            label = { Text(CurrencyFormatter.format(bill), style = MaterialTheme.typography.labelSmall) }
                                        )
                                    }
                                }
                            }

                            if (paidDouble != null && amountDouble > 0) {
                                if (paidDouble >= amountDouble) {
                                    val change = paidDouble - amountDouble
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(IncomeGreen.copy(alpha = 0.15f))
                                            .padding(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("Cambio a devolver:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = IncomeGreen)
                                            Text(CurrencyFormatter.format(change), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = IncomeGreen)
                                        }
                                    }
                                } else {
                                    val missing = amountDouble - paidDouble
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(ExpenseRed.copy(alpha = 0.15f))
                                            .padding(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("Faltan por cobrar:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = ExpenseRed)
                                            Text(CurrencyFormatter.format(missing), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = ExpenseRed)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Opción para generar comprobante
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { generateReceipt = !generateReceipt }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Checkbox(
                        checked = generateReceipt,
                        onCheckedChange = { generateReceipt = it }
                    )
                    Column {
                        Text("Generar y compartir comprobante", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        Text("Muestra el ticket listo para enviar al cliente", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (amountDouble > 0) {
                        onSave(
                            description.ifBlank { "Venta Rápida" },
                            amountDouble,
                            selectedAccountId,
                            paidDouble,
                            changeAmount,
                            generateReceipt,
                            selectedBusinessId
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = IncomeGreen),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(if (generateReceipt) "Registrar y Ver Ticket" else "Registrar Venta", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreTransactionDialog(
    accounts: List<Account>,
    businesses: List<Business> = emptyList(),
    initialBusinessId: Long? = null,
    currencySymbol: String,
    onDismiss: () -> Unit,
    onSave: (description: String, amount: Double, isSale: Boolean, accountId: Long, date: Date, targetBusinessId: Long) -> Unit
) {
    var description by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var isSale by remember { mutableStateOf(true) }
    var selectedAccountId by remember { mutableStateOf(accounts.firstOrNull()?.id ?: 1L) }
    var selectedBusinessId by remember { mutableStateOf(initialBusinessId ?: businesses.firstOrNull()?.id ?: 1L) }
    var accountMenuExpanded by remember { mutableStateOf(false) }
    var selectedDate by remember { mutableStateOf(Date()) }
    var isDatePickerOpen by remember { mutableStateOf(false) }

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = selectedDate.time
    )

    if (isDatePickerOpen) {
        DatePickerDialog(
            onDismissRequest = { isDatePickerOpen = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val calendar = Calendar.getInstance()
                            calendar.timeInMillis = millis
                            // Compensate UTC offset from DatePicker to local date
                            val offset = Calendar.getInstance().timeZone.getOffset(millis)
                            selectedDate = Date(millis - offset)
                        }
                        isDatePickerOpen = false
                    }
                ) {
                    Text("Aceptar")
                }
            },
            dismissButton = {
                TextButton(onClick = { isDatePickerOpen = false }) {
                    Text("Cancelar")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            val bizName = businesses.find { it.id == selectedBusinessId }?.name ?: "Negocio"
            Text(if (isSale) "Registrar Venta - $bizName" else "Registrar Gasto - $bizName", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Selector de Negocio si hay más de 1
                if (businesses.size > 1) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "Negocio Asignado",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(businesses, key = { it.id }) { biz ->
                                val isSelected = selectedBusinessId == biz.id
                                val bizColor = try {
                                    Color(android.graphics.Color.parseColor(biz.colorHex))
                                } catch (e: Exception) {
                                    MaterialTheme.colorScheme.primary
                                }
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedBusinessId = biz.id },
                                    label = { Text(biz.name, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                    leadingIcon = {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(bizColor)
                                        )
                                    }
                                )
                            }
                        }
                    }
                }

                // Selector de Tipo: Venta (+) o Gasto/Inventario (-)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = isSale,
                        onClick = { isSale = true },
                        label = { Text("Venta (+)", fontWeight = FontWeight.SemiBold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = IncomeGreen.copy(alpha = 0.2f),
                            selectedLabelColor = IncomeGreen
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = !isSale,
                        onClick = { isSale = false },
                        label = { Text("Gasto/Inv. (-)", fontWeight = FontWeight.SemiBold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ExpenseRed.copy(alpha = 0.2f),
                            selectedLabelColor = ExpenseRed
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }

                // Selector de Fecha
                OutlinedTextField(
                    value = DateFormatter.format(selectedDate),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Fecha de Operación") },
                    leadingIcon = {
                        Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(20.dp))
                    },
                    trailingIcon = {
                        IconButton(onClick = { isDatePickerOpen = true }) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = "Seleccionar fecha")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isDatePickerOpen = true },
                    shape = RoundedCornerShape(12.dp)
                )

                // Campo Descripción
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Concepto o Descripción") },
                    placeholder = { Text(if (isSale) "Ej: Venta de productos" else "Ej: Reposición de mercancía") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("store_description_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                // Campo Monto
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Monto ($currencySymbol)") },
                    placeholder = { Text("0.00") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("store_amount_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                // Selector de Cuenta Afectada
                Box(modifier = Modifier.fillMaxWidth()) {
                    val currentAccount = accounts.find { it.id == selectedAccountId } ?: accounts.firstOrNull()
                    OutlinedTextField(
                        value = currentAccount?.name ?: "Seleccionar Cuenta",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Cuenta de Pago/Depósito") },
                        leadingIcon = {
                            Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, modifier = Modifier.size(20.dp))
                        },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = accountMenuExpanded)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { accountMenuExpanded = !accountMenuExpanded }
                    )

                    DropdownMenu(
                        expanded = accountMenuExpanded,
                        onDismissRequest = { accountMenuExpanded = false }
                    ) {
                        accounts.forEach { account ->
                            DropdownMenuItem(
                                text = { Text("${account.name} (Saldo: ${CurrencyFormatter.format(account.currentBalance)})") },
                                onClick = {
                                    selectedAccountId = account.id
                                    accountMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amountDouble = amount.toDoubleOrNull() ?: 0.0
                    if (amountDouble > 0) {
                        onSave(description, amountDouble, isSale, selectedAccountId, selectedDate, selectedBusinessId)
                    }
                },
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Guardar Registro")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditStoreTransactionDialog(
    item: StoreTransactionItem,
    accounts: List<Account>,
    businesses: List<Business> = emptyList(),
    currencySymbol: String,
    onDismiss: () -> Unit,
    onSave: (description: String, amount: Double, isSale: Boolean, accountId: Long, date: Date, targetBusinessId: Long) -> Unit
) {
    var description by remember { mutableStateOf(item.description) }
    var amount by remember { mutableStateOf(item.amount.toString()) }
    var isSale by remember { mutableStateOf(item.isSale) }
    var selectedAccountId by remember { mutableStateOf(item.accountId) }
    var selectedBusinessId by remember { mutableStateOf(item.businessId) }
    var accountMenuExpanded by remember { mutableStateOf(false) }
    var selectedDate by remember { mutableStateOf(Date(item.date)) }
    var isDatePickerOpen by remember { mutableStateOf(false) }

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = selectedDate.time
    )

    if (isDatePickerOpen) {
        DatePickerDialog(
            onDismissRequest = { isDatePickerOpen = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val calendar = Calendar.getInstance()
                            calendar.timeInMillis = millis
                            val offset = Calendar.getInstance().timeZone.getOffset(millis)
                            selectedDate = Date(millis - offset)
                        }
                        isDatePickerOpen = false
                    }
                ) {
                    Text("Aceptar")
                }
            },
            dismissButton = {
                TextButton(onClick = { isDatePickerOpen = false }) {
                    Text("Cancelar")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar Registro de Negocio", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Selector de Negocio
                if (businesses.size > 1) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "Negocio Asignado",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(businesses, key = { it.id }) { biz ->
                                val isSelected = selectedBusinessId == biz.id
                                val bizColor = try {
                                    Color(android.graphics.Color.parseColor(biz.colorHex))
                                } catch (e: Exception) {
                                    MaterialTheme.colorScheme.primary
                                }
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedBusinessId = biz.id },
                                    label = { Text(biz.name, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                    leadingIcon = {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(bizColor)
                                        )
                                    }
                                )
                            }
                        }
                    }
                }

                // Selector de Tipo
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = isSale,
                        onClick = { isSale = true },
                        label = { Text("Venta (+)", fontWeight = FontWeight.SemiBold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = IncomeGreen.copy(alpha = 0.2f),
                            selectedLabelColor = IncomeGreen
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = !isSale,
                        onClick = { isSale = false },
                        label = { Text("Gasto/Inv. (-)", fontWeight = FontWeight.SemiBold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ExpenseRed.copy(alpha = 0.2f),
                            selectedLabelColor = ExpenseRed
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }

                // Selector de Fecha
                OutlinedTextField(
                    value = DateFormatter.format(selectedDate),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Fecha de Operación") },
                    leadingIcon = {
                        Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(20.dp))
                    },
                    trailingIcon = {
                        IconButton(onClick = { isDatePickerOpen = true }) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = "Seleccionar fecha")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isDatePickerOpen = true },
                    shape = RoundedCornerShape(12.dp)
                )

                // Campo Descripción
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Concepto o Descripción") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                // Campo Monto
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Monto ($currencySymbol)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                // Selector de Cuenta
                Box(modifier = Modifier.fillMaxWidth()) {
                    val currentAccount = accounts.find { it.id == selectedAccountId } ?: accounts.firstOrNull()
                    OutlinedTextField(
                        value = currentAccount?.name ?: "Seleccionar Cuenta",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Cuenta Asignada") },
                        leadingIcon = {
                            Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, modifier = Modifier.size(20.dp))
                        },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = accountMenuExpanded)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { accountMenuExpanded = !accountMenuExpanded }
                    )

                    DropdownMenu(
                        expanded = accountMenuExpanded,
                        onDismissRequest = { accountMenuExpanded = false }
                    ) {
                        accounts.forEach { account ->
                            DropdownMenuItem(
                                text = { Text("${account.name} (Saldo: ${CurrencyFormatter.format(account.currentBalance)})") },
                                onClick = {
                                    selectedAccountId = account.id
                                    accountMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amountDouble = amount.toDoubleOrNull() ?: 0.0
                    if (amountDouble > 0) {
                        onSave(description, amountDouble, isSale, selectedAccountId, selectedDate, selectedBusinessId)
                    }
                },
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Actualizar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
fun EditInventoryDialog(
    currentValue: Double,
    currencySymbol: String,
    onDismiss: () -> Unit,
    onSave: (newValue: Double) -> Unit
) {
    var valueText by remember { mutableStateOf(currentValue.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Valor de Inventario", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Ingresa la valoración estimada actual de tu mercancía o inventario almacenado.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = valueText,
                    onValueChange = { valueText = it },
                    label = { Text("Valor Total ($currencySymbol)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val doubleVal = valueText.toDoubleOrNull() ?: 0.0
                    onSave(doubleVal)
                },
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Actualizar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

// Calculadora de Margen de Ganancia y Fijación de Precios de Venta
@Composable
fun StoreCalculatorsDialog(
    currencySymbol: String,
    onDismiss: () -> Unit
) {
    var costPrice by remember { mutableStateOf("") }
    var targetMargin by remember { mutableStateOf("30") } // % deseado

    val cost = costPrice.toDoubleOrNull() ?: 0.0
    val margin = targetMargin.toDoubleOrNull() ?: 0.0

    // Precio de Venta sugerido = Costo / (1 - Margen%)
    val suggestedSalePrice = if (cost > 0 && margin in 0.0..99.0) {
        cost / (1 - (margin / 100.0))
    } else {
        0.0
    }
    val profitPerUnit = (suggestedSalePrice - cost).coerceAtLeast(0.0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Calculate, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Calculadora de Precios", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Calcula el precio óptimo de venta para obtener el margen de ganancia que deseas.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = costPrice,
                    onValueChange = { costPrice = it },
                    label = { Text("Costo del Producto ($currencySymbol)") },
                    placeholder = { Text("Ej: 500") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = targetMargin,
                    onValueChange = { targetMargin = it },
                    label = { Text("Margen Deseado (%)") },
                    placeholder = { Text("Ej: 30") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                if (cost > 0) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Precio de Venta Recomendado:", style = MaterialTheme.typography.labelSmall)
                            Text(
                                text = CurrencyFormatter.format(suggestedSalePrice),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Ganancia neta estimada: ${CurrencyFormatter.format(profitPerUnit)} por unidad",
                                style = MaterialTheme.typography.bodySmall,
                                color = IncomeGreen,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, shape = RoundedCornerShape(10.dp)) {
                Text("Entendido")
            }
        }
    )
}

@Composable
fun StoreTransactionDetailDialog(
    item: StoreTransactionItem,
    business: Business? = null,
    onDismiss: () -> Unit,
    onGenerateReceipt: (() -> Unit)? = null,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val isSale = item.isSale
    val color = if (isSale) IncomeGreen else ExpenseRed
    val prefix = if (isSale) "+" else "-"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isSale) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = if (isSale) "Detalle de Venta" else "Detalle de Gasto / Inventario",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Monto Destacado
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f)),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (isSale) "Ingreso por Venta" else "Egreso Comercial",
                            style = MaterialTheme.typography.labelMedium,
                            color = color
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "$prefix${CurrencyFormatter.format(item.amount)}",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = color
                        )
                    }
                }

                // Información detallada
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Negocio Asignado
                    if (business != null) {
                        val bizColor = remember(business.colorHex) {
                            try {
                                Color(android.graphics.Color.parseColor(business.colorHex))
                            } catch (e: Exception) {
                                Color(0xFF2563EB)
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Negocio:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(bizColor))
                                Text(
                                    text = business.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = bizColor
                                )
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    }

                    // Concepto
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Text("Concepto:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = item.description,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    // Fecha
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Fecha:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = DateFormatter.format(Date(item.date)),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    // Cuenta
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Cuenta:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = item.accountName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    // Tipo
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Tipo de registro:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = if (isSale) "Venta de Negocio" else "Compra / Gasto Comercial",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = color
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (isSale && onGenerateReceipt != null) {
                    FilledTonalButton(
                        onClick = onGenerateReceipt,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Recibo")
                    }
                }
                OutlinedButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ExpenseRed),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Eliminar")
                }
                Button(
                    onClick = onEdit,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Editar")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        }
    )
}

// Dialogo para generar y compartir Recibo / Comprobante de Venta Digital
@Composable
fun StoreReceiptDialog(
    item: StoreTransactionItem,
    businessName: String = "Comercio",
    currencySymbol: String,
    initialPaidWith: Double? = null,
    initialChange: Double? = null,
    onDismiss: () -> Unit,
    onShare: (String) -> Unit,
    onCopy: (String) -> Unit
) {
    var customerName by remember { mutableStateOf("") }
    var paidWith by remember { mutableStateOf(initialPaidWith?.let { String.format(Locale.US, "%.2f", it) } ?: "") }

    val paidWithDouble = paidWith.toDoubleOrNull()
    val changeVal = if (paidWithDouble != null && paidWithDouble >= item.amount) paidWithDouble - item.amount else initialChange

    val formattedReceiptText = remember(customerName, paidWith, changeVal, businessName) {
        val dateStr = DateFormatter.format(Date(item.date))
        val client = customerName.ifBlank { "Consumidor Final" }
        buildString {
            append("================================\n")
            append("        🏪 ${businessName.uppercase()}\n")
            append("     COMPROBANTE DE VENTA\n")
            append("================================\n")
            append("Folio: #VTA-${item.id}\n")
            append("Fecha: $dateStr\n")
            append("Cliente: $client\n")
            append("--------------------------------\n")
            append("Concepto: ${item.description}\n")
            append("Método de Pago: ${item.accountName}\n")
            append("--------------------------------\n")
            append("TOTAL PAGADO: ${CurrencyFormatter.format(item.amount)}\n")
            if (paidWithDouble != null && paidWithDouble >= item.amount) {
                append("Pagó con: ${CurrencyFormatter.format(paidWithDouble)}\n")
                if (changeVal != null && changeVal > 0) {
                    append("Cambio devuelto: ${CurrencyFormatter.format(changeVal)}\n")
                }
            }
            append("--------------------------------\n")
            append("¡Muchas gracias por su preferencia!\n")
            append("Generado por $businessName\n")
            append("================================\n")
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Comprobante de Venta", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Entrada opcional de nombre del cliente
                OutlinedTextField(
                    value = customerName,
                    onValueChange = { customerName = it },
                    label = { Text("Nombre del Cliente (Opcional)") },
                    placeholder = { Text("Ej: Carlos Méndez / Consumidor Final") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                // Visual Ticket Preview Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(businessName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                            Text("#VTA-${item.id}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            text = DateFormatter.format(Date(item.date)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Cliente: ${customerName.ifBlank { "Consumidor Final" }}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(item.description, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text(CurrencyFormatter.format(item.amount), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        }

                        Text(
                            text = "Cobrado en: ${item.accountName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (paidWithDouble != null && paidWithDouble >= item.amount) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Pagó con:", style = MaterialTheme.typography.labelSmall)
                                Text(CurrencyFormatter.format(paidWithDouble), style = MaterialTheme.typography.labelSmall)
                            }
                            if (changeVal != null && changeVal > 0) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Cambio:", style = MaterialTheme.typography.labelSmall, color = IncomeGreen, fontWeight = FontWeight.Bold)
                                    Text(CurrencyFormatter.format(changeVal), style = MaterialTheme.typography.labelSmall, color = IncomeGreen, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("TOTAL", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Text(
                                CurrencyFormatter.format(item.amount),
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                color = IncomeGreen
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onShare(formattedReceiptText) },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = IncomeGreen)
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Compartir", color = Color.White)
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onDismiss) {
                    Text("Cerrar")
                }
                OutlinedButton(
                    onClick = { onCopy(formattedReceiptText) },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Copiar")
                }
            }
        }
    )
}

// Dialogo de Corte de Caja Diario
@Composable
fun DailyCashRegisterDialog(
    closeout: StoreDailyCloseout,
    businessName: String = "Consolidado de Negocios",
    onDismiss: () -> Unit,
    onShare: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column {
                    Text("Corte de Caja Diario", fontWeight = FontWeight.Bold)
                    Text(
                        text = businessName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Fecha: ${DateFormatter.format(closeout.date)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Resumen Balances Hoy
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (closeout.netBalance >= 0) IncomeGreen.copy(alpha = 0.12f) else ExpenseRed.copy(alpha = 0.12f)
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Efectivo Neto a Cuadrar Hoy", style = MaterialTheme.typography.labelSmall)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = CurrencyFormatter.format(closeout.netBalance),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (closeout.netBalance >= 0) IncomeGreen else ExpenseRed
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Ventas (${closeout.salesCount})", style = MaterialTheme.typography.labelSmall)
                            Text(
                                CurrencyFormatter.format(closeout.salesTotal),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = IncomeGreen
                            )
                        }
                    }

                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Gastos (${closeout.expensesCount})", style = MaterialTheme.typography.labelSmall)
                            Text(
                                CurrencyFormatter.format(closeout.expensesTotal),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = ExpenseRed
                            )
                        }
                    }
                }

                Text(
                    text = "Operaciones de Hoy (${closeout.todayTransactions.size}):",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )

                if (closeout.todayTransactions.isEmpty()) {
                    Text(
                        "No se han registrado movimientos comerciales hoy.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    closeout.todayTransactions.forEach { tx ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(tx.description, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(tx.accountName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(
                                text = (if (tx.isSale) "+" else "-") + CurrencyFormatter.format(tx.amount),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = if (tx.isSale) IncomeGreen else ExpenseRed
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onShare,
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Compartir Corte")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        }
    )
}
