package com.example.presentation.assistant

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.Account
import com.example.presentation.common.components.AmountTextField
import com.example.presentation.common.util.CurrencyFormatter
import com.example.ui.theme.BluePrimary
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.IncomeGreen
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantScreen(
    viewModel: AssistantViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onNavigateBack)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val keyboardController = LocalSoftwareKeyboardController.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.contributionSuccessMessage) {
        state.contributionSuccessMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissContributionMessage()
        }
    }

    // Auto-scroll al final del chat cuando llegan nuevos mensajes
    LaunchedEffect(state.messages.size, state.isLoading, state.selectedTab) {
        if (state.selectedTab == AssistantTab.CHAT && state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Asistente Financiero IA",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "Gemini 2.5 • Chat, Predicción y Metas",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("assistant_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver"
                        )
                    }
                },
                actions = {
                    if (state.selectedTab == AssistantTab.CHAT) {
                        IconButton(
                            onClick = { viewModel.clearChat() },
                            modifier = Modifier.testTag("assistant_clear_chat_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "Limpiar Chat",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 840.dp)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                // Selector de pestañas para las 3 opciones de IA
                PrimaryTabRow(
                    selectedTabIndex = state.selectedTab.ordinal,
                    containerColor = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth().testTag("assistant_tab_row")
                ) {
                    Tab(
                        selected = state.selectedTab == AssistantTab.CHAT,
                        onClick = { viewModel.onTabSelected(AssistantTab.CHAT) },
                        text = { Text("Chat Financiero", fontWeight = FontWeight.SemiBold) },
                        icon = { Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(20.dp)) },
                        modifier = Modifier.testTag("assistant_tab_chat")
                    )
                    Tab(
                        selected = state.selectedTab == AssistantTab.PREDICTIONS,
                        onClick = { viewModel.onTabSelected(AssistantTab.PREDICTIONS) },
                        text = { Text("Predicción", fontWeight = FontWeight.SemiBold) },
                        icon = { Icon(Icons.AutoMirrored.Filled.TrendingUp, contentDescription = null, modifier = Modifier.size(20.dp)) },
                        modifier = Modifier.testTag("assistant_tab_predictions")
                    )
                    Tab(
                        selected = state.selectedTab == AssistantTab.SAVINGS_STRATEGY,
                        onClick = { viewModel.onTabSelected(AssistantTab.SAVINGS_STRATEGY) },
                        text = { Text("Estrategia Ahorro", fontWeight = FontWeight.SemiBold) },
                        icon = { Icon(Icons.Default.Savings, contentDescription = null, modifier = Modifier.size(20.dp)) },
                        modifier = Modifier.testTag("assistant_tab_savings")
                    )
                }

                // Banner de advertencia si no hay API key
                if (!state.isKeyConfigured) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .testTag("api_key_warning_banner"),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
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
                                contentDescription = "Advertencia",
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Column {
                                Text(
                                    text = "API Key No Configurada",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Por favor, configure su variable GEMINI_API_KEY a través del panel de Secretos de AI Studio.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }

                // Error general
                state.error?.let { errorMsg ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .testTag("general_error_banner"),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
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
                                contentDescription = "Error",
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Atención",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = errorMsg,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }

                // Contenido dinámico según la pestaña elegida
                when (state.selectedTab) {
                    AssistantTab.CHAT -> {
                        ChatTabContent(
                            state = state,
                            listState = listState,
                            onSendMessage = {
                                viewModel.sendMessage(it)
                                keyboardController?.hide()
                            },
                            onInputTextChanged = { viewModel.onInputTextChanged(it) }
                        )
                    }
                    AssistantTab.PREDICTIONS -> {
                        PredictionsTabContent(
                            state = state,
                            onRunDeepAnalysis = { viewModel.runDeepAnomalyAndForecastAnalysis() },
                            onSimulatedDailyPaceChanged = { viewModel.onSimulatedDailyPaceChanged(it) },
                            onSelectPredictionDay = { viewModel.onSelectPredictionDay(it) },
                            onResetSimulation = { viewModel.resetSimulation() }
                        )
                    }
                    AssistantTab.SAVINGS_STRATEGY -> {
                        SavingsStrategyTabContent(
                            state = state,
                            onGenerateStrategy = { viewModel.generateIntelligentSavingsStrategy() },
                            onSetAllocationMode = { viewModel.setAllocationMode(it) },
                            onSetSimulatedSurplus = { viewModel.setSimulatedSurplus(it) },
                            onOpenContributeDialog = { viewModel.openContributeDialog(it) },
                            onSelectStressScenario = { viewModel.selectStressScenario(it) }
                        )
                    }
                }
            }
        }
    }

    // Modal de Aporte Directo a Meta desde el Asistente
    state.showContributeDialogForGoal?.let { targetGoal ->
        AssistantContributeDialog(
            goal = targetGoal,
            accounts = state.currentAccountsList,
            onConfirm = { amount, accountId ->
                viewModel.contributeToGoal(targetGoal.goalId, amount, accountId)
            },
            onDismiss = { viewModel.dismissContributeDialog() }
        )
    }
}

// -----------------------------------------------------------------------------------------
// PESTAÑA 1: CHAT CONVERSACIONAL
// -----------------------------------------------------------------------------------------
@Composable
private fun ColumnScope.ChatTabContent(
    state: AssistantUiState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onSendMessage: (String) -> Unit,
    onInputTextChanged: (String) -> Unit
) {
    LazyColumn(
        state = listState,
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(state.messages) { message ->
            MessageBubble(message = message)
        }

        if (state.isLoading) {
            item {
                TypingIndicatorBubble()
            }
        }

        // Sugerencias interactivas
        if (state.messages.size <= 2 && !state.isLoading) {
            item {
                SuggestedPromptsSection(
                    onPromptSelected = onSendMessage
                )
            }
        }
    }

    // Barra inferior de entrada
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = state.inputText,
                onValueChange = onInputTextChanged,
                placeholder = { Text("Haz una pregunta sobre tus finanzas...") },
                modifier = Modifier
                    .weight(1f)
                    .testTag("assistant_input_field"),
                shape = RoundedCornerShape(24.dp),
                maxLines = 4,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Send
                ),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (state.inputText.isNotBlank() && !state.isLoading) {
                            onSendMessage(state.inputText)
                        }
                    }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                )
            )

            FloatingActionButton(
                onClick = {
                    if (state.inputText.isNotBlank() && !state.isLoading) {
                        onSendMessage(state.inputText)
                    }
                },
                modifier = Modifier
                    .size(48.dp)
                    .testTag("assistant_send_button"),
                shape = CircleShape,
                containerColor = if (state.inputText.isNotBlank() && !state.isLoading) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.38f)
                },
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Enviar",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------------------
// PESTAÑA 3: PREDICCIONES Y DETECCIÓN DE ANOMALÍAS
// -----------------------------------------------------------------------------------------
@Composable
private fun PredictionsTabContent(
    state: AssistantUiState,
    onRunDeepAnalysis: () -> Unit,
    onSimulatedDailyPaceChanged: (Double) -> Unit,
    onSelectPredictionDay: (Int?) -> Unit,
    onResetSimulation: () -> Unit
) {
    val forecast = state.forecast

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("predictions_scroll_container"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Tarjeta Hero de Proyección y Ritmo de Gasto
        item {
            ForecastHeroCard(forecast = forecast)
        }

        // 2. Gráfico Interactivo de Curva de Proyección / Runway
        if (forecast != null && forecast.dailyCumulativeHistory.isNotEmpty()) {
            item {
                RunwayProjectionChartCard(
                    forecast = forecast,
                    selectedDay = state.selectedPredictionDay,
                    onSelectDay = onSelectPredictionDay
                )
            }
        }

        // 3. Simulador Interactivo de Ritmo Diario
        if (forecast != null) {
            item {
                DailySpendingSimulatorCard(
                    forecast = forecast,
                    simulatedPace = state.simulatedDailyPace ?: forecast.averageDailySpent,
                    simulatedExpense = state.simulatedProjectedExpense ?: forecast.expectedMonthEndExpense,
                    simulatedSavings = state.simulatedProjectedSavings ?: forecast.projectedSavings,
                    onPaceChanged = onSimulatedDailyPaceChanged,
                    onReset = onResetSimulation
                )
            }
        }

        // 4. Escenarios Predictivos Comparativos
        if (forecast != null && forecast.predictiveScenarios.isNotEmpty()) {
            item {
                PredictiveScenariosCard(scenarios = forecast.predictiveScenarios)
            }
        }

        // 5. Velocidad de Gasto por Categoría
        if (forecast != null && forecast.categoryVelocities.isNotEmpty()) {
            item {
                CategoryVelocitiesCard(velocities = forecast.categoryVelocities)
            }
        }

        // 6. Recomendaciones Financieras Accionables
        if (forecast != null && forecast.actionableTips.isNotEmpty()) {
            item {
                ActionableTipsSection(tips = forecast.actionableTips)
            }
        }

        // 7. Botón de Auditoría Profunda con IA (Gemini)
        item {
            Button(
                onClick = onRunDeepAnalysis,
                enabled = !state.isAnalyzingAnomalies,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("run_deep_anomaly_analysis_button"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                if (state.isAnalyzingAnomalies) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Gemini está analizando tus gastos...")
                } else {
                    Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Auditar anomalías y predicción con IA", fontWeight = FontWeight.Bold)
                }
            }
        }

        // 8. Resultado de la auditoría Gemini
        state.aiAnomalyAnalysis?.let { analysisText ->
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("ai_anomaly_result_card"),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Psychology,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Diagnóstico Predictivo de Gemini",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Text(
                            text = analysisText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // 9. Lista de anomalías detectadas
        item {
            Text(
                text = "Alertas y Puntos Críticos Detectados (${state.anomalies.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        if (state.anomalies.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = borderStroke()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = IncomeGreen,
                            modifier = Modifier.size(28.dp)
                        )
                        Column {
                            Text("Sin anomalías graves", fontWeight = FontWeight.Bold)
                            Text(
                                "No se detectaron fugas de dinero ni gastos atípicos desproporcionados.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        } else {
            items(state.anomalies, key = { it.id }) { anomaly ->
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("anomaly_item_${anomaly.id}"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = when (anomaly.severity) {
                            AnomalySeverity.HIGH -> ExpenseRed.copy(alpha = 0.08f)
                            AnomalySeverity.MEDIUM -> Color(0xFFFFA000).copy(alpha = 0.08f)
                            AnomalySeverity.INFO -> MaterialTheme.colorScheme.surface
                        }
                    ),
                    border = BorderStroke(
                        1.dp,
                        when (anomaly.severity) {
                            AnomalySeverity.HIGH -> ExpenseRed.copy(alpha = 0.35f)
                            AnomalySeverity.MEDIUM -> Color(0xFFFFA000).copy(alpha = 0.35f)
                            AnomalySeverity.INFO -> MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                        }
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
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
                                    imageVector = when (anomaly.severity) {
                                        AnomalySeverity.HIGH -> Icons.Default.Warning
                                        AnomalySeverity.MEDIUM -> Icons.Default.ReportProblem
                                        AnomalySeverity.INFO -> Icons.Default.Info
                                    },
                                    contentDescription = null,
                                    tint = when (anomaly.severity) {
                                        AnomalySeverity.HIGH -> ExpenseRed
                                        AnomalySeverity.MEDIUM -> Color(0xFFFFA000)
                                        AnomalySeverity.INFO -> BluePrimary
                                    }
                                )
                                Text(
                                    text = anomaly.title,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }
                        }

                        Text(
                            text = anomaly.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lightbulb,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = anomaly.recommendation,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
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

// -----------------------------------------------------------------------------------------
// COMPONENTES AUXILIARES DE PREDICCIONES
// -----------------------------------------------------------------------------------------

@Composable
private fun ForecastHeroCard(forecast: ExpenseForecast?) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("forecast_hero_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = borderStroke()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Cabecera con estado de ritmo
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Proyección y Ritmo",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                val badgeColor = when (forecast?.trendStatus) {
                    "SAVING_ON_TRACK" -> IncomeGreen
                    "INCREASING" -> ExpenseRed
                    else -> BluePrimary
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = badgeColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = when (forecast?.trendStatus) {
                            "SAVING_ON_TRACK" -> "Ritmo Seguro"
                            "INCREASING" -> "Gasto Acelerado"
                            else -> "Gasto Estable"
                        },
                        color = badgeColor,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            if (forecast != null) {
                // Dos pilares de ritmo: Ritmo Actual vs Ritmo Seguro Restante
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = borderStroke()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                "Ritmo Actual",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "${CurrencyFormatter.format(forecast.averageDailySpent)} / día",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "${forecast.daysElapsed} días registrados",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = borderStroke()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                "Ritmo Seguro Sugerido",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "${CurrencyFormatter.format(forecast.safeDailyRemainingBudget)} / día",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (forecast.dailyPaceDifference > 0) ExpenseRed else IncomeGreen
                            )
                            Text(
                                "${forecast.daysRemaining} días restantes",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Banner de alerta de ritmo o colchón
                if (forecast.dailyPaceDifference > 0) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = ExpenseRed.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, ExpenseRed.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                                contentDescription = null,
                                tint = ExpenseRed,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Exceso de ritmo: +${CurrencyFormatter.format(forecast.dailyPaceDifference)}/día sobre el límite seguro.",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = ExpenseRed
                            )
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = IncomeGreen.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, IncomeGreen.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.VerifiedUser,
                                contentDescription = null,
                                tint = IncomeGreen,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Ritmo óptimo: Cuentas con un margen de ${CurrencyFormatter.format(abs(forecast.dailyPaceDifference))}/día disponible.",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = IncomeGreen
                            )
                        }
                    }
                }

                // Alerta de agotamiento de presupuesto (Burnout Day)
                forecast.burnoutDayOfMonth?.let { bDay ->
                    val totalDaysInMonth = forecast.daysElapsed + forecast.daysRemaining
                    if (bDay <= totalDaysInMonth) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFD32F2F).copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, Color(0xFFD32F2F).copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = Color(0xFFD32F2F),
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "A este ritmo agotarás el presupuesto disponible el día $bDay del mes (en ${forecast.daysUntilBurnout ?: 0} días).",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFD32F2F)
                                )
                            }
                        }
                    }
                }

                // Comparación de progreso: Tiempo transcurrido vs Presupuesto consumido
                val totalDays = (forecast.daysElapsed + forecast.daysRemaining).coerceAtLeast(1)
                val timeFraction = (forecast.daysElapsed.toFloat() / totalDays.toFloat()).coerceIn(0f, 1f)
                val ceiling = forecast.targetCeiling.coerceAtLeast(1.0)
                val expenseFraction = (forecast.currentSpent / ceiling).toFloat().coerceIn(0f, 1f)

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Tiempo del mes transcurrido (${(timeFraction * 100).toInt()}%)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Presupuesto consumido (${(expenseFraction * 100).toInt()}%)",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (expenseFraction > timeFraction) ExpenseRed else IncomeGreen
                        )
                    }

                    LinearProgressIndicator(
                        progress = { expenseFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = if (expenseFraction > timeFraction) ExpenseRed else MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                // Resumen Numérico Inferior
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Gasto Actual", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            CurrencyFormatter.format(forecast.currentSpent),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Proyección Cierre", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            CurrencyFormatter.format(forecast.expectedMonthEndExpense),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text("Balance Proyectado", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val isSurplus = forecast.projectedSavings >= 0
                        Text(
                            CurrencyFormatter.format(forecast.projectedSavings),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isSurplus) IncomeGreen else ExpenseRed
                        )
                    }
                }

                Text(
                    text = forecast.summaryInsight,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "Calculando pronósticos y métricas de ritmo...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------------------
// GRÁFICO INTERACTIVO DE RUNWAY Y PROYECCIÓN (CANVAS)
// -----------------------------------------------------------------------------------------

@Composable
private fun RunwayProjectionChartCard(
    forecast: ExpenseForecast,
    selectedDay: Int?,
    onSelectDay: (Int?) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("runway_projection_chart_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = borderStroke()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Timeline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Curva de Gasto y Proyección",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "Toca para explorar días",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Leyendas
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(modifier = Modifier.size(10.dp, 3.dp).background(BluePrimary, RoundedCornerShape(2.dp)))
                    Text("Gasto Real", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(modifier = Modifier.size(10.dp, 3.dp).background(Color(0xFFAB47BC), RoundedCornerShape(2.dp)))
                    Text("Proyección", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(modifier = Modifier.size(10.dp, 2.dp).background(ExpenseRed, RoundedCornerShape(2.dp)))
                    Text("Techo (${CurrencyFormatter.format(forecast.targetCeiling)})", style = MaterialTheme.typography.labelSmall, color = ExpenseRed)
                }
            }

            // Canvas interactivo
            val history = forecast.dailyCumulativeHistory
            val maxDays = history.size.coerceAtLeast(1)
            val maxAmount = maxOf(
                forecast.targetCeiling,
                forecast.expectedMonthEndExpense,
                forecast.currentSpent,
                100.0
            ) * 1.15

            val primaryColor = MaterialTheme.colorScheme.primary
            val onSurfaceColor = MaterialTheme.colorScheme.onSurface
            val outlineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)

            Box(modifier = Modifier.fillMaxWidth().height(180.dp)) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(history) {
                            detectTapGestures { offset ->
                                val leftPad = 40f
                                val rightPad = 24f
                                val chartWidth = size.width - leftPad - rightPad
                                if (chartWidth > 0 && offset.x in leftPad..(size.width - rightPad)) {
                                    val ratio = (offset.x - leftPad) / chartWidth
                                    val tappedDay = (1 + ratio * (maxDays - 1)).toInt().coerceIn(1, maxDays)
                                    onSelectDay(tappedDay)
                                } else {
                                    onSelectDay(null)
                                }
                            }
                        }
                ) {
                    val leftPad = 40f
                    val rightPad = 24f
                    val topPad = 24f
                    val bottomPad = 28f
                    val chartW = size.width - leftPad - rightPad
                    val chartH = size.height - topPad - bottomPad

                    fun getX(day: Int): Float {
                        return leftPad + ((day - 1).toFloat() / (maxDays - 1).coerceAtLeast(1)) * chartW
                    }

                    fun getY(amount: Double): Float {
                        val fraction = (amount / maxAmount).toFloat().coerceIn(0f, 1f)
                        return topPad + chartH * (1f - fraction)
                    }

                    // Líneas de cuadrícula horizontal
                    listOf(0.25, 0.5, 0.75, 1.0).forEach { frac ->
                        val y = topPad + chartH * (1f - frac.toFloat())
                        drawLine(
                            color = outlineColor,
                            start = Offset(leftPad, y),
                            end = Offset(size.width - rightPad, y),
                            strokeWidth = 1f
                        )
                    }

                    // Línea horizontal de Techo de Gasto
                    val ceilingY = getY(forecast.targetCeiling)
                    drawLine(
                        color = ExpenseRed.copy(alpha = 0.7f),
                        start = Offset(leftPad, ceilingY),
                        end = Offset(size.width - rightPad, ceilingY),
                        strokeWidth = 2f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
                    )

                    // Línea diagonal ideal de presupuesto
                    val idealPath = Path().apply {
                        moveTo(getX(1), topPad + chartH)
                        lineTo(getX(maxDays), ceilingY)
                    }
                    drawPath(
                        path = idealPath,
                        color = Color.Gray.copy(alpha = 0.35f),
                        style = Stroke(width = 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f))
                    )

                    // Curva de Gasto Real Acumulado (1 hasta currentDay)
                    val realPoints = history.filter { it.actualCumulative != null }
                    if (realPoints.isNotEmpty()) {
                        val realPath = Path()
                        val fillPath = Path()

                        realPoints.forEachIndexed { index, pt ->
                            val x = getX(pt.day)
                            val y = getY(pt.actualCumulative ?: 0.0)
                            if (index == 0) {
                                realPath.moveTo(x, y)
                                fillPath.moveTo(x, topPad + chartH)
                                fillPath.lineTo(x, y)
                            } else {
                                realPath.lineTo(x, y)
                                fillPath.lineTo(x, y)
                            }
                        }

                        val lastReal = realPoints.last()
                        fillPath.lineTo(getX(lastReal.day), topPad + chartH)
                        fillPath.close()

                        // Degradado bajo la curva real
                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(primaryColor.copy(alpha = 0.35f), Color.Transparent),
                                startY = topPad,
                                endY = topPad + chartH
                            )
                        )

                        // Trazo de la curva real
                        drawPath(
                            path = realPath,
                            color = primaryColor,
                            style = Stroke(width = 3.5f)
                        )
                    }

                    // Curva Proyectada (desde currentDay hasta fin de mes)
                    val projPath = Path()
                    val projPoints = history.filter { it.day >= forecast.daysElapsed }
                    projPoints.forEachIndexed { index, pt ->
                        val x = getX(pt.day)
                        val y = getY(pt.projectedCumulative ?: 0.0)
                        if (index == 0) {
                            projPath.moveTo(x, y)
                        } else {
                            projPath.lineTo(x, y)
                        }
                    }
                    drawPath(
                        path = projPath,
                        color = Color(0xFFAB47BC),
                        style = Stroke(width = 2.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f))
                    )

                    // Marcador del día de hoy
                    val todayPoint = history.firstOrNull { it.isToday }
                    if (todayPoint != null) {
                        val todayX = getX(todayPoint.day)
                        val todayY = getY(todayPoint.actualCumulative ?: 0.0)
                        drawCircle(
                            color = primaryColor.copy(alpha = 0.3f),
                            radius = 10f,
                            center = Offset(todayX, todayY)
                        )
                        drawCircle(
                            color = primaryColor,
                            radius = 5f,
                            center = Offset(todayX, todayY)
                        )
                    }

                    // Marcador del día seleccionado
                    selectedDay?.let { selDay ->
                        val selPt = history.firstOrNull { it.day == selDay }
                        if (selPt != null) {
                            val selX = getX(selPt.day)
                            val selAmount = selPt.actualCumulative ?: selPt.projectedCumulative ?: 0.0
                            val selY = getY(selAmount)

                            drawLine(
                                color = onSurfaceColor.copy(alpha = 0.4f),
                                start = Offset(selX, topPad),
                                end = Offset(selX, topPad + chartH),
                                strokeWidth = 1.5f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)
                            )
                            drawCircle(
                                color = primaryColor,
                                radius = 6f,
                                center = Offset(selX, selY)
                            )
                        }
                    }
                }
            }

            // Detalle interactivo del día seleccionado
            selectedDay?.let { sDay ->
                val point = history.firstOrNull { it.day == sDay }
                if (point != null) {
                    val isPastOrToday = point.actualCumulative != null
                    val amount = point.actualCumulative ?: point.projectedCumulative ?: 0.0
                    val ideal = point.idealBudgetCumulative ?: 0.0
                    val diff = amount - ideal

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        border = borderStroke()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    "Día $sDay del mes ${if (point.isToday) "(Hoy)" else if (isPastOrToday) "(Real)" else "(Proyectado)"}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Gasto acumulado: ${CurrencyFormatter.format(amount)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    if (diff > 0) "+${CurrencyFormatter.format(diff)} vs ideal" else "${CurrencyFormatter.format(diff)} vs ideal",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (diff > 0) ExpenseRed else IncomeGreen
                                )
                                IconButton(
                                    onClick = { onSelectDay(null) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Cerrar detalle",
                                        modifier = Modifier.size(16.dp)
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

// -----------------------------------------------------------------------------------------
// SIMULADOR INTERACTIVO DE RITMO DIARIO ("¿QUÉ PASA SI CAMBIO MI RITMO?")
// -----------------------------------------------------------------------------------------

@Composable
private fun DailySpendingSimulatorCard(
    forecast: ExpenseForecast,
    simulatedPace: Double,
    simulatedExpense: Double,
    simulatedSavings: Double,
    onPaceChanged: (Double) -> Unit,
    onReset: () -> Unit
) {
    val maxSliderValue = (forecast.averageDailySpent * 2.5).coerceAtLeast(150.0).toFloat()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("daily_spending_simulator_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = borderStroke()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Simulador de Ritmo Diario",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                TextButton(onClick = onReset) {
                    Text("Restablecer", style = MaterialTheme.typography.labelSmall)
                }
            }

            Text(
                text = "Ajusta cuánto gastar por día durante los ${forecast.daysRemaining} días restantes para ver el impacto en tu cierre de mes:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Chips de acceso rápido
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = abs(simulatedPace - forecast.safeDailyRemainingBudget) < 1.0,
                    onClick = { onPaceChanged(forecast.safeDailyRemainingBudget) },
                    label = { Text("Seguro (${CurrencyFormatter.format(forecast.safeDailyRemainingBudget)}/d)") },
                    modifier = Modifier.testTag("simulator_chip_safe")
                )
                FilterChip(
                    selected = abs(simulatedPace - (forecast.averageDailySpent * 0.85)) < 1.0,
                    onClick = { onPaceChanged(forecast.averageDailySpent * 0.85) },
                    label = { Text("-15% Moderado") },
                    modifier = Modifier.testTag("simulator_chip_moderate")
                )
                FilterChip(
                    selected = abs(simulatedPace - forecast.averageDailySpent) < 1.0,
                    onClick = { onPaceChanged(forecast.averageDailySpent) },
                    label = { Text("Actual (${CurrencyFormatter.format(forecast.averageDailySpent)}/d)") },
                    modifier = Modifier.testTag("simulator_chip_current")
                )
            }

            // Control deslizante
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Ritmo diario simulado:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "${CurrencyFormatter.format(simulatedPace)} / día",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Slider(
                    value = simulatedPace.toFloat().coerceIn(0f, maxSliderValue),
                    onValueChange = { onPaceChanged(it.toDouble()) },
                    valueRange = 0f..maxSliderValue,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("simulator_pace_slider"),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
            }

            // Resultados en tiempo real de la simulación
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = borderStroke()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "Gasto Total Proyectado",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            CurrencyFormatter.format(simulatedExpense),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "Superávit / Déficit",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val isPositive = simulatedSavings >= 0
                        Text(
                            CurrencyFormatter.format(simulatedSavings),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isPositive) IncomeGreen else ExpenseRed
                        )
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------------------
// ESCENARIOS PREDICTIVOS
// -----------------------------------------------------------------------------------------

@Composable
private fun PredictiveScenariosCard(scenarios: List<PredictiveScenario>) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("predictive_scenarios_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = borderStroke()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.CompareArrows,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Escenarios de Cierre de Mes",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            scenarios.forEach { scenario ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (scenario.isCurrentPace) {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                    border = BorderStroke(
                        1.dp,
                        if (scenario.isCurrentPace) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = scenario.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${CurrencyFormatter.format(scenario.dailyPace)} / día",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Text(
                            text = scenario.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Gasto final: ${CurrencyFormatter.format(scenario.projectedTotalExpense)}",
                                style = MaterialTheme.typography.labelSmall
                            )
                            val isPos = scenario.projectedBalance >= 0
                            Text(
                                "Balance: ${CurrencyFormatter.format(scenario.projectedBalance)}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isPos) IncomeGreen else ExpenseRed
                            )
                        }
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------------------
// VELOCIDAD DE CONSUMO POR CATEGORÍA
// -----------------------------------------------------------------------------------------

@Composable
private fun CategoryVelocitiesCard(velocities: List<CategorySpendingVelocity>) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("category_velocities_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = borderStroke()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Category,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Velocidad por Categoría",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "${velocities.size} activas",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            velocities.forEach { cat ->
                val icon = getPredictionCategoryIcon(cat.iconKey)
                val catColor = getPredictionCategoryColor(cat.iconKey)

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border = BorderStroke(
                        1.dp,
                        if (cat.isOverBudgetRisk) ExpenseRed.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                    )
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
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = catColor.copy(alpha = 0.18f),
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = null,
                                            tint = catColor,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                Column {
                                    Text(
                                        text = cat.categoryName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${CurrencyFormatter.format(cat.dailyAverage)} / día",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    CurrencyFormatter.format(cat.currentSpent),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Proyecta: ${CurrencyFormatter.format(cat.projectedMonthEnd)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (cat.isOverBudgetRisk) ExpenseRed else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Presupuesto y ritmo sugerido si aplica
                        if (cat.budgetLimit != null && cat.budgetLimit > 0) {
                            val fraction = (cat.currentSpent / cat.budgetLimit).toFloat().coerceIn(0f, 1f)
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                LinearProgressIndicator(
                                    progress = { fraction },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = if (cat.isOverBudgetRisk) ExpenseRed else catColor,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "Límite: ${CurrencyFormatter.format(cat.budgetLimit)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    cat.safeDailyAllowance?.let { safeDaily ->
                                        Text(
                                            "Ritmo sugerido: ${CurrencyFormatter.format(safeDaily)}/día",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (cat.isOverBudgetRisk) ExpenseRed else IncomeGreen
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
}

// -----------------------------------------------------------------------------------------
// RECOMENDACIONES FINANCIERAS ACCIONABLES
// -----------------------------------------------------------------------------------------

@Composable
private fun ActionableTipsSection(tips: List<ActionableFinancialTip>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Lightbulb,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Recomendaciones Accionables",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        tips.forEach { tip ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("actionable_tip_${tip.id}"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when (tip.priority) {
                        AnomalySeverity.HIGH -> ExpenseRed.copy(alpha = 0.08f)
                        AnomalySeverity.MEDIUM -> Color(0xFFFFA000).copy(alpha = 0.08f)
                        AnomalySeverity.INFO -> MaterialTheme.colorScheme.surface
                    }
                ),
                border = BorderStroke(
                    1.dp,
                    when (tip.priority) {
                        AnomalySeverity.HIGH -> ExpenseRed.copy(alpha = 0.35f)
                        AnomalySeverity.MEDIUM -> Color(0xFFFFA000).copy(alpha = 0.35f)
                        AnomalySeverity.INFO -> MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                    }
                )
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = tip.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = when (tip.priority) {
                                AnomalySeverity.HIGH -> ExpenseRed
                                AnomalySeverity.MEDIUM -> Color(0xFFFFA000)
                                AnomalySeverity.INFO -> MaterialTheme.colorScheme.primary
                            }
                        )
                        tip.potentialMonthlySaving?.let { saving ->
                            if (saving > 0) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = IncomeGreen.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = "+${CurrencyFormatter.format(saving)} ahorro",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = IncomeGreen,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        text = tip.recommendation,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

private fun getPredictionCategoryIcon(key: String): ImageVector {
    return when (key) {
        "restaurant" -> Icons.Default.Restaurant
        "directions_car" -> Icons.Default.DirectionsCar
        "sports_esports" -> Icons.Default.SportsEsports
        "medical_services" -> Icons.Default.MedicalServices
        "school" -> Icons.Default.School
        "receipt" -> Icons.Default.Receipt
        "attach_money" -> Icons.Default.AttachMoney
        "storefront" -> Icons.Default.Storefront
        "swap_horiz" -> Icons.Default.SwapHoriz
        else -> Icons.Default.Wallet
    }
}

private fun getPredictionCategoryColor(key: String): Color {
    return when (key) {
        "restaurant" -> Color(0xFFFF9800)
        "directions_car" -> Color(0xFF2196F3)
        "sports_esports" -> Color(0xFF9C27B0)
        "medical_services" -> Color(0xFFE91E63)
        "school" -> Color(0xFF00BCD4)
        "receipt" -> Color(0xFF607D8B)
        "attach_money" -> Color(0xFF4CAF50)
        "storefront" -> Color(0xFF3F51B5)
        else -> Color(0xFF795548)
    }
}

// -----------------------------------------------------------------------------------------
// PESTAÑA 4: ESTRATEGIA DE AHORRO Y SIMULADOR DE SALUD FINANCIERA
// -----------------------------------------------------------------------------------------
@Composable
private fun SavingsStrategyTabContent(
    state: AssistantUiState,
    onGenerateStrategy: () -> Unit,
    onSetAllocationMode: (AllocationStrategyMode) -> Unit,
    onSetSimulatedSurplus: (Double) -> Unit,
    onOpenContributeDialog: (SavingsGoalStrategy) -> Unit,
    onSelectStressScenario: (Int) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("savings_strategy_scroll_container"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Tarjeta Hero de Estrategia
        item {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("savings_header_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                ),
                border = borderStroke()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(IncomeGreen.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Savings,
                                contentDescription = null,
                                tint = IncomeGreen
                            )
                        }
                        Column {
                            Text(
                                text = "Optimizador Dinámico de Metas",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Alineación inteligente de tus aportes con tus ingresos",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Text(
                        text = "Gemini analiza el tiempo restante, la prioridad y la liquidez disponible de tus cuentas para recomendarte el plan óptimo sin asfixiar tus finanzas diarias.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 2. Botón para generar estrategia con IA
        item {
            Button(
                onClick = onGenerateStrategy,
                enabled = !state.isGeneratingStrategy,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("generate_savings_strategy_button"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = IncomeGreen
                )
            ) {
                if (state.isGeneratingStrategy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Generando plan con Gemini...")
                } else {
                    Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Crear Estrategia de Ahorro Integral", fontWeight = FontWeight.Bold)
                }
            }
        }

        // 3. Consejo global de Gemini (si está disponible)
        state.aiGlobalSavingsAdvice?.let { advice ->
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("ai_savings_advice_card"),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(1.dp, IncomeGreen.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = IncomeGreen
                            )
                            Text(
                                text = "Plan Personalizado de Ahorro",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = IncomeGreen
                            )
                        }
                        Text(
                            text = advice,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // 4. Tarjeta de Salud Financiera y Prueba de Estrés
        state.financialHealthReport?.let { healthReport ->
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("financial_health_report_card"),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = borderStroke()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Cabecera con Score
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
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when {
                                                healthReport.overallScore >= 70 -> IncomeGreen.copy(alpha = 0.15f)
                                                healthReport.overallScore >= 50 -> Color(0xFFFFA000).copy(alpha = 0.15f)
                                                else -> ExpenseRed.copy(alpha = 0.15f)
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Shield,
                                        contentDescription = null,
                                        tint = when {
                                            healthReport.overallScore >= 70 -> IncomeGreen
                                            healthReport.overallScore >= 50 -> Color(0xFFFFA000)
                                            else -> ExpenseRed
                                        },
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = "Salud Financiera",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Evaluación integral de solvencia",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // Score Badge
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = when {
                                    healthReport.overallScore >= 70 -> IncomeGreen.copy(alpha = 0.15f)
                                    healthReport.overallScore >= 50 -> Color(0xFFFFA000).copy(alpha = 0.15f)
                                    else -> ExpenseRed.copy(alpha = 0.15f)
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "${healthReport.overallScore}/100",
                                        fontWeight = FontWeight.ExtraBold,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = when {
                                            healthReport.overallScore >= 70 -> IncomeGreen
                                            healthReport.overallScore >= 50 -> Color(0xFFFFA000)
                                            else -> ExpenseRed
                                        }
                                    )
                                    Text(
                                        text = "• ${healthReport.grade}",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = when {
                                            healthReport.overallScore >= 70 -> IncomeGreen
                                            healthReport.overallScore >= 50 -> Color(0xFFFFA000)
                                            else -> ExpenseRed
                                        }
                                    )
                                }
                            }
                        }

                        Text(
                            text = healthReport.summary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        // 4 Pilares de salud
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            healthReport.metrics.forEach { metric ->
                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = metric.name,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = metric.status,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = when (metric.status) {
                                                "Excelente", "Robusto", "Equilibrado", "Óptimo" -> IncomeGreen
                                                "Bueno", "Aceptable", "En Construcción", "Regular" -> Color(0xFFFFA000)
                                                else -> ExpenseRed
                                            }
                                        )
                                    }
                                    LinearProgressIndicator(
                                        progress = { (metric.score / 100f).coerceIn(0f, 1f) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                            .clip(CircleShape),
                                        color = when (metric.status) {
                                            "Excelente", "Robusto", "Equilibrado", "Óptimo" -> IncomeGreen
                                            "Bueno", "Aceptable", "En Construcción", "Regular" -> Color(0xFFFFA000)
                                            else -> ExpenseRed
                                        },
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    Text(
                                        text = metric.description,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        // Simulador de Estrés Financiero ("¿Qué pasaría si...?")
                        Text(
                            text = "Prueba de Estrés: Simular Escenarios de Riesgo",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val scenarios = listOf(
                                0 to "Línea Base",
                                1 to "Caída Ingresos (-25%)",
                                2 to "Emergencia $500",
                                3 to "Ahorro Hormiga (-20%)"
                            )
                            scenarios.forEach { (idx, title) ->
                                FilterChip(
                                    selected = state.selectedStressScenario == idx,
                                    onClick = { onSelectStressScenario(idx) },
                                    label = { Text(title, style = MaterialTheme.typography.labelSmall) },
                                    leadingIcon = if (state.selectedStressScenario == idx) {
                                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                    } else null
                                )
                            }
                        }

                        state.stressTestResult?.let { stress ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = when (stress.impactSeverity) {
                                    AnomalySeverity.HIGH -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                                    AnomalySeverity.MEDIUM -> Color(0xFFFFF3E0)
                                    AnomalySeverity.INFO -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                }
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = stress.scenarioName,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        Text(
                                            text = "Colchón: ${String.format(Locale.US, "%.1f", stress.simulatedRunwayMonths)} meses",
                                            fontWeight = FontWeight.ExtraBold,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (stress.simulatedRunwayMonths >= 3.0) IncomeGreen else ExpenseRed
                                        )
                                    }
                                    Text(
                                        text = stress.advice,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5. Distribuidor Inteligente de Excedente (Surplus Allocator)
        if (state.savingsStrategies.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("surplus_allocator_card"),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = borderStroke()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
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
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Distribución Inteligente de Excedente",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        val availableIncome = state.forecast?.totalIncome ?: 0.0
                        val spentSoFar = state.forecast?.currentSpent ?: 0.0
                        val currentSurplus = (availableIncome - spentSoFar).coerceAtLeast(0.0)

                        Text(
                            text = if (currentSurplus > 0) {
                                "Excedente mensual detectado: ${CurrencyFormatter.format(currentSurplus)}. Simula cómo distribuirlo entre tus metas activas:"
                            } else {
                                "Ajusta un monto de aporte mensual para simular cómo acelerará la culminación de cada una de tus metas:"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Selector de Modo de Asignación
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AllocationStrategyMode.values().forEach { mode ->
                                FilterChip(
                                    selected = state.allocationMode == mode,
                                    onClick = { onSetAllocationMode(mode) },
                                    label = { Text(mode.label, style = MaterialTheme.typography.labelSmall) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        Text(
                            text = state.allocationMode.description,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )

                        // Slider de Monto a Distribuir
                        val currentAllocate = state.simulatedSurplusToAllocate ?: (if (currentSurplus > 0) currentSurplus else 200.0)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Monto Total a Asignar:",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = CurrencyFormatter.format(currentAllocate),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = IncomeGreen
                            )
                        }

                        Slider(
                            value = currentAllocate.toFloat(),
                            onValueChange = { onSetSimulatedSurplus(it.toDouble()) },
                            valueRange = 0f..2000f,
                            steps = 39,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Chips rápidos de monto
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(50.0, 100.0, 250.0, 500.0).forEach { quickAmt ->
                                SuggestionChip(
                                    onClick = { onSetSimulatedSurplus(quickAmt) },
                                    label = { Text("+${CurrencyFormatter.format(quickAmt)}", style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                            if (currentSurplus > 0) {
                                SuggestionChip(
                                    onClick = { onSetSimulatedSurplus(currentSurplus) },
                                    label = { Text("Excedente Real", style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // 6. Lista de Metas Activas con Asignación Simulada e Hitos
        item {
            Text(
                text = "Metas Activas y Asignación Simulada (${state.savingsStrategies.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        if (state.savingsStrategies.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = borderStroke()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Savings,
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text("No tienes metas de ahorro registradas aún.", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Agrega tus metas en la sección 'Metas' para que la IA elabore un plan paso a paso.",
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(state.savingsStrategies, key = { it.goalId }) { itemStrategy ->
                val allocation = state.goalAllocations.find { it.goalId == itemStrategy.goalId }
                val allocatedAmount = allocation?.allocatedAmount ?: 0.0

                Card(
                    modifier = Modifier.fillMaxWidth().testTag("savings_strategy_item_${itemStrategy.goalId}"),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = borderStroke()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Fila superior: Categoría, Prioridad y Nombre
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                SuggestionChip(
                                    onClick = {},
                                    label = { Text(itemStrategy.category, style = MaterialTheme.typography.labelSmall) }
                                )
                                val priorityColor = when (itemStrategy.priority) {
                                    "Alta" -> MaterialTheme.colorScheme.error
                                    "Media" -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.secondary
                                }
                                val priorityBg = when (itemStrategy.priority) {
                                    "Alta" -> MaterialTheme.colorScheme.errorContainer
                                    "Media" -> MaterialTheme.colorScheme.primaryContainer
                                    else -> MaterialTheme.colorScheme.secondaryContainer
                                }
                                Surface(
                                    color = priorityBg,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = itemStrategy.priority,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = priorityColor,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Text(
                                text = "${String.format(Locale.US, "%.1f", itemStrategy.progressPercentage * 100)}%",
                                fontWeight = FontWeight.ExtraBold,
                                color = IncomeGreen,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }

                        Text(
                            text = itemStrategy.goalName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        // Progreso monetario
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${CurrencyFormatter.format(itemStrategy.currentAmount)} de ${CurrencyFormatter.format(itemStrategy.targetAmount)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Faltan ${CurrencyFormatter.format(itemStrategy.remainingAmount)}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // Barra de progreso con simulación de aporte
                        val displayProgress = allocation?.newProgressPercentage ?: itemStrategy.progressPercentage
                        LinearProgressIndicator(
                            progress = { displayProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .clip(CircleShape),
                            color = if (allocatedAmount > 0) IncomeGreen else BluePrimary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )

                        // Impacto de asignación simulada
                        if (allocatedAmount > 0 && allocation != null) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = IncomeGreen.copy(alpha = 0.12f)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "+${CurrencyFormatter.format(allocatedAmount)} asignado en simulación",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = IncomeGreen
                                    )
                                    if (allocation.monthsSaved > 0) {
                                        Text(
                                            text = "Avanzas ~${String.format(Locale.US, "%.1f", allocation.monthsSaved)} meses",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = IncomeGreen
                                        )
                                    }
                                }
                            }
                        }

                        // Seguimiento de Hitos de Ahorro (25%, 50%, 75%, 100%)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Hitos de cumplimiento:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                itemStrategy.milestones.forEach { milestone ->
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (milestone.isReached) {
                                            IncomeGreen.copy(alpha = 0.15f)
                                        } else if (milestone.percentage == itemStrategy.nextMilestone?.percentage) {
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        },
                                        border = if (milestone.percentage == itemStrategy.nextMilestone?.percentage) {
                                            BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                                        } else null,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                                            ) {
                                                if (milestone.isReached) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = IncomeGreen,
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                }
                                                Text(
                                                    text = "${milestone.percentage}%",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (milestone.isReached) IncomeGreen else MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            itemStrategy.nextMilestone?.let { next ->
                                Text(
                                    text = "Siguiente hito (${next.percentage}%): faltan ${CurrencyFormatter.format(next.remainingToMilestone)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        // Aporte mensual recomendado
                        itemStrategy.recommendedMonthlyRate?.let { rate ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Aporte mensual sugerido:",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        CurrencyFormatter.format(rate),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        Text(
                            text = itemStrategy.aiInsight,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (itemStrategy.actionItems.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                itemStrategy.actionItems.forEach { action ->
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = IncomeGreen
                                        )
                                        Text(
                                            text = action,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }

                        // Botón de Aportar Fondos directo
                        Button(
                            onClick = { onOpenContributeDialog(itemStrategy) },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = IncomeGreen
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                                .testTag("contribute_to_goal_button_${itemStrategy.goalId}")
                        ) {
                            Icon(imageVector = Icons.Default.Paid, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Aportar Dinero a esta Meta", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------------------
// DIÁLOGO DE APORTE DIRECTO DESDE EL ASISTENTE
// -----------------------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantContributeDialog(
    goal: SavingsGoalStrategy,
    accounts: List<Account>,
    onConfirm: (amount: Double, accountId: Long) -> Unit,
    onDismiss: () -> Unit
) {
    var amountText by remember { mutableStateOf("") }
    var selectedAccountId by remember { mutableStateOf<Long?>(accounts.firstOrNull()?.id) }
    var accountMenuExpanded by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val selectedAccount = accounts.find { it.id == selectedAccountId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Paid,
                    contentDescription = null,
                    tint = IncomeGreen
                )
                Text(
                    text = "Aportar a '${goal.goalName}'",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Faltan ${CurrencyFormatter.format(goal.remainingAmount)} para completar esta meta (${String.format(Locale.US, "%.1f", goal.progressPercentage * 100)}% alcanzado).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Selector de Cuenta de Origen
                Column {
                    Text(
                        text = "Cuenta de Origen:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    if (accounts.isEmpty()) {
                        Text(
                            text = "No tienes cuentas registradas.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        ExposedDropdownMenuBox(
                            expanded = accountMenuExpanded,
                            onExpandedChange = { accountMenuExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = selectedAccount?.let { "${it.name} (${CurrencyFormatter.format(it.currentBalance)})" } ?: "Seleccionar cuenta",
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = accountMenuExpanded) },
                                modifier = Modifier
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                                    .fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )
                            ExposedDropdownMenu(
                                expanded = accountMenuExpanded,
                                onDismissRequest = { accountMenuExpanded = false }
                            ) {
                                accounts.forEach { acc ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(acc.name, fontWeight = FontWeight.Medium)
                                                Text(
                                                    CurrencyFormatter.format(acc.currentBalance),
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (acc.currentBalance > 0) IncomeGreen else ExpenseRed
                                                )
                                            }
                                        },
                                        onClick = {
                                            selectedAccountId = acc.id
                                            accountMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Campo de Monto con AmountTextField
                AmountTextField(
                    value = amountText,
                    onValueChange = {
                        amountText = it
                        errorMsg = null
                    },
                    label = "Monto a aportar",
                    modifier = Modifier.fillMaxWidth()
                )

                // Chips rápidos de aporte
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val availableBal = selectedAccount?.currentBalance ?: 0.0
                    val quickAmounts = listOf(
                        "25" to 25.0,
                        "50" to 50.0,
                        "100" to 100.0,
                        "Todo" to availableBal.coerceAtMost(goal.remainingAmount)
                    )
                    quickAmounts.forEach { (label, amt) ->
                        if (amt > 0) {
                            FilterChip(
                                selected = false,
                                onClick = {
                                    amountText = String.format(Locale.US, "%.2f", amt)
                                    errorMsg = null
                                },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                errorMsg?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsed = amountText.toDoubleOrNull()
                    if (parsed == null || parsed <= 0.0) {
                        errorMsg = "Por favor ingresa un monto válido mayor a 0."
                        return@Button
                    }
                    val accId = selectedAccountId
                    if (accId == null) {
                        errorMsg = "Por favor selecciona una cuenta de origen."
                        return@Button
                    }
                    val bal = selectedAccount?.currentBalance ?: 0.0
                    if (parsed > bal) {
                        errorMsg = "Fondos insuficientes en la cuenta seleccionada (${CurrencyFormatter.format(bal)} disponibles)."
                        return@Button
                    }
                    onConfirm(parsed, accId)
                },
                colors = ButtonDefaults.buttonColors(containerColor = IncomeGreen),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Confirmar Aporte", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

// -----------------------------------------------------------------------------------------
// COMPONENTES COMUNES DE BURBUJAS Y ESTILO
// -----------------------------------------------------------------------------------------
@Composable
fun MessageBubble(
    message: ChatMessage,
    modifier: Modifier = Modifier
) {
    val bubbleShape = if (message.isUser) {
        RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
    } else {
        RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)
    }

    val bubbleColor = if (message.isUser) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
    }

    val alignment = if (message.isUser) Alignment.End else Alignment.Start

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = if (message.isUser) 64.dp else 0.dp,
                end = if (message.isUser) 0.dp else 64.dp
            ),
        horizontalAlignment = alignment
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!message.isUser) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(BluePrimary, Color(0xFF8E24AA))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Surface(
                color = bubbleColor,
                shape = bubbleShape,
                border = if (!message.isUser) {
                    borderStroke()
                } else null
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (message.isUser) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun TypingIndicatorBubble(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "typing")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    val bubbleShape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)
    val bubbleColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(end = 64.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(BluePrimary, Color(0xFF8E24AA))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }

        Surface(
            color = bubbleColor,
            shape = bubbleShape,
            border = borderStroke()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = alpha)))
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = (alpha + 0.3f).coerceIn(0f, 1f))))
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = (alpha + 0.6f).coerceIn(0f, 1f))))
            }
        }
    }
}

@Composable
fun SuggestedPromptsSection(
    onPromptSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val prompts = listOf(
        "¿Cómo voy con mis metas de ahorro este mes?",
        "¿Cuáles son mis categorías con mayor gasto?",
        "Estrategia para ahorrar más en las próximas dos semanas",
        "¿Cuál es mi balance general de cuentas?"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Preguntas rápidas sugeridas:",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        prompts.forEachIndexed { index, prompt ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPromptSelected(prompt) }
                    .testTag("suggested_prompt_$index"),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ),
                border = borderStroke()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = prompt,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun borderStroke() = androidx.compose.foundation.BorderStroke(
    width = 1.dp,
    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
)
