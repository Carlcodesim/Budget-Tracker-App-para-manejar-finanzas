package com.example.presentation.analysis.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DonutLarge
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.presentation.analysis.CategoryExpenseShare
import com.example.presentation.analysis.ExpenseGroupingType
import com.example.presentation.analysis.ExpenseTypeClassifier
import com.example.presentation.analysis.TypedExpenseShare
import com.example.presentation.common.components.D3RechartsPieChart
import com.example.presentation.common.util.CurrencyFormatter
import com.example.presentation.common.util.IconHelper

/**
 * Recharts / D3-inspired qualitative color palette designed for high distinction
 * in data visualization and charts.
 */
val VISUALIZATION_PALETTE = listOf(
    Color(0xFF3B82F6), // Blue
    Color(0xFF10B981), // Emerald Green
    Color(0xFFF59E0B), // Amber / Gold
    Color(0xFFEF4444), // Coral Red
    Color(0xFF8B5CF6), // Violet / Purple
    Color(0xFF06B6D4), // Cyan
    Color(0xFFEC4899), // Pink
    Color(0xFFF97316), // Orange
    Color(0xFF14B8A6), // Teal
    Color(0xFF6366F1), // Indigo
    Color(0xFF84CC16), // Lime Green
    Color(0xFF64748B)  // Slate
)

/**
 * Modos de visualización Recharts / D3.
 */
enum class DistributionViewMode(val title: String, val shortTitle: String) {
    PIE("Circular (Pie)", "Pie"),
    DONUT("Donut Arc", "Donut"),
    STACKED_BAR("Barra 100%", "Barra"),
    RANKING("Ranking", "Ranking")
}

/**
 * Componente de visualización de gastos con motor D3 / Recharts nativo en Jetpack Compose.
 *
 * Características implementadas:
 * 1. Gráfico Circular (Pie Chart) con particionamiento de ángulos D3 (d3.pie, d3.arc).
 * 2. Efecto Pop-Out de Recharts: traslación tridimensional radial del sector activo.
 * 3. Categorización de gastos por tipo: Por Categoría, Fijos vs Variables, Regla 50/30/20 y Frecuencia.
 * 4. Tooltip flotante interactivo estilo Recharts (<Tooltip content={<CustomTooltip />} />).
 * 5. Leyenda interactiva sincronizada con badges de color y chips de filtrado.
 * 6. Vistas alternativas: Donut Arc con telemetría central, Barra 100% normalizada y Ranking jerárquico.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MonthlyCategoryDistributionVisualizer(
    categoryExpenses: List<CategoryExpenseShare>,
    totalExpense: Double,
    modifier: Modifier = Modifier,
    title: String = "Distribución de Gastos"
) {
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    var viewMode by remember { mutableStateOf(DistributionViewMode.PIE) }
    var groupingType by remember { mutableStateOf(ExpenseGroupingType.BY_CATEGORY) }

    val sortedCategories = remember(categoryExpenses) {
        categoryExpenses.sortedByDescending { it.amount }
    }

    // Clasificación de gastos según el tipo seleccionado (D3 / Recharts data pipeline)
    val typedShares = remember(sortedCategories, groupingType, totalExpense) {
        ExpenseTypeClassifier.classifyExpenses(
            groupingType = groupingType,
            categoryShares = sortedCategories,
            totalExpense = totalExpense
        )
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .testTag("monthly_category_distribution_visualizer"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            val isCompact = maxWidth < 460.dp

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header principal con título y selector de modo de gráfico
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
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.PieChart,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Column {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${typedShares.size} grupos • Total: ${CurrencyFormatter.format(totalExpense)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (totalExpense > 0) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                        ) {
                            Text(
                                text = groupingType.shortLabel,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                // Selector de Modo de Gráficos (Pie, Donut, Barra 100%, Ranking)
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                ) {
                    DistributionViewMode.values().forEachIndexed { index, mode ->
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = DistributionViewMode.values().size
                            ),
                            onClick = {
                                viewMode = mode
                                selectedIndex = null
                            },
                            selected = viewMode == mode,
                            icon = {}
                        ) {
                            Text(
                                text = if (isCompact) mode.shortTitle else mode.title,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (viewMode == mode) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1
                            )
                        }
                    }
                }

                // Selector de Agrupación por Tipo de Gasto
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Clasificar gastos por:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = groupingType.description,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.outline,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

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
                }

                if (sortedCategories.isEmpty() || totalExpense <= 0) {
                    EmptyDistributionPlaceholder()
                } else {
                    // Tarjeta informativa contextual según la clasificación seleccionada
                    GroupingInsightBanner(groupingType = groupingType, typedShares = typedShares)

                    // Área visualizadora animada según el modo seleccionado
                    AnimatedContent(
                        targetState = viewMode,
                        transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(180)) },
                        label = "DistributionViewModeTransition"
                    ) { currentMode ->
                        when (currentMode) {
                            DistributionViewMode.PIE -> {
                                D3RechartsPieChart(
                                    items = typedShares,
                                    totalAmount = totalExpense,
                                    isDonut = false,
                                    chartSize = if (isCompact) 210.dp else 240.dp,
                                    showLegend = true,
                                    showTooltipCard = true,
                                    selectedIndex = selectedIndex,
                                    onSelectIndex = { selectedIndex = it }
                                )
                            }

                            DistributionViewMode.DONUT -> {
                                D3RechartsPieChart(
                                    items = typedShares,
                                    totalAmount = totalExpense,
                                    isDonut = true,
                                    chartSize = if (isCompact) 210.dp else 240.dp,
                                    showLegend = true,
                                    showTooltipCard = true,
                                    selectedIndex = selectedIndex,
                                    onSelectIndex = { selectedIndex = it }
                                )
                            }

                            DistributionViewMode.STACKED_BAR -> {
                                StackedBarDistributionSection(
                                    items = typedShares,
                                    totalExpense = totalExpense,
                                    selectedIndex = selectedIndex,
                                    onSelectIndex = { selectedIndex = if (selectedIndex == it) null else it }
                                )
                            }

                            DistributionViewMode.RANKING -> {
                                RankingDistributionSection(
                                    items = typedShares,
                                    totalExpense = totalExpense,
                                    selectedIndex = selectedIndex,
                                    onSelectIndex = { selectedIndex = if (selectedIndex == it) null else it }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Banner informativo de balance según el tipo de clasificación.
 */
@Composable
private fun GroupingInsightBanner(
    groupingType: ExpenseGroupingType,
    typedShares: List<TypedExpenseShare>
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )

            val insightText = when (groupingType) {
                ExpenseGroupingType.BY_CATEGORY -> {
                    val top = typedShares.firstOrNull()
                    if (top != null) {
                        "Mayor gasto: ${top.name} con ${CurrencyFormatter.format(top.amount)} (${(top.percentage * 100).toInt()}%)"
                    } else "Distribución por categorías individuales"
                }

                ExpenseGroupingType.FIXED_VS_VARIABLE -> {
                    val fixed = typedShares.find { it.id == "type_fixed" }
                    val variable = typedShares.find { it.id == "type_variable" }
                    val fixedPct = fixed?.let { (it.percentage * 100).toInt() } ?: 0
                    val varPct = variable?.let { (it.percentage * 100).toInt() } ?: 0
                    "Fijos: $fixedPct% (${fixed?.categoryCount ?: 0} cat.) • Variables: $varPct% (${variable?.categoryCount ?: 0} cat.)"
                }

                ExpenseGroupingType.RULE_50_30_20 -> {
                    val needs = typedShares.find { it.id == "rule_needs" }
                    val wants = typedShares.find { it.id == "rule_wants" }
                    val needsPct = needs?.let { (it.percentage * 100).toInt() } ?: 0
                    val wantsPct = wants?.let { (it.percentage * 100).toInt() } ?: 0
                    "Necesidades: $needsPct% (Meta 50%) • Deseos: $wantsPct% (Meta 30%)"
                }

                ExpenseGroupingType.RECURRING_VS_OCCASIONAL -> {
                    val rec = typedShares.find { it.id == "freq_recurring" }
                    val occ = typedShares.find { it.id == "freq_occasional" }
                    val recPct = rec?.let { (it.percentage * 100).toInt() } ?: 0
                    val occPct = occ?.let { (it.percentage * 100).toInt() } ?: 0
                    "Recurrentes: $recPct% • Ocasionales / Puntuales: $occPct%"
                }
            }

            Text(
                text = insightText,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * 2. Barra de Distribución Normalizada 100% estilo Recharts Stacked Bar.
 */
@Composable
private fun StackedBarDistributionSection(
    items: List<TypedExpenseShare>,
    totalExpense: Double,
    selectedIndex: Int?,
    onSelectIndex: (Int) -> Unit
) {
    var animationPlayed by remember { mutableStateOf(false) }
    val progressFactor by animateFloatAsState(
        targetValue = if (animationPlayed) 1f else 0f,
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label = "stackedBarAnimation"
    )

    LaunchedEffect(items) {
        animationPlayed = false
        animationPlayed = true
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Composición Proporcional de Gastos (100%)",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Barra segmentada 100%
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                items.forEachIndexed { index, share ->
                    val isSelected = selectedIndex == index
                    val weight = (share.percentage * progressFactor).coerceAtLeast(0.001f)

                    Box(
                        modifier = Modifier
                            .weight(weight)
                            .fillMaxSize()
                            .background(share.color)
                            .border(
                                width = if (isSelected) 2.dp else 0.5.dp,
                                color = if (isSelected) Color.White else Color.Transparent
                            )
                            .clickable { onSelectIndex(index) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (share.percentage >= 0.12f) {
                            Text(
                                text = "${(share.percentage * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }

        // Detalle del elemento seleccionado en la barra
        selectedIndex?.let { idx ->
            items.getOrNull(idx)?.let { selected ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = selected.color.copy(alpha = 0.1f),
                    border = BorderStroke(1.dp, selected.color.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = IconHelper.getIcon(selected.iconKey),
                                contentDescription = null,
                                tint = selected.color,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = selected.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = CurrencyFormatter.format(selected.amount),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = selected.color
                            )
                            Text(
                                text = "${(selected.percentage * 100).toInt()}% del total de gastos",
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

/**
 * 3. Ranking jerárquico detallado con barras de progreso individuales.
 */
@Composable
private fun RankingDistributionSection(
    items: List<TypedExpenseShare>,
    totalExpense: Double,
    selectedIndex: Int?,
    onSelectIndex: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items.forEachIndexed { index, share ->
            val isSelected = selectedIndex == index

            OutlinedCard(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = if (isSelected) share.color.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(
                    width = if (isSelected) 1.5.dp else 1.dp,
                    color = if (isSelected) share.color else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectIndex(index) }
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
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
                            Surface(
                                shape = CircleShape,
                                color = share.color.copy(alpha = 0.15f),
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = IconHelper.getIcon(share.iconKey),
                                        contentDescription = null,
                                        tint = share.color,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            Column {
                                Text(
                                    text = "${index + 1}. ${share.name}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (share.description.isNotEmpty()) {
                                    Text(
                                        text = share.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = CurrencyFormatter.format(share.amount),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${String.format("%.1f", share.percentage * 100)}%",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = share.color
                            )
                        }
                    }

                    LinearProgressIndicator(
                        progress = { share.percentage.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = share.color,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyDistributionPlaceholder() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Default.PieChart,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.size(48.dp)
        )
        Text(
            text = "Sin gastos en el periodo actual",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Registra movimientos para visualizar la distribución proporcional",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
    }
}
