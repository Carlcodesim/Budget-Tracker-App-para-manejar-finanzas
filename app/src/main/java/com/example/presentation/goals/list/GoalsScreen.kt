package com.example.presentation.goals.list

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.BorderStroke
import com.example.ui.theme.*
import com.example.domain.model.SavingsGoal
import com.example.presentation.common.components.AmountTextField
import com.example.presentation.common.components.ConfirmDeleteDialog
import com.example.presentation.common.components.EmptyState
import com.example.presentation.common.components.LoadingIndicator
import com.example.presentation.common.util.CurrencyFormatter
import com.example.presentation.common.util.DateFormatter
import com.example.presentation.common.util.GoogleWalletManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.saveable.rememberSaveable
import com.example.presentation.goals.components.GoalsOverviewHeaderCard
import com.example.presentation.goals.components.GoalFilterType
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalsScreen(
    viewModel: GoalsViewModel,
    onNavigateToForm: (Long?) -> Unit,
    onNavigateBack: () -> Unit = {},
    onNavigateToAssistant: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var goalToDelete by remember { mutableStateOf<SavingsGoal?>(null) }
    var goalToContribute by remember { mutableStateOf<SavingsGoal?>(null) }
    val context = LocalContext.current

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Metas de Ahorro", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("goals_back_to_home_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver al inicio"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { onNavigateToForm(null) },
                        modifier = Modifier.testTag("goals_add_topbar_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Nueva Meta"
                        )
                    }
                    IconButton(
                        onClick = onNavigateToAssistant,
                        modifier = Modifier.testTag("goals_assistant_strategy_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Estrategia IA",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onNavigateToForm(null) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Agregar meta")
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
            when (val currentState = state) {
                is GoalsUiState.Loading -> {
                    LoadingIndicator()
                }
                is GoalsUiState.Success -> {
                    if (currentState.goals.isEmpty()) {
                        EmptyState(
                            icon = Icons.Default.Savings,
                            title = "Sin metas creadas",
                            subtitle = "Crea tu primera meta de ahorro usando el botón '+' de abajo."
                        )
                    } else {
                        var selectedFilter by rememberSaveable { mutableStateOf(GoalFilterType.ALL) }
                        val filteredGoals = remember(currentState.goals, selectedFilter) {
                            when (selectedFilter) {
                                GoalFilterType.ALL -> currentState.goals
                                GoalFilterType.IN_PROGRESS -> currentState.goals.filter { it.currentAmount < it.targetAmount }
                                GoalFilterType.COMPLETED -> currentState.goals.filter { it.currentAmount >= it.targetAmount }
                            }
                        }

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 840.dp),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Tarjeta Resumen Hero de Metas Globales
                            item {
                                GoalsOverviewHeaderCard(
                                    goals = currentState.goals,
                                    selectedFilter = selectedFilter,
                                    onFilterSelected = { selectedFilter = it }
                                )
                            }

                            if (filteredGoals.isEmpty()) {
                                item {
                                    Card(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                                        shape = RoundedCornerShape(16.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = if (selectedFilter == GoalFilterType.COMPLETED) {
                                                    "Aún no has completado ninguna meta. ¡Sigue aportando!"
                                                } else {
                                                    "¡Excelente! No tienes metas pendientes en este momento."
                                                },
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            } else {
                                items(filteredGoals, key = { it.id }) { goal ->
                                    GoalCard(
                                        goal = goal,
                                        onContributeClick = { goalToContribute = goal },
                                        onEditClick = { onNavigateToForm(goal.id) },
                                        onDeleteClick = { goalToDelete = goal },
                                        onShareClick = { GoogleWalletManager.shareGoalPass(context, goal) }
                                    )
                                }
                            }
                        }
                    }
                }
                is GoalsUiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = currentState.message, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    // Modal de Confirmación para Eliminar
    goalToDelete?.let { goal ->
        ConfirmDeleteDialog(
            title = "Eliminar meta de ahorro",
            text = "¿Estás seguro de que deseas eliminar la meta '${goal.name}'? Toda la acumulación registrada se perderá de la vista.",
            onConfirm = {
                viewModel.deleteGoal(goal)
                goalToDelete = null
                scope.launch {
                    val result = snackbarHostState.showSnackbar(
                        message = "Meta eliminada",
                        actionLabel = "DESHACER",
                        duration = SnackbarDuration.Short
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.undoDelete()
                    }
                }
            },
            onDismiss = { goalToDelete = null }
        )
    }

    // Modal para realizar Aportes
    goalToContribute?.let { goal ->
        if (state is GoalsUiState.Success) {
            val accounts = (state as GoalsUiState.Success).accounts
            ContributeDialog(
                goalName = goal.name,
                accounts = accounts,
                onConfirm = { amount, accountId ->
                    viewModel.contribute(goal.id, amount, accountId)
                    goalToContribute = null
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            message = "¡Aporte registrado con éxito en ${goal.name}!",
                            duration = SnackbarDuration.Short
                        )
                    }
                },
                onDismiss = { goalToContribute = null }
            )
        }
    }
}

@Composable
fun GoalCard(
    goal: SavingsGoal,
    onContributeClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onShareClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val progressAnimated by animateFloatAsState(
        targetValue = goal.progressPercentage,
        animationSpec = tween(durationMillis = 800)
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, SoftGreyBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Cabecera de la Meta
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 6.dp)
                    ) {
                        // Badge de Categoría
                        SuggestionChip(
                            onClick = {},
                            label = { Text(goal.category, style = MaterialTheme.typography.bodySmall) },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                labelColor = MaterialTheme.colorScheme.primary
                            )
                        )
                        
                        // Badge de Prioridad
                        val priorityColor = when (goal.priority) {
                            "Alta" -> MaterialTheme.colorScheme.error
                            "Media" -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.secondary
                        }
                        val priorityBg = when (goal.priority) {
                            "Alta" -> MaterialTheme.colorScheme.errorContainer
                            "Media" -> MaterialTheme.colorScheme.primaryContainer
                            else -> MaterialTheme.colorScheme.secondaryContainer
                        }
                        Card(
                            colors = CardDefaults.cardColors(containerColor = priorityBg),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = goal.priority,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = priorityColor,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Text(
                        text = goal.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = DarkCharcoal
                    )
                    
                    if (goal.description.isNotBlank()) {
                        Text(
                            text = goal.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = NeutralVariant,
                            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                        )
                    }

                    if (goal.deadline != null) {
                        Text(
                            text = "Límite: ${DateFormatter.format(goal.deadline)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = NeutralVariant
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onShareClick
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Compartir meta",
                            tint = BluePrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(onClick = onEditClick) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Editar meta",
                            tint = BluePrimary
                        )
                    }
                    IconButton(onClick = onDeleteClick) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Eliminar meta",
                            tint = ExpenseRed
                        )
                    }
                }
            }

            // Panel de Información inteligente (Dynamic Savings Assistant) o Celebración de Meta Cumplida
            if (goal.currentAmount >= goal.targetAmount) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = IncomeGreen.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, IncomeGreen.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Celebration,
                            contentDescription = null,
                            tint = IncomeGreen,
                            modifier = Modifier.size(24.dp)
                        )
                        Column {
                            Text(
                                text = "¡Meta Alcanzada al 100%! 🏆",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = IncomeGreen
                            )
                            Text(
                                text = "¡Felicitaciones! Has completado tu objetivo de ahorro con éxito.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else if (goal.deadline != null) {
                val days = goal.daysRemaining ?: 0
                val monthly = goal.recommendedMonthlySaving ?: 0.0
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Asistente de Ahorro",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = if (days > 0) {
                                "Te quedan $days días para llegar a tu meta. Necesitas ahorrar aproximadamente ${CurrencyFormatter.format(monthly)} por mes."
                            } else {
                                "La fecha límite ha pasado. ¡Aporta fondos para completar tu meta pronto!"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Progreso Monetario
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = "${CurrencyFormatter.format(goal.currentAmount)} de ${CurrencyFormatter.format(goal.targetAmount)}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = DarkCharcoal
                )
                Text(
                    text = "${(goal.progressPercentage * 100).toInt()}%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (goal.currentAmount >= goal.targetAmount) IncomeGreen else BluePrimary
                )
            }

            // Barra de Progreso
            LinearProgressIndicator(
                progress = { progressAnimated },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp),
                color = if (goal.currentAmount >= goal.targetAmount) IncomeGreen else BluePrimary,
                trackColor = LightBlueContainer.copy(alpha = 0.4f),
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
            )

            // Hitos de Progreso (25%, 50%, 75%, 100%)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(25, 50, 75, 100).forEach { pct ->
                    val isReached = goal.progressPercentage >= (pct / 100f)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isReached) {
                            IncomeGreen.copy(alpha = 0.15f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 4.dp, horizontal = 2.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = if (isReached) "$pct% ✓" else "$pct%",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isReached) IncomeGreen else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Botón de Aportar fondos
            val isCompleted = goal.currentAmount >= goal.targetAmount
            Button(
                onClick = onContributeClick,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isCompleted) IncomeGreen else BluePrimary,
                    contentColor = Color.White
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = if (isCompleted) Icons.Default.AddCircle else Icons.Default.Paid,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isCompleted) "Aportar fondos adicionales" else "Aportar dinero",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContributeDialog(
    goalName: String,
    accounts: List<com.example.domain.model.Account>,
    onConfirm: (amount: Double, accountId: Long) -> Unit,
    onDismiss: () -> Unit
) {
    var amount by remember { mutableStateOf("") }
    var selectedAccountId by remember { mutableStateOf<Long?>(accounts.firstOrNull()?.id) }
    var accountMenuExpanded by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Aportar a '$goalName'") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Ingresa el monto y selecciona la cuenta de origen:")
                
                AmountTextField(
                    value = amount,
                    onValueChange = {
                        amount = it
                        errorMsg = null
                    },
                    label = "Monto del aporte",
                    isError = errorMsg != null,
                    errorMessage = errorMsg,
                    modifier = Modifier.fillMaxWidth()
                )

                // Chips de Aporte Rápido (+100, +500, +1000, +2000)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Aportes rápidos:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(100.0, 500.0, 1000.0, 2000.0).forEach { quickVal ->
                            SuggestionChip(
                                onClick = {
                                    val current = amount.toDoubleOrNull() ?: 0.0
                                    amount = String.format(java.util.Locale.US, "%.0f", current + quickVal)
                                    errorMsg = null
                                },
                                label = {
                                    Text(
                                        text = "+${CurrencyFormatter.format(quickVal, includeDecimals = false)}",
                                        fontSize = 11.sp
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Selección de Cuenta
                ExposedDropdownMenuBox(
                    expanded = accountMenuExpanded,
                    onExpandedChange = { accountMenuExpanded = it }
                ) {
                    OutlinedTextField(
                        value = accounts.find { it.id == selectedAccountId }?.name ?: "Seleccionar cuenta",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Cuenta origen") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = accountMenuExpanded) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = accountMenuExpanded,
                        onDismissRequest = { accountMenuExpanded = false }
                    ) {
                        accounts.forEach { account ->
                            DropdownMenuItem(
                                text = { Text("${account.name} (${CurrencyFormatter.format(account.currentBalance)})") },
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
                    val doubleVal = amount.toDoubleOrNull()
                    if (doubleVal == null || doubleVal <= 0) {
                        errorMsg = "El monto de aporte debe ser numérico y mayor a 0"
                    } else if (selectedAccountId == null) {
                        errorMsg = "Debes seleccionar una cuenta origen"
                    } else {
                        onConfirm(doubleVal, selectedAccountId!!)
                    }
                }
            ) {
                Text("Confirmar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
