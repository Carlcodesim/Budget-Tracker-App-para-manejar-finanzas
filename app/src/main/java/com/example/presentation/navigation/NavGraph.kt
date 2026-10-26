package com.example.presentation.navigation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Category as CategoryIcon
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.BudgetTrackerApp
import com.example.domain.model.Account
import com.example.domain.model.Category
import com.example.domain.model.Transaction
import com.example.domain.model.TransactionType
import com.example.presentation.accounts.AccountsScreen
import com.example.presentation.accounts.AccountsViewModel
import com.example.presentation.categories.CategoriesScreen
import com.example.presentation.categories.CategoriesViewModel
import com.example.presentation.analysis.AnalysisScreen
import com.example.presentation.analysis.AnalysisViewModel
import com.example.presentation.assistant.AssistantScreen
import com.example.presentation.assistant.AssistantViewModel
import com.example.presentation.calculator.CalculatorScreen
import com.example.presentation.calculator.CalculatorViewModel
import com.example.presentation.common.components.RapidExpenseDialog
import com.example.presentation.common.util.ViewModelFactory
import com.example.presentation.dashboard.DashboardScreen
import com.example.presentation.dashboard.DashboardViewModel
import com.example.presentation.goals.form.GoalFormScreen
import com.example.presentation.goals.form.GoalFormViewModel
import com.example.presentation.goals.list.GoalsScreen
import com.example.presentation.goals.list.GoalsViewModel
import com.example.presentation.monthlysummary.MonthlySummaryScreen
import com.example.presentation.monthlysummary.MonthlySummaryViewModel
import com.example.presentation.recurring.RecurringExpensesScreen
import com.example.presentation.recurring.RecurringExpensesViewModel
import com.example.presentation.reminders.RemindersScreen
import com.example.presentation.reminders.RemindersViewModel
import com.example.presentation.settings.SettingsScreen
import com.example.presentation.settings.SettingsViewModel
import com.example.presentation.store.StoreScreen
import com.example.presentation.store.StoreViewModel
import com.example.presentation.transactions.form.TransactionFormScreen
import com.example.presentation.transactions.form.TransactionFormViewModel
import com.example.presentation.transactions.list.TransactionsScreen
import com.example.presentation.transactions.list.TransactionsViewModel
import com.example.ui.theme.BottomBarBackground
import com.example.ui.theme.ExpenseRed
import android.widget.Toast
import java.util.Date
import kotlinx.coroutines.launch

/**
 * Grafo de navegación principal para BudgetTracker.
 * Gestiona el paso de parámetros seguros, el ciclo de vida de los ViewModels y diseño adaptativo (NavigationRail en pantallas anchas y NavigationBar en móviles).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation(
    modifier: Modifier = Modifier,
    initialDestination: String? = null,
    initialType: String? = null
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val appContainer = remember { (context.applicationContext as BudgetTrackerApp).appContainer }
    val factory = remember { ViewModelFactory(appContainer) }

    LaunchedEffect(initialDestination) {
        if (initialDestination != null) {
            when (initialDestination) {
                "ADD_TRANSACTION", "ADD_EXPENSE" -> {
                    navController.navigate(Screen.TransactionForm.createRoute(initialType = initialType ?: "EXPENSE"))
                }
                "SCAN_RECEIPT" -> {
                    navController.navigate(Screen.TransactionForm.createRoute(initialType = "EXPENSE", autoScan = true))
                }
                "REMINDERS" -> {
                    navController.navigate(Screen.Reminders.route)
                }
                "RECURRING_EXPENSES" -> {
                    navController.navigate(Screen.RecurringExpenses.route)
                }
                "ANALYSIS" -> {
                    navController.navigate(Screen.Analysis.route)
                }
            }
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val coroutineScope = rememberCoroutineScope()
    var showMoreSheet by remember { mutableStateOf(false) }

    val secondaryRoutes = listOf(
        Screen.Goals.route,
        Screen.Calculator.route,
        Screen.Settings.route,
        Screen.Reminders.route,
        Screen.Accounts.route,
        Screen.Assistant.route,
        Screen.MonthlySummary.route,
        Screen.RecurringExpenses.route,
        Screen.Categories.route
    )

    // Mostrar barra inferior o rail para las vistas principales y secundarias
    val showNavigation = currentRoute in listOf(
        Screen.Dashboard.route,
        Screen.Analysis.route,
        Screen.Transactions.route,
        Screen.Calculator.route,
        Screen.Store.route,
        Screen.Goals.route,
        Screen.Settings.route,
        Screen.Reminders.route,
        Screen.Accounts.route,
        Screen.Assistant.route,
        Screen.MonthlySummary.route,
        Screen.RecurringExpenses.route,
        Screen.Categories.route
    )

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isExpandedScreen = maxWidth >= 600.dp

        if (isExpandedScreen) {
            Row(modifier = Modifier.fillMaxSize()) {
                if (showNavigation) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        header = {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(vertical = 12.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Wallet,
                                            contentDescription = "BudgetTracker",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        Spacer(modifier = Modifier.height(8.dp))
                        // 1. Dashboard Tab
                        NavigationRailItem(
                            selected = currentRoute == Screen.Dashboard.route,
                            onClick = {
                                navController.navigate(Screen.Dashboard.route) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (currentRoute == Screen.Dashboard.route) Icons.Filled.Dashboard else Icons.Outlined.Dashboard,
                                    contentDescription = "Inicio"
                                )
                            },
                            label = { Text("Inicio") }
                        )

                        // 2. Movimientos Tab
                        NavigationRailItem(
                            selected = currentRoute == Screen.Transactions.route,
                            onClick = {
                                navController.navigate(Screen.Transactions.route) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (currentRoute == Screen.Transactions.route) Icons.Filled.History else Icons.Outlined.History,
                                    contentDescription = "Movimientos"
                                )
                            },
                            label = { Text("Movimientos") }
                        )

                        // 3. Análisis Tab
                        NavigationRailItem(
                            selected = currentRoute == Screen.Analysis.route,
                            onClick = {
                                navController.navigate(Screen.Analysis.route) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (currentRoute == Screen.Analysis.route) Icons.Filled.Analytics else Icons.Outlined.Analytics,
                                    contentDescription = "Análisis"
                                )
                            },
                            label = { Text("Análisis") }
                        )

                        // 4. Negocio Tab (INSERC Store)
                        NavigationRailItem(
                            selected = currentRoute == Screen.Store.route,
                            onClick = {
                                navController.navigate(Screen.Store.route) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (currentRoute == Screen.Store.route) Icons.Filled.Storefront else Icons.Outlined.Storefront,
                                    contentDescription = "Negocio"
                                )
                            },
                            label = { Text("Negocio") }
                        )

                        // 5. Más Tab
                        NavigationRailItem(
                            selected = currentRoute in secondaryRoutes,
                            onClick = { showMoreSheet = true },
                            icon = {
                                Icon(
                                    imageVector = if (currentRoute in secondaryRoutes) Icons.Filled.MoreVert else Icons.Outlined.MoreVert,
                                    contentDescription = "Más"
                                )
                            },
                            label = { Text("Más") }
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    AppNavHost(
                        navController = navController,
                        factory = factory,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        } else {
            Scaffold(
                bottomBar = {
                    if (showNavigation) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 6.dp
                        ) {
                            val navItemColors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // 1. Dashboard Tab
                            NavigationBarItem(
                                selected = currentRoute == Screen.Dashboard.route,
                                colors = navItemColors,
                                onClick = {
                                    navController.navigate(Screen.Dashboard.route) {
                                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (currentRoute == Screen.Dashboard.route) Icons.Filled.Dashboard else Icons.Outlined.Dashboard,
                                        contentDescription = "Inicio"
                                    )
                                },
                                label = { Text("Inicio") }
                            )

                            // 2. Movimientos Tab
                            NavigationBarItem(
                                selected = currentRoute == Screen.Transactions.route,
                                colors = navItemColors,
                                onClick = {
                                    navController.navigate(Screen.Transactions.route) {
                                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (currentRoute == Screen.Transactions.route) Icons.Filled.History else Icons.Outlined.History,
                                        contentDescription = "Movimientos"
                                    )
                                },
                                label = { Text("Movimientos") }
                            )

                            // 3. Análisis Tab
                            NavigationBarItem(
                                selected = currentRoute == Screen.Analysis.route,
                                colors = navItemColors,
                                onClick = {
                                    navController.navigate(Screen.Analysis.route) {
                                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (currentRoute == Screen.Analysis.route) Icons.Filled.Analytics else Icons.Outlined.Analytics,
                                        contentDescription = "Análisis"
                                    )
                                },
                                label = { Text("Análisis") }
                            )

                            // 4. Negocio Tab (INSERC Store)
                            NavigationBarItem(
                                selected = currentRoute == Screen.Store.route,
                                colors = navItemColors,
                                onClick = {
                                    navController.navigate(Screen.Store.route) {
                                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (currentRoute == Screen.Store.route) Icons.Filled.Storefront else Icons.Outlined.Storefront,
                                        contentDescription = "Negocio"
                                    )
                                },
                                label = { Text("Negocio") }
                            )

                            // 5. Más Tab
                            NavigationBarItem(
                                selected = currentRoute in secondaryRoutes,
                                colors = navItemColors,
                                onClick = { showMoreSheet = true },
                                icon = {
                                    Icon(
                                        imageVector = if (currentRoute in secondaryRoutes) Icons.Filled.MoreVert else Icons.Outlined.MoreVert,
                                        contentDescription = "Más"
                                    )
                                },
                                label = { Text("Más") }
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            ) { innerPadding ->
                AppNavHost(
                    navController = navController,
                    factory = factory,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                )
            }
        }
    }

    if (showMoreSheet) {
        ModalBottomSheet(
            onDismissRequest = { showMoreSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 640.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Menú y Herramientas",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Acceso rápido a todas las funciones",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        MoreMenuItem(
                            title = "Mis Cuentas",
                            subtitle = "Billetera y bancos",
                            icon = Icons.Default.Wallet,
                            iconContainerColor = Color(0xFF0072C6).copy(alpha = 0.15f),
                            iconTint = Color(0xFF0072C6),
                            modifier = Modifier.weight(1f),
                            onClick = {
                                showMoreSheet = false
                                navController.navigate(Screen.Accounts.route)
                            }
                        )
                        MoreMenuItem(
                            title = "Metas de Ahorro",
                            subtitle = "Planes de ahorro",
                            icon = Icons.Default.Savings,
                            iconContainerColor = Color(0xFF2ECC71).copy(alpha = 0.15f),
                            iconTint = Color(0xFF2ECC71),
                            modifier = Modifier.weight(1f),
                            onClick = {
                                showMoreSheet = false
                                navController.navigate(Screen.Goals.route)
                            }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        MoreMenuItem(
                            title = "Resumen Mensual",
                            subtitle = "Gastos agregados",
                            icon = Icons.Default.Analytics,
                            iconContainerColor = Color(0xFF06B6D4).copy(alpha = 0.15f),
                            iconTint = Color(0xFF06B6D4),
                            modifier = Modifier.weight(1f),
                            onClick = {
                                showMoreSheet = false
                                navController.navigate(Screen.MonthlySummary.route)
                            }
                        )
                        MoreMenuItem(
                            title = "Categorías",
                            subtitle = "Límites y colores",
                            icon = Icons.Default.CategoryIcon,
                            iconContainerColor = Color(0xFFF59E0B).copy(alpha = 0.15f),
                            iconTint = Color(0xFFF59E0B),
                            modifier = Modifier.weight(1f),
                            onClick = {
                                showMoreSheet = false
                                navController.navigate(Screen.Categories.route)
                            }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        MoreMenuItem(
                            title = "Recordatorios",
                            subtitle = "Alertas de pago",
                            icon = Icons.Default.NotificationsActive,
                            iconContainerColor = Color(0xFF8B5CF6).copy(alpha = 0.15f),
                            iconTint = Color(0xFF8B5CF6),
                            modifier = Modifier.weight(1f),
                            onClick = {
                                showMoreSheet = false
                                navController.navigate(Screen.Reminders.route)
                            }
                        )
                        MoreMenuItem(
                            title = "Asistente AI",
                            subtitle = "Consejero inteligente",
                            icon = Icons.Default.AutoAwesome,
                            iconContainerColor = Color(0xFFEC4899).copy(alpha = 0.15f),
                            iconTint = Color(0xFFEC4899),
                            modifier = Modifier.weight(1f),
                            onClick = {
                                showMoreSheet = false
                                navController.navigate(Screen.Assistant.route)
                            }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        MoreMenuItem(
                            title = "Gastos Recurrentes",
                            subtitle = "Cobros mensuales auto",
                            icon = Icons.Default.Repeat,
                            iconContainerColor = Color(0xFFFF7675).copy(alpha = 0.15f),
                            iconTint = Color(0xFFFF7675),
                            modifier = Modifier.weight(1f),
                            onClick = {
                                showMoreSheet = false
                                navController.navigate(Screen.RecurringExpenses.route)
                            }
                        )
                        MoreMenuItem(
                            title = "Calculadora",
                            subtitle = "Préstamos e interés",
                            icon = Icons.Default.Calculate,
                            iconContainerColor = Color(0xFF6366F1).copy(alpha = 0.15f),
                            iconTint = Color(0xFF6366F1),
                            modifier = Modifier.weight(1f),
                            onClick = {
                                showMoreSheet = false
                                navController.navigate(Screen.Calculator.route)
                            }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        MoreMenuItem(
                            title = "Mi Negocio",
                            subtitle = "Ventas e inventario",
                            icon = Icons.Default.Storefront,
                            iconContainerColor = Color(0xFF10B981).copy(alpha = 0.15f),
                            iconTint = Color(0xFF10B981),
                            modifier = Modifier.weight(1f),
                            onClick = {
                                showMoreSheet = false
                                navController.navigate(Screen.Store.route)
                            }
                        )
                        MoreMenuItem(
                            title = "Ajustes",
                            subtitle = "Preferencias y backup",
                            icon = Icons.Default.Settings,
                            iconContainerColor = Color(0xFF64748B).copy(alpha = 0.15f),
                            iconTint = Color(0xFF64748B),
                            modifier = Modifier.weight(1f),
                            onClick = {
                                showMoreSheet = false
                                navController.navigate(Screen.Settings.route)
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun AppNavHost(
    navController: NavHostController,
    factory: ViewModelFactory,
    modifier: Modifier = Modifier
) {
    val navigateBackOrHome: () -> Unit = {
        if (!navController.popBackStack()) {
            navController.navigate(Screen.Dashboard.route) {
                popUpTo(navController.graph.startDestinationId) { saveState = true }
                launchSingleTop = true
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = Screen.Dashboard.route,
        modifier = modifier
    ) {
        // Pantalla: Dashboard
        composable(Screen.Dashboard.route) {
            val dashboardViewModel: DashboardViewModel = viewModel(factory = factory)
            DashboardScreen(
                viewModel = dashboardViewModel,
                onNavigateToTransactionForm = { type ->
                    navController.navigate(Screen.TransactionForm.createRoute(initialType = type.name))
                },
                onNavigateToTransactions = {
                    navController.navigate(Screen.Transactions.route)
                },
                onNavigateToAnalysis = {
                    navController.navigate(Screen.Analysis.route)
                },
                onNavigateToReminders = {
                    navController.navigate(Screen.Reminders.route)
                },
                onNavigateToAccounts = {
                    navController.navigate(Screen.Accounts.route)
                },
                onNavigateToAssistant = {
                    navController.navigate(Screen.Assistant.route)
                },
                onNavigateToMonthlySummary = {
                    navController.navigate(Screen.MonthlySummary.route)
                },
                onNavigateToRecurringExpenses = {
                    navController.navigate(Screen.RecurringExpenses.route)
                },
                onNavigateToCategories = {
                    navController.navigate(Screen.Categories.route)
                },
                onNavigateToScanReceipt = {
                    navController.navigate(Screen.TransactionForm.createRoute(initialType = "EXPENSE", autoScan = true))
                }
            )
        }

        // Pantalla: Historial de Transacciones
        composable(Screen.Transactions.route) {
            val transactionsViewModel: TransactionsViewModel = viewModel(factory = factory)
            TransactionsScreen(
                viewModel = transactionsViewModel,
                onNavigateToForm = { transactionId ->
                    navController.navigate(Screen.TransactionForm.createRoute(transactionId = transactionId))
                },
                onNavigateToScanReceipt = {
                    navController.navigate(Screen.TransactionForm.createRoute(initialType = "EXPENSE", autoScan = true))
                }
            )
        }

        // Pantalla: Metas de Ahorro
        composable(Screen.Goals.route) {
            val goalsViewModel: GoalsViewModel = viewModel(factory = factory)
            GoalsScreen(
                viewModel = goalsViewModel,
                onNavigateToForm = { goalId ->
                    navController.navigate(Screen.GoalForm.createRoute(goalId = goalId))
                },
                onNavigateBack = navigateBackOrHome,
                onNavigateToAssistant = {
                    navController.navigate(Screen.Assistant.route)
                }
            )
        }

        // Pantalla: Formulario Transacción (Agregar/Editar)
        composable(
            route = Screen.TransactionForm.route,
            arguments = listOf(
                navArgument("transactionId") {
                    type = NavType.StringType
                    nullable = true
                },
                navArgument("initialType") {
                    type = NavType.StringType
                    nullable = true
                },
                navArgument("autoScan") {
                    type = NavType.BoolType
                    defaultValue = false
                }
            )
        ) { backStackEntry ->
            val transactionIdStr = backStackEntry.arguments?.getString("transactionId")
            val transactionId = transactionIdStr?.toLongOrNull()?.takeIf { it > 0 }
            val initialTypeStr = backStackEntry.arguments?.getString("initialType")
            val initialType = initialTypeStr?.let {
                try {
                    TransactionType.valueOf(it)
                } catch (e: Exception) {
                    null
                }
            }
            val autoScan = backStackEntry.arguments?.getBoolean("autoScan") ?: false

            val transactionFormViewModel: TransactionFormViewModel = viewModel(factory = factory)
            TransactionFormScreen(
                viewModel = transactionFormViewModel,
                transactionId = transactionId,
                initialType = initialType,
                autoScan = autoScan,
                onNavigateBack = navigateBackOrHome
            )
        }

        // Pantalla: Formulario Metas (Agregar/Editar)
        composable(
            route = Screen.GoalForm.route,
            arguments = listOf(
                navArgument("goalId") {
                    type = NavType.StringType
                    nullable = true
                }
            )
        ) { backStackEntry ->
            val goalIdStr = backStackEntry.arguments?.getString("goalId")
            val goalId = goalIdStr?.toLongOrNull()?.takeIf { it > 0 }

            val goalFormViewModel: GoalFormViewModel = viewModel(factory = factory)
            GoalFormScreen(
                viewModel = goalFormViewModel,
                goalId = goalId,
                onNavigateBack = navigateBackOrHome
            )
        }

        // Pantalla: Calculadora Financiera
        composable(Screen.Calculator.route) {
            val calculatorViewModel: CalculatorViewModel = viewModel(factory = factory)
            CalculatorScreen(
                viewModel = calculatorViewModel,
                onNavigateBack = navigateBackOrHome
            )
        }

        // Pantalla: INSERC Store (Negocio)
        composable(Screen.Store.route) {
            val storeViewModel: StoreViewModel = viewModel(factory = factory)
            StoreScreen(
                viewModel = storeViewModel,
                onNavigateBack = navigateBackOrHome
            )
        }

        // Pantalla: Configuración / Ajustes
        composable(Screen.Settings.route) {
            val settingsViewModel: SettingsViewModel = viewModel(factory = factory)
            SettingsScreen(
                viewModel = settingsViewModel,
                onNavigateToAccounts = {
                    navController.navigate(Screen.Accounts.route)
                },
                onNavigateToReminders = {
                    navController.navigate(Screen.Reminders.route)
                },
                onNavigateToCategories = {
                    navController.navigate(Screen.Categories.route)
                },
                onNavigateToRecurringExpenses = {
                    navController.navigate(Screen.RecurringExpenses.route)
                },
                onNavigateBack = navigateBackOrHome
            )
        }

        // Pantalla: Análisis Financiero
        composable(Screen.Analysis.route) {
            val analysisViewModel: AnalysisViewModel = viewModel(factory = factory)
            AnalysisScreen(
                viewModel = analysisViewModel,
                onNavigateToAssistant = {
                    navController.navigate(Screen.Assistant.route)
                }
            )
        }

        // Pantalla: Recordatorios Financieros
        composable(Screen.Reminders.route) {
            val remindersViewModel: RemindersViewModel = viewModel(factory = factory)
            RemindersScreen(
                viewModel = remindersViewModel,
                onNavigateBack = navigateBackOrHome
            )
        }

        // Pantalla: Gestión de Cuentas Financieras
        composable(Screen.Accounts.route) {
            val accountsViewModel: AccountsViewModel = viewModel(factory = factory)
            AccountsScreen(
                viewModel = accountsViewModel,
                onNavigateBack = navigateBackOrHome
            )
        }

        // Pantalla: Asistente Financiero Inteligente
        composable(Screen.Assistant.route) {
            val assistantViewModel: AssistantViewModel = viewModel(factory = factory)
            AssistantScreen(
                viewModel = assistantViewModel,
                onNavigateBack = navigateBackOrHome
            )
        }

        // Pantalla: Resumen Mensual de Gastos (Room Aggregations)
        composable(Screen.MonthlySummary.route) {
            val monthlySummaryViewModel: MonthlySummaryViewModel = viewModel(factory = factory)
            MonthlySummaryScreen(
                viewModel = monthlySummaryViewModel,
                onNavigateBack = navigateBackOrHome,
                onNavigateToAddExpense = {
                    navController.navigate(Screen.TransactionForm.createRoute(initialType = "EXPENSE"))
                }
            )
        }

        // Pantalla: Gastos Recurrentes y Cobros Automáticos
        composable(Screen.RecurringExpenses.route) {
            val recurringExpensesViewModel: RecurringExpensesViewModel = viewModel(factory = factory)
            RecurringExpensesScreen(
                viewModel = recurringExpensesViewModel,
                onNavigateBack = navigateBackOrHome
            )
        }

        // Pantalla: Gestión Completa de Categorías de Gastos e Ingresos
        composable(Screen.Categories.route) {
            val categoriesViewModel: CategoriesViewModel = viewModel(factory = factory)
            CategoriesScreen(
                viewModel = categoriesViewModel,
                onNavigateBack = navigateBackOrHome
            )
        }
    }
}

@Composable
private fun MoreMenuItem(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconContainerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    iconTint: Color = MaterialTheme.colorScheme.primary
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = iconContainerColor,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

