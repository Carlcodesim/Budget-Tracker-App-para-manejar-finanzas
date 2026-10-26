package com.example.presentation.analysis

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.presentation.analysis.components.BudgetRule503020Card
import com.example.presentation.analysis.components.MonthlyCategoryDistributionVisualizer
import com.example.presentation.common.components.MonthlySpendingBarChart
import com.example.presentation.common.util.CurrencyFormatter
import com.example.presentation.common.util.IconHelper
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.IncomeGreen
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisScreen(
    viewModel: AnalysisViewModel,
    onNavigateToAssistant: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val currentSuccess = state as? AnalysisUiState.Success

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Análisis Financiero",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                actions = {
                    if (currentSuccess?.hasTransactions == true) {
                        IconButton(
                            onClick = { shareAnalysisReport(context, currentSuccess) },
                            modifier = Modifier.testTag("analysis_share_report_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Compartir Informe",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    IconButton(
                        onClick = onNavigateToAssistant,
                        modifier = Modifier.testTag("analysis_assistant_predictions_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Predicciones IA",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.testTag("analysis_screen")
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            when (val currentState = state) {
                is AnalysisUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is AnalysisUiState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = currentState.message,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                is AnalysisUiState.Success -> {
                    if (!currentState.hasTransactions) {
                        EmptyStateView(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                        )
                    } else {
                        SuccessAnalysisContent(
                            state = currentState,
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SuccessAnalysisContent(
    state: AnalysisUiState.Success,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .widthIn(max = 840.dp)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Resumen Ejecutivo del Mes (Hero Summary Card)
        ExecutiveSummaryCard(state = state)

        // 2. Indicadores Clave en Cuadrícula Adaptativa (Promedio Diario & Categoría Principal)
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            if (maxWidth < 380.dp) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DailySpendCard(
                        currentAverage = state.averageDailySpendingCurrent,
                        previousAverage = state.averageDailySpendingPrevious,
                        modifier = Modifier.fillMaxWidth()
                    )
                    TopCategoryCard(
                        topShare = state.topExpenseCategory,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    DailySpendCard(
                        currentAverage = state.averageDailySpendingCurrent,
                        previousAverage = state.averageDailySpendingPrevious,
                        modifier = Modifier.weight(1f)
                    )
                    TopCategoryCard(
                        topShare = state.topExpenseCategory,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 3. Gráfico de Evolución Mensual de Gastos e Ingresos
        MonthlySpendingBarChart(
            data = state.monthlyAggregates,
            title = "Gastos Totales por Mes",
            subtitle = "Evolución histórica de ingresos frente a gastos"
        )

        // 4. Gráfico y Desglose de Distribución por Categoría y Tipo (D3 / Recharts)
        MonthlyCategoryDistributionVisualizer(
            categoryExpenses = state.categoryExpenses,
            totalExpense = state.currentMonthExpense,
            title = "Distribución de Gastos por Categoría y Tipo"
        )

        // 4.1 Regla Presupuestaria 50/30/20 (Necesidades, Deseos, Ahorro)
        state.budgetRule503020?.let { rule ->
            BudgetRule503020Card(rule = rule)
        }

        // 5. Comparativa de Flujo entre Meses
        FlowComparisonCard(state = state)

        // 5.1 Ritmo y Variación por Categoría
        if (state.categoryVelocities.isNotEmpty()) {
            CategoryVelocityComparisonCard(velocities = state.categoryVelocities)
        }

        // 6. Diagnóstico de Ahorro y Recomendaciones Inteligentes
        SavingsDiagnosisCard(state = state)

        Spacer(modifier = Modifier.height(32.dp))
    }
}

/**
 * Tarjeta Resumen Ejecutivo con balance mensual, ingresos y gastos totales.
 */
@Composable
private fun ExecutiveSummaryCard(
    state: AnalysisUiState.Success,
    modifier: Modifier = Modifier
) {
    val netBalance = state.currentMonthIncome - state.currentMonthExpense
    val savingsRate = if (state.currentMonthIncome > 0) {
        ((netBalance / state.currentMonthIncome) * 100).toInt()
    } else 0

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = modifier.fillMaxWidth()
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
                Column {
                    val cycleRange = remember { com.example.util.BudgetCycleManager.getCurrentCycleRange() }
                    Text(
                        text = if (cycleRange.isCustomCycle) "Balance del Ciclo (${cycleRange.dateRangeLabel})" else "Balance Neto del Mes",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = CurrencyFormatter.format(netBalance),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (netBalance >= 0) IncomeGreen else ExpenseRed
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (savingsRate >= 20) IncomeGreen.copy(alpha = 0.12f) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (savingsRate >= 0) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                            contentDescription = null,
                            tint = if (savingsRate >= 0) IncomeGreen else ExpenseRed,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "$savingsRate% ahorro",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (savingsRate >= 0) IncomeGreen else ExpenseRed
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Ingresos
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(IncomeGreen)
                        )
                        Text(
                            text = "Ingresos",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = CurrencyFormatter.format(state.currentMonthIncome),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Gastos
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.End
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(ExpenseRed)
                        )
                        Text(
                            text = "Gastos",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = CurrencyFormatter.format(state.currentMonthExpense),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun DailySpendCard(
    currentAverage: Double,
    previousAverage: Double,
    modifier: Modifier = Modifier
) {
    val isBetter = currentAverage <= previousAverage
    val color = if (isBetter) IncomeGreen else ExpenseRed
    val icon = if (isBetter) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Gasto Diario Medio",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = CurrencyFormatter.format(currentAverage),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "Prev: ${CurrencyFormatter.format(previousAverage)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun TopCategoryCard(
    topShare: CategoryExpenseShare?,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Mayor Categoría",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (topShare != null) {
                Text(
                    text = topShare.categoryName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.errorContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = IconHelper.getIcon(topShare.iconKey),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(11.dp)
                        )
                    }
                    Text(
                        text = "${(topShare.percentage * 100).toInt()}% del total",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }
            } else {
                Text(
                    text = "Sin gastos",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun FlowComparisonCard(
    state: AnalysisUiState.Success,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column {
                Text(
                    text = "Comparativa de Flujo Mensual",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Ingresos y gastos comparados con el mes anterior",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Barras Comparativas de Ingresos
            FlowComparisonBar(
                label = "Ingresos",
                currentValue = state.currentMonthIncome,
                previousValue = state.previousMonthIncome,
                color = IncomeGreen
            )

            // Barras Comparativas de Gastos
            FlowComparisonBar(
                label = "Gastos",
                currentValue = state.currentMonthExpense,
                previousValue = state.previousMonthExpense,
                color = ExpenseRed
            )
        }
    }
}

@Composable
private fun FlowComparisonBar(
    label: String,
    currentValue: Double,
    previousValue: Double,
    color: Color,
    modifier: Modifier = Modifier
) {
    val maxValue = Math.max(currentValue, previousValue).coerceAtLeast(1.0)
    val currentRatio = (currentValue / maxValue).toFloat().coerceIn(0.02f, 1f)
    val previousRatio = (previousValue / maxValue).toFloat().coerceIn(0.02f, 1f)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        // Barra Actual
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Este mes",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = CurrencyFormatter.format(currentValue),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            LinearProgressIndicator(
                progress = { currentRatio },
                color = color,
                trackColor = color.copy(alpha = 0.12f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
            )
        }

        // Barra Mes Anterior
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Mes anterior",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = CurrencyFormatter.format(previousValue),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            LinearProgressIndicator(
                progress = { previousRatio },
                color = color.copy(alpha = 0.45f),
                trackColor = color.copy(alpha = 0.08f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
            )
        }
    }
}

@Composable
private fun CategoryVelocityComparisonCard(
    velocities: List<CategorySpendingVelocity>,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column {
                Text(
                    text = "Ritmo y Variación por Categoría",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Comparativa del gasto de este mes frente al mes anterior",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            velocities.take(6).forEach { vel ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = IconHelper.getIcon(vel.iconKey),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column {
                            Text(
                                text = vel.categoryName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Ant: ${CurrencyFormatter.format(vel.previousMonthAmount)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = CurrencyFormatter.format(vel.currentMonthAmount),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val badgeColor = if (vel.isIncrease) ExpenseRed else IncomeGreen
                        val sign = if (vel.isIncrease) "+" else ""
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = badgeColor.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = "$sign${vel.percentageChange.toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = badgeColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SavingsDiagnosisCard(
    state: AnalysisUiState.Success,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = modifier.fillMaxWidth()
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
                Text(
                    text = "Tendencia de Ahorro",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                val trendIcon = if (state.savingsTrend.isPositiveTrend) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown
                val trendColor = if (state.savingsTrend.isPositiveTrend) IncomeGreen else ExpenseRed

                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(trendColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = trendIcon,
                        contentDescription = null,
                        tint = trendColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Ahorrado este mes",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = CurrencyFormatter.format(state.savingsTrend.currentMonthSavings),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (state.savingsTrend.currentMonthSavings >= 0) IncomeGreen else ExpenseRed
                    )
                    Text(
                        text = "Tasa: ${(state.savingsTrend.savingsRateCurrent * 100).toInt()}% de ingresos",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Mes anterior",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = CurrencyFormatter.format(state.savingsTrend.previousMonthSavings),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Tasa: ${(state.savingsTrend.savingsRatePrevious * 100).toInt()}% de ingresos",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Indicador de Tendencia
            val changeText = if (state.savingsTrend.isPositiveTrend) {
                "Tus ahorros aumentaron un ${state.savingsTrend.savingsChangePercentage.toInt()}% respecto al mes anterior."
            } else {
                "Tus ahorros disminuyeron un ${Math.abs(state.savingsTrend.savingsChangePercentage).toInt()}% respecto al mes anterior."
            }
            val badgeColor = if (state.savingsTrend.isPositiveTrend) IncomeGreen else ExpenseRed

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(badgeColor.copy(alpha = 0.08f))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (state.savingsTrend.isPositiveTrend) Icons.Default.CheckCircle else Icons.Default.Info,
                    contentDescription = null,
                    tint = badgeColor,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = changeText,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = badgeColor
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Consejos Inteligentes
            Text(
                text = "Recomendación Financiera",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            val advice = remember(state.savingsTrend, state.topExpenseCategory) {
                getFinancialAdvice(state.savingsTrend, state.topExpenseCategory)
            }

            Text(
                text = advice,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 19.sp
            )
        }
    }
}

@Composable
private fun EmptyStateView(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Analytics,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                modifier = Modifier.size(72.dp)
            )

            Text(
                text = "Sin registros suficientes",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Agrega tus ingresos y gastos de este mes y del mes anterior para ver gráficos de categoría, tendencias de ahorro, promedios diarios y comparativas dinámicas.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
        }
    }
}

private fun getFinancialAdvice(trend: SavingsTrendData, topCategory: CategoryExpenseShare?): String {
    if (topCategory == null) return "¡Excelente! Aún no registras gastos este mes. Mantén un seguimiento continuo de tus ingresos y gastos para entender mejor el flujo de tu dinero."

    val categoryAdvice = when (topCategory.categoryName.lowercase()) {
        "comida", "alimentos", "restaurantes" -> "Tu categoría de mayor gasto es '${topCategory.categoryName}'. Planificar tus compras de supermercado, llevar almuerzo preparado y moderar las salidas a comer fuera de casa puede reducir este monto notablemente."
        "transporte", "gasolina", "uber" -> "Has consumido una porción relevante en transporte. Considera optimizar trayectos o combinar viajes para aminorar gastos de combustible o tarifas de servicio."
        "ocio", "entretenimiento", "salidas" -> "El entretenimiento encabeza tus consumos del mes. Establecer un presupuesto específico semanal para ocio te ayudará a evitar gastos impulsivos."
        "servicios", "facturas" -> "Los servicios y facturas fijas representan tu mayor gasto. Revisar suscripciones inactivas o contratos de telefonía puede generar un ahorro inmediato."
        else -> "Has consumido ${CurrencyFormatter.format(topCategory.amount)} en '${topCategory.categoryName}' (${(topCategory.percentage * 100).toInt()}% de tu gasto mensual). Te sugerimos fijar un tope para esta categoría en tus metas de presupuesto."
    }

    val trendAdvice = if (trend.currentMonthSavings > trend.previousMonthSavings) {
        " ¡Buen trabajo! Tu capacidad de ahorro mejoró frente al mes anterior, acelerando tus objetivos financieros."
    } else {
        " Nota: Tu tasa de ahorro disminuyó frente al mes anterior. Revisar gastos secundarios te permitirá retomar el ritmo deseado."
    }

    return categoryAdvice + trendAdvice
}

private fun shareAnalysisReport(context: Context, state: AnalysisUiState.Success) {
    try {
        val netBalance = state.currentMonthIncome - state.currentMonthExpense
        val savingsRate = if (state.currentMonthIncome > 0) {
            ((netBalance / state.currentMonthIncome) * 100).toInt()
        } else 0

        val report = buildString {
            appendLine("📊 REPORTE FINANCIERO MENSUAL")
            appendLine("Remix Budget Tracker")
            appendLine("─────────────────────────────────────")
            appendLine("💰 BALANCE GENERAL:")
            appendLine("• Ingresos Totales: ${CurrencyFormatter.format(state.currentMonthIncome)}")
            appendLine("• Gastos Totales: ${CurrencyFormatter.format(state.currentMonthExpense)}")
            appendLine("• Balance Neto: ${CurrencyFormatter.format(netBalance)}")
            appendLine("• Tasa de Ahorro: $savingsRate%")
            appendLine()
            appendLine("📈 INDICADORES CLAVE:")
            appendLine("• Gasto Promedio Diario: ${CurrencyFormatter.format(state.averageDailySpendingCurrent)}/día")
            state.topExpenseCategory?.let { top ->
                val pct = String.format(Locale.getDefault(), "%.1f", top.percentage * 100)
                appendLine("• Categoría Principal: ${top.categoryName} (${CurrencyFormatter.format(top.amount)} • $pct%)")
            }
            appendLine()
            state.budgetRule503020?.let { rule ->
                appendLine("🎯 DISTRIBUCIÓN REGLA 50/30/20:")
                appendLine("• Necesidades (50%): ${CurrencyFormatter.format(rule.needsAmount)} (${rule.needsPercentage.toInt()}%)")
                appendLine("• Deseos (30%): ${CurrencyFormatter.format(rule.wantsAmount)} (${rule.wantsPercentage.toInt()}%)")
                appendLine("• Ahorro (20%): ${CurrencyFormatter.format(rule.savingsAmount)} (${rule.savingsPercentage.toInt()}%)")
                appendLine()
            }
            if (state.categoryExpenses.isNotEmpty()) {
                appendLine("🏷️ PRINCIPALES GASTOS POR CATEGORÍA:")
                state.categoryExpenses.take(5).forEach { share ->
                    val pct = String.format(Locale.getDefault(), "%.1f", share.percentage * 100)
                    appendLine("• ${share.categoryName}: ${CurrencyFormatter.format(share.amount)} ($pct%)")
                }
                appendLine()
            }
            appendLine("🛡️ Generado con Remix Budget Tracker")
        }

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Reporte Financiero Mensual")
            putExtra(Intent.EXTRA_TEXT, report)
        }
        val chooser = Intent.createChooser(sendIntent, "Compartir Informe Financiero")
        context.startActivity(chooser)
    } catch (e: Exception) {
        Toast.makeText(context, "Error al compartir reporte: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}

