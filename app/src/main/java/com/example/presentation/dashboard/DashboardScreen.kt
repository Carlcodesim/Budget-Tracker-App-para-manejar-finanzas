package com.example.presentation.dashboard
 
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Category as CategoryIcon
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.launch
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.sqrt
import kotlin.math.atan2
import com.example.ui.theme.*
import com.example.domain.model.BalanceSummary
import com.example.domain.model.Transaction
import com.example.domain.model.TransactionType
import com.example.domain.model.Account
import com.example.domain.model.FinancialHealthMetrics
import com.example.domain.model.CustomCycleBalancePrediction
import com.example.presentation.dashboard.components.FinancialHealthBannerCard
import com.example.presentation.dashboard.components.CyclePredictionDashboardCard
import com.example.presentation.common.components.EmptyState
import com.example.presentation.common.components.LoadingIndicator
import com.example.presentation.common.util.CurrencyFormatter
import com.example.presentation.common.util.DateFormatter
import com.example.presentation.common.util.IconHelper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast
import com.example.presentation.common.util.BackupManager
import com.example.presentation.common.components.MonthlyBackupPromptCard
import com.example.presentation.common.components.RapidExpenseDialog
import com.example.domain.model.FrequentExpensePreset
import com.example.domain.model.Category
import com.example.presentation.common.components.SpeedDialFloatingActionButton
import com.example.presentation.common.components.SpeedDialBackdropScrim
import com.example.presentation.common.components.FrequentExpensesBottomSheet
import com.example.presentation.dashboard.components.FrequentExpensesQuickCard
import com.example.presentation.dashboard.components.SpendingPieChartCard
import com.example.presentation.dashboard.components.TotalBudgetBalanceCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Colores modernos para el gráfico de categorías
private val CHART_COLORS = listOf(
    BluePrimary,
    IncomeGreen,
    ExpenseRed,
    OtherCategoryBrown,
    Color(0xFF8E24AA), // Purple
    Color(0xFF3949AB), // Indigo
    Color(0xFFD81B60), // Magenta
    Color(0xFF78909C)  // Grey
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onNavigateToTransactionForm: (TransactionType) -> Unit,
    onNavigateToTransactions: () -> Unit,
    onNavigateToAnalysis: () -> Unit,
    onNavigateToReminders: () -> Unit,
    onNavigateToAccounts: () -> Unit,
    onNavigateToAssistant: () -> Unit,
    onNavigateToMonthlySummary: () -> Unit = {},
    onNavigateToRecurringExpenses: () -> Unit = {},
    onNavigateToCategories: () -> Unit = {},
    onNavigateToScanReceipt: () -> Unit = { onNavigateToTransactionForm(TransactionType.EXPENSE) },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showRapidExpenseDialog by remember { mutableStateOf(false) }
    var isSpeedDialExpanded by remember { mutableStateOf(false) }
    var showFrequentExpensesSheet by remember { mutableStateOf(false) }

    val onLogPreset: (FrequentExpensePreset) -> Unit = { preset ->
        val successState = state as? DashboardUiState.Success
        val categories = successState?.categories ?: emptyList()
        val accounts = successState?.accounts ?: emptyList()

        val matchedCategory = categories.firstOrNull {
            it.name.equals(preset.categoryName, ignoreCase = true)
        } ?: categories.firstOrNull { it.name.lowercase().contains("comida") }
          ?: categories.firstOrNull()
          ?: Category(id = 1L, name = "General", iconKey = "category", isDefault = true)

        val selectedAccount = accounts.firstOrNull()
            ?: Account(id = 1L, name = "Efectivo", type = "CASH", initialBalance = 0.0)

        viewModel.addRapidExpense(
            amount = preset.defaultAmount,
            category = matchedCategory,
            account = selectedAccount,
            date = Date(),
            note = preset.note,
            onSuccess = { createdTx ->
                scope.launch {
                    val result = snackbarHostState.showSnackbar(
                        message = "Gasto '${preset.title}' (${CurrencyFormatter.format(preset.defaultAmount)}) registrado",
                        actionLabel = "Deshacer",
                        duration = SnackbarDuration.Short
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.undoTransaction(createdTx)
                    }
                }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.AccountBalanceWallet,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = "Resumen Financiero",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = (-0.2).sp
                                    ),
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = "Panel de control y balance",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    actions = {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            IconButton(
                                onClick = onNavigateToAssistant,
                                modifier = Modifier
                                    .size(38.dp)
                                    .testTag("dashboard_assistant_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "Asistente AI",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                            modifier = Modifier.padding(end = 6.dp)
                        ) {
                            IconButton(
                                onClick = onNavigateToReminders,
                                modifier = Modifier
                                    .size(38.dp)
                                    .testTag("dashboard_notifications_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.NotificationsActive,
                                    contentDescription = "Recordatorios",
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    },
                    scrollBehavior = scrollBehavior,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                    )
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.TopCenter
            ) {
                when (val currentState = state) {
                    is DashboardUiState.Loading -> {
                        LoadingIndicator()
                    }
                    is DashboardUiState.Success -> {
                        DashboardContent(
                            summary = currentState.summary,
                            recentTransactions = currentState.recentTransactions,
                            categoryExpenses = currentState.categoryExpenseMap,
                            monthlyCategoryExpenses = currentState.monthlyCategoryExpenseMap,
                            allTimeCategoryExpenses = currentState.allTimeCategoryExpenseMap,
                            budgetStatuses = currentState.budgetStatuses,
                            accounts = currentState.accounts,
                            financialHealth = currentState.financialHealth,
                            cyclePrediction = currentState.cyclePrediction,
                            onUpdatePredictedIncome = { amount ->
                                viewModel.updatePredictedIncome(context, amount)
                            },
                            onLogPreset = onLogPreset,
                            onOpenAllPresets = { showFrequentExpensesSheet = true },
                            onNavigateToTransactionForm = onNavigateToTransactionForm,
                            onNavigateToTransactions = onNavigateToTransactions,
                            onNavigateToAnalysis = onNavigateToAnalysis,
                            onNavigateToReminders = onNavigateToReminders,
                            onNavigateToAccounts = onNavigateToAccounts,
                            onNavigateToAssistant = onNavigateToAssistant,
                            onNavigateToMonthlySummary = onNavigateToMonthlySummary,
                            onNavigateToRecurringExpenses = onNavigateToRecurringExpenses,
                            onNavigateToCategories = onNavigateToCategories,
                            onNavigateToScanReceipt = onNavigateToScanReceipt
                        )
                    }
                    is DashboardUiState.Error -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = currentState.message,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }
        }

        // Cortina de fondo cuando el Speed Dial está expandido (debajo del FAB y sus opciones)
        SpeedDialBackdropScrim(
            visible = isSpeedDialExpanded,
            onDismiss = { isSpeedDialExpanded = false }
        )

        // Speed Dial FAB posicionado en la esquina inferior derecha sobre la cortina de fondo
        SpeedDialFloatingActionButton(
            isExpanded = isSpeedDialExpanded,
            onToggleExpand = { isSpeedDialExpanded = !isSpeedDialExpanded },
            onDismissRequest = { isSpeedDialExpanded = false },
            onFrequentExpensesClick = { showFrequentExpensesSheet = true },
            onRapidExpenseClick = { showRapidExpenseDialog = true },
            onNewExpenseClick = { onNavigateToTransactionForm(TransactionType.EXPENSE) },
            onNewIncomeClick = { onNavigateToTransactionForm(TransactionType.INCOME) },
            onAssistantClick = onNavigateToAssistant,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 16.dp, end = 16.dp)
        )
    }

    if (showRapidExpenseDialog) {
        val successState = state as? DashboardUiState.Success
        val categories = successState?.categories ?: emptyList()
        val accounts = successState?.accounts ?: emptyList()

        RapidExpenseDialog(
            categories = categories,
            accounts = accounts,
            onDismissRequest = { showRapidExpenseDialog = false },
            onSaveExpense = { amount, category, account, dateEpoch, note ->
                viewModel.addRapidExpense(
                    amount = amount,
                    category = category,
                    account = account,
                    date = Date(dateEpoch),
                    note = note,
                    onSuccess = { createdTx ->
                        scope.launch {
                            val result = snackbarHostState.showSnackbar(
                                message = "Gasto de ${CurrencyFormatter.format(amount)} registrado con éxito",
                                actionLabel = "Deshacer",
                                duration = SnackbarDuration.Short
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                viewModel.undoTransaction(createdTx)
                            }
                        }
                    }
                )
                showRapidExpenseDialog = false
            }
        )
    }

    if (showFrequentExpensesSheet) {
        val successState = state as? DashboardUiState.Success
        val categories = successState?.categories ?: emptyList()
        val accounts = successState?.accounts ?: emptyList()

        FrequentExpensesBottomSheet(
            categories = categories,
            accounts = accounts,
            onDismissRequest = { showFrequentExpensesSheet = false },
            onLogFrequentExpense = { preset, amount, category, account ->
                viewModel.addRapidExpense(
                    amount = amount,
                    category = category,
                    account = account,
                    date = Date(),
                    note = preset.note,
                    onSuccess = { createdTx ->
                        scope.launch {
                            val result = snackbarHostState.showSnackbar(
                                message = "Gasto '${preset.title}' (${CurrencyFormatter.format(amount)}) registrado",
                                actionLabel = "Deshacer",
                                duration = SnackbarDuration.Short
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                viewModel.undoTransaction(createdTx)
                            }
                        }
                    }
                )
                showFrequentExpensesSheet = false
            },
            onCustomExpenseClick = {
                showFrequentExpensesSheet = false
                showRapidExpenseDialog = true
            }
        )
    }
}

@Composable
fun DashboardContent(
    summary: BalanceSummary,
    recentTransactions: List<Transaction>,
    categoryExpenses: Map<String, Double>,
    budgetStatuses: List<BudgetStatus>,
    accounts: List<Account>,
    monthlyCategoryExpenses: Map<String, Double> = emptyMap(),
    allTimeCategoryExpenses: Map<String, Double> = emptyMap(),
    financialHealth: FinancialHealthMetrics? = null,
    cyclePrediction: CustomCycleBalancePrediction? = null,
    onUpdatePredictedIncome: (Double?) -> Unit = {},
    onLogPreset: (FrequentExpensePreset) -> Unit,
    onOpenAllPresets: () -> Unit,
    onNavigateToTransactionForm: (TransactionType) -> Unit,
    onNavigateToTransactions: () -> Unit,
    onNavigateToAnalysis: () -> Unit,
    onNavigateToReminders: () -> Unit,
    onNavigateToAccounts: () -> Unit,
    onNavigateToAssistant: () -> Unit,
    onNavigateToMonthlySummary: () -> Unit = {},
    onNavigateToRecurringExpenses: () -> Unit = {},
    onNavigateToCategories: () -> Unit = {},
    onNavigateToScanReceipt: () -> Unit = { onNavigateToTransactionForm(TransactionType.EXPENSE) }
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showBackupPrompt by remember { mutableStateOf(BackupManager.shouldShowMonthlyBackupPrompt(context)) }

    val exportJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val db = (context.applicationContext as com.example.BudgetTrackerApp).appContainer.database
                    val jsonString = BackupManager.exportFullBackupJson(context, db)
                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        outputStream.write(jsonString.toByteArray(Charsets.UTF_8))
                    }
                    showBackupPrompt = false
                    Toast.makeText(context, "¡Copia de seguridad guardada con éxito!", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Error al guardar copia de seguridad: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 840.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (showBackupPrompt) {
            item {
                MonthlyBackupPromptCard(
                    onBackupNowClick = {
                        try {
                            val dateStr = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
                            exportJsonLauncher.launch("respaldo_presupuesto_$dateStr.json")
                        } catch (e: Exception) {
                            Toast.makeText(context, "Error al abrir exportador: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onDismissClick = {
                        BackupManager.dismissPromptForCurrentMonth(context)
                        showBackupPrompt = false
                    }
                )
            }
        }

        // Tarjeta de Balance General
        item {
            TotalBudgetBalanceCard(summary = summary)
        }

        // Panel de Resumen: Balance Real vs Ingreso Previsto (Recharts / D3) para el Ciclo de Presupuesto Actual
        if (cyclePrediction != null) {
            item {
                CyclePredictionDashboardCard(
                    prediction = cyclePrediction,
                    onUpdatePredictedIncome = onUpdatePredictedIncome
                )
            }
        }

        // Diagnóstico y Ritmo de Salud Financiera
        if (financialHealth != null) {
            item {
                FinancialHealthBannerCard(
                    metrics = financialHealth,
                    onNavigateToAnalysis = onNavigateToAnalysis
                )
            }
        }

        // Widget de Cuentas (Billetera)
        item {
            WalletWidget(
                accounts = accounts,
                onNavigateToAccounts = onNavigateToAccounts
            )
        }

        // Acciones Rápidas de Gastos Frecuentes (1 Toque)
        item {
            FrequentExpensesQuickCard(
                onLogPreset = onLogPreset,
                onOpenAllPresets = onOpenAllPresets
            )
        }

        // Accesos Rápidos (Ingreso / Gasto / Escanear)
        item {
            QuickActions(
                onNavigateToTransactionForm = onNavigateToTransactionForm,
                onNavigateToScanReceipt = onNavigateToScanReceipt
            )
        }

        // Centro de Herramientas y Servicios Financieros (Cuadrícula Moderna 2-Col)
        item {
            FinancialServicesGrid(
                onNavigateToAssistant = onNavigateToAssistant,
                onNavigateToMonthlySummary = onNavigateToMonthlySummary,
                onNavigateToRecurringExpenses = onNavigateToRecurringExpenses,
                onNavigateToReminders = onNavigateToReminders,
                onNavigateToAnalysis = onNavigateToAnalysis
            )
        }

        // Alertas y Control de Presupuesto
        if (budgetStatuses.isNotEmpty()) {
            item {
                BudgetAlertsSection(budgetStatuses = budgetStatuses)
            }
        } else {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.CategoryIcon,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Control de Presupuesto Mensual",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Establece topes de gasto en tus categorías para recibir alertas de consumo y evitar sobrecostos.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(
                            onClick = onNavigateToCategories,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Configurar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Gráfico de Gastos por Categoría (Recharts / Compose Canvas: Donut, Pie y Barras desde Room DB)
        if (categoryExpenses.isNotEmpty() || monthlyCategoryExpenses.isNotEmpty() || allTimeCategoryExpenses.isNotEmpty() || recentTransactions.isNotEmpty()) {
            item {
                SpendingPieChartCard(
                    categoryExpenses = categoryExpenses,
                    monthlyCategoryExpenses = monthlyCategoryExpenses,
                    allTimeCategoryExpenses = allTimeCategoryExpenses,
                    title = "Gastos por Categoría"
                )
            }
        }

        // Movimientos Recientes Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Movimientos recientes",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = onNavigateToTransactions) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Ver todos")
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // Lista de Movimientos Recientes
        if (recentTransactions.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Default.AccountBalanceWallet,
                    title = "Sin transacciones",
                    subtitle = "Agrega tu primer ingreso o gasto con los botones de arriba."
                )
            }
        } else {
            items(
                items = recentTransactions,
                key = { it.id },
                contentType = { "recent_transaction" }
            ) { transaction ->
                RecentTransactionItem(transaction = transaction)
            }
        }
    }
}

@Composable
fun BalanceCard(summary: BalanceSummary) {
    val context = LocalContext.current

    val formattedBalance = remember(summary.currentBalance) { CurrencyFormatter.format(summary.currentBalance) }
    val formattedIncome = remember(summary.monthlyIncome) { CurrencyFormatter.format(summary.monthlyIncome) }
    val formattedExpense = remember(summary.monthlyExpense) { CurrencyFormatter.format(summary.monthlyExpense) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF0A2540), // Premium Midnight Slate Blue (Stripe style)
                        Color(0xFF004B87), // Strong Deep Blue
                        Color(0xFF0072C6)  // High-end Azure Blue
                    )
                )
            ),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "BALANCE TOTAL",
                    style = MaterialTheme.typography.labelMedium.copy(
                        letterSpacing = 1.5.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color.White.copy(alpha = 0.7f)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = formattedBalance,
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = Color.White
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Ingresos y Gastos del Mes
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Ingreso Mensual
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.12f))
                        .padding(12.dp)
                ) {
                    Column {
                        Text(
                            text = "INGRESOS",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color.White.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "+$formattedIncome",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF2ECC71) // Fluent Emerald Green
                        )
                    }
                }

                // Gasto Mensual
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.12f))
                        .padding(12.dp)
                ) {
                    Column {
                        Text(
                            text = "GASTOS",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color.White.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "-$formattedExpense",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFFFF7675) // Fluorescent Red-Coral
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun QuickActions(
    onNavigateToTransactionForm: (TransactionType) -> Unit,
    onNavigateToScanReceipt: () -> Unit = { onNavigateToTransactionForm(TransactionType.EXPENSE) }
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val isCompact = maxWidth < 340.dp
        val spacing = if (isCompact) 6.dp else 10.dp
        val iconSize = if (isCompact) 16.dp else 18.dp
        val btnHeight = if (isCompact) 48.dp else 52.dp
        val fontSize = if (isCompact) 13.sp else 14.sp

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing)
        ) {
            Button(
                onClick = { onNavigateToTransactionForm(TransactionType.INCOME) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = IncomeGreen
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(btnHeight)
                    .testTag("dashboard_quick_income_button"),
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(horizontal = if (isCompact) 4.dp else 8.dp)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(iconSize))
                Spacer(modifier = Modifier.width(3.dp))
                Text("Ingreso", fontWeight = FontWeight.Bold, fontSize = fontSize)
            }

            Button(
                onClick = { onNavigateToTransactionForm(TransactionType.EXPENSE) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = ExpenseRed
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(btnHeight)
                    .testTag("dashboard_quick_expense_button"),
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(horizontal = if (isCompact) 4.dp else 8.dp)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(iconSize))
                Spacer(modifier = Modifier.width(3.dp))
                Text("Gasto", fontWeight = FontWeight.Bold, fontSize = fontSize)
            }

            Button(
                onClick = onNavigateToScanReceipt,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF8E24AA)
                ),
                modifier = Modifier
                    .weight(1.05f)
                    .height(btnHeight)
                    .testTag("dashboard_quick_scan_button"),
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(horizontal = if (isCompact) 4.dp else 8.dp)
            ) {
                Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(iconSize))
                Spacer(modifier = Modifier.width(3.dp))
                Text("Escanear", fontWeight = FontWeight.Bold, fontSize = fontSize)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryExpenseChart(
    categoryExpenses: Map<String, Double>
) {
    val totalExpense = remember(categoryExpenses) { categoryExpenses.values.sum() }
    if (totalExpense <= 0) return

    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    val entriesList = remember(categoryExpenses) { categoryExpenses.entries.toList() }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, NeutralOutline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PieChart,
                    contentDescription = null,
                    tint = BluePrimary
                )
                Text(
                    text = "Distribución de Gastos",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = DarkCharcoal
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Gráfico Circular de Canvas y Leyendas en Fila/Columna adaptativa
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Gráfico de Canvas
                Box(
                    modifier = Modifier.size(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(entriesList, totalExpense) {
                                detectTapGestures { offset ->
                                    val centerX = size.width / 2f
                                    val centerY = size.height / 2f
                                    val dx = offset.x - centerX
                                    val dy = offset.y - centerY
                                    val distance = sqrt(dx * dx + dy * dy)
                                    
                                    val sizeMin = if (size.width < size.height) size.width.toFloat() else size.height.toFloat()
                                    val maxStrokeWidth = 28.dp.toPx()
                                    val radius = (sizeMin - maxStrokeWidth) / 2f
                                    
                                    val innerLimit = radius - maxStrokeWidth
                                    val outerLimit = radius + maxStrokeWidth

                                    if (distance in innerLimit..outerLimit) {
                                        var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                                        if (angle < 0) {
                                            angle += 360f
                                        }
                                        // Adjust because drawing starts at -90f (270 degrees)
                                        var adjustedAngle = angle - 270f
                                        if (adjustedAngle < 0) {
                                            adjustedAngle += 360f
                                        }

                                        var currentAngle = 0f
                                        var foundIndex: Int? = null
                                        entriesList.forEachIndexed { index, entry ->
                                            val sweepAngle = ((entry.value / totalExpense) * 360f).toFloat()
                                            if (adjustedAngle >= currentAngle && adjustedAngle < currentAngle + sweepAngle) {
                                                foundIndex = index
                                            }
                                            currentAngle += sweepAngle
                                        }

                                        if (foundIndex != null) {
                                            selectedIndex = if (selectedIndex == foundIndex) null else foundIndex
                                        }
                                    }
                                }
                            }
                    ) {
                        val maxStrokeWidth = 28.dp.toPx()
                        val sizeMin = size.minDimension
                        val radius = (sizeMin - maxStrokeWidth) / 2f
                        val centerOffset = Offset(size.width / 2f, size.height / 2f)

                        var startAngle = -90f
                        entriesList.forEachIndexed { index, entry ->
                            val sweepAngle = ((entry.value / totalExpense) * 360f).toFloat()
                            val color = CHART_COLORS[index % CHART_COLORS.size]
                            
                            val isSelected = selectedIndex == index
                            val strokeWidth = if (isSelected) 28.dp.toPx() else 22.dp.toPx()
                            val alpha = if (selectedIndex == null || isSelected) 1.0f else 0.3f
                            
                            drawArc(
                                color = color.copy(alpha = alpha),
                                startAngle = startAngle,
                                sweepAngle = sweepAngle,
                                useCenter = false,
                                topLeft = Offset(centerOffset.x - radius, centerOffset.y - radius),
                                size = Size(radius * 2, radius * 2),
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                            )
                            startAngle += sweepAngle
                        }
                    }
                    
                    // Central Text
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        if (selectedIndex != null) {
                            val entry = entriesList.getOrNull(selectedIndex!!)
                            if (entry != null) {
                                val percentage = (entry.value / totalExpense) * 100
                                Text(
                                    text = entry.key,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                    color = NeutralVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${String.format("%.1f", percentage)}%",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = CHART_COLORS[selectedIndex!! % CHART_COLORS.size]
                                )
                                Text(
                                    text = CurrencyFormatter.format(entry.value),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = DarkCharcoal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        } else {
                            Text(
                                text = "Total",
                                style = MaterialTheme.typography.labelMedium,
                                color = NeutralVariant
                            )
                            Text(
                                text = CurrencyFormatter.format(totalExpense),
                                style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
                                fontWeight = FontWeight.Bold,
                                color = DarkCharcoal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Leyendas del gráfico
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    entriesList.take(4).forEachIndexed { index, entry ->
                        val color = CHART_COLORS[index % CHART_COLORS.size]
                        val percentage = (entry.value / totalExpense) * 100
                        val isSelected = selectedIndex == index
                        val isAnySelected = selectedIndex != null
                        
                        val rowAlpha = if (!isAnySelected || isSelected) 1.0f else 0.4f
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    selectedIndex = if (selectedIndex == index) null else index
                                }
                                .padding(vertical = 4.dp, horizontal = 6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(if (isSelected) 14.dp else 10.dp)
                                    .clip(CircleShape)
                                    .background(color.copy(alpha = rowAlpha))
                            )
                            Text(
                                text = "${entry.key} (${String.format("%.1f", percentage)}%)",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = if (isSelected) DarkCharcoal else NeutralVariant.copy(alpha = rowAlpha),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
            
            // Helpful microinteraction tip
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "💡 Toca una sección o categoría para ver detalles",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = NeutralVariant.copy(alpha = 0.7f),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
fun FinancialServicesGrid(
    onNavigateToAssistant: () -> Unit,
    onNavigateToMonthlySummary: () -> Unit,
    onNavigateToRecurringExpenses: () -> Unit,
    onNavigateToReminders: () -> Unit,
    onNavigateToAnalysis: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Centro Financiero",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = DarkCharcoal
            )
            Text(
                text = "Servicios y Reportes",
                style = MaterialTheme.typography.labelSmall,
                color = NeutralVariant
            )
        }

        // Fila 1: Asistente IA + Reportes Mensuales
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FinancialServiceTile(
                title = "Asistente IA",
                subtitle = "Consejos & Chat",
                badge = "Gemini",
                icon = Icons.Default.AutoAwesome,
                gradientColors = listOf(Color(0xFF6366F1), Color(0xFF8B5CF6)),
                onClick = onNavigateToAssistant,
                testTag = "dashboard_assistant_banner",
                modifier = Modifier.weight(1f)
            )

            FinancialServiceTile(
                title = "Reportes",
                subtitle = "Resumen Mensual",
                badge = "PDF",
                icon = Icons.Default.PieChart,
                gradientColors = listOf(Color(0xFF0072C6), Color(0xFF00ACC1)),
                onClick = onNavigateToMonthlySummary,
                testTag = "dashboard_monthly_summary_banner",
                modifier = Modifier.weight(1f)
            )
        }

        // Fila 2: Gastos Recurrentes + Recordatorios
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FinancialServiceTile(
                title = "Recurrentes",
                subtitle = "Suscripciones & Fijos",
                badge = "Auto",
                icon = Icons.Default.Repeat,
                gradientColors = listOf(Color(0xFF059669), Color(0xFF10B981)),
                onClick = onNavigateToRecurringExpenses,
                testTag = "dashboard_recurring_banner",
                modifier = Modifier.weight(1f)
            )

            FinancialServiceTile(
                title = "Recordatorios",
                subtitle = "Alertas de Pago",
                badge = "Alertas",
                icon = Icons.Default.NotificationsActive,
                gradientColors = listOf(Color(0xFFD97706), Color(0xFFF59E0B)),
                onClick = onNavigateToReminders,
                testTag = "dashboard_reminders_banner",
                modifier = Modifier.weight(1f)
            )
        }

        // Fila 3 (Tile completo): Análisis Financiero y Gráficas
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .clickable { onNavigateToAnalysis() }
                .testTag("dashboard_analysis_banner"),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(1.dp, NeutralOutline)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF3B82F6), Color(0xFF1D4ED8))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Análisis Financiero",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = DarkCharcoal
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF3B82F6).copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = "Tendencias",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF3B82F6),
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                    Text(
                        text = "Comparativas mensuales, promedios y salud de presupuesto.",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = NeutralVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun FinancialServiceTile(
    title: String,
    subtitle: String,
    badge: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    gradientColors: List<Color>,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable { onClick() }
            .testTag(testTag),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, NeutralOutline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(gradientColors)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = gradientColors.first().copy(alpha = 0.12f)
                ) {
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        fontWeight = FontWeight.Bold,
                        color = gradientColors.first(),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = DarkCharcoal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = NeutralVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun RecentTransactionItem(transaction: Transaction) {
    val isIncome = remember(transaction.type) { transaction.type == TransactionType.INCOME }
    val isTransfer = remember(transaction.type) { transaction.type == TransactionType.TRANSFER }
    val isIncomingTransfer = remember(transaction.tags, transaction.description) {
        transaction.tags?.contains("IN", ignoreCase = true) == true ||
        transaction.description?.contains("desde", ignoreCase = true) == true
    }

    val valueColor = remember(isIncome, isTransfer) {
        when {
            isTransfer -> Color(0xFF2563EB)
            isIncome -> IncomeGreen
            else -> ExpenseRed
        }
    }
    val prefix = remember(isIncome, isTransfer, isIncomingTransfer) {
        when {
            isTransfer -> if (isIncomingTransfer) "+ " else "- "
            isIncome -> "+"
            else -> "-"
        }
    }
    val formattedAmount = remember(transaction.amount) { CurrencyFormatter.format(transaction.amount) }
    val formattedDate = remember(transaction.date) { DateFormatter.format(transaction.date) }
    val displayTitle = remember(transaction.description, transaction.category.name) {
        transaction.description?.takeIf { it.isNotBlank() } ?: transaction.category.name
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, SoftGreyBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icono de la categoría
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isTransfer) Color(0xFF2563EB).copy(alpha = 0.15f) else LightBlueContainer.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = IconHelper.getIcon(if (isTransfer) "swap_horiz" else transaction.category.iconKey),
                    contentDescription = transaction.category.name,
                    tint = if (isTransfer) Color(0xFF2563EB) else OnBlueContainer,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Información (Título y categoría)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = DarkCharcoal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                val subtitle = if (isTransfer) {
                    if (isIncomingTransfer) "Transferencia Entrante • $formattedDate" else "Transferencia Saliente • $formattedDate"
                } else {
                    "${transaction.category.name} • $formattedDate"
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = NeutralVariant
                )
            }

            // Monto de la transacción
            Text(
                text = "$prefix$formattedAmount",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = valueColor
            )
        }
    }
}

@Composable
fun BudgetAlertsSection(
    budgetStatuses: List<BudgetStatus>,
    modifier: Modifier = Modifier
) {
    val exceededBudgets = remember(budgetStatuses) { budgetStatuses.filter { it.isExceeded } }
    val warningBudgets = remember(budgetStatuses) { budgetStatuses.filter { !it.isExceeded && it.percentage >= 0.8f } }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Banner de Alerta Visual para Presupuestos Excedidos
        if (exceededBudgets.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Advertencia de presupuesto superado",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "¡Alerta de Presupuesto!",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        val exceededNames = remember(exceededBudgets) { exceededBudgets.joinToString(", ") { it.categoryName } }
                        Text(
                            text = "Has superado el límite mensual en: $exceededNames. Intenta reducir tus gastos en estas categorías.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        } else if (warningBudgets.isNotEmpty()) {
            // Banner de Advertencia de Consumo Alto
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFFFF9C4) // Color de fondo amarillo claro
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFBC02D).copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = "Advertencia de consumo alto",
                            tint = Color(0xFFF57F17),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Presupuestos en Riesgo",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF5D4037)
                        )
                        val riskNames = remember(warningBudgets) { warningBudgets.joinToString(", ") { it.categoryName } }
                        Text(
                            text = "Has consumido más del 80% de tu presupuesto mensual en: $riskNames.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF5D4037).copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }

        // Tarjeta contenedora con la lista de presupuestos y sus barras de progreso
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Control de Presupuestos",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                budgetStatuses.forEach { status ->
                    key(status.categoryId) {
                        val errorColor = MaterialTheme.colorScheme.error
                        val primaryColor = MaterialTheme.colorScheme.primary

                        val progressColor = remember(status.isExceeded, status.percentage, errorColor, primaryColor) {
                            if (status.isExceeded) {
                                errorColor
                            } else if (status.percentage >= 0.8f) {
                                Color(0xFFF57F17) // Naranja de advertencia
                            } else {
                                primaryColor
                            }
                        }

                        val availableText = remember(status.isExceeded, status.spent, status.limit) {
                            if (status.isExceeded) {
                                "¡Excedido por ${CurrencyFormatter.format(status.spent - status.limit)}!"
                            } else {
                                "Disponible: ${CurrencyFormatter.format(status.limit - status.spent)}"
                            }
                        }

                        val formattedSpentLimit = remember(status.spent, status.limit) {
                            "${CurrencyFormatter.format(status.spent)} / ${CurrencyFormatter.format(status.limit)}"
                        }

                        val percentageText = remember(status.percentage) {
                            "${(status.percentage * 100).toInt()}% consumido"
                        }

                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(progressColor.copy(alpha = 0.1f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = IconHelper.getIcon(status.iconKey),
                                            contentDescription = null,
                                            tint = progressColor,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = status.categoryName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = availableText,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (status.isExceeded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = formattedSpentLimit,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (status.isExceeded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = percentageText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (status.isExceeded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            LinearProgressIndicator(
                                progress = { status.percentage.coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = progressColor,
                                trackColor = progressColor.copy(alpha = 0.15f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WalletWidget(
    accounts: List<Account>,
    onNavigateToAccounts: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountBalanceWallet,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Mis Cuentas",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                TextButton(
                    onClick = onNavigateToAccounts,
                    modifier = Modifier.testTag("dashboard_manage_accounts_button")
                ) {
                    Text("Gestionar")
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            
            if (accounts.isEmpty()) {
                Card(
                    onClick = onNavigateToAccounts,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dashboard_empty_accounts_card"),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Aún no tienes cuentas registradas",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Agrega cuentas (Efectivo, Banco, etc.) para organizar tus finanzas.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Button(
                            onClick = onNavigateToAccounts,
                            modifier = Modifier.testTag("dashboard_add_first_account_btn")
                        ) {
                            Text("Configurar Cuentas")
                        }
                    }
                }
            } else {
                // Fila de tarjetas de cuenta miniatura
                androidx.compose.foundation.lazy.LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(accounts, key = { it.id }) { account ->
                        val accountColor = remember(account.colorHex) {
                            try {
                                Color(android.graphics.Color.parseColor(account.colorHex))
                            } catch (e: Exception) {
                                Color(0xFF0072C6)
                            }
                        }
                        
                        val typeLabel = remember(account.type) {
                            when (account.type) {
                                "CASH" -> "Efectivo"
                                "BANK" -> "Banco"
                                "DEBIT" -> "Débito"
                                "CREDIT" -> "Crédito"
                                else -> "Wallet"
                            }
                        }

                        val formattedBalance = remember(account.currentBalance) {
                            CurrencyFormatter.format(account.currentBalance)
                        }
                        
                        Card(
                            onClick = onNavigateToAccounts,
                            modifier = Modifier
                                .width(160.dp)
                                .testTag("dashboard_account_chip_${account.id}"),
                            colors = CardDefaults.cardColors(
                                containerColor = accountColor.copy(alpha = 0.08f)
                            ),
                            border = BorderStroke(1.dp, accountColor.copy(alpha = 0.25f))
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = account.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = typeLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = formattedBalance,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (account.currentBalance >= 0) accountColor else MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
