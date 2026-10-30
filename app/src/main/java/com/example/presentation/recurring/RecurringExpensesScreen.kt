package com.example.presentation.recurring

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.presentation.recurring.components.RecurringExpenseFormSheet
import com.example.presentation.recurring.components.RecurringExpenseItemCard
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringExpensesScreen(
    viewModel: RecurringExpensesViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onNavigateBack)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearSnackbar()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Gastos Recurrentes",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Pagos periódicos y gastos programados",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("btn_back_recurring")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Regresar"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.syncAndProcessNow(forceAllDue = false) },
                        modifier = Modifier.testTag("btn_sync_recurring")
                    ) {
                        if (uiState.isAutoProcessing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = "Verificar gastos con automatización activada",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.openForm() },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Programar Gasto") },
                modifier = Modifier.testTag("fab_add_recurring"),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 700.dp)
                    .fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Tarjeta Principal de Resumen Mensual
                item {
                    RecurringSummaryHeroCard(
                        totalCommitted = uiState.totalMonthlyCommitted,
                        totalPaid = uiState.totalPaidThisMonth,
                        totalPending = uiState.totalPendingThisMonth,
                        paidCount = uiState.executedCountThisMonth,
                        pendingCount = uiState.pendingCountThisMonth,
                        nextDueDays = uiState.daysUntilNextDue,
                        nextDueTitle = uiState.nextDueExpense?.title,
                        nextDueAutoCreate = uiState.nextDueExpense?.autoCreate == true,
                        onProcessAllClick = { viewModel.syncAndProcessNow(forceAllDue = true) }
                    )
                }

                // Calendario de pagos recurrentes del mes
                if (uiState.recurringExpenses.isNotEmpty()) {
                    item {
                        MonthlyScheduleStrip(
                            expenses = uiState.recurringExpenses,
                            currentDay = uiState.currentDayOfMonth,
                            currentYearMonth = uiState.currentYearMonth
                        )
                    }
                }

                // Chips de Filtro
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = uiState.selectedFilter == RecurringFilter.ALL,
                            onClick = { viewModel.setFilter(RecurringFilter.ALL) },
                            label = { Text("Todos (${uiState.recurringExpenses.size})") },
                            colors = FilterChipDefaults.filterChipColors()
                        )
                        FilterChip(
                            selected = uiState.selectedFilter == RecurringFilter.PENDING_THIS_MONTH,
                            onClick = { viewModel.setFilter(RecurringFilter.PENDING_THIS_MONTH) },
                            label = { Text("Pendientes (${uiState.pendingCountThisMonth})") },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.PendingActions,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                        FilterChip(
                            selected = uiState.selectedFilter == RecurringFilter.EXECUTED_THIS_MONTH,
                            onClick = { viewModel.setFilter(RecurringFilter.EXECUTED_THIS_MONTH) },
                            label = { Text("Registrados (${uiState.executedCountThisMonth})") },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                        FilterChip(
                            selected = uiState.selectedFilter == RecurringFilter.PAUSED,
                            onClick = { viewModel.setFilter(RecurringFilter.PAUSED) },
                            label = { Text("Pausados") },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.PauseCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }
                }

                // Listado de Gastos Recurrentes
                if (uiState.filteredExpenses.isEmpty()) {
                    item {
                        EmptyRecurringState(
                            filter = uiState.selectedFilter,
                            onAddNew = { viewModel.openForm() }
                        )
                    }
                } else {
                    items(
                        items = uiState.filteredExpenses,
                        key = { it.id }
                    ) { expense ->
                        RecurringExpenseItemCard(
                            expense = expense,
                            currentYearMonth = uiState.currentYearMonth,
                            currentDay = uiState.currentDayOfMonth,
                            onExecuteNow = { paidAmount -> viewModel.executeSingleExpenseNow(expense, paidAmount) },
                            onToggleEnabled = { isChecked ->
                                viewModel.toggleExpenseStatus(expense, isChecked)
                            },
                            onEdit = { viewModel.openForm(expense) },
                            onDelete = { viewModel.deleteExpense(expense) }
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(64.dp))
                }
            }

            // BottomSheet para crear/editar
            if (uiState.isFormOpen) {
                RecurringExpenseFormSheet(
                    isEditing = uiState.editingExpense != null,
                    initialExpense = uiState.editingExpense,
                    categories = uiState.categories,
                    accounts = uiState.accounts,
                    onSave = { title, amount, categoryId, accountId, dayOfMonth, desc, autoCreate ->
                        viewModel.saveRecurringExpense(
                            title = title,
                            amount = amount,
                            categoryId = categoryId,
                            accountId = accountId,
                            dayOfMonth = dayOfMonth,
                            description = desc,
                            autoCreate = autoCreate,
                            editingId = uiState.editingExpense?.id ?: 0L
                        )
                    },
                    onDismiss = { viewModel.closeForm() }
                )
            }
        }
    }
}

@Composable
private fun RecurringSummaryHeroCard(
    totalCommitted: Double,
    totalPaid: Double,
    totalPending: Double,
    paidCount: Int,
    pendingCount: Int,
    nextDueDays: Int?,
    nextDueTitle: String?,
    nextDueAutoCreate: Boolean,
    onProcessAllClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Fila 1: Total mensual comprometido
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(
                        text = "TOTAL MENSUAL COMPROMETIDO",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        letterSpacing = 1.1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "$${String.format(Locale.US, "%,.2f", totalCommitted)}",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Repeat,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // Barra de progreso de pagos registrados frente al total programado
            val progress = if (totalCommitted > 0) (totalPaid / totalCommitted).toFloat().coerceIn(0f, 1f) else 0f
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Progreso del mes (${(progress * 100).toInt()}%)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "$paidCount de ${paidCount + pendingCount} pagos del mes",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                )
            }

            // Fila de métricas: registrado vs por registrar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Registrado este mes",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "$${String.format(Locale.US, "%,.2f", totalPaid)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Por registrar este mes",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "$${String.format(Locale.US, "%,.2f", totalPending)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // Alerta de Próximo Vencimiento
            if (nextDueTitle != null && nextDueDays != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(20.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Próximo vencimiento: $nextDueTitle",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = when {
                                    nextDueDays == 0 && nextDueAutoCreate ->
                                        "La generación automática está activada para hoy."
                                    nextDueDays == 0 ->
                                        "Fecha de pago hoy. Registra el movimiento cuando pagues."
                                    nextDueAutoCreate ->
                                        "Faltan $nextDueDays días para el registro automático."
                                    else ->
                                        "Faltan $nextDueDays días para su fecha de pago."
                                },
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

@Composable
private fun MonthlyScheduleStrip(
    expenses: List<com.example.domain.model.RecurringExpense>,
    currentDay: Int,
    currentYearMonth: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
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
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Calendario de pagos",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "Hoy es día $currentDay",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Tira horizontal de fechas de pago
            val daysInCurrentMonth = Calendar.getInstance().getActualMaximum(Calendar.DAY_OF_MONTH)
            val activeExpensesByDay = expenses
                .filter { it.isEnabled }
                .groupBy { it.effectiveDayInMonth(daysInCurrentMonth) }
            val sortedDays = activeExpensesByDay.keys.sorted()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                sortedDays.forEach { day ->
                    val dayExpenses = activeExpensesByDay[day] ?: emptyList()
                    val allExecuted = dayExpenses.all { it.isExecutedInMonth(currentYearMonth) }
                    val isToday = day == currentDay
                    val isPast = day < currentDay

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isToday) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else if (allExecuted) {
                            MaterialTheme.colorScheme.surface
                        } else if (isPast) {
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        border = if (isToday) {
                            CardDefaults.outlinedCardBorder().copy(
                                brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                                width = 1.5.dp
                            )
                        } else null
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Día $day",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${dayExpenses.size} ${if (dayExpenses.size == 1) "gasto" else "gastos"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "$${String.format(Locale.US, "%,.0f", dayExpenses.sumOf { it.amount })}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (allExecuted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyRecurringState(
    filter: RecurringFilter,
    onAddNew: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Repeat,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Text(
                text = when (filter) {
                    RecurringFilter.ALL -> "No tienes gastos recurrentes programados"
                    RecurringFilter.PENDING_THIS_MONTH -> "No hay gastos pendientes este mes"
                    RecurringFilter.EXECUTED_THIS_MONTH -> "Aún no has registrado pagos este mes"
                    RecurringFilter.PAUSED -> "No tienes pagos recurrentes pausados"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Agrega tus propios pagos periódicos. Define el importe, la categoría, la cuenta y la fecha; registra cada pago cuando realmente lo realices. La automatización es opcional.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Button(
                onClick = onAddNew,
                modifier = Modifier.testTag("btn_empty_add_recurring")
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Programar primer gasto")
            }
        }
    }
}
