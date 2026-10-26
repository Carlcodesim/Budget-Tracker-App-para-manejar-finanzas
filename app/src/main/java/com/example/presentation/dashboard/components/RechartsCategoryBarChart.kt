package com.example.presentation.dashboard.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.presentation.analysis.TypedExpenseShare
import com.example.presentation.common.components.RechartsShareTooltip
import com.example.presentation.common.util.CurrencyFormatter
import com.example.presentation.common.util.IconHelper

/**
 * Componente de gráfico de barras interactivo estilo Recharts (<BarChart layout="vertical" />)
 * implementado con Jetpack Compose Canvas estándar para visualizar gastos mensuales por categoría.
 *
 * Características:
 * - Barras animadas con aceleración suave (FastOutSlowInEasing).
 * - Renderizado en Canvas de barra de fondo (track) y barra de valor con esquinas redondeadas.
 * - Líneas de referencia y marcas de cuadrícula estilo Recharts (25%, 50%, 75%, 100%).
 * - Selección interactiva por toque que resalta la categoría y despliega el Tooltip con telemetría completa.
 */
@Composable
fun RechartsCategoryBarChart(
    items: List<TypedExpenseShare>,
    totalAmount: Double,
    selectedIndex: Int?,
    onSelectIndex: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return

    // Ordenar de mayor a menor gasto para óptima lectura visual
    val sortedItems = remember(items) { items.sortedByDescending { it.amount } }
    val maxCategoryAmount = remember(sortedItems) {
        sortedItems.maxOfOrNull { it.amount }?.coerceAtLeast(1.0) ?: 1.0
    }

    val selectedShare = remember(selectedIndex, sortedItems) {
        if (selectedIndex != null && selectedIndex in sortedItems.indices) {
            sortedItems[selectedIndex]
        } else {
            null
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("recharts_category_bar_chart"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Tooltip interactivo flotante cuando hay una categoría seleccionada
        AnimatedVisibility(
            visible = selectedShare != null,
            enter = fadeIn(tween(250)),
            exit = fadeOut(tween(200))
        ) {
            if (selectedShare != null) {
                RechartsShareTooltip(
                    share = selectedShare,
                    totalAmount = totalAmount,
                    onDismiss = { onSelectIndex(null) }
                )
            }
        }

        if (selectedShare == null) {
            // Hint de interacción sutil
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.TouchApp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "Toca cualquier barra para inspeccionar porcentaje y detalles",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }

        // Lista de filas de barras horizontales dibujadas con Compose Canvas
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            sortedItems.forEachIndexed { index, item ->
                val isSelected = selectedIndex == index
                val hasSelection = selectedIndex != null

                RechartsBarRow(
                    item = item,
                    maxAmount = maxCategoryAmount,
                    isSelected = isSelected,
                    hasSelection = hasSelection,
                    onClick = {
                        onSelectIndex(if (isSelected) null else index)
                    }
                )
            }
        }

        // Eje de referencia Recharts (0%, 25%, 50%, 75%, 100% de la categoría dominante)
        RechartsBarReferenceAxis(maxAmount = maxCategoryAmount)
    }
}

/**
 * Renglón individual de categoría con barra dibujada en Canvas nativo.
 */
@Composable
private fun RechartsBarRow(
    item: TypedExpenseShare,
    maxAmount: Double,
    isSelected: Boolean,
    hasSelection: Boolean,
    onClick: () -> Unit
) {
    val targetRatio = (item.amount / maxAmount).toFloat().coerceIn(0f, 1f)
    val animatedRatio by animateFloatAsState(
        targetValue = targetRatio,
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "barRatioAnimation"
    )

    val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant
    val outlineVariantColor = MaterialTheme.colorScheme.outlineVariant
    val pctFormatted = "${String.format("%.1f", item.percentage * 100)}%"

    val alpha = if (!hasSelection || isSelected) 1.0f else 0.4f

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) {
            item.color.copy(alpha = 0.08f)
        } else {
            Color.Transparent
        },
        border = if (isSelected) {
            BorderStroke(1.5.dp, item.color.copy(alpha = 0.7f))
        } else {
            null
        },
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag("recharts_bar_row_${item.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Cabecera de la barra: Icono, Nombre, Porcentaje y Monto
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = item.color.copy(alpha = 0.16f * alpha),
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = IconHelper.getIcon(item.iconKey),
                                contentDescription = null,
                                tint = item.color.copy(alpha = alpha),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = item.color.copy(alpha = 0.12f * alpha)
                    ) {
                        Text(
                            text = pctFormatted,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = item.color.copy(alpha = alpha),
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }

                    Text(
                        text = CurrencyFormatter.format(item.amount),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
                    )
                }
            }

            // Canvas Bar: Pista de fondo + Barra coloreada con esquinas redondeadas
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
            ) {
                val barHeight = size.height
                val barWidth = size.width
                val cornerRadius = CornerRadius(barHeight / 2f, barHeight / 2f)

                // 1. Pista de fondo (Track)
                drawRoundRect(
                    color = surfaceVariantColor.copy(alpha = 0.5f),
                    size = Size(barWidth, barHeight),
                    cornerRadius = cornerRadius
                )

                // 2. Líneas de referencia sutiles a 25%, 50%, 75%
                val tickAlpha = 0.25f
                val tickWidth = 1.5.dp.toPx()
                listOf(0.25f, 0.50f, 0.75f).forEach { fraction ->
                    val x = barWidth * fraction
                    drawLine(
                        color = outlineVariantColor.copy(alpha = tickAlpha),
                        start = Offset(x, 0f),
                        end = Offset(x, barHeight),
                        strokeWidth = tickWidth
                    )
                }

                // 3. Barra animada con degradado
                val activeWidth = (barWidth * animatedRatio).coerceAtLeast(barHeight)
                val barBrush = Brush.horizontalGradient(
                    colors = listOf(
                        item.color.copy(alpha = 0.85f * alpha),
                        item.color.copy(alpha = alpha)
                    ),
                    startX = 0f,
                    endX = activeWidth
                )

                drawRoundRect(
                    brush = barBrush,
                    size = Size(activeWidth, barHeight),
                    cornerRadius = cornerRadius
                )

                // 4. Borde activo si está seleccionada
                if (isSelected) {
                    drawRoundRect(
                        color = item.color,
                        size = Size(activeWidth, barHeight),
                        cornerRadius = cornerRadius,
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }
            }
        }
    }
}

/**
 * Eje de referencia inferior estilo Recharts con marcas de cuadrícula porcentuales.
 */
@Composable
private fun RechartsBarReferenceAxis(
    maxAmount: Double
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        listOf(0.0, 0.25, 0.50, 0.75, 1.0).forEach { frac ->
            val valueAtFrac = maxAmount * frac
            Text(
                text = if (frac == 0.0) "0" else CurrencyFormatter.format(valueAtFrac),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}
