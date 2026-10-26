package com.example.presentation.monthlysummary

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.Transaction
import com.example.presentation.common.util.CurrencyFormatter
import com.example.presentation.common.util.DateFormatter
import com.example.presentation.common.util.IconHelper
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.IncomeGreen
import java.util.Calendar
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private val REPORT_PALETTE = listOf(
    Color(0xFF3B82F6), // Blue
    Color(0xFFEF4444), // Red
    Color(0xFF10B981), // Emerald
    Color(0xFFF59E0B), // Amber
    Color(0xFF8B5CF6), // Purple
    Color(0xFF06B6D4), // Cyan
    Color(0xFFEC4899), // Pink
    Color(0xFF6366F1), // Indigo
    Color(0xFF14B8A6), // Teal
    Color(0xFFF97316)  // Orange
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthlySummaryScreen(
    viewModel: MonthlySummaryViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToAddExpense: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onNavigateBack)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showMonthPickerSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Reportes Mensuales",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (state is MonthlySummaryUiState.Success) {
                            val summary = (state as MonthlySummaryUiState.Success).summary
                            Text(
                                text = summary.monthName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("monthly_summary_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver"
                        )
                    }
                },
                actions = {
                    if (state is MonthlySummaryUiState.Success) {
                        val summary = (state as MonthlySummaryUiState.Success).summary
                        IconButton(
                            onClick = {
                                shareSummaryReport(context, summary)
                            },
                            modifier = Modifier.testTag("monthly_summary_share_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Share,
                                contentDescription = "Compartir reporte"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            when (val currentState = state) {
                is MonthlySummaryUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(40.dp))
                    }
                }
                is MonthlySummaryUiState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = currentState.message,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                is MonthlySummaryUiState.Success -> {
                    MonthlySummaryContent(
                        state = currentState,
                        onPreviousMonth = { viewModel.previousMonth() },
                        onNextMonth = { viewModel.nextMonth() },
                        onOpenMonthPicker = { showMonthPickerSheet = true },
                        onResetToCurrentMonth = { viewModel.resetToCurrentMonth() },
                        onNavigateToAddExpense = onNavigateToAddExpense,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }

    if (showMonthPickerSheet && state is MonthlySummaryUiState.Success) {
        val successState = state as MonthlySummaryUiState.Success
        MonthPickerBottomSheet(
            selectedYear = successState.selectedYear,
            selectedMonth = successState.selectedMonth,
            onSelect = { year, month ->
                viewModel.setMonthYear(year, month)
                showMonthPickerSheet = false
            },
            onDismiss = { showMonthPickerSheet = false }
        )
    }
}

@Composable
private fun MonthlySummaryContent(
    state: MonthlySummaryUiState.Success,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onOpenMonthPicker: () -> Unit,
    onResetToCurrentMonth: () -> Unit,
    onNavigateToAddExpense: () -> Unit,
    modifier: Modifier = Modifier
) {
    val summary = state.summary
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedCategoryDetail by remember { mutableStateOf<CategoryMonthlyShare?>(null) }
    var selectedDayItem by remember { mutableStateOf<DailySpendingItem?>(null) }

    val tabTitles = listOf("Visión General", "Categorías", "Informe")

    Column(
        modifier = modifier
            .widthIn(max = 840.dp)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Selector de Mes y Año con navegación rápida compacta y rango de ciclo
        MonthNavigatorBar(
            monthName = summary.monthName,
            dateRangeLabel = if (summary.isCustomCycle) summary.dateRangeLabel else "",
            isCurrentMonth = isCurrentCalendarMonth(summary.year, summary.month),
            onPrevious = onPreviousMonth,
            onNext = onNextMonth,
            onOpenPicker = onOpenMonthPicker,
            onResetToCurrent = onResetToCurrentMonth
        )

        // 2. Selector de Pestañas de Reporte
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
        ) {
            tabTitles.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Text(
                            text = title,
                            fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 13.sp
                        )
                    }
                )
            }
        }

        when (selectedTab) {
            0 -> {
                // Pestaña 1: Visión General
                HeroSpendingCard(summary = summary)
                MonthlyBalanceCard(summary = summary)

                if (!summary.hasExpenses) {
                    EmptyMonthCard(
                        monthName = summary.monthName,
                        onNavigateToAddExpense = onNavigateToAddExpense
                    )
                } else {
                    // Desglose Quincenal (armonización de sueldos de días 15 y 30)
                    if (summary.fortnights.isNotEmpty()) {
                        FortnightsBreakdownCard(fortnights = summary.fortnights)
                    }

                    // Distribución Semanal
                    if (summary.weeklySpendingHistory.isNotEmpty()) {
                        WeeklyBreakdownCard(weeks = summary.weeklySpendingHistory)
                    }

                    // Gráfico Diario Compacto
                    DailySpendingChartCard(
                        dailyHistory = summary.dailySpendingHistory,
                        selectedDay = selectedDayItem,
                        onSelectDay = { day ->
                            selectedDayItem = if (selectedDayItem?.day == day.day) null else day
                        }
                    )

                    // Mayores Compras del Mes
                    if (summary.topExpenses.isNotEmpty()) {
                        TopExpensesCard(topExpenses = summary.topExpenses)
                    }

                    // Observaciones Financieras
                    MonthlyInsightsCard(summary = summary)
                }
            }

            1 -> {
                // Pestaña 2: Desglose y Gráficos de Categorías
                if (!summary.hasExpenses) {
                    EmptyMonthCard(
                        monthName = summary.monthName,
                        onNavigateToAddExpense = onNavigateToAddExpense
                    )
                } else {
                    // Gráfico Donut de Categorías
                    CategoryDonutChartCard(
                        categories = summary.categoryBreakdown,
                        totalExpense = summary.totalExpense,
                        onCategoryClick = { selectedCategoryDetail = it }
                    )

                    // Lista de Categorías
                    CategoryBreakdownCard(
                        categories = summary.categoryBreakdown,
                        totalExpense = summary.totalExpense,
                        onCategoryClick = { selectedCategoryDetail = it }
                    )
                }
            }

            2 -> {
                // Pestaña 3: Informe Completo y Exportación
                FullReportView(
                    summary = summary,
                    onShareReport = { shareSummaryReport(context, summary) },
                    onCopyReport = { copyReportToClipboard(context, summary) }
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }

    // Modal BottomSheet de detalle de transacciones por categoría
    selectedCategoryDetail?.let { catDetail ->
        val categoryTransactions = remember(catDetail, summary.allMonthExpenses) {
            summary.allMonthExpenses.filter { it.category.id == catDetail.categoryId }
        }
        CategoryDetailBottomSheet(
            category = catDetail,
            monthName = summary.monthName,
            transactions = categoryTransactions,
            onDismiss = { selectedCategoryDetail = null }
        )
    }
}

@Composable
private fun MonthNavigatorBar(
    monthName: String,
    dateRangeLabel: String = "",
    isCurrentMonth: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onOpenPicker: () -> Unit,
    onResetToCurrent: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = onPrevious,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("prev_month_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Mes anterior",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onOpenPicker() }
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.DateRange,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = monthName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    if (dateRangeLabel.isNotBlank()) {
                        Text(
                            text = "Ciclo: $dateRangeLabel",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!isCurrentMonth) {
                    FilledTonalButton(
                        onClick = onResetToCurrent,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text("Hoy", style = MaterialTheme.typography.labelSmall)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }

                IconButton(
                    onClick = onNext,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("next_month_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Mes siguiente",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroSpendingCard(summary: MonthlySpendingSummary) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "GASTO TOTAL",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = CurrencyFormatter.format(summary.totalExpense),
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Black
                        ),
                        color = ExpenseRed
                    )
                }

                // Insignia comparativa
                if (summary.hasExpenses) {
                    val isHigher = summary.isExpenseHigherThanPrevious
                    val deltaAbs = Math.abs(summary.expenseDeltaPercentage)
                    val deltaFormatted = String.format("%.1f", deltaAbs)

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isHigher) ExpenseRed.copy(alpha = 0.12f) else IncomeGreen.copy(alpha = 0.12f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = if (isHigher) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                                contentDescription = null,
                                tint = if (isHigher) ExpenseRed else IncomeGreen,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "$deltaFormatted%",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isHigher) ExpenseRed else IncomeGreen
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            // Cuadrícula 2x2 de métricas compactas
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricItem(
                    label = "Promedio Diario",
                    value = CurrencyFormatter.format(summary.dailyAverage),
                    icon = Icons.Default.Analytics,
                    modifier = Modifier.weight(1f)
                )

                MetricItem(
                    label = "Ticket Promedio",
                    value = CurrencyFormatter.format(summary.averagePerTransaction),
                    icon = Icons.AutoMirrored.Filled.ReceiptLong,
                    modifier = Modifier.weight(1f)
                )

                MetricItem(
                    label = "Transacciones",
                    value = "${summary.transactionCount}",
                    icon = Icons.Default.ShoppingCart,
                    modifier = Modifier.weight(1f)
                )

                MetricItem(
                    label = "Día Pico",
                    value = if (summary.peakExpenseDay != null) "Día ${summary.peakExpenseDay}" else "-",
                    icon = Icons.Default.DateRange,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun MetricItem(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun MonthlyBalanceCard(summary: MonthlySpendingSummary) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Balance del Período",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Ingresos: ${CurrencyFormatter.format(summary.totalIncome)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = IncomeGreen,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Gastos: ${CurrencyFormatter.format(summary.totalExpense)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = ExpenseRed,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (summary.netSavings >= 0) IncomeGreen.copy(alpha = 0.12f) else ExpenseRed.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, if (summary.netSavings >= 0) IncomeGreen.copy(alpha = 0.25f) else ExpenseRed.copy(alpha = 0.25f))
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = if (summary.netSavings >= 0) "Ahorro Neto" else "Déficit",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = if (summary.netSavings >= 0) IncomeGreen else ExpenseRed,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = CurrencyFormatter.format(summary.netSavings),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Black,
                        color = if (summary.netSavings >= 0) IncomeGreen else ExpenseRed
                    )
                }
            }
        }
    }
}

@Composable
private fun FortnightsBreakdownCard(fortnights: List<FortnightSpendingItem>) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("monthly_summary_fortnights_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
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
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DateRange,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Desglose por Quincenas",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Control de sueldos de días 15 y 30",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                fortnights.forEach { item ->
                    val isSurplus = item.netBalance >= 0
                    val formattedIncome = CurrencyFormatter.format(item.incomeAmount)
                    val formattedExpense = CurrencyFormatter.format(item.expenseAmount)
                    val formattedBalance = CurrencyFormatter.format(item.netBalance)

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSurplus) Color(0xFF2ECC71).copy(alpha = 0.35f)
                            else Color(0xFFFF7675).copy(alpha = 0.35f)
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = item.dateRangeLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp
                            )

                            Spacer(modifier = Modifier.height(2.dp))

                            // Ingreso
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Ingresos:",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                                Text(
                                    text = "+$formattedIncome",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF2ECC71),
                                    fontSize = 11.sp
                                )
                            }

                            // Gasto
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Gastos:",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                                Text(
                                    text = "-$formattedExpense",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFF7675),
                                    fontSize = 11.sp
                                )
                            }

                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                thickness = 0.5.dp
                            )

                            // Balance neto
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Neto:",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (isSurplus) "+$formattedBalance" else formattedBalance,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (isSurplus) Color(0xFF2ECC71) else Color(0xFFFF7675)
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
private fun WeeklyBreakdownCard(weeks: List<WeeklySpendingItem>) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
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
                Text(
                    text = "Distribución Semanal",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Por semanas del mes",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            weeks.forEachIndexed { index, week ->
                val barColor = REPORT_PALETTE[index % REPORT_PALETTE.size]
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(barColor)
                            )
                            Text(
                                text = "${week.weekLabel} (${week.dateRangeLabel})",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Text(
                            text = CurrencyFormatter.format(week.totalAmount),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    LinearProgressIndicator(
                        progress = { week.percentageOfTotal.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = barColor,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

@Composable
private fun DailySpendingChartCard(
    dailyHistory: List<DailySpendingItem>,
    selectedDay: DailySpendingItem?,
    onSelectDay: (DailySpendingItem) -> Unit
) {
    val maxDailyAmount = dailyHistory.maxOfOrNull { it.amount }?.coerceAtLeast(100.0) ?: 100.0

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
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
                Column {
                    Text(
                        text = "Historial Diario de Gastos",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Toca una barra para ver detalle",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (selectedDay != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "Día ${selectedDay.day}: ${CurrencyFormatter.format(selectedDay.amount)}",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            // Gráfico de barras desplazable con altura optimizada para móvil
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                dailyHistory.forEach { item ->
                    val isSelected = selectedDay?.day == item.day
                    val barHeightFraction = (item.amount / maxDailyAmount).toFloat().coerceIn(0.04f, 1f)

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onSelectDay(item) }
                            .padding(horizontal = 2.dp, vertical = 2.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(14.dp)
                                .height(80.dp),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(barHeightFraction)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(
                                        when {
                                            isSelected -> MaterialTheme.colorScheme.primary
                                            item.isPeakDay -> ExpenseRed
                                            item.amount > 0.0 -> ExpenseRed.copy(alpha = 0.7f)
                                            else -> Color.Transparent
                                        }
                                    )
                            )
                        }

                        Text(
                            text = "${item.day}",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            fontWeight = if (isSelected || item.isPeakDay) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryDonutChartCard(
    categories: List<CategoryMonthlyShare>,
    totalExpense: Double,
    onCategoryClick: (CategoryMonthlyShare) -> Unit
) {
    if (categories.isEmpty() || totalExpense <= 0) return
    var selectedIndex by remember { mutableStateOf<Int?>(null) }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Distribución Porcentual",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Start)
            )

            // Donut Canvas Compacto
            Box(
                modifier = Modifier.size(160.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(categories) {
                            detectTapGestures { offset ->
                                val centerX = size.width / 2f
                                val centerY = size.height / 2f
                                val dx = offset.x - centerX
                                val dy = offset.y - centerY
                                val dist = sqrt(dx * dx + dy * dy)
                                val radius = minOf(size.width, size.height) / 2f
                                val innerRadius = radius * 0.55f

                                if (dist in innerRadius..radius * 1.2f) {
                                    var touchAngle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                                    if (touchAngle < 0) touchAngle += 360f
                                    val relAngle = (touchAngle - 270f + 360f) % 360f

                                    var curAngle = 0f
                                    var clicked: Int? = null
                                    for (i in categories.indices) {
                                        val sweep = (categories[i].totalAmount / totalExpense).toFloat() * 360f
                                        if (relAngle in curAngle..(curAngle + sweep)) {
                                            clicked = i
                                            break
                                        }
                                        curAngle += sweep
                                    }
                                    selectedIndex = if (selectedIndex == clicked) null else clicked
                                }
                            }
                        }
                ) {
                    val strokeWidth = 24.dp.toPx()
                    val chartRadius = (size.minDimension - strokeWidth) / 2f
                    var startAngle = 270f

                    categories.forEachIndexed { index, cat ->
                        val sweep = (cat.totalAmount / totalExpense).toFloat() * 360f
                        val color = REPORT_PALETTE[index % REPORT_PALETTE.size]
                        val isSelected = selectedIndex == index

                        drawArc(
                            color = color,
                            startAngle = startAngle,
                            sweepAngle = sweep.coerceAtLeast(1f),
                            useCenter = false,
                            style = Stroke(
                                width = if (isSelected) strokeWidth * 1.25f else strokeWidth,
                                cap = StrokeCap.Butt
                            ),
                            topLeft = Offset((size.width - chartRadius * 2) / 2f, (size.height - chartRadius * 2) / 2f),
                            size = Size(chartRadius * 2, chartRadius * 2)
                        )
                        startAngle += sweep
                    }
                }

                // Centro del Donut con información
                val activeCat = selectedIndex?.let { categories.getOrNull(it) }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(16.dp)
                ) {
                    if (activeCat != null) {
                        Text(
                            text = activeCat.categoryName,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = CurrencyFormatter.format(activeCat.totalAmount),
                            style = MaterialTheme.typography.titleSmall.copy(fontSize = 12.sp),
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = "${String.format("%.1f", activeCat.percentageOfTotal * 100)}%",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = "TOTAL",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = CurrencyFormatter.format(totalExpense),
                            style = MaterialTheme.typography.titleSmall.copy(fontSize = 12.sp),
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryBreakdownCard(
    categories: List<CategoryMonthlyShare>,
    totalExpense: Double,
    onCategoryClick: (CategoryMonthlyShare) -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
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
                Text(
                    text = "Detalle por Categorías",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${categories.size} rubros",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                categories.forEachIndexed { index, item ->
                    val color = REPORT_PALETTE[index % REPORT_PALETTE.size]
                    CategoryShareRow(
                        item = item,
                        color = color,
                        onClick = { onCategoryClick(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryShareRow(
    item: CategoryMonthlyShare,
    color: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = IconHelper.getIcon(item.iconKey),
                    contentDescription = item.categoryName,
                    tint = color,
                    modifier = Modifier.size(16.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = item.categoryName,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = CurrencyFormatter.format(item.totalAmount),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(3.dp))

                LinearProgressIndicator(
                    progress = { item.percentageOfTotal },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = color,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${item.transactionCount} compras",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${String.format("%.1f", item.percentageOfTotal * 100)}%",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        fontWeight = FontWeight.SemiBold,
                        color = color
                    )
                }
            }
        }
    }
}

@Composable
private fun FullReportView(
    summary: MonthlySpendingSummary,
    onShareReport: () -> Unit,
    onCopyReport: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Tarjeta de Informe Ejecutivo
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "INFORME EJECUTIVO",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = summary.monthName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(onClick = onCopyReport, modifier = Modifier.size(36.dp)) {
                            Icon(
                                imageVector = Icons.Outlined.ContentCopy,
                                contentDescription = "Copiar reporte",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(onClick = onShareReport, modifier = Modifier.size(36.dp)) {
                            Icon(
                                imageVector = Icons.Outlined.Share,
                                contentDescription = "Compartir reporte",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                // Métricas Consolidadas del Informe
                ReportRowItem(label = "Ingresos Totales", value = CurrencyFormatter.format(summary.totalIncome), color = IncomeGreen)
                ReportRowItem(label = "Gastos Totales", value = CurrencyFormatter.format(summary.totalExpense), color = ExpenseRed)
                ReportRowItem(
                    label = "Ahorro / Balance Neto",
                    value = CurrencyFormatter.format(summary.netSavings),
                    color = if (summary.netSavings >= 0) IncomeGreen else ExpenseRed
                )
                ReportRowItem(label = "Tasa de Ahorro", value = "${String.format("%.1f", summary.savingsRatePercentage)}%", color = MaterialTheme.colorScheme.primary)
                ReportRowItem(label = "Promedio Diario de Gasto", value = CurrencyFormatter.format(summary.dailyAverage))
                ReportRowItem(label = "Ticket Promedio por Compra", value = CurrencyFormatter.format(summary.averagePerTransaction))
                ReportRowItem(label = "Total de Transacciones", value = "${summary.transactionCount}")

                if (summary.peakExpenseDay != null && summary.peakExpenseAmount > 0) {
                    ReportRowItem(
                        label = "Día de Mayor Gasto",
                        value = "Día ${summary.peakExpenseDay} (${CurrencyFormatter.format(summary.peakExpenseAmount)})"
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Botones de acción rápida del informe
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onShareReport,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Compartir", fontSize = 13.sp)
                    }

                    OutlinedButton(
                        onClick = onCopyReport,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copiar Texto", fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportRowItem(label: String, value: String, color: Color = MaterialTheme.colorScheme.onSurface) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
private fun TopExpensesCard(topExpenses: List<Transaction>) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Mayores Gastos del Mes",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                topExpenses.forEach { tx ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(ExpenseRed.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = IconHelper.getIcon(tx.category.iconKey),
                                contentDescription = null,
                                tint = ExpenseRed,
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = tx.description?.takeIf { it.isNotBlank() } ?: tx.category.name,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${DateFormatter.format(tx.date)} • ${tx.category.name}",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Text(
                            text = CurrencyFormatter.format(tx.amount),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = ExpenseRed
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthlyInsightsCard(summary: MonthlySpendingSummary) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Observaciones Financieras",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            val topCat = summary.categoryBreakdown.firstOrNull()
            if (topCat != null) {
                Text(
                    text = "• Tu principal categoría fue ${topCat.categoryName} (${String.format("%.1f", topCat.percentageOfTotal * 100)}% del total).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            if (summary.peakExpenseDay != null && summary.peakExpenseAmount > 0) {
                Text(
                    text = "• El día con mayor egreso fue el día ${summary.peakExpenseDay} (${CurrencyFormatter.format(summary.peakExpenseAmount)}).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            val trendText = if (summary.isExpenseHigherThanPrevious) {
                "Tus gastos subieron ${String.format("%.1f", Math.abs(summary.expenseDeltaPercentage))}% frente al mes anterior."
            } else {
                "Redujiste tus gastos ${String.format("%.1f", Math.abs(summary.expenseDeltaPercentage))}% respecto al mes previo. ¡Buen control!"
            }
            Text(
                text = "• $trendText",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun EmptyMonthCard(monthName: String, onNavigateToAddExpense: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.AccountBalanceWallet,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
            )
            Text(
                text = "Sin gastos en $monthName",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = "No se encontraron gastos registrados en este período. Registra tus salidas para ver el desglose.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Button(
                onClick = onNavigateToAddExpense,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Registrar Gasto", fontSize = 13.sp)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryDetailBottomSheet(
    category: CategoryMonthlyShare,
    monthName: String,
    transactions: List<Transaction>,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = IconHelper.getIcon(category.iconKey),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = category.categoryName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "$monthName • ${transactions.size} movimientos",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = CurrencyFormatter.format(category.totalAmount),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = ExpenseRed
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            if (transactions.isEmpty()) {
                Text(
                    text = "No se encontraron movimientos detallados para esta categoría.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    transactions.forEach { tx ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (!tx.description.isNullOrBlank()) tx.description else category.categoryName,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = DateFormatter.format(tx.date),
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Text(
                                    text = "-${CurrencyFormatter.format(tx.amount)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = ExpenseRed
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MonthPickerBottomSheet(
    selectedYear: Int,
    selectedMonth: Int,
    onSelect: (year: Int, month: Int) -> Unit,
    onDismiss: () -> Unit
) {
    val months = listOf(
        "Ene", "Feb", "Mar", "Abr", "May", "Jun",
        "Jul", "Ago", "Sep", "Oct", "Nov", "Dic"
    )
    val years = listOf(2024, 2025, 2026, 2027)
    var currentSelectedYear by remember { mutableIntStateOf(selectedYear) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Seleccionar Período",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            // Selector de Año
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                years.forEach { year ->
                    FilterChip(
                        selected = currentSelectedYear == year,
                        onClick = { currentSelectedYear = year },
                        label = { Text("$year", fontSize = 12.sp) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            HorizontalDivider()

            // Grilla de meses
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (row in 0 until 4) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        for (col in 0 until 3) {
                            val monthIndex = row * 3 + col
                            val isSelected = (currentSelectedYear == selectedYear && monthIndex == selectedMonth)

                            FilledTonalButton(
                                onClick = { onSelect(currentSelectedYear, monthIndex) },
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                                ),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = months[monthIndex],
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 12.sp,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

private fun isCurrentCalendarMonth(year: Int, month: Int): Boolean {
    val now = Calendar.getInstance()
    return now.get(Calendar.YEAR) == year && now.get(Calendar.MONTH) == month
}

private fun buildFormattedReportText(summary: MonthlySpendingSummary): String {
    val builder = StringBuilder()
    builder.append("📊 INFORME MENSUAL DE FINANZAS - ${summary.monthName}\n")
    builder.append("═════════════════════════════════════\n")
    builder.append("💰 Ingresos: ${CurrencyFormatter.format(summary.totalIncome)}\n")
    builder.append("💸 Gastos: ${CurrencyFormatter.format(summary.totalExpense)}\n")
    builder.append("💵 Balance Neto: ${CurrencyFormatter.format(summary.netSavings)}\n")
    builder.append("📈 Tasa de Ahorro: ${String.format("%.1f", summary.savingsRatePercentage)}%\n")
    builder.append("🗓️ Promedio Diario: ${CurrencyFormatter.format(summary.dailyAverage)}/día\n")
    builder.append("🧾 Ticket Promedio: ${CurrencyFormatter.format(summary.averagePerTransaction)}/compra\n")
    builder.append("🛒 Total Transacciones: ${summary.transactionCount}\n\n")

    if (summary.categoryBreakdown.isNotEmpty()) {
        builder.append("📂 DESGLOSE POR CATEGORÍA:\n")
        summary.categoryBreakdown.forEach { cat ->
            val pct = String.format("%.1f", cat.percentageOfTotal * 100)
            builder.append("• ${cat.categoryName}: ${CurrencyFormatter.format(cat.totalAmount)} ($pct%) - ${cat.transactionCount} compras\n")
        }
        builder.append("\n")
    }

    if (summary.topExpenses.isNotEmpty()) {
        builder.append("🔥 MAYORES GASTOS DEL MES:\n")
        summary.topExpenses.take(5).forEach { tx ->
            builder.append("• ${tx.description ?: tx.category.name}: ${CurrencyFormatter.format(tx.amount)} (${DateFormatter.format(tx.date)})\n")
        }
        builder.append("\n")
    }

    builder.append("Generado automáticamente por BudgetTracker.")
    return builder.toString()
}

private fun copyReportToClipboard(context: Context, summary: MonthlySpendingSummary) {
    val text = buildFormattedReportText(summary)
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    val clip = ClipData.newPlainText("Informe Mensual", text)
    clipboard?.setPrimaryClip(clip)
    Toast.makeText(context, "Informe copiado al portapapeles", Toast.LENGTH_SHORT).show()
}

private fun shareSummaryReport(context: Context, summary: MonthlySpendingSummary) {
    val reportText = buildFormattedReportText(summary)
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Informe Financiero - ${summary.monthName}")
        putExtra(Intent.EXTRA_TEXT, reportText)
    }

    try {
        context.startActivity(Intent.createChooser(shareIntent, "Compartir informe mensual"))
    } catch (e: Exception) {
        Toast.makeText(context, "No se pudo compartir el informe", Toast.LENGTH_SHORT).show()
    }
}
