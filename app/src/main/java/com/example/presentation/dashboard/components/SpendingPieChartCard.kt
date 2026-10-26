package com.example.presentation.dashboard.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DonutLarge
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.presentation.analysis.CategoryExpenseShare
import com.example.presentation.analysis.ExpenseGroupingType
import com.example.presentation.analysis.ExpenseTypeClassifier
import com.example.presentation.common.components.D3RechartsPieChart
import com.example.presentation.common.util.CurrencyFormatter

enum class ChartDisplayType {
    DONUT,
    PIE,
    BAR
}

enum class SpendingTimeframe(val label: String) {
    CURRENT_MONTH("Este Mes"),
    ALL_TIME("Histórico")
}

/**
 * Card para el Dashboard que implementa visualización de gastos mensuales por categoría
 * utilizando el motor de gráficos estilo Recharts y Jetpack Compose Canvas estándar.
 *
 * Soporta:
 * - Selección de modo visual: Donut, Pie (Circular) y Barras horizontales (Canvas).
 * - Selector de temporalidad: Gastos mensuales del mes actual desde Room vs Histórico total.
 * - Clasificación analítica: Por Categoría, Fijo vs Variable, y Regla 50/30/20.
 * - Tooltips interactivos y leyendas reactivas sincronizadas.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpendingPieChartCard(
    categoryExpenses: Map<String, Double>,
    modifier: Modifier = Modifier,
    monthlyCategoryExpenses: Map<String, Double> = emptyMap(),
    allTimeCategoryExpenses: Map<String, Double> = emptyMap(),
    title: String = "Gastos por Categoría"
) {
    // Determinar temporalidad inicial: si hay gastos en el mes actual, priorizar CURRENT_MONTH
    var timeframe by remember(monthlyCategoryExpenses, allTimeCategoryExpenses) {
        mutableStateOf(
            if (monthlyCategoryExpenses.isNotEmpty()) {
                SpendingTimeframe.CURRENT_MONTH
            } else if (allTimeCategoryExpenses.isNotEmpty()) {
                SpendingTimeframe.ALL_TIME
            } else {
                SpendingTimeframe.CURRENT_MONTH
            }
        )
    }

    // Mapa de datos activo según la temporalidad seleccionada
    val activeExpenseMap = remember(timeframe, categoryExpenses, monthlyCategoryExpenses, allTimeCategoryExpenses) {
        when (timeframe) {
            SpendingTimeframe.CURRENT_MONTH -> {
                if (monthlyCategoryExpenses.isNotEmpty()) monthlyCategoryExpenses else categoryExpenses
            }
            SpendingTimeframe.ALL_TIME -> {
                if (allTimeCategoryExpenses.isNotEmpty()) allTimeCategoryExpenses else categoryExpenses
            }
        }
    }

    val totalExpense = remember(activeExpenseMap) { activeExpenseMap.values.sum() }

    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    var chartDisplayType by remember { mutableStateOf(ChartDisplayType.DONUT) }
    var groupingType by remember { mutableStateOf(ExpenseGroupingType.BY_CATEGORY) }

    // Mapear el mapa a CategoryExpenseShare para el clasificador de tipos
    val categoryShares = remember(activeExpenseMap, totalExpense) {
        activeExpenseMap.entries
            .sortedByDescending { it.value }
            .mapIndexed { idx, entry ->
                val pct = if (totalExpense > 0) (entry.value / totalExpense).toFloat() else 0f
                CategoryExpenseShare(
                    categoryId = idx.toLong() + 1L,
                    categoryName = entry.key,
                    iconKey = "category",
                    amount = entry.value,
                    percentage = pct
                )
            }
    }

    // Datos procesados según la clasificación de gastos
    val typedShares = remember(categoryShares, groupingType, totalExpense) {
        ExpenseTypeClassifier.classifyExpenses(
            groupingType = groupingType,
            categoryShares = categoryShares,
            totalExpense = totalExpense
        )
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .testTag("spending_pie_chart_card"),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
        ) {
            val isWide = maxWidth >= 520.dp
            val isCompact = maxWidth < 360.dp
            val cardPadding = when {
                isCompact -> 14.dp
                isWide -> 24.dp
                else -> 18.dp
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(cardPadding),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header del gráfico con badge y conmutador Donut / Pie / Barras
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            modifier = Modifier.size(if (isCompact) 32.dp else 38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                val currentIcon = when (chartDisplayType) {
                                    ChartDisplayType.DONUT -> Icons.Default.DonutLarge
                                    ChartDisplayType.PIE -> Icons.Default.PieChart
                                    ChartDisplayType.BAR -> Icons.Default.BarChart
                                }
                                Icon(
                                    imageVector = currentIcon,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(if (isCompact) 18.dp else 22.dp)
                                )
                            }
                        }

                        Column {
                            Text(
                                text = title,
                                style = if (isCompact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (timeframe == SpendingTimeframe.CURRENT_MONTH) "Gastos del mes (Room DB)" else "Histórico acumulado",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = if (isCompact) 10.sp else 12.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (totalExpense > 0) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                            ) {
                                Text(
                                    text = CurrencyFormatter.format(totalExpense),
                                    style = if (isCompact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = if (isCompact) 8.dp else 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        // Selector de modo visual: Donut / Pie / Barras
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.clip(RoundedCornerShape(12.dp))
                        ) {
                            Row(modifier = Modifier.padding(2.dp)) {
                                IconButton(
                                    onClick = {
                                        chartDisplayType = ChartDisplayType.DONUT
                                        selectedIndex = null
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DonutLarge,
                                        contentDescription = "Modo Donut",
                                        tint = if (chartDisplayType == ChartDisplayType.DONUT) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        chartDisplayType = ChartDisplayType.PIE
                                        selectedIndex = null
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PieChart,
                                        contentDescription = "Modo Pie",
                                        tint = if (chartDisplayType == ChartDisplayType.PIE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        chartDisplayType = ChartDisplayType.BAR
                                        selectedIndex = null
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.BarChart,
                                        contentDescription = "Modo Barras Recharts",
                                        tint = if (chartDisplayType == ChartDisplayType.BAR) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Selector de temporalidad y filtros (Este Mes vs Histórico)
                val hasBothTimeframes = allTimeCategoryExpenses.isNotEmpty() && monthlyCategoryExpenses.isNotEmpty()
                if (hasBothTimeframes) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SpendingTimeframe.values().forEach { tf ->
                            val isSelected = timeframe == tf
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    timeframe = tf
                                    selectedIndex = null
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.CalendarMonth,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                },
                                label = {
                                    Text(
                                        text = tf.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            )
                        }
                    }
                }

                if (totalExpense <= 0 || activeExpenseMap.isEmpty()) {
                    // Estado vacío amigable
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(horizontal = 24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                text = if (timeframe == SpendingTimeframe.CURRENT_MONTH) "Sin gastos registrados este mes" else "Sin gastos registrados",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Tus gastos clasificados por categoría aparecerán aquí con gráficos interactivos tipo Recharts.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    // Selector de tipo de clasificación de gastos (Categoría, Fijo/Variable, 50/30/20)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ExpenseGroupingType.values().forEach { type ->
                            val isSelected = groupingType == type
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    groupingType = type
                                    selectedIndex = null
                                },
                                label = {
                                    Text(
                                        text = if (isCompact) type.shortLabel else type.title,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isSelected,
                                    borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                            )
                        }
                    }

                    // Renderizado condicional del gráfico Recharts seleccionado
                    when (chartDisplayType) {
                        ChartDisplayType.BAR -> {
                            // Gráfico de Barras con Canvas estándar
                            RechartsCategoryBarChart(
                                items = typedShares,
                                totalAmount = totalExpense,
                                selectedIndex = selectedIndex,
                                onSelectIndex = { selectedIndex = it }
                            )
                        }
                        ChartDisplayType.DONUT, ChartDisplayType.PIE -> {
                            // Gráfico Circular D3 / Recharts con Pop-Out y Tooltip interactivo
                            D3RechartsPieChart(
                                items = typedShares,
                                totalAmount = totalExpense,
                                isDonut = chartDisplayType == ChartDisplayType.DONUT,
                                chartSize = if (isCompact) 180.dp else if (isWide) 230.dp else 200.dp,
                                showLegend = true,
                                showTooltipCard = true,
                                selectedIndex = selectedIndex,
                                onSelectIndex = { selectedIndex = it }
                            )
                        }
                    }
                }
            }
        }
    }
}
