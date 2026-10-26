package com.example.presentation.categories

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Category as CategoryIcon
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.Category
import com.example.presentation.common.util.CurrencyFormatter
import com.example.presentation.common.util.IconHelper
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.IncomeGreen

private val AVAILABLE_CATEGORY_COLORS = listOf(
    "#EF4444", // Rojo
    "#F97316", // Naranja
    "#F59E0B", // Ámbar
    "#10B981", // Esmeralda / Verde
    "#06B6D4", // Cian
    "#3B82F6", // Azul
    "#6366F1", // Índigo
    "#8B5CF6", // Púrpura
    "#EC4899", // Rosa
    "#64748B"  // Gris Pizarra
)

private val AVAILABLE_CATEGORY_ICONS = listOf(
    "restaurant", "attach_money", "directions_car", "sports_esports",
    "medical_services", "school", "receipt", "shopping_cart", "home",
    "flight", "movie", "work", "pets", "fitness_center", "payment",
    "card_giftcard", "storefront", "local_cafe", "directions_bus",
    "local_gas_station", "fastfood", "shopping_bag"
)

private fun parseHexColor(hex: String, defaultColor: Color = Color(0xFF3B82F6)): Color {
    return try {
        val clean = hex.removePrefix("#")
        val colorLong = clean.toLong(16)
        if (clean.length == 6) {
            Color(colorLong or 0x00000000FF000000)
        } else {
            Color(colorLong)
        }
    } catch (_: Exception) {
        defaultColor
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    viewModel: CategoriesViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onNavigateBack)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.successMessage, state.errorMessage) {
        state.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Categorías de Gastos",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("categories_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Regresar"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.startCreatingCategory() },
                        modifier = Modifier.testTag("add_category_topbar_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Nueva Categoría"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.startCreatingCategory() },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Nueva Categoría") },
                modifier = Modifier.testTag("add_category_fab")
            )
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
                    .widthIn(max = 840.dp)
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Tarjeta de resumen ejecutivo
                CategoryExecutiveSummaryCard(
                    totalCount = state.totalCount,
                    expenseCount = state.expenseCount,
                    incomeCount = state.incomeCount,
                    withBudgetCount = state.withBudgetCount,
                    modifier = Modifier.testTag("categories_summary_card")
                )

                // Barra de búsqueda y filtros
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = { viewModel.onSearchQueryChanged(it) },
                    placeholder = { Text("Buscar categoría...") },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null)
                    },
                    trailingIcon = {
                        if (state.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Limpiar búsqueda")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("categories_search_input")
                )

                // Chips de filtro por tipo
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = state.selectedFilterType == "ALL",
                            onClick = { viewModel.onFilterTypeSelected("ALL") },
                            label = { Text("Todas (${state.totalCount})") },
                            modifier = Modifier.testTag("filter_all_chip")
                        )
                    }
                    item {
                        FilterChip(
                            selected = state.selectedFilterType == "EXPENSE",
                            onClick = { viewModel.onFilterTypeSelected("EXPENSE") },
                            label = { Text("Gastos (${state.expenseCount})") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ExpenseRed.copy(alpha = 0.15f),
                                selectedLabelColor = ExpenseRed
                            ),
                            modifier = Modifier.testTag("filter_expense_chip")
                        )
                    }
                    item {
                        FilterChip(
                            selected = state.selectedFilterType == "INCOME",
                            onClick = { viewModel.onFilterTypeSelected("INCOME") },
                            label = { Text("Ingresos (${state.incomeCount})") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = IncomeGreen.copy(alpha = 0.15f),
                                selectedLabelColor = IncomeGreen
                            ),
                            modifier = Modifier.testTag("filter_income_chip")
                        )
                    }
                    item {
                        FilterChip(
                            selected = state.selectedFilterType == "WITH_BUDGET",
                            onClick = { viewModel.onFilterTypeSelected("WITH_BUDGET") },
                            label = { Text("Con Presupuesto (${state.withBudgetCount})") },
                            modifier = Modifier.testTag("filter_with_budget_chip")
                        )
                    }
                    if (state.overBudgetCount > 0) {
                        item {
                            FilterChip(
                                selected = state.selectedFilterType == "OVER_BUDGET",
                                onClick = { viewModel.onFilterTypeSelected("OVER_BUDGET") },
                                label = { Text("En Riesgo (${state.overBudgetCount})") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.errorContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onErrorContainer
                                ),
                                modifier = Modifier.testTag("filter_over_budget_chip")
                            )
                        }
                    }
                }

                // Lista de categorías
                if (state.isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (state.filteredCategories.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CategoryIcon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(64.dp)
                            )
                            Text(
                                text = if (state.searchQuery.isNotBlank()) "No se encontraron categorías para \"${state.searchQuery}\""
                                else "No hay categorías registradas en esta sección",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Button(
                                onClick = { viewModel.startCreatingCategory() },
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Crear Nueva Categoría")
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .testTag("categories_lazy_column"),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 88.dp)
                    ) {
                        items(
                            items = state.filteredCategories,
                            key = { it.id }
                        ) { category ->
                            CategoryCardItem(
                                category = category,
                                budgetInfo = state.categoryBudgets[category.id],
                                onEditClick = { viewModel.startEditingCategory(category) },
                                onQuickBudgetClick = { viewModel.openQuickBudgetDialog(category) },
                                onDeleteClick = { viewModel.requestDeleteCategory(category) },
                                modifier = Modifier.testTag("category_card_${category.id}")
                            )
                        }
                    }
                }
            }
        }
    }

    // Modal BottomSheet para Crear o Editar Categoría
    if (state.isAddEditBottomSheetOpen) {
        CategoryAddEditBottomSheet(
            state = state,
            onDismiss = { viewModel.dismissAddEditBottomSheet() },
            onNameChange = { viewModel.onFormNameChanged(it) },
            onIconKeyChange = { viewModel.onFormIconKeyChanged(it) },
            onColorHexChange = { viewModel.onFormColorHexChanged(it) },
            onTypeChange = { viewModel.onFormTypeChanged(it) },
            onBudgetChange = { viewModel.onFormMonthlyBudgetChanged(it) },
            onSave = { viewModel.saveCategory() }
        )
    }

    // Diálogo de Ajuste Rápido de Presupuesto
    if (state.categoryForQuickBudget != null) {
        val cat = state.categoryForQuickBudget!!
        var budgetText by remember(cat.id) {
            mutableStateOf(cat.maxMonthlyBudget?.let { if (it % 1 == 0.0) it.toLong().toString() else it.toString() } ?: "")
        }
        AlertDialog(
            onDismissRequest = { viewModel.dismissQuickBudgetDialog() },
            icon = {
                Icon(
                    imageVector = Icons.Default.Payments,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = "Límite para ${cat.name}",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Establece el límite de gasto mensual para recibir alertas:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = budgetText,
                        onValueChange = { input -> budgetText = input.filter { it.isDigit() || it == '.' } },
                        label = { Text("Monto mensual") },
                        prefix = { Text("$ ") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(2000.0, 5000.0, 8000.0, 15000.0).forEach { preset ->
                            SuggestionChip(
                                onClick = { budgetText = preset.toLong().toString() },
                                label = { Text("$${preset.toInt()}") }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsed = budgetText.toDoubleOrNull()
                        viewModel.saveQuickBudget(cat, parsed)
                    }
                ) {
                    Text("Guardar Límite")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissQuickBudgetDialog() }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Diálogo de Confirmación de Eliminación
    if (state.categoryToDelete != null) {
        val cat = state.categoryToDelete!!
        AlertDialog(
            onDismissRequest = { viewModel.dismissDeleteDialog() },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = "¿Eliminar categoría?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Estás a punto de eliminar la categoría \"${cat.name}\"."
                    )
                    if (cat.isDefault) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Nota: Esta es una categoría predeterminada del sistema. Si la eliminas, las transacciones asociadas conservarán su identificador numérico.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    } else {
                        Text(
                            text = "Esta acción no se puede deshacer.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmDeleteCategory() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_delete_category_button")
                ) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.dismissDeleteDialog() },
                    modifier = Modifier.testTag("cancel_delete_category_button")
                ) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun CategoryExecutiveSummaryCard(
    totalCount: Int,
    expenseCount: Int,
    incomeCount: Int,
    withBudgetCount: Int,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SummaryMetricItem(
                label = "Total",
                value = "$totalCount",
                color = MaterialTheme.colorScheme.primary
            )
            VerticalDivider(
                modifier = Modifier.height(36.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
            SummaryMetricItem(
                label = "Gastos",
                value = "$expenseCount",
                color = ExpenseRed
            )
            VerticalDivider(
                modifier = Modifier.height(36.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
            SummaryMetricItem(
                label = "Ingresos",
                value = "$incomeCount",
                color = IncomeGreen
            )
            VerticalDivider(
                modifier = Modifier.height(36.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
            SummaryMetricItem(
                label = "Con Límite",
                value = "$withBudgetCount",
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

@Composable
private fun SummaryMetricItem(
    label: String,
    value: String,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CategoryCardItem(
    category: Category,
    budgetInfo: CategoryBudgetInfo? = null,
    onEditClick: () -> Unit,
    onQuickBudgetClick: () -> Unit = {},
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val categoryColor = remember(category.colorHex) {
        parseHexColor(category.colorHex)
    }
    val iconVector = remember(category.iconKey) {
        IconHelper.getIcon(category.iconKey)
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (budgetInfo?.isOverBudget == true) MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        ),
        shadowElevation = 1.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Icono en contenedor circular con fondo dinámico
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(categoryColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = iconVector,
                        contentDescription = null,
                        tint = categoryColor,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Datos principales de la categoría
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = category.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        // Badge de Tipo (Gasto / Ingreso)
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (category.type == "INCOME") IncomeGreen.copy(alpha = 0.12f)
                            else ExpenseRed.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = if (category.type == "INCOME") "Ingreso" else "Gasto",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (category.type == "INCOME") IncomeGreen else ExpenseRed,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        if (category.isDefault) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = "Predeterminada",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                // Botones de acción
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    IconButton(
                        onClick = onEditClick,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Editar categoría ${category.name}",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = onDeleteClick,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Eliminar categoría ${category.name}",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Sección de Seguimiento de Presupuesto en tiempo real
            val hasLimit = category.maxMonthlyBudget != null && category.maxMonthlyBudget > 0.0
            if (hasLimit) {
                val limit = category.maxMonthlyBudget!!
                val spent = budgetInfo?.spentThisMonth ?: 0.0
                val pct = if (limit > 0) (spent / limit).toFloat() else 0f
                val isOver = spent > limit
                val progressColor = when {
                    isOver -> ExpenseRed
                    pct >= 0.75f -> Color(0xFFF59E0B)
                    else -> IncomeGreen
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Consumo mensual: ${CurrencyFormatter.format(spent)} / ${CurrencyFormatter.format(limit)}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${(pct * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = progressColor
                            )
                        }

                        LinearProgressIndicator(
                            progress = { pct.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = progressColor,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            strokeCap = StrokeCap.Round
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val remaining = limit - spent
                            Text(
                                text = if (isOver) "¡Excedido por ${CurrencyFormatter.format(spent - limit)}!"
                                else "Restan ${CurrencyFormatter.format(remaining)} para el mes",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isOver) FontWeight.Bold else FontWeight.Normal,
                                color = if (isOver) ExpenseRed else MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            TextButton(
                                onClick = onQuickBudgetClick,
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                modifier = Modifier.height(26.dp)
                            ) {
                                Text("Ajustar", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            } else if (category.type == "EXPENSE") {
                val spent = budgetInfo?.spentThisMonth ?: 0.0
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (spent > 0) "Gastado este mes: ${CurrencyFormatter.format(spent)}" else "Sin límite mensual asignado",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(
                            onClick = onQuickBudgetClick,
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                            modifier = Modifier.height(24.dp)
                        ) {
                            Text("+ Asignar Límite", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryAddEditBottomSheet(
    state: CategoriesUiState,
    onDismiss: () -> Unit,
    onNameChange: (String) -> Unit,
    onIconKeyChange: (String) -> Unit,
    onColorHexChange: (String) -> Unit,
    onTypeChange: (String) -> Unit,
    onBudgetChange: (String) -> Unit,
    onSave: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Título
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (state.isEditing) "Editar Categoría" else "Nueva Categoría",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Cerrar")
                }
            }

            // Tipo de Categoría (Gasto vs Ingreso)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Tipo de Categoría",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val isExpense = state.formType == "EXPENSE"
                    val isIncome = state.formType == "INCOME"

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isExpense) ExpenseRed.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = if (isExpense) BorderStroke(2.dp, ExpenseRed) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clickable { onTypeChange("EXPENSE") }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "Gasto",
                                fontWeight = if (isExpense) FontWeight.Bold else FontWeight.Normal,
                                color = if (isExpense) ExpenseRed else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isIncome) IncomeGreen.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = if (isIncome) BorderStroke(2.dp, IncomeGreen) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clickable { onTypeChange("INCOME") }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "Ingreso",
                                fontWeight = if (isIncome) FontWeight.Bold else FontWeight.Normal,
                                color = if (isIncome) IncomeGreen else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Nombre de la Categoría
            OutlinedTextField(
                value = state.formName,
                onValueChange = onNameChange,
                label = { Text("Nombre de la categoría *") },
                placeholder = { Text("Ej. Supermercado, Gimnasio, Freelance") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("category_name_input")
            )

            // Presupuesto mensual límite (opcional)
            OutlinedTextField(
                value = state.formMonthlyBudget,
                onValueChange = onBudgetChange,
                label = { Text("Límite mensual de presupuesto (opcional)") },
                placeholder = { Text("Ej. 5000.00") },
                prefix = { Text("$ ") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("category_budget_input")
            )

            // Selector de Color
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Color Distintivo",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(AVAILABLE_CATEGORY_COLORS) { colorHex ->
                        val isSelected = state.formColorHex.equals(colorHex, ignoreCase = true)
                        val color = parseHexColor(colorHex)
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(color)
                                .clickable { onColorHexChange(colorHex) }
                                .then(
                                    if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                    else Modifier
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Seleccionado",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Selector de Icono
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Icono",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(AVAILABLE_CATEGORY_ICONS) { iconKey ->
                        val isSelected = state.formIconKey == iconKey
                        val iconVector = IconHelper.getIcon(iconKey)
                        val selectedColor = parseHexColor(state.formColorHex)

                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isSelected) selectedColor.copy(alpha = 0.2f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                                .clickable { onIconKeyChange(iconKey) }
                                .then(
                                    if (isSelected) Modifier.border(2.dp, selectedColor, RoundedCornerShape(12.dp))
                                    else Modifier
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = iconVector,
                                contentDescription = null,
                                tint = if (isSelected) selectedColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }

            // Botones de acción
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Cancelar")
                }

                Button(
                    onClick = onSave,
                    enabled = state.isFormValid && !state.isSaving,
                    modifier = Modifier
                        .weight(1.4f)
                        .height(50.dp)
                        .testTag("save_category_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (state.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = if (state.isEditing) Icons.Default.Save else Icons.Default.Add,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (state.isEditing) "Guardar Cambios" else "Crear Categoría",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
