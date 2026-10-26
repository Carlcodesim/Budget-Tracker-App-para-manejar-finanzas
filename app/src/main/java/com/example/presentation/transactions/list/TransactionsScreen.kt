package com.example.presentation.transactions.list

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import com.example.domain.model.Category
import com.example.domain.model.Transaction
import com.example.domain.model.TransactionType
import com.example.presentation.common.components.AmountTextField
import com.example.presentation.common.components.ConfirmDeleteDialog
import com.example.presentation.common.components.EmptyState
import com.example.presentation.common.components.LoadingIndicator
import com.example.presentation.common.util.CsvExporter
import com.example.presentation.common.util.CurrencyFormatter
import com.example.presentation.common.util.DateFormatter
import com.example.presentation.common.util.IconHelper
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.util.Date

/**
 * Pantalla de Historial de Movimientos refinada.
 * Ofrece arquitectura visual moderna, agrupación por fechas con cabeceras fijas,
 * filtrado por categorías/tipo/búsqueda, badges de estado activo y registro rápido.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun TransactionsScreen(
    viewModel: TransactionsViewModel,
    onNavigateToForm: (Long?) -> Unit,
    onNavigateToScanReceipt: () -> Unit = { onNavigateToForm(null) },
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lazyPagingItems = viewModel.pagedTransactions.collectAsLazyPagingItems()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    var transactionToDelete by remember { mutableStateOf<Transaction?>(null) }
    var exportAllTransactions by remember { mutableStateOf(false) }
    var showAddExpenseDialog by remember { mutableStateOf(false) }
    var showAdvancedFilterSheet by remember { mutableStateOf(false) }
    var isSummaryExpanded by remember { mutableStateOf(true) }

    val activeFilterCount = remember(
        state.selectedTypeFilter,
        state.selectedCategoryIdFilter,
        state.searchQuery,
        state.startDate,
        state.endDate,
        state.minAmount,
        state.maxAmount
    ) {
        var count = 0
        if (state.selectedTypeFilter != "ALL") count++
        if (state.selectedCategoryIdFilter != -1L) count++
        if (state.searchQuery.isNotBlank()) count++
        if (state.startDate != null || state.endDate != null) count++
        if (state.minAmount != null || state.maxAmount != null) count++
        count
    }

    val exportCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val listToExport = if (exportAllTransactions) state.transactions else state.filteredTransactions
                    val csvData = CsvExporter.generateTransactionsCsv(listToExport)
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        output.write(csvData.toByteArray(Charsets.UTF_8))
                    }
                    snackbarHostState.showSnackbar("¡Historial exportado con éxito (${listToExport.size} registros)!")
                } catch (e: Exception) {
                    snackbarHostState.showSnackbar("Error al exportar CSV: ${e.localizedMessage}")
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (state.isSelectionMode) {
                TopAppBar(
                    title = {
                        Text(
                            text = "${state.selectedTransactionIds.size} seleccionados",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Cerrar selección")
                        }
                    },
                    actions = {
                        TextButton(
                            onClick = {
                                if (state.selectedTransactionIds.size == state.filteredTransactions.size) {
                                    viewModel.clearSelection()
                                } else {
                                    viewModel.selectAllFilteredTransactions(state.filteredTransactions)
                                }
                            }
                        ) {
                            Text(
                                text = if (state.selectedTransactionIds.size == state.filteredTransactions.size) "Deseleccionar" else "Todos",
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        IconButton(
                            onClick = {
                                val count = state.selectedTransactionIds.size
                                viewModel.deleteSelectedTransactions { deletedCount ->
                                    scope.launch {
                                        val result = snackbarHostState.showSnackbar(
                                            message = "$deletedCount movimientos eliminados",
                                            actionLabel = "DESHACER",
                                            duration = SnackbarDuration.Short
                                        )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            viewModel.undoBatchDelete()
                                        }
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Eliminar seleccionados",
                                tint = ExpenseRed
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            } else {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Movimientos",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Surface(
                                shape = CircleShape,
                                color = if (activeFilterCount > 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (activeFilterCount > 0) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            ) {
                                Text(
                                    text = if (activeFilterCount > 0) "${state.filteredTransactions.size} / ${state.transactions.size}" else "${state.transactions.size}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    },
                    actions = {
                        // Botón para escanear recibo físico con cámara y Gemini OCR
                        IconButton(
                            onClick = onNavigateToScanReceipt,
                            modifier = Modifier.testTag("scan_receipt_topbar_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = "Escanear Recibo con IA",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Botón rápido para agregar gasto directo
                        IconButton(
                            onClick = { showAddExpenseDialog = true },
                            modifier = Modifier.testTag("quick_add_expense_topbar")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = "Gasto Rápido",
                                tint = ExpenseRed
                            )
                        }

                        var menuExpanded by remember { mutableStateOf(false) }
                        Box {
                            IconButton(
                                onClick = { menuExpanded = true },
                                modifier = Modifier.testTag("export_csv_topbar_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Exportar CSV"
                                )
                            }

                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Exportar filtrados (${state.filteredTransactions.size})") },
                                    leadingIcon = { Icon(Icons.Default.FilterList, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        exportAllTransactions = false
                                        try {
                                            exportCsvLauncher.launch("movimientos_filtrados.csv")
                                        } catch (e: Exception) {
                                            scope.launch {
                                                snackbarHostState.showSnackbar("Error al iniciar exportación: ${e.localizedMessage}")
                                            }
                                        }
                                    }
                                )

                                DropdownMenuItem(
                                    text = { Text("Exportar todo (${state.transactions.size})") },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.ListAlt, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        exportAllTransactions = true
                                        try {
                                            exportCsvLauncher.launch("historial_completo_transacciones.csv")
                                        } catch (e: Exception) {
                                            scope.launch {
                                                snackbarHostState.showSnackbar("Error al iniciar exportación: ${e.localizedMessage}")
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        },
        floatingActionButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Botón rápido para escanear recibo físico con la cámara y Gemini AI
                FloatingActionButton(
                    onClick = onNavigateToScanReceipt,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    shape = CircleShape,
                    modifier = Modifier.testTag("scan_receipt_fab")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Escanear",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }

                ExtendedFloatingActionButton(
                    text = { Text("Nuevo", fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    onClick = { onNavigateToForm(null) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = CircleShape,
                    modifier = Modifier.testTag("add_transaction_fab")
                )
            }
        },
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 840.dp)
            ) {
            // Barra unificada de búsqueda y filtros
            UnifiedFilterBar(
                searchQuery = state.searchQuery,
                onSearchQueryChanged = { viewModel.onSearchQueryChanged(it) },
                selectedType = state.selectedTypeFilter,
                onTypeChanged = { viewModel.onTypeFilterChanged(it) },
                selectedSortOption = state.selectedSortOption,
                onSortOptionChanged = { viewModel.onSortOptionChanged(it) },
                selectedTimePreset = state.selectedTimePreset,
                onTimePresetSelected = { viewModel.onTimePresetSelected(it) },
                activeFilterCount = activeFilterCount,
                onToggleAdvancedFilters = { showAdvancedFilterSheet = !showAdvancedFilterSheet },
                categories = state.categories,
                selectedCategoryId = state.selectedCategoryIdFilter,
                onCategoryChanged = { viewModel.onCategoryFilterChanged(it) },
                onClearFilters = { viewModel.clearAllFilters() }
            )

            if (state.isLoading) {
                LoadingIndicator()
            } else {

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Tarjeta Resumen Consolidada (Gasto del mes & balance, compacta y elegante)
                    item(key = "header_summary") {
                        ExecutiveSummaryCard(
                            transactions = if (activeFilterCount > 0) state.filteredTransactions else state.transactions,
                            isFiltered = activeFilterCount > 0,
                            filteredCount = state.filteredTransactions.size,
                            isExpanded = isSummaryExpanded,
                            onToggleExpand = { isSummaryExpanded = !isSummaryExpanded }
                        )
                    }

                    // Estado Vacío o Lista Agrupada por Fechas
                    if (state.filteredTransactions.isEmpty()) {
                        item(key = "empty_state") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 36.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    EmptyState(
                                        icon = Icons.Default.History,
                                        title = "Sin movimientos",
                                        subtitle = "No hay registros que coincidan con los filtros o la búsqueda."
                                    )
                                    if (activeFilterCount > 0) {
                                        Button(
                                            onClick = { viewModel.clearAllFilters() },
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Icon(Icons.Default.FilterAltOff, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Restablecer todos los filtros")
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        state.groupedTransactions.forEach { group ->
                            stickyHeader(key = "header_${group.dateHeader}") {
                                DateHeaderItem(
                                    dateText = group.dateHeader,
                                    itemCount = group.transactions.size,
                                    dailyNetBalance = group.netBalance
                                )
                            }

                            items(
                                items = group.transactions,
                                key = { it.id },
                                contentType = { "transaction" }
                            ) { transaction ->
                                val currentTransaction by rememberUpdatedState(transaction)
                                val dismissState = rememberSwipeToDismissBoxState(
                                    confirmValueChange = { value ->
                                        if (value == SwipeToDismissBoxValue.EndToStart) {
                                            val txToDelete = currentTransaction
                                            val itemName = txToDelete.description?.takeIf { it.isNotBlank() } ?: txToDelete.category.name
                                            viewModel.deleteTransaction(txToDelete)
                                            scope.launch {
                                                val result = snackbarHostState.showSnackbar(
                                                    message = "Movimiento '$itemName' eliminado",
                                                    actionLabel = "DESHACER",
                                                    duration = SnackbarDuration.Short
                                                )
                                                if (result == SnackbarResult.ActionPerformed) {
                                                    viewModel.undoDelete()
                                                }
                                            }
                                            true
                                        } else {
                                            false
                                        }
                                    }
                                )

                                SwipeToDismissBox(
                                    state = dismissState,
                                    enableDismissFromStartToEnd = false,
                                    enableDismissFromEndToStart = true,
                                    backgroundContent = {
                                        val color = if (dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart) {
                                            MaterialTheme.colorScheme.errorContainer
                                        } else {
                                            Color.Transparent
                                        }
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(color)
                                                .padding(horizontal = 20.dp),
                                            contentAlignment = Alignment.CenterEnd
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Eliminar",
                                                tint = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                        }
                                    },
                                    content = {
                                        TransactionListItem(
                                            transaction = transaction,
                                            isSelected = state.selectedTransactionIds.contains(transaction.id),
                                            isSelectionMode = state.isSelectionMode,
                                            onToggleSelect = { viewModel.toggleSelectTransaction(transaction.id) },
                                            onLongClick = { viewModel.toggleSelectTransaction(transaction.id) },
                                            onEditClick = {
                                                if (state.isSelectionMode) {
                                                    viewModel.toggleSelectTransaction(transaction.id)
                                                } else {
                                                    onNavigateToForm(transaction.id)
                                                }
                                            },
                                            onDeleteClick = { transactionToDelete = transaction },
                                            onDuplicateClick = {
                                                viewModel.duplicateTransaction(transaction) { createdTx ->
                                                    scope.launch {
                                                        val result = snackbarHostState.showSnackbar(
                                                            message = "Movimiento duplicado para hoy (${CurrencyFormatter.format(transaction.amount)})",
                                                            actionLabel = "Deshacer",
                                                            duration = SnackbarDuration.Short
                                                        )
                                                        if (result == SnackbarResult.ActionPerformed) {
                                                            viewModel.deleteTransaction(createdTx)
                                                        }
                                                    }
                                                }
                                            }
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal de Filtros Avanzados (Fechas y Rangos)
    if (showAdvancedFilterSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAdvancedFilterSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            AdvancedFilterBottomSheetContent(
                startDate = state.startDate,
                endDate = state.endDate,
                minAmount = state.minAmount,
                maxAmount = state.maxAmount,
                onDateRangeChanged = { start, end -> viewModel.onDateRangeChanged(start, end) },
                onAmountRangeChanged = { min, max -> viewModel.onAmountRangeChanged(min, max) },
                onClearFilters = {
                    viewModel.clearAllFilters()
                    showAdvancedFilterSheet = false
                },
                onDismiss = { showAdvancedFilterSheet = false }
            )
        }
    }
}

    // Modal de diálogo para registrar gasto rápido
    if (showAddExpenseDialog) {
        AddExpenseDialog(
            categories = state.categories,
            onDismiss = { showAddExpenseDialog = false },
            onConfirm = { amount, category, description, date ->
                viewModel.addExpense(amount, category, description, date)
                showAddExpenseDialog = false
                scope.launch {
                    snackbarHostState.showSnackbar("Gasto de ${CurrencyFormatter.format(amount)} registrado con éxito")
                }
            }
        )
    }

    // Confirmación de eliminación
    transactionToDelete?.let { transaction ->
        val itemName = transaction.description?.takeIf { it.isNotBlank() } ?: transaction.category.name
        ConfirmDeleteDialog(
            title = "Eliminar movimiento",
            text = "¿Deseas eliminar el movimiento '$itemName'? Podrás deshacer la acción inmediatamente desde la notificación inferior.",
            onConfirm = {
                viewModel.deleteTransaction(transaction)
                transactionToDelete = null
                scope.launch {
                    val result = snackbarHostState.showSnackbar(
                        message = "Movimiento '$itemName' eliminado",
                        actionLabel = "DESHACER",
                        duration = SnackbarDuration.Short
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.undoDelete()
                    }
                }
            },
            onDismiss = { transactionToDelete = null }
        )
    }
}

/**
 * Cabecera pegajosa (Sticky Header) para agrupar transacciones por fecha con diseño minimalista
 */
@Composable
private fun DateHeaderItem(
    dateText: String,
    itemCount: Int,
    dailyNetBalance: Double
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp, horizontal = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                ) {
                    Text(
                        text = dateText,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                Text(
                    text = "$itemCount ${if (itemCount == 1) "movimiento" else "movimientos"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (dailyNetBalance != 0.0) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (dailyNetBalance > 0) IncomeGreen.copy(alpha = 0.12f) else ExpenseRed.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = "${if (dailyNetBalance > 0) "+" else ""}${CurrencyFormatter.format(dailyNetBalance)}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (dailyNetBalance > 0) IncomeGreen else ExpenseRed,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

/**
 * Barra unificada de búsqueda rápida, selector segmentado de tipos y carrusel de categorías
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnifiedFilterBar(
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    selectedType: String,
    onTypeChanged: (String) -> Unit,
    selectedSortOption: TransactionSortOption,
    onSortOptionChanged: (TransactionSortOption) -> Unit,
    selectedTimePreset: TimeRangePreset,
    onTimePresetSelected: (TimeRangePreset) -> Unit,
    activeFilterCount: Int,
    onToggleAdvancedFilters: () -> Unit,
    categories: List<Category>,
    selectedCategoryId: Long,
    onCategoryChanged: (Long) -> Unit,
    onClearFilters: () -> Unit
) {
    var sortMenuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Fila 1: Campo de búsqueda con esquinas redondeadas + Botón Ordenar + Botón Filtros Avanzados
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChanged,
                placeholder = { Text("Buscar concepto o #tag...", style = MaterialTheme.typography.bodyMedium) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { onSearchQueryChanged("") },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Limpiar búsqueda",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("advanced_search_input"),
                shape = RoundedCornerShape(20.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                )
            )

            // Botón de Ordenamiento
            Box {
                FilledTonalIconButton(
                    onClick = { sortMenuExpanded = true },
                    modifier = Modifier.testTag("sort_filter_chip"),
                    shape = RoundedCornerShape(14.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.SwapVert,
                        contentDescription = "Ordenar",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                DropdownMenu(
                    expanded = sortMenuExpanded,
                    onDismissRequest = { sortMenuExpanded = false }
                ) {
                    TransactionSortOption.values().forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = option.displayName,
                                    fontWeight = if (option == selectedSortOption) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            onClick = {
                                onSortOptionChanged(option)
                                sortMenuExpanded = false
                            },
                            leadingIcon = {
                                if (option == selectedSortOption) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        )
                    }
                }
            }

            // Botón de Filtros Avanzados (Rango de fechas y montos)
            Box {
                FilledTonalIconButton(
                    onClick = onToggleAdvancedFilters,
                    shape = RoundedCornerShape(14.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = if (activeFilterCount > 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Filtros Avanzados",
                        tint = if (activeFilterCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (activeFilterCount > 0) {
                    Badge(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = (-2).dp, y = (-2).dp)
                    ) {
                        Text("$activeFilterCount")
                    }
                }
            }
        }

        // Fila 2: Selector Segmentado Organizado para Tipo de Transacción
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val types = listOf(
                    "ALL" to "Todos",
                    "EXPENSE" to "Gastos",
                    "INCOME" to "Ingresos",
                    "TRANSFER" to "Transf."
                )

                types.forEach { (value, label) ->
                    val isSelected = selectedType == value
                    Surface(
                        onClick = { onTypeChanged(value) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(11.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
                        shadowElevation = if (isSelected) 1.dp else 0.dp,
                        border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)) else null
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Fila 2.5: Presets temporales rápidos (Todo, Este Mes, Mes Anterior, 7 Días, Este Año)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TimeRangePreset.values().forEach { preset ->
                val isSelected = selectedTimePreset == preset
                FilterChip(
                    selected = isSelected,
                    onClick = { onTimePresetSelected(preset) },
                    label = { Text(preset.label, style = MaterialTheme.typography.labelSmall) },
                    leadingIcon = if (isSelected) {
                        {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    } else null,
                    shape = RoundedCornerShape(12.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        }

        // Fila 3: Carrusel horizontal dedicado EXCLUSIVAMENTE a categorías
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (activeFilterCount > 0) {
                AssistChip(
                    onClick = onClearFilters,
                    label = { Text("Limpiar ($activeFilterCount)") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                        labelColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                )
            }

            FilterChip(
                selected = selectedCategoryId == -1L,
                onClick = { onCategoryChanged(-1L) },
                label = { Text("Todas") },
                shape = RoundedCornerShape(12.dp)
            )

            categories.forEach { category ->
                val isCatSelected = selectedCategoryId == category.id
                FilterChip(
                    selected = isCatSelected,
                    onClick = { onCategoryChanged(category.id) },
                    label = { Text(category.name) },
                    leadingIcon = {
                        Icon(
                            imageVector = IconHelper.getIcon(category.iconKey),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }
    }
}

/**
 * Tarjeta Resumen Ejecutiva Compacta y Elegante
 */
@Composable
private fun ExecutiveSummaryCard(
    transactions: List<Transaction>,
    isFiltered: Boolean = false,
    filteredCount: Int = 0,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (totalExpenses, expenseCount, averageExpense) = remember(transactions) {
        val expenseTxs = transactions.filter { it.type == TransactionType.EXPENSE }
        val sum = expenseTxs.sumOf { it.amount }
        val count = expenseTxs.size
        val avg = if (count > 0) sum / count else 0.0
        Triple(sum, count, avg)
    }

    val summaryData = remember(transactions, isFiltered) {
        if (isFiltered) {
            val inc = transactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
            val exp = transactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
            Triple(inc, exp, inc - exp)
        } else {
            val cal = java.util.Calendar.getInstance()
            val currentMonth = cal.get(java.util.Calendar.MONTH)
            val currentYear = cal.get(java.util.Calendar.YEAR)

            val txCal = java.util.Calendar.getInstance()
            val monthTxs = transactions.filter { tx ->
                txCal.time = tx.date
                txCal.get(java.util.Calendar.MONTH) == currentMonth && txCal.get(java.util.Calendar.YEAR) == currentYear
            }

            val income = monthTxs.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
            val expense = monthTxs.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
            Triple(income, expense, income - expense)
        }
    }

    val (income, expense, balance) = summaryData

    val titleText = remember(isFiltered, filteredCount) {
        if (isFiltered) {
            "Filtro activo ($filteredCount)"
        } else {
            val locale = java.util.Locale.forLanguageTag("es-ES")
            java.text.SimpleDateFormat("MMMM yyyy", locale).format(java.util.Date())
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("total_expenses_summary_card"),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Cabecera compacta: Título de vista + Balance Neto
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (isFiltered) Icons.Default.FilterList else Icons.Default.CalendarToday,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = titleText,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (balance >= 0) IncomeGreen.copy(alpha = 0.12f) else ExpenseRed.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = "Neto: ${if (balance >= 0) "+" else ""}${CurrencyFormatter.format(balance)}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (balance >= 0) IncomeGreen else ExpenseRed,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Expandir/Colapsar detalles",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Cápsulas visuales simétricas para Ingresos y Gastos
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Ingresos
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    color = IncomeGreen.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, IncomeGreen.copy(alpha = 0.18f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Ingresos",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "+${CurrencyFormatter.format(income)}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = IncomeGreen
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                            contentDescription = null,
                            tint = IncomeGreen,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Gastos
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    color = ExpenseRed.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, ExpenseRed.copy(alpha = 0.18f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Gastos",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "-${CurrencyFormatter.format(expense)}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = ExpenseRed
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.TrendingDown,
                            contentDescription = null,
                            tint = ExpenseRed,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Estadísticas adicionales animadas al expandir
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Gasto total histórico:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = CurrencyFormatter.format(totalExpenses),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = ExpenseRed,
                            modifier = Modifier.testTag("total_expenses_amount_text")
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Promedio por gasto:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "$expenseCount gasto(s) • Prom. ${CurrencyFormatter.format(averageExpense)}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

/**
 * Item individual de la lista de movimientos con diseño moderno, tags y espaciado limpio
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TransactionListItem(
    transaction: Transaction,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit = {},
    onDuplicateClick: () -> Unit = {},
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    onToggleSelect: () -> Unit = {},
    onLongClick: () -> Unit = {}
) {
    val isIncome = remember(transaction.type) { transaction.type == TransactionType.INCOME }
    val isTransfer = remember(transaction.type) { transaction.type == TransactionType.TRANSFER }
    val isIncomingTransfer = remember(transaction.tags, transaction.description) {
        transaction.tags?.contains("IN", ignoreCase = true) == true ||
        transaction.description?.contains("desde", ignoreCase = true) == true
    }

    val valueColor = remember(isIncome, isTransfer, isIncomingTransfer) {
        when {
            isTransfer -> Color(0xFF2563EB)
            isIncome -> IncomeGreen
            else -> ExpenseRed
        }
    }

    val containerBgColor = remember(isIncome, isTransfer) {
        when {
            isTransfer -> Color(0xFF2563EB).copy(alpha = 0.12f)
            isIncome -> IncomeGreen.copy(alpha = 0.12f)
            else -> ExpenseRed.copy(alpha = 0.12f)
        }
    }

    val prefix = remember(isIncome, isTransfer, isIncomingTransfer) {
        when {
            isTransfer -> if (isIncomingTransfer) "+ " else "- "
            isIncome -> "+ "
            else -> "- "
        }
    }
    val formattedAmount = remember(transaction.amount) { CurrencyFormatter.format(transaction.amount) }
    val formattedDate = remember(transaction.date) { DateFormatter.format(transaction.date) }
    val displayTitle = remember(transaction.description, transaction.category.name) {
        transaction.description?.takeIf { it.isNotBlank() } ?: transaction.category.name
    }

    val parsedTags = remember(transaction.tags) {
        transaction.tags?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() && it != "TRANSFER" && it != "IN" && it != "OUT" } ?: emptyList()
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    if (isSelectionMode) onToggleSelect() else onEditClick()
                },
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            1.dp,
            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 2.dp else 0.5.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelect() },
                    modifier = Modifier.padding(end = 6.dp)
                )
            }

            // Icono de la Categoría con squircle redondeado
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(containerBgColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isTransfer) Icons.Default.SwapHoriz else IconHelper.getIcon(transaction.category.iconKey),
                    contentDescription = null,
                    tint = valueColor,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Textos descriptivos (Título, Categoría, Tags)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayTitle,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                val subtitleText = if (isTransfer) {
                    "Transferencia • $formattedDate"
                } else {
                    "${transaction.category.name} • $formattedDate"
                }
                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Tags si existen
                if (isTransfer || parsedTags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isTransfer) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF2563EB).copy(alpha = 0.10f)
                            ) {
                                Text(
                                    text = if (isIncomingTransfer) "Entrante" else "Saliente",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF2563EB),
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        val visibleTags = if (isTransfer) parsedTags.take(1) else parsedTags.take(2)
                        visibleTags.forEach { tag ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                            ) {
                                Text(
                                    text = "#$tag",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        val extraTagsCount = parsedTags.size - visibleTags.size
                        if (extraTagsCount > 0) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            ) {
                                Text(
                                    text = "+$extraTagsCount",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Monto formateado con jerarquía clara y acción rápida de duplicar
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "$prefix$formattedAmount",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = valueColor
                )
                Spacer(modifier = Modifier.height(2.dp))
                IconButton(
                    onClick = onDuplicateClick,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Duplicar movimiento para hoy",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
    }
}

/**
 * Contenido del BottomSheet para selección avanzada de rango de fechas y montos
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdvancedFilterBottomSheetContent(
    startDate: Long?,
    endDate: Long?,
    minAmount: Double?,
    maxAmount: Double?,
    onDateRangeChanged: (Long?, Long?) -> Unit,
    onAmountRangeChanged: (Double?, Double?) -> Unit,
    onClearFilters: () -> Unit,
    onDismiss: () -> Unit
) {
    var isStartDatePickerOpen by remember { mutableStateOf(false) }
    var isEndDatePickerOpen by remember { mutableStateOf(false) }
    var minAmountText by remember { mutableStateOf(minAmount?.toString() ?: "") }
    var maxAmountText by remember { mutableStateOf(maxAmount?.toString() ?: "") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Filtros Avanzados",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Atajos de Fechas",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Atajos Rápidos
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val cal = remember { java.util.Calendar.getInstance() }

            FilterChip(
                selected = false,
                onClick = {
                    cal.time = Date()
                    cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                    cal.set(java.util.Calendar.MINUTE, 0)
                    cal.set(java.util.Calendar.SECOND, 0)
                    val start = cal.timeInMillis
                    cal.set(java.util.Calendar.HOUR_OF_DAY, 23)
                    cal.set(java.util.Calendar.MINUTE, 59)
                    val end = cal.timeInMillis
                    onDateRangeChanged(start, end)
                    onDismiss()
                },
                label = { Text("Hoy") },
                modifier = Modifier.weight(1f)
            )

            FilterChip(
                selected = false,
                onClick = {
                    cal.time = Date()
                    cal.set(java.util.Calendar.DAY_OF_MONTH, 1)
                    cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                    cal.set(java.util.Calendar.MINUTE, 0)
                    val start = cal.timeInMillis
                    cal.time = Date()
                    cal.set(java.util.Calendar.HOUR_OF_DAY, 23)
                    cal.set(java.util.Calendar.MINUTE, 59)
                    val end = cal.timeInMillis
                    onDateRangeChanged(start, end)
                    onDismiss()
                },
                label = { Text("Este Mes") },
                modifier = Modifier.weight(1f)
            )

            FilterChip(
                selected = false,
                onClick = {
                    cal.time = Date()
                    cal.set(java.util.Calendar.DAY_OF_YEAR, 1)
                    cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                    cal.set(java.util.Calendar.MINUTE, 0)
                    val start = cal.timeInMillis
                    cal.time = Date()
                    cal.set(java.util.Calendar.HOUR_OF_DAY, 23)
                    cal.set(java.util.Calendar.MINUTE, 59)
                    val end = cal.timeInMillis
                    onDateRangeChanged(start, end)
                    onDismiss()
                },
                label = { Text("Este Año") },
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { isStartDatePickerOpen = true },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (startDate != null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (startDate != null) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = if (startDate != null) "Desde: ${DateFormatter.format(Date(startDate))}" else "Fecha Desde",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Button(
                onClick = { isEndDatePickerOpen = true },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (endDate != null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (endDate != null) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = if (endDate != null) "Hasta: ${DateFormatter.format(Date(endDate))}" else "Fecha Hasta",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Text(
            text = "Rango de Montos",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = minAmountText,
                onValueChange = { minAmountText = it },
                label = { Text("Monto Min.") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = maxAmountText,
                onValueChange = { maxAmountText = it },
                label = { Text("Monto Máx.") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onClearFilters) {
                Text("Limpiar Filtros")
            }

            Button(
                onClick = {
                    onAmountRangeChanged(minAmountText.toDoubleOrNull(), maxAmountText.toDoubleOrNull())
                    onDismiss()
                },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Aplicar Filtros")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    if (isStartDatePickerOpen) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = startDate ?: System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { isStartDatePickerOpen = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDateRangeChanged(datePickerState.selectedDateMillis, endDate)
                        isStartDatePickerOpen = false
                    }
                ) {
                    Text("Aceptar")
                }
            },
            dismissButton = {
                TextButton(onClick = { isStartDatePickerOpen = false }) {
                    Text("Cancelar")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (isEndDatePickerOpen) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = endDate ?: System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { isEndDatePickerOpen = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val millis = datePickerState.selectedDateMillis
                        val adjustedMillis = if (millis != null) millis + 86399999L else null
                        onDateRangeChanged(startDate, adjustedMillis)
                        isEndDatePickerOpen = false
                    }
                ) {
                    Text("Aceptar")
                }
            },
            dismissButton = {
                TextButton(onClick = { isEndDatePickerOpen = false }) {
                    Text("Cancelar")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

/**
 * Diálogo para agregar un gasto rápido
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddExpenseDialog(
    categories: List<Category>,
    onDismiss: () -> Unit,
    onConfirm: (amount: Double, category: Category, description: String, date: Date) -> Unit
) {
    var amountText by remember { mutableStateOf("") }
    var descriptionText by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<Category?>(categories.firstOrNull()) }
    var amountError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(ExpenseRed.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.TrendingDown,
                        contentDescription = null,
                        tint = ExpenseRed,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text("Registrar Nuevo Gasto", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AmountTextField(
                    value = amountText,
                    onValueChange = {
                        amountText = it
                        amountError = false
                    },
                    label = "Monto del Gasto",
                    isError = amountError,
                    errorMessage = if (amountError) "Ingrese un monto válido mayor a 0" else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_expense_amount_input")
                )

                if (categories.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Categoría de Gasto",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            categories.forEach { category ->
                                val isSelected = selectedCategory?.id == category.id
                                val catIcon = IconHelper.getIcon(category.iconKey)

                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedCategory = category },
                                    label = { Text(category.name, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = catIcon,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = isSelected,
                                        borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = descriptionText,
                    onValueChange = { descriptionText = it },
                    label = { Text("Descripción / Nota") },
                    placeholder = { Text("Ej. Almuerzo, supermercado...") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_expense_description_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsedAmount = amountText.toDoubleOrNull()
                    if (parsedAmount == null || parsedAmount <= 0.0) {
                        amountError = true
                    } else if (selectedCategory != null) {
                        onConfirm(parsedAmount, selectedCategory!!, descriptionText, Date())
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = ExpenseRed,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("save_expense_confirm_button")
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Text("Guardar Gasto", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("cancel_expense_button")
            ) {
                Text("Cancelar")
            }
        }
    )
}

/**
 * Formatea una fecha para la cabecera pegajosa de grupo
 */
private fun formatGroupHeaderDate(date: Date): String {
    return DateFormatter.formatGroupHeader(date)
}
