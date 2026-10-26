package com.example.presentation.analysis.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.presentation.analysis.BudgetRule503020
import com.example.presentation.common.util.CurrencyFormatter
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.IncomeGreen

/**
 * Componente visual de análisis financiero para la regla 50/30/20 (Necesidades, Deseos, Ahorro).
 * Diseño optimizado, centrado, altamente legible y compacto.
 */
@Composable
fun BudgetRule503020Card(
    rule: BudgetRule503020,
    modifier: Modifier = Modifier
) {
    val needsColor = if (rule.needsExceeded) ExpenseRed else Color(0xFF2563EB)
    val wantsColor = if (rule.wantsExceeded) ExpenseRed else Color(0xFF8B5CF6)
    val savingsColor = if (rule.savingsOnTrack) IncomeGreen else Color(0xFFF59E0B)

    val hasData = (rule.needsPercentage + rule.wantsPercentage + rule.savingsPercentage) > 0.01f
    val animNeedsWeight by animateFloatAsState(
        targetValue = if (hasData) rule.needsPercentage.coerceAtLeast(0.01f) else 50f,
        animationSpec = tween(700),
        label = "needsRatio"
    )
    val animWantsWeight by animateFloatAsState(
        targetValue = if (hasData) rule.wantsPercentage.coerceAtLeast(0.01f) else 30f,
        animationSpec = tween(700),
        label = "wantsRatio"
    )
    val animSavingsWeight by animateFloatAsState(
        targetValue = if (hasData) rule.savingsPercentage.coerceAtLeast(0.01f) else 20f,
        animationSpec = tween(700),
        label = "savingsRatio"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("budget_rule_503020_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Cabecera compacta y alineada
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
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Regla 50 / 30 / 20",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Distribución ideal del presupuesto",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (rule.isBalanced) IncomeGreen.copy(alpha = 0.12f) else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                    border = BorderStroke(
                        1.dp,
                        if (rule.isBalanced) IncomeGreen.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (rule.isBalanced) Icons.Default.CheckCircle else Icons.Default.Info,
                            contentDescription = null,
                            tint = if (rule.isBalanced) IncomeGreen else MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = if (rule.isBalanced) "En Balance" else "Ajuste Sugerido",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = if (rule.isBalanced) IncomeGreen else MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            // 2. Barra segmentada proporcional centrada
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(animNeedsWeight)
                            .fillMaxHeight()
                            .background(needsColor.copy(alpha = if (hasData) 1f else 0.4f))
                    )
                    Box(
                        modifier = Modifier
                            .weight(animWantsWeight)
                            .fillMaxHeight()
                            .background(wantsColor.copy(alpha = if (hasData) 1f else 0.4f))
                    )
                    Box(
                        modifier = Modifier
                            .weight(animSavingsWeight)
                            .fillMaxHeight()
                            .background(savingsColor.copy(alpha = if (hasData) 1f else 0.4f))
                    )
                }

                // Leyenda horizontal centrada
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LegendIndicator(label = "Necesidades 50%", color = needsColor)
                    LegendIndicator(label = "Deseos 30%", color = wantsColor)
                    LegendIndicator(label = "Ahorro 20%", color = savingsColor)
                }
            }

            // 3. Tres columnas métricas centradas (Necesidades, Deseos, Ahorro)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BudgetPillarColumn(
                    title = "Necesidades",
                    targetText = "Meta 50%",
                    currentPercentage = rule.needsPercentage,
                    amount = rule.needsAmount,
                    icon = Icons.Default.Favorite,
                    color = needsColor,
                    isExceeded = rule.needsExceeded,
                    isOnTrack = rule.needsPercentage in 1f..50f,
                    modifier = Modifier.weight(1f)
                )

                BudgetPillarColumn(
                    title = "Deseos",
                    targetText = "Meta 30%",
                    currentPercentage = rule.wantsPercentage,
                    amount = rule.wantsAmount,
                    icon = Icons.Default.ShoppingBag,
                    color = wantsColor,
                    isExceeded = rule.wantsExceeded,
                    isOnTrack = rule.wantsPercentage in 1f..30f,
                    modifier = Modifier.weight(1f)
                )

                BudgetPillarColumn(
                    title = "Ahorro",
                    targetText = "Meta 20%",
                    currentPercentage = rule.savingsPercentage,
                    amount = rule.savingsAmount,
                    icon = Icons.Default.Savings,
                    color = savingsColor,
                    isExceeded = false,
                    isOnTrack = rule.savingsOnTrack,
                    modifier = Modifier.weight(1f)
                )
            }

            // 4. Diagnóstico y sugerencia inteligente compacta
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (rule.isBalanced) Icons.Default.CheckCircle else Icons.Default.Info,
                        contentDescription = null,
                        tint = if (rule.isBalanced) IncomeGreen else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = rule.statusSummary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/**
 * Columna métrica individual centrada para cada pilar del 50/30/20.
 */
@Composable
private fun BudgetPillarColumn(
    title: String,
    targetText: String,
    currentPercentage: Float,
    amount: Double,
    icon: ImageVector,
    color: Color,
    isExceeded: Boolean,
    isOnTrack: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        border = BorderStroke(
            1.dp,
            if (isExceeded) ExpenseRed.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            // Icono en cápsula circular
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(15.dp)
                )
            }

            // Título centrado
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Porcentaje actual grande y legible
            Text(
                text = "${String.format("%.1f", currentPercentage)}%",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (isExceeded) ExpenseRed else color,
                textAlign = TextAlign.Center
            )

            // Meta porcentual
            Text(
                text = targetText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
                textAlign = TextAlign.Center
            )

            // Monto monetario formateado
            Text(
                text = CurrencyFormatter.format(amount),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Etiqueta de estado
            val (statusText, statusBg, statusColor) = when {
                isExceeded -> Triple("Excedido", ExpenseRed.copy(alpha = 0.12f), ExpenseRed)
                isOnTrack -> Triple("En meta", IncomeGreen.copy(alpha = 0.12f), IncomeGreen)
                currentPercentage == 0f -> Triple("Sin gasto", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                else -> Triple("Ajustable", color.copy(alpha = 0.12f), color)
            }

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = statusBg,
                modifier = Modifier.padding(top = 2.dp)
            ) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = statusColor,
                    fontSize = 9.sp,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun LegendIndicator(
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp
        )
    }
}
