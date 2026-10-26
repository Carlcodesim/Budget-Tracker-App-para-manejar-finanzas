package com.example.presentation.reminders

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.core.content.ContextCompat
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.unit.dp
import com.example.domain.model.Reminder
import com.example.domain.model.SavingsGoal
import com.example.domain.model.Category
import com.example.presentation.common.components.LoadingIndicator
import kotlinx.coroutines.launch
import java.util.Calendar

import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindersScreen(
    viewModel: RemindersViewModel,
    onNavigateBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onNavigateBack)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Permission State for Notifications (Android 13+)
    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationPermission = isGranted
        if (isGranted) {
            scope.launch {
                snackbarHostState.showSnackbar("Acceso a notificaciones activado correctamente")
            }
        }
    }

    // Dialog State for Editing / Creating Reminder
    var editingReminder by remember { mutableStateOf<Reminder?>(null) }
    var isCreatingReminder by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Recordatorios de Pago",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("reminders_back_to_home_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver al inicio"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                actions = {
                    IconButton(
                        onClick = { viewModel.resetAllMonthlyReminders(context) },
                        modifier = Modifier.testTag("reset_all_reminders_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reiniciar pagos del mes",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { isCreatingReminder = true },
                icon = { Icon(Icons.Default.Add, contentDescription = "Nuevo Recordatorio") },
                text = { Text("Nuevo Recordatorio", fontWeight = FontWeight.Bold) },
                modifier = Modifier.testTag("add_reminder_fab"),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
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
            when (val currentState = state) {
                is RemindersUiState.Loading -> {
                    LoadingIndicator()
                }
                is RemindersUiState.Success -> {
                    RemindersContent(
                        reminders = currentState.reminders,
                        savingsGoals = currentState.savingsGoals,
                        categories = currentState.categories,
                        hasNotificationPermission = hasNotificationPermission,
                        onRequestPermission = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                        onToggle = { reminder ->
                            if (!hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            viewModel.toggleReminder(reminder, context)
                        },
                        onEditClick = { editingReminder = it },
                        onDeleteClick = { reminder ->
                            viewModel.deleteReminder(reminder, context)
                            scope.launch {
                                val result = snackbarHostState.showSnackbar(
                                    message = "Recordatorio '${reminder.title}' eliminado",
                                    actionLabel = "DESHACER",
                                    duration = SnackbarDuration.Short
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    viewModel.undoDeleteReminder(context)
                                }
                            }
                        },
                        onSimulate = { reminder ->
                            if (!hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            viewModel.triggerNotificationSimulation(reminder, context)
                            scope.launch {
                                snackbarHostState.showSnackbar("Notificación de prueba emitida a la barra de estado")
                            }
                        },
                        onComplete = { reminder, catId -> viewModel.completeReminder(reminder, catId, context) }
                    )

                    // Floating Notification Card Popup
                    currentState.simulatedNotification?.let { notification ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                                .align(Alignment.TopCenter)
                        ) {
                            SimulatedNotificationCard(
                                notification = notification,
                                onDismiss = { viewModel.clearMessages() }
                            )
                        }
                    }

                    // Handle success/error snackbars
                    LaunchedEffect(currentState.successMessage) {
                        currentState.successMessage?.let {
                            snackbarHostState.showSnackbar(it)
                            viewModel.clearMessages()
                        }
                    }
                    LaunchedEffect(currentState.errorMessage) {
                        currentState.errorMessage?.let {
                            snackbarHostState.showSnackbar(it)
                            viewModel.clearMessages()
                        }
                    }
                }
            }

            // Edit Reminder Dialog
            editingReminder?.let { reminder ->
                EditReminderDialog(
                    reminder = reminder,
                    savingsGoals = (state as? RemindersUiState.Success)?.savingsGoals ?: emptyList(),
                    onDismiss = { editingReminder = null },
                    onSave = { title, desc, amt, day, t, daysBefore, notifyTime, goalId ->
                        viewModel.updateReminderDetails(
                            reminder = reminder,
                            title = title,
                            description = desc,
                            amount = amt,
                            dueDateDay = day,
                            time = t,
                            notifyDaysBefore = daysBefore,
                            notifyTime = notifyTime,
                            associatedGoalId = goalId,
                            context = context
                        )
                        editingReminder = null
                    }
                )
            }

            // Create Reminder Dialog
            if (isCreatingReminder) {
                CreateReminderDialog(
                    savingsGoals = (state as? RemindersUiState.Success)?.savingsGoals ?: emptyList(),
                    onDismiss = { isCreatingReminder = false },
                    onCreate = { title, desc, type, amt, day, t, daysBefore, notifyTime, goalId ->
                        viewModel.addNewReminder(
                            title = title,
                            description = desc,
                            type = type,
                            amount = amt,
                            dueDateDay = day,
                            time = t,
                            notifyDaysBefore = daysBefore,
                            notifyTime = notifyTime,
                            associatedGoalId = goalId,
                            context = context
                        )
                        isCreatingReminder = false
                    }
                )
            }
        }
    }
}

@Composable
fun NotificationPermissionCard(
    onRequestPermission: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("notification_permission_banner"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.NotificationsActive,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(32.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Acceso a Notificaciones del Dispositivo",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "Permite que la app te notifique en la barra de estado de tu dispositivo antes de tus fechas de pago.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                )
            }
            Button(
                onClick = onRequestPermission,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Activar", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun RemindersContent(
    reminders: List<Reminder>,
    savingsGoals: List<SavingsGoal>,
    categories: List<Category>,
    hasNotificationPermission: Boolean,
    onRequestPermission: () -> Unit,
    onToggle: (Reminder) -> Unit,
    onEditClick: (Reminder) -> Unit,
    onDeleteClick: (Reminder) -> Unit,
    onSimulate: (Reminder) -> Unit,
    onComplete: (Reminder, Long?) -> Unit
) {
    val context = LocalContext.current
    val currentDayOfMonth = remember { Calendar.getInstance().get(Calendar.DAY_OF_MONTH) }
    
    val overdueReminders = remember(reminders, currentDayOfMonth) {
        reminders.filter {
            it.isEnabled &&
            !it.isCompletedThisMonth &&
            it.dueDateDay != null &&
            it.dueDateDay < currentDayOfMonth
        }
    }

    val pendingReminders = remember(reminders, currentDayOfMonth) {
        reminders.filter {
            it.isEnabled &&
            !it.isCompletedThisMonth &&
            (it.dueDateDay == null || it.dueDateDay >= currentDayOfMonth)
        }
    }

    val completedReminders = remember(reminders) {
        reminders.filter { it.isEnabled && it.isCompletedThisMonth }
    }

    val totalPendingAmount = remember(reminders) {
        reminders.filter { it.isEnabled && !it.isCompletedThisMonth }.sumOf { it.amount ?: 0.0 }
    }

    val totalPaidAmount = remember(reminders) {
        reminders.filter { it.isEnabled && it.isCompletedThisMonth }.sumOf { it.amount ?: 0.0 }
    }

    var selectedFilterTab by remember { mutableIntStateOf(0) }

    val filteredReminders = remember(reminders, selectedFilterTab, overdueReminders, pendingReminders, completedReminders) {
        when (selectedFilterTab) {
            1 -> pendingReminders
            2 -> overdueReminders
            3 -> completedReminders
            else -> reminders
        }
    }

    if (reminders.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.NotificationsOff,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                )
                Text(
                    text = "No hay recordatorios configurados",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 840.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (!hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                item {
                    NotificationPermissionCard(onRequestPermission = onRequestPermission)
                }
            }

            // Banner informativo de sincronización en segundo plano garantizada
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("background_sync_info_banner"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Canal de Alta Prioridad Activo",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Canal prioritario configurado con sonido, vibración y aviso emergente (heads-up) según mejores prácticas de Android.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = { com.example.util.NotificationScheduler.openReminderChannelSettings(context) },
                            modifier = Modifier.testTag("open_channel_settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Ajustes del canal",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Resumen de Compromisos Mensuales
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("monthly_reminders_summary_card"),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Compromisos del Mes",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Por Pagar",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = com.example.presentation.common.util.CurrencyFormatter.format(totalPendingAmount),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "Ya Pagado",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = com.example.presentation.common.util.CurrencyFormatter.format(totalPaidAmount),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF10B981)
                                )
                            }
                        }
                    }
                }
            }

            if (overdueReminders.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("overdue_reminders_warning_banner"),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Pagos Vencidos",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(28.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "¡Atención! Tienes ${overdueReminders.size} ${if (overdueReminders.size == 1) "pago vencido" else "pagos vencidos"}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    text = "Revisa los recordatorios resaltados en rojo y presiona 'Pagar Vencido' para registrar el gasto en tus cuentas.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
                                )
                            }
                        }
                    }
                }
            }

            // Pestañas de Filtrado Rápido
            item {
                val filterLabels = listOf(
                    "Todos (${reminders.size})",
                    "Pendientes (${pendingReminders.size})",
                    "Vencidos (${overdueReminders.size})",
                    "Pagados (${completedReminders.size})"
                )
                ScrollableTabRow(
                    selectedTabIndex = selectedFilterTab,
                    edgePadding = 0.dp,
                    divider = {},
                    containerColor = Color.Transparent,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    filterLabels.forEachIndexed { index, label ->
                        FilterChip(
                            selected = selectedFilterTab == index,
                            onClick = { selectedFilterTab = index },
                            label = { Text(label, fontWeight = if (selectedFilterTab == index) FontWeight.Bold else FontWeight.Normal) },
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .testTag("reminder_filter_chip_$index"),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                }
            }

            if (filteredReminders.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No hay recordatorios en esta categoría",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            } else {
                items(filteredReminders, key = { it.id }) { reminder ->
                    ReminderCard(
                        reminder = reminder,
                        savingsGoals = savingsGoals,
                        categories = categories,
                        onToggle = { onToggle(reminder) },
                        onEditClick = { onEditClick(reminder) },
                        onDeleteClick = { onDeleteClick(reminder) },
                        onSimulate = { onSimulate(reminder) },
                        onComplete = { catId -> onComplete(reminder, catId) }
                    )
                }
            }
        }
    }
}

@Composable
fun ReminderCard(
    reminder: Reminder,
    savingsGoals: List<SavingsGoal>,
    categories: List<Category>,
    onToggle: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onSimulate: () -> Unit,
    onComplete: (Long?) -> Unit
) {
    val currentDayOfMonth = remember { Calendar.getInstance().get(Calendar.DAY_OF_MONTH) }
    val isOverdue = remember(reminder, currentDayOfMonth) {
        reminder.isEnabled &&
        !reminder.isCompletedThisMonth &&
        reminder.dueDateDay != null &&
        reminder.dueDateDay < currentDayOfMonth
    }

    val cardColor = if (reminder.isEnabled) {
        if (reminder.isCompletedThisMonth) {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        } else if (isOverdue) {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
        } else {
            MaterialTheme.colorScheme.surface
        }
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
    }

    val borderStroke = if (reminder.isEnabled && !reminder.isCompletedThisMonth) {
        if (isOverdue) {
            BorderStroke(1.5.dp, MaterialTheme.colorScheme.error)
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        }
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
    }

    val iconInfo = when (reminder.type) {
        "REGISTRO_GASTOS" -> Pair(Icons.Default.EditCalendar, MaterialTheme.colorScheme.primary)
        "PAGO_UNIVERSIDAD" -> Pair(Icons.Default.School, Color(0xFFE57373))
        "PAGO_SERVICIOS" -> Pair(Icons.Default.Receipt, Color(0xFF64B5F6))
        "META_AHORRO" -> Pair(Icons.Default.Savings, Color(0xFF81C784))
        else -> Pair(Icons.Default.Notifications, MaterialTheme.colorScheme.primary)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("reminder_card_${reminder.id}"),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        border = borderStroke,
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icon block
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            if (isOverdue) MaterialTheme.colorScheme.error.copy(alpha = 0.18f)
                            else iconInfo.second.copy(alpha = 0.12f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isOverdue) Icons.Default.Warning else iconInfo.first,
                        contentDescription = null,
                        tint = if (isOverdue) MaterialTheme.colorScheme.error else iconInfo.second,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = reminder.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isOverdue) MaterialTheme.colorScheme.error
                            else if (reminder.isEnabled) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.outline
                        )

                        if (reminder.isCompletedThisMonth && reminder.isEnabled) {
                            SuggestionChip(
                                onClick = {},
                                label = { Text("Pagado", style = MaterialTheme.typography.labelSmall) },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                    labelColor = MaterialTheme.colorScheme.primary
                                ),
                                border = null,
                                modifier = Modifier.height(20.dp)
                            )
                        } else if (isOverdue) {
                            SuggestionChip(
                                onClick = {},
                                icon = {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(12.dp)
                                    )
                                },
                                label = { Text("¡Vencido!", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    labelColor = MaterialTheme.colorScheme.onErrorContainer
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                                modifier = Modifier.height(20.dp)
                            )
                        }
                    }

                    Text(
                        text = reminder.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Switch
                Switch(
                    checked = reminder.isEnabled,
                    onCheckedChange = { onToggle() },
                    modifier = Modifier.testTag("reminder_switch_${reminder.id}")
                )
            }

            if (reminder.isEnabled) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(12.dp))

                // Metadata block
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        if (reminder.amount != null && reminder.amount > 0) {
                            Text(
                                text = "Monto: ${com.example.presentation.common.util.CurrencyFormatter.format(reminder.amount)}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        val scheduleText = when {
                            reminder.dueDateDay != null -> {
                                if (isOverdue) {
                                    val daysLate = currentDayOfMonth - reminder.dueDateDay
                                    "¡Vencido hace $daysLate ${if (daysLate == 1) "día" else "días"}! (Vencía día ${reminder.dueDateDay})"
                                } else {
                                    "Vence: Día ${reminder.dueDateDay} de cada mes"
                                }
                            }
                            reminder.time != null -> "Hora diaria: ${reminder.time}"
                            else -> "Frecuencia: Mensual"
                        }
                        Text(
                            text = scheduleText,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (isOverdue) FontWeight.Bold else FontWeight.Normal,
                            color = if (isOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )

                        val notifyBadgeText = when (reminder.notifyDaysBefore) {
                            0 -> "🔔 Notifica el mismo día a las ${reminder.notifyTime}"
                            1 -> "🔔 Notifica 1 día antes a las ${reminder.notifyTime}"
                            else -> "🔔 Notifica ${reminder.notifyDaysBefore} días antes a las ${reminder.notifyTime}"
                        }
                        Text(
                            text = notifyBadgeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.Medium
                        )

                        if (reminder.type == "META_AHORRO" && reminder.associatedGoalId != null) {
                            val goalName = savingsGoals.find { it.id == reminder.associatedGoalId }?.name ?: "Meta activa"
                            Text(
                                text = "Meta asociada: $goalName",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }

                        val context = LocalContext.current
                        val scheduleStatus = remember(reminder) {
                            com.example.util.ReminderNotificationManager.getScheduleStatusText(context, reminder)
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            val isSentToday = scheduleStatus.contains("Alerta enviada hoy")
                            Icon(
                                imageVector = if (isSentToday) Icons.Default.CheckCircle else Icons.Default.AccessTime,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = if (isSentToday) Color(0xFF10B981) else MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = scheduleStatus,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSentToday) Color(0xFF10B981) else MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Action buttons
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onDeleteClick,
                            modifier = Modifier.testTag("delete_reminder_${reminder.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Eliminar recordatorio",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }

                        IconButton(
                            onClick = onEditClick,
                            modifier = Modifier.testTag("edit_reminder_${reminder.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Editar recordatorio",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        IconButton(
                            onClick = onSimulate,
                            modifier = Modifier.testTag("simulate_reminder_${reminder.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = "Simular notificación",
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }

                        // Complete Action Button
                        if (reminder.type != "REGISTRO_GASTOS" && !reminder.isCompletedThisMonth) {
                            Button(
                                onClick = {
                                    val matchedCategory = when (reminder.type) {
                                        "PAGO_UNIVERSIDAD" -> categories.find { it.name.contains("Educación", ignoreCase = true) }?.id
                                        "PAGO_SERVICIOS" -> categories.find { it.name.contains("Servicios", ignoreCase = true) }?.id
                                        "META_AHORRO" -> categories.find { it.name.contains("Otros", ignoreCase = true) }?.id
                                        else -> null
                                    }
                                    onComplete(matchedCategory)
                                },
                                modifier = Modifier
                                    .height(36.dp)
                                    .testTag("complete_reminder_${reminder.id}"),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                    contentColor = if (isOverdue) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isOverdue) Icons.Default.Warning else Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = if (isOverdue) "Pagar Vencido" else if (reminder.type == "META_AHORRO") "Aportar" else "Pagar",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SimulatedNotificationCard(
    notification: SimulatedNotification,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onDismiss() }
            .testTag("simulated_notification_card"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.inverseOnSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.NotificationsActive,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Notificación de Sistema",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cerrar",
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier
                            .size(16.dp)
                            .clickable { onDismiss() }
                    )
                }

                Text(
                    text = notification.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.inverseSurface
                )

                Text(
                    text = notification.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditReminderDialog(
    reminder: Reminder,
    savingsGoals: List<SavingsGoal>,
    onDismiss: () -> Unit,
    onSave: (String, String, Double?, Int?, String?, Int, String, Long?) -> Unit
) {
    var title by remember { mutableStateOf(reminder.title) }
    var description by remember { mutableStateOf(reminder.description) }
    var amountStr by remember { mutableStateOf(reminder.amount?.toString() ?: "") }
    var dueDateDayStr by remember { mutableStateOf(reminder.dueDateDay?.toString() ?: "") }
    var time by remember { mutableStateOf(reminder.time ?: "") }
    var notifyDaysBefore by remember { mutableStateOf(reminder.notifyDaysBefore) }
    var notifyTime by remember { mutableStateOf(reminder.notifyTime) }
    var notifyDaysExpanded by remember { mutableStateOf(false) }
    var selectedGoalId by remember { mutableStateOf(reminder.associatedGoalId) }

    val daysOptions = listOf(
        0 to "El mismo día del vencimiento",
        1 to "1 día antes del vencimiento (Por defecto)",
        2 to "2 días antes del vencimiento",
        3 to "3 días antes del vencimiento",
        5 to "5 días antes del vencimiento",
        7 to "1 semana antes del vencimiento"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Editar Recordatorio",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Título") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_reminder_title_input")
                    )
                }

                item {
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Descripción") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (reminder.type != "REGISTRO_GASTOS") {
                    item {
                        val sym = com.example.presentation.common.util.CurrencyFormatter.getCurrencySymbol()
                        OutlinedTextField(
                            value = amountStr,
                            onValueChange = { amountStr = it },
                            label = { Text("Monto ($sym)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("edit_reminder_amount_input")
                        )
                    }

                    item {
                        OutlinedTextField(
                            value = dueDateDayStr,
                            onValueChange = { dueDateDayStr = it },
                            label = { Text("Día del mes de vencimiento (1-31)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    item {
                        OutlinedTextField(
                            value = time,
                            onValueChange = { time = it },
                            label = { Text("Hora diaria (HH:MM)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                item {
                    Text(
                        text = "Notificación al Dispositivo",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                item {
                    ExposedDropdownMenuBox(
                        expanded = notifyDaysExpanded,
                        onExpandedChange = { notifyDaysExpanded = !notifyDaysExpanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = daysOptions.find { it.first == notifyDaysBefore }?.second ?: "$notifyDaysBefore días antes",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Anticipación de la notificación") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = notifyDaysExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                        )
                        ExposedDropdownMenu(
                            expanded = notifyDaysExpanded,
                            onDismissRequest = { notifyDaysExpanded = false }
                        ) {
                            daysOptions.forEach { opt ->
                                DropdownMenuItem(
                                    text = { Text(opt.second) },
                                    onClick = {
                                        notifyDaysBefore = opt.first
                                        notifyDaysExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = notifyTime,
                        onValueChange = { notifyTime = it },
                        label = { Text("Hora de la notificación (ej. 09:00 AM, 08:00 PM)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (reminder.type == "META_AHORRO") {
                    item {
                        Text(
                            text = "Asociar Meta de Ahorro",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        savingsGoals.forEach { goal ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedGoalId = goal.id }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedGoalId == goal.id,
                                    onClick = { selectedGoalId = goal.id }
                                )
                                Text(
                                    text = "${goal.name} (Meta: ${com.example.presentation.common.util.CurrencyFormatter.format(goal.targetAmount)})",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = amountStr.toDoubleOrNull()
                    val dueDateDay = dueDateDayStr.toIntOrNull()
                    onSave(title, description, amount, dueDateDay, time.ifEmpty { null }, notifyDaysBefore, notifyTime, selectedGoalId)
                },
                modifier = Modifier.testTag("save_reminder_button")
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateReminderDialog(
    savingsGoals: List<SavingsGoal>,
    onDismiss: () -> Unit,
    onCreate: (String, String, String, Double?, Int?, String?, Int, String, Long?) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("PAGO_SERVICIOS") }
    var amountStr by remember { mutableStateOf("") }
    var dueDateDayStr by remember { mutableStateOf("") }
    var time by remember { mutableStateOf("09:00 AM") }
    var notifyDaysBefore by remember { mutableStateOf(1) }
    var notifyTime by remember { mutableStateOf("09:00 AM") }
    var notifyDaysExpanded by remember { mutableStateOf(false) }
    var selectedGoalId by remember { mutableStateOf<Long?>(null) }
    var typeExpanded by remember { mutableStateOf(false) }

    val typeOptions = listOf(
        "PAGO_SERVICIOS" to "Pago de Servicios Públicos / Suscripción",
        "PAGO_UNIVERSIDAD" to "Pago de Educación / Universidad / Colegio",
        "PAGO_TARJETA" to "Pago de Tarjeta de Crédito / Préstamo",
        "META_AHORRO" to "Aporte a Meta de Ahorro",
        "REGISTRO_GASTOS" to "Recordatorio Diario de Gastos",
        "OTRO" to "Otro Pago / Compromiso"
    )

    val daysOptions = listOf(
        0 to "El mismo día del vencimiento",
        1 to "1 día antes del vencimiento (Por defecto)",
        2 to "2 días antes del vencimiento",
        3 to "3 días antes del vencimiento",
        5 to "5 días antes del vencimiento",
        7 to "1 semana antes del vencimiento"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Nuevo Recordatorio de Pago",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Título del recordatorio") },
                        placeholder = { Text("Ej. Pago de Luz, Netflix, Tarjeta") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("create_reminder_title_input")
                    )
                }

                item {
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Descripción o detalle") },
                        placeholder = { Text("Ej. Vence el día 15 de cada mes") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    ExposedDropdownMenuBox(
                        expanded = typeExpanded,
                        onExpandedChange = { typeExpanded = !typeExpanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = typeOptions.find { it.first == type }?.second ?: type,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Tipo de recordatorio") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                        )
                        ExposedDropdownMenu(
                            expanded = typeExpanded,
                            onDismissRequest = { typeExpanded = false }
                        ) {
                            typeOptions.forEach { opt ->
                                DropdownMenuItem(
                                    text = { Text(opt.second) },
                                    onClick = {
                                        type = opt.first
                                        typeExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                if (type != "REGISTRO_GASTOS") {
                    item {
                        val sym = com.example.presentation.common.util.CurrencyFormatter.getCurrencySymbol()
                        OutlinedTextField(
                            value = amountStr,
                            onValueChange = { amountStr = it },
                            label = { Text("Monto ($sym)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("create_reminder_amount_input")
                        )
                    }

                    item {
                        OutlinedTextField(
                            value = dueDateDayStr,
                            onValueChange = { dueDateDayStr = it },
                            label = { Text("Día de vencimiento del mes (1-31)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    item {
                        OutlinedTextField(
                            value = time,
                            onValueChange = { time = it },
                            label = { Text("Hora diaria de notificación") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                item {
                    Text(
                        text = "Notificación al Dispositivo",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                item {
                    ExposedDropdownMenuBox(
                        expanded = notifyDaysExpanded,
                        onExpandedChange = { notifyDaysExpanded = !notifyDaysExpanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = daysOptions.find { it.first == notifyDaysBefore }?.second ?: "$notifyDaysBefore días antes",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Anticipación de la notificación") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = notifyDaysExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                        )
                        ExposedDropdownMenu(
                            expanded = notifyDaysExpanded,
                            onDismissRequest = { notifyDaysExpanded = false }
                        ) {
                            daysOptions.forEach { opt ->
                                DropdownMenuItem(
                                    text = { Text(opt.second) },
                                    onClick = {
                                        notifyDaysBefore = opt.first
                                        notifyDaysExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = notifyTime,
                        onValueChange = { notifyTime = it },
                        label = { Text("Hora de la notificación (ej. 09:00 AM, 08:00 PM)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (type == "META_AHORRO") {
                    item {
                        Text(
                            text = "Asociar Meta de Ahorro",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        savingsGoals.forEach { goal ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedGoalId = goal.id }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedGoalId == goal.id,
                                    onClick = { selectedGoalId = goal.id }
                                )
                                Text(
                                    text = "${goal.name} (Meta: ${com.example.presentation.common.util.CurrencyFormatter.format(goal.targetAmount)})",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        val amount = amountStr.toDoubleOrNull()
                        val dueDateDay = dueDateDayStr.toIntOrNull()
                        onCreate(title, description, type, amount, dueDateDay, time.ifEmpty { null }, notifyDaysBefore, notifyTime, selectedGoalId)
                    }
                },
                modifier = Modifier.testTag("save_new_reminder_button")
            ) {
                Text("Crear")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
