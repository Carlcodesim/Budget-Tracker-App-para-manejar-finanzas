package com.example.presentation.common.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.MonthlySpendingAggregate
import com.example.presentation.common.util.CurrencyFormatter
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.IncomeGreen
import kotlin.math.max

/**
 * Reusable and interactive Jetpack Compose Bar Chart for visualizing aggregated monthly spending and income.
 * Features animated bars, interactive tooltips, M3 theming, and responsive layout.
 */
@Composable
fun MonthlySpendingBarChart(
    data: List<MonthlySpendingAggregate>,
    modifier: Modifier = Modifier,
    title: String = "Gastos vs Ingresos Mensuales",
    subtitle: String? = "Histórico de los últimos meses agregados",
    showIncomeBars: Boolean = true,
    selectedIndex: Int? = null,
    onMonthSelected: (Int) -> Unit = {}
) {
    var internalSelectedIndex by remember { mutableStateOf(data.lastIndex.coerceAtLeast(0)) }
    val activeIndex = selectedIndex ?: internalSelectedIndex
    val currentSelected = data.getOrNull(activeIndex)

    val maxAmount = remember(data) {
        val peak = data.maxOfOrNull { item ->
            if (showIncomeBars) max(item.totalExpense, item.totalIncome) else item.totalExpense
        } ?: 0.0
        if (peak > 0.0) peak else 1.0
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("monthly_spending_bar_chart_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.BarChart,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (subtitle != null) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Legend
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
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
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (showIncomeBars) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
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
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            if (data.isEmpty() || data.all { it.totalExpense == 0.0 && it.totalIncome == 0.0 }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = "Aún no hay datos de gastos registrados para este periodo",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                // Interactive Summary Card (Tooltip Details)
                AnimatedVisibility(
                    visible = currentSelected != null,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    currentSelected?.let { month ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .testTag("selected_month_tooltip"),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = "${month.monthName} ${month.year}",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.TrendingDown,
                                            contentDescription = null,
                                            tint = ExpenseRed,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = "Gasto: ${CurrencyFormatter.format(month.totalExpense)}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = ExpenseRed
                                        )
                                    }
                                }

                                Column(
                                    horizontalAlignment = Alignment.End,
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    if (showIncomeBars) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                                                contentDescription = null,
                                                tint = IncomeGreen,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = "Ingreso: ${CurrencyFormatter.format(month.totalIncome)}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = IncomeGreen
                                            )
                                        }
                                    }
                                    val netText = if (month.netSavings >= 0) {
                                        "+${CurrencyFormatter.format(month.netSavings)}"
                                    } else {
                                        "-${CurrencyFormatter.format(kotlin.math.abs(month.netSavings))}"
                                    }
                                    Text(
                                        text = "Ahorro: $netText (${month.savingsRatePercentage.toInt()}%)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (month.netSavings >= 0) IncomeGreen else ExpenseRed,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                // Bar Chart Visualizer
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .padding(top = 8.dp)
                ) {
                    // Grid background lines
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        repeat(4) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                thickness = 1.dp
                            )
                        }
                    }

                    // Columns row
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 24.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        data.forEachIndexed { index, item ->
                            val isSelected = index == activeIndex

                            val expenseRatio = (item.totalExpense / maxAmount).toFloat().coerceIn(0.04f, 1f)
                            val incomeRatio = (item.totalIncome / maxAmount).toFloat().coerceIn(0.04f, 1f)

                            val animatedExpenseRatio by animateFloatAsState(
                                targetValue = expenseRatio,
                                animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
                                label = "expense_bar_$index"
                            )

                            val animatedIncomeRatio by animateFloatAsState(
                                targetValue = incomeRatio,
                                animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
                                label = "income_bar_$index"
                            )

                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        internalSelectedIndex = index
                                        onMonthSelected(index)
                                    }
                                    .testTag("bar_item_$index"),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Bottom
                            ) {
                                Row(
                                    modifier = Modifier
                                        .weight(1f, fill = true)
                                        .padding(horizontal = 2.dp),
                                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                                    verticalAlignment = Alignment.Bottom
                                ) {
                                    // Expense Bar
                                    Box(
                                        modifier = Modifier
                                            .width(if (showIncomeBars) 12.dp else 20.dp)
                                            .fillMaxHeight()
                                            .graphicsLayer {
                                                scaleY = animatedExpenseRatio
                                                transformOrigin = TransformOrigin(0.5f, 1f)
                                            }
                                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                            .background(
                                                if (isSelected) ExpenseRed else ExpenseRed.copy(alpha = 0.65f)
                                            )
                                    )

                                    // Income Bar
                                    if (showIncomeBars) {
                                        Box(
                                            modifier = Modifier
                                                .width(12.dp)
                                                .fillMaxHeight()
                                                .graphicsLayer {
                                                scaleY = animatedIncomeRatio
                                                transformOrigin = TransformOrigin(0.5f, 1f)
                                            }
                                                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                                .background(
                                                    if (isSelected) IncomeGreen else IncomeGreen.copy(alpha = 0.65f)
                                                )
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Month Label
                                Text(
                                    text = item.monthName,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
