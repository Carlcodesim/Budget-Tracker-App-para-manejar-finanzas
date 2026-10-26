package com.example.presentation.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Category as CategoryIcon
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.ui.text.style.TextAlign
import androidx.activity.compose.BackHandler
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.Category
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import com.example.presentation.common.util.CsvExporter
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Share
import com.example.presentation.common.util.CurrencyFormatter
import com.example.presentation.common.util.GoogleWalletManager
import com.example.presentation.common.components.AddToGoogleWalletButton
import com.example.presentation.common.components.GoogleWalletPassCard
import com.example.presentation.common.components.GoogleWalletPassModalDialog
import com.example.presentation.common.components.GoogleWalletLogoIcon
import com.example.presentation.common.components.ConfirmDeleteDialog
import com.example.presentation.common.util.IconHelper
import com.example.presentation.common.util.BackupManager
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Check
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.ui.theme.IncomeGreen
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.BluePrimary
import com.example.ui.theme.SoftGreyBorder
import androidx.compose.ui.platform.testTag

import androidx.lifecycle.compose.collectAsStateWithLifecycle

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

private data class ThemeOption(
    val mode: com.example.ui.theme.ThemeMode,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateToAccounts: () -> Unit,
    onNavigateToReminders: () -> Unit,
    onNavigateToCategories: () -> Unit = {},
    onNavigateToRecurringExpenses: () -> Unit = {},
    onNavigateBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onNavigateBack)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val themeManager = remember { (context.applicationContext as com.example.BudgetTrackerApp).appContainer.themeManager }
    val themeMode by themeManager.themeMode.collectAsStateWithLifecycle()

    var showPinDialog by remember { mutableStateOf(false) }
    var showWalletDialog by remember { mutableStateOf(false) }
    var walletIssuerId by remember { mutableStateOf(GoogleWalletManager.getIssuerId(context) ?: "") }
    val currentCurrencyOption by CurrencyFormatter.currencyOptionFlow.collectAsStateWithLifecycle()
    var pinEnabledState by remember { mutableStateOf(com.example.presentation.common.util.SecurityManager.isPinEnabled(context)) }
    var hasSetPin by remember { mutableStateOf(com.example.presentation.common.util.SecurityManager.getPin(context) != null) }

    val cycleStartDay by com.example.util.BudgetCycleManager.startDayFlow.collectAsStateWithLifecycle()
    val currentCycleRange = remember(cycleStartDay) {
        com.example.util.BudgetCycleManager.getCurrentCycleRange(cycleStartDay)
    }
    var showCycleDayDialog by remember { mutableStateOf(false) }

    var hasNotificationPermission by remember {
        mutableStateOf(
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationPermission = isGranted
        if (isGranted) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Acceso a notificaciones activado")
            }
        }
    }

    var monthlyPromptEnabled by remember { mutableStateOf(BackupManager.isMonthlyPromptEnabled(context)) }
    var lastBackupTimestamp by remember { mutableLongStateOf(BackupManager.getLastBackupTimestamp(context)) }

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
                    lastBackupTimestamp = BackupManager.getLastBackupTimestamp(context)
                    snackbarHostState.showSnackbar("¡Copia de seguridad completa (JSON) exportada con éxito!")
                } catch (e: Exception) {
                    snackbarHostState.showSnackbar("Error al crear copia de seguridad: ${e.localizedMessage}")
                }
            }
        }
    }

    val importJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val jsonString = inputStream?.bufferedReader()?.use { it.readText() }
                    if (!jsonString.isNullOrEmpty()) {
                        val db = (context.applicationContext as com.example.BudgetTrackerApp).appContainer.database
                        val count = BackupManager.importBackupFromJson(context, db, jsonString)
                        lastBackupTimestamp = BackupManager.getLastBackupTimestamp(context)
                        snackbarHostState.showSnackbar("¡Restauración exitosa! Se restauraron $count registros.")
                    } else {
                        snackbarHostState.showSnackbar("El archivo seleccionado está vacío.")
                    }
                } catch (e: Exception) {
                    snackbarHostState.showSnackbar("Error al restaurar: ${e.localizedMessage}")
                }
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val transactions = viewModel.getTransactionsForExport()
                    val csvData = CsvExporter.generateTransactionsCsv(transactions)
                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        outputStream.write(csvData.toByteArray(Charsets.UTF_8))
                    }
                    snackbarHostState.showSnackbar("¡Historial de transacciones exportado en CSV con éxito!")
                } catch (e: Exception) {
                    snackbarHostState.showSnackbar("Error al exportar: ${e.localizedMessage}")
                }
            }
        }
    }

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
        topBar = {
            TopAppBar(
                title = { Text("Configuración", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("settings_back_to_home_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver al inicio"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
            // Sección: Categorías de Gastos e Ingresos
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToCategories() }
                    .testTag("settings_categories_card")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CategoryIcon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Categorías",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (state.categories.isNotEmpty()) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                    ) {
                                        Text(
                                            text = "${state.categories.size} activas",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Personaliza tus categorías, límites de gasto mensual, colores e iconos.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Ir a Categorías",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Preview chips de categorías principales
                    if (state.categories.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            state.categories.take(3).forEach { cat ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(parseHexColor(cat.colorHex))
                                        )
                                        Text(
                                            text = cat.name,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                            if (state.categories.size > 3) {
                                Text(
                                    text = "+${state.categories.size - 3} más",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            // Sección: Cuentas Financieras
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToAccounts() }
                    .testTag("settings_accounts_card")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountBalanceWallet,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Gestionar Cuentas",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Configura tus cuentas de banco, efectivo, tarjetas de débito o crédito para un control preciso de saldos.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Ir a Gestionar Cuentas",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Sección: Recordatorios de Pago y Alertas
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToReminders() }
                    .testTag("settings_reminders_card")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Recordatorios de Pago",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Configura y administra tus fechas de pago para luz, internet, tarjetas de crédito, préstamos y aportes.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Ir a Recordatorios de Pago",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Sección: Gastos Recurrentes y Suscripciones
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToRecurringExpenses() }
                    .testTag("settings_recurring_card")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Repeat,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Gastos Recurrentes y Suscripciones",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Configura suscripciones mensuales automáticas (Netflix, Spotify, alquiler, internet) para que se registren solas.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Ir a Gastos Recurrentes",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Sección: Ciclo Financiero y Día de Corte de Sueldo
            BudgetCycleSettingsCard(
                currentStartDay = cycleStartDay,
                currentRangeLabel = currentCycleRange.displayTitle,
                dateRange = currentCycleRange.dateRangeLabel,
                onSelectStartDay = { day ->
                    com.example.util.BudgetCycleManager.setStartDay(context, day)
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(
                            if (day == 1) "Ciclo configurado: Mes calendario estándar (1 al fin de mes)"
                            else "Ciclo configurado: Inicia cada día $day de mes (adaptado a tu cobro)"
                        )
                    }
                },
                onOpenCustomDayDialog = { showCycleDayDialog = true }
            )

            // Sección: Preferencias del Sistema (Tema, Moneda, PIN)
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Preferencias del Sistema",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    // 1. Selector de Moneda
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Moneda principal", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Text("Formato de valores monetarios en toda la app", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        
                        var currencyExpanded by remember { mutableStateOf(false) }
                        Box {
                            Button(
                                onClick = { currencyExpanded = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("settings_currency_selector_button")
                            ) {
                                Text("${currentCurrencyOption.flag} ${currentCurrencyOption.code} (${currentCurrencyOption.symbol})")
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                            
                            DropdownMenu(
                                expanded = currencyExpanded,
                                onDismissRequest = { currencyExpanded = false }
                            ) {
                                CurrencyFormatter.availableCurrencies.forEach { option ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(option.flag, style = MaterialTheme.typography.titleMedium)
                                                Column {
                                                    Text(
                                                        "${option.code} • ${option.symbol}",
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Text(
                                                        option.name,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        },
                                        onClick = {
                                            CurrencyFormatter.setCurrency(context, option.code)
                                            currencyExpanded = false
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar("Moneda cambiada a ${option.flag} ${option.name} (${option.code})")
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // 2. Selector de Tema (Modo Claro / Oscuro / Sistema)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("theme_selection_section"),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column {
                            Text(
                                text = "Tema visual",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Elige la apariencia de la aplicación. Se guardará tu preferencia.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Selector Segmentado de Tema
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val options = listOf(
                                ThemeOption(com.example.ui.theme.ThemeMode.LIGHT, "Claro", Icons.Default.WbSunny),
                                ThemeOption(com.example.ui.theme.ThemeMode.DARK, "Oscuro", Icons.Default.NightsStay),
                                ThemeOption(com.example.ui.theme.ThemeMode.SYSTEM, "Sistema", Icons.Default.PhoneAndroid)
                            )

                            options.forEach { option ->
                                val isSelected = themeMode == option.mode
                                Surface(
                                    onClick = { themeManager.setThemeMode(option.mode) },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                    contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(42.dp)
                                        .testTag("theme_option_${option.mode.name.lowercase(java.util.Locale.ROOT)}")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 8.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = option.icon,
                                            contentDescription = option.label,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = option.label,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // 3. Seguridad con PIN
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Bloqueo con PIN", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                Text("Proteger con un PIN al abrir la app", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }

                            Switch(
                                checked = pinEnabledState,
                                modifier = Modifier.testTag("pin_lock_switch"),
                                onCheckedChange = { checked ->
                                    if (checked && !hasSetPin) {
                                        showPinDialog = true
                                    } else {
                                        com.example.presentation.common.util.SecurityManager.setPinEnabled(context, checked)
                                        pinEnabledState = checked
                                    }
                                }
                            )
                        }

                        if (pinEnabledState && hasSetPin) {
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(
                                onClick = { showPinDialog = true },
                                modifier = Modifier.align(Alignment.End).testTag("change_pin_button")
                            ) {
                                Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Cambiar PIN de seguridad", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }

            // Sección: Notificaciones y Recordatorios Programados
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth().testTag("settings_notifications_card")
            ) {
                var dailyReminderEnabled by remember {
                    mutableStateOf(com.example.util.DailyReminderManager.isDailyReminderEnabled(context))
                }

                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.NotificationsActive,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Text(
                                text = "Notificaciones y Recordatorios",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Switch Recordatorio diario de fin de jornada
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Recordatorio diario de fin de jornada",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Te avisa a las 8:30 PM si no has registrado gastos hoy",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Switch(
                            checked = dailyReminderEnabled,
                            onCheckedChange = { checked ->
                                if (checked && !hasNotificationPermission && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                    notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                }
                                com.example.util.DailyReminderManager.setDailyReminderEnabled(context, checked)
                                dailyReminderEnabled = checked
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(
                                        if (checked) "Recordatorio diario activado (8:30 PM)"
                                        else "Recordatorio diario desactivado"
                                    )
                                }
                            },
                            modifier = Modifier.testTag("daily_reminder_switch")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (!hasNotificationPermission && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                    notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                }
                                com.example.util.NotificationScheduler.showDailyExpenseReminderNotification(context)
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Notificación de prueba enviada")
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Probar Alerta", style = MaterialTheme.typography.labelMedium)
                        }

                        Button(
                            onClick = onNavigateToReminders,
                            modifier = Modifier.weight(1.2f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Ver Recordatorios", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            // Sección: Google Wallet Integration y Pases Digitales
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth().testTag("settings_google_wallet_card")
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            GoogleWalletLogoIcon(modifier = Modifier.size(28.dp))
                            Column {
                                Text(
                                    text = "Google Wallet",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Pases digitales y enlaces",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        val isWalletInstalled = remember { GoogleWalletManager.isWalletInstalled(context) }
                        Surface(
                            shape = CircleShape,
                            color = if (isWalletInstalled) IncomeGreen.copy(alpha = 0.15f) else MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = if (isWalletInstalled) "App Instalada" else "Disponible",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isWalletInstalled) IncomeGreen else MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp).padding(top = 2.dp)
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "Cómo funciona la integración",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Google Wallet requiere una cuenta de emisor (Issuer ID) en Google Pay & Wallet Console para emitir pases firmados en la app Wallet del dispositivo. Puedes ingresar tu Issuer ID aquí o utilizar las opciones de exportación y ficha digital.",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Configuración de Issuer ID
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "ID de Emisor (Google Pay Console)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = walletIssuerId,
                                onValueChange = { walletIssuerId = it },
                                placeholder = { Text("Ej. 3388000000022345678", fontSize = 12.sp) },
                                modifier = Modifier.weight(1f).testTag("settings_wallet_issuer_input"),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )
                            Button(
                                onClick = {
                                    GoogleWalletManager.setIssuerId(context, walletIssuerId)
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(
                                            if (walletIssuerId.isNotBlank()) "✅ Issuer ID guardado con éxito"
                                            else "Issuer ID restablecido"
                                        )
                                    }
                                },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Guardar")
                            }
                        }
                    }

                    // Pass Preview Card
                    val budgetToUse = if (state.totalMonthlyBudget > 0) state.totalMonthlyBudget else 2500.0
                    val spentToUse = state.totalMonthlySpent

                    GoogleWalletPassCard(
                        title = "Presupuesto Mensual",
                        subtitle = "Estado de Gastos del Mes",
                        amountSpent = spentToUse,
                        amountLimit = budgetToUse,
                        onAddToWalletClick = {
                            showWalletDialog = true
                        }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                GoogleWalletManager.shareBudgetPass(context, budgetToUse, spentToUse)
                            },
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Compartir", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }

                        OutlinedButton(
                            onClick = { GoogleWalletManager.openGoogleWallet(context) },
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            GoogleWalletLogoIcon(modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Abrir App", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            if (showWalletDialog) {
                val budgetToUse = if (state.totalMonthlyBudget > 0) state.totalMonthlyBudget else 2500.0
                val spentToUse = state.totalMonthlySpent
                GoogleWalletPassModalDialog(
                    title = "Presupuesto Mensual",
                    subtitle = "Control de Gastos del Mes",
                    amountSpent = spentToUse,
                    amountLimit = budgetToUse,
                    onDismissRequest = { showWalletDialog = false },
                    onAddToWallet = {
                        showWalletDialog = false
                    },
                    onSharePass = {
                        GoogleWalletManager.shareBudgetPass(context, budgetToUse, spentToUse)
                    },
                    onOpenWalletApp = {
                        GoogleWalletManager.openGoogleWallet(context)
                    }
                )
            }

            // Sección: Copia de Seguridad y Restauración
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_backup_card")
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Copia de Seguridad y Restauración",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Icon(
                            imageVector = Icons.Default.Backup,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Estado del último respaldo
                    val lastBackupText = if (lastBackupTimestamp > 0L) {
                        val formatted = SimpleDateFormat("dd 'de' MMMM 'de' yyyy, hh:mm a", Locale.getDefault()).format(Date(lastBackupTimestamp))
                        "Última copia realizada: $formatted"
                    } else {
                        "Sin copias de seguridad realizadas aún"
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = lastBackupText,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    // Interruptor para Recordatorio Mensual de Copia de Seguridad
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Recordatorio mensual de respaldo",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Muestra un aviso en el inicio cada mes para recordarte guardar una copia de seguridad manual.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Switch(
                            checked = monthlyPromptEnabled,
                            modifier = Modifier.testTag("monthly_backup_prompt_switch"),
                            onCheckedChange = { checked ->
                                BackupManager.setMonthlyPromptEnabled(context, checked)
                                monthlyPromptEnabled = checked
                                coroutineScope.launch {
                                    val msg = if (checked) "Aviso mensual de respaldo activado" else "Aviso mensual desactivado"
                                    snackbarHostState.showSnackbar(msg)
                                }
                            }
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    Text(
                        text = "Genera un respaldo completo de tus cuentas, categorías, transacciones, metas y recordatorios en un archivo JSON o exporta tu historial en CSV.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Botón 1: Crear copia de seguridad completa (JSON)
                    Button(
                        onClick = {
                            try {
                                val dateStr = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
                                exportJsonLauncher.launch("respaldo_presupuesto_$dateStr.json")
                            } catch (e: Exception) {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Error al iniciar exportación: ${e.localizedMessage}")
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("export_json_backup_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.FileUpload, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Crear Respaldo Completo (JSON)", fontWeight = FontWeight.Bold)
                    }

                    // Botón 2: Restaurar datos desde archivo JSON
                    OutlinedButton(
                        onClick = {
                            try {
                                importJsonLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                            } catch (e: Exception) {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Error al abrir selector de archivos: ${e.localizedMessage}")
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("import_json_backup_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.FileDownload, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Restaurar Datos desde Respaldo (JSON)", fontWeight = FontWeight.Bold)
                    }

                    // Botón 3: Exportar Historial en CSV
                    TextButton(
                        onClick = {
                            try {
                                exportLauncher.launch("backups_transacciones.csv")
                            } catch (e: Exception) {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Error al iniciar exportación CSV: ${e.localizedMessage}")
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("export_csv_button")
                    ) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Exportar Historial en Formato CSV (Excel)")
                    }
                }
            }
        }
    }
}

    if (showPinDialog) {
        PinSetupDialog(
            onDismiss = { showPinDialog = false },
            onSavePin = { pin ->
                com.example.presentation.common.util.SecurityManager.setPin(context, pin)
                com.example.presentation.common.util.SecurityManager.setPinEnabled(context, true)
                pinEnabledState = true
                hasSetPin = true
                showPinDialog = false
            }
        )
    }

    if (showCycleDayDialog) {
        CycleStartDayDialog(
            currentDay = cycleStartDay,
            onDismiss = { showCycleDayDialog = false },
            onConfirm = { newDay ->
                com.example.util.BudgetCycleManager.setStartDay(context, newDay)
                showCycleDayDialog = false
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(
                        if (newDay == 1) "Ciclo configurado: Mes calendario estándar"
                        else "Ciclo configurado: Inicia cada día $newDay de cada mes"
                    )
                }
            }
        )
    }
}

@Composable
fun PinSetupDialog(
    onDismiss: () -> Unit,
    onSavePin: (String) -> Unit
) {
    var pinValue by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Establecer PIN de Seguridad",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Introduce un PIN de 4 dígitos para proteger tu información financiera al abrir la aplicación.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = pinValue,
                    onValueChange = {
                        if (it.length <= 4 && it.all { char -> char.isDigit() }) {
                            pinValue = it
                            pinError = null
                        }
                    },
                    label = { Text("PIN de 4 dígitos") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("settings_pin_setup_input")
                )

                if (pinError != null) {
                    Text(
                        text = pinError!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (pinValue.length == 4) {
                        onSavePin(pinValue)
                    } else {
                        pinError = "El PIN debe tener exactamente 4 dígitos"
                    }
                }
            ) {
                Text("Guardar")
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
private fun BudgetCycleSettingsCard(
    currentStartDay: Int,
    currentRangeLabel: String,
    dateRange: String,
    onSelectStartDay: (Int) -> Unit,
    onOpenCustomDayDialog: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("settings_budget_cycle_card")
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DateRange,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Ciclo Financiero y Día de Cobro",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Sincroniza tus reportes y presupuestos con la fecha en que cobras tu sueldo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Banner explicativo del desfase 15 y 30
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(20.dp)
                            .padding(top = 2.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text = "Alineación quincenal y de fin de mes",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Si cobras los días 15 y 30, configurar el día 30 agrupa el sueldo de fin de mes junto a los gastos que cubre en la primera quincena del mes siguiente, resolviendo los falsos déficits contables.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f)
                        )
                    }
                }
            }

            // Estado del ciclo actual
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Período activo en curso",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = currentRangeLabel,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = dateRange,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Opciones predefinidas de inicio de ciclo
            Text(
                text = "Día de inicio del ciclo contable:",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val presets = listOf(
                    1 to "Día 1\n(Mes cal.)",
                    15 to "Día 15\n(Quincena)",
                    30 to "Día 30\n(Fin de mes)",
                    25 to "Día 25\n(Habitual)"
                )
                presets.forEach { (day, label) ->
                    val isSelected = currentStartDay == day
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = BorderStroke(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onSelectStartDay(day) }
                            .testTag("settings_cycle_preset_$day")
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                textAlign = TextAlign.Center,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Botón para personalizar a cualquier día del 1 al 31
            OutlinedButton(
                onClick = onOpenCustomDayDialog,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_custom_cycle_day_button")
            ) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (currentStartDay !in listOf(1, 15, 25, 30)) {
                        "Día personalizado activo: Día $currentStartDay (Cambiar)"
                    } else {
                        "Elegir otro día específico (1 a 31)..."
                    }
                )
            }
        }
    }
}

@Composable
private fun CycleStartDayDialog(
    currentDay: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var selectedDay by remember { mutableIntStateOf(currentDay) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Personalizar Día de Corte",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Selecciona el día del mes en que entra tu sueldo o inicia tu ciclo presupuestario:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 32.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Día $selectedDay",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = if (selectedDay == 1) "Mes calendario tradicional (1 al fin de mes)" else "El ciclo iniciará cada día $selectedDay",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }

                Slider(
                    value = selectedDay.toFloat(),
                    onValueChange = { selectedDay = it.toInt().coerceIn(1, 31) },
                    valueRange = 1f..31f,
                    steps = 29,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("cycle_day_slider")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Día 1", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Día 15", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Día 31", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(selectedDay) }) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
