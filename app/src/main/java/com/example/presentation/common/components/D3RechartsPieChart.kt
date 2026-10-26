package com.example.presentation.common.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.TouchApp
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.presentation.analysis.TypedExpenseShare
import com.example.presentation.common.util.CurrencyFormatter
import com.example.presentation.common.util.IconHelper
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Representa la geometría precalculada de un sector según el algoritmo de D3 (d3.pie + d3.arc).
 * Incluye valores trigonométricos y ángulos normalizados precomputados para eliminar sobrecosto en tiempo de renderizado.
 */
data class D3PieArcSector(
    val index: Int,
    val data: TypedExpenseShare,
    val startAngleDeg: Float,
    val sweepAngleDeg: Float,
    val endAngleDeg: Float,
    val midAngleDeg: Float,
    val midAngleRad: Float,
    val cosMid: Float,
    val sinMid: Float,
    val startNormDeg: Float,
    val padAngleDeg: Float
) {
    /**
     * Hit-testing optimizado de punto angular sin cálculos trigonométricos adicionales.
     */
    fun containsNormalizedAngle(relAngle: Float): Boolean {
        val sweep = sweepAngleDeg
        return if (startNormDeg + sweep <= 360f) {
            relAngle >= startNormDeg && relAngle < startNormDeg + sweep
        } else {
            relAngle >= startNormDeg || relAngle < (startNormDeg + sweep) % 360f
        }
    }
}

/**
 * Componente nativo de Jetpack Compose de alto rendimiento que implementa la lógica de
 * D3.js (d3.pie, d3.arc) y Recharts (Pie, Sector Pop-out, Tooltip dinámico y Leyenda interactiva).
 *
 * Optimizaciones de rendimiento:
 * 1. Geometría desacoplada de la selección: el cambio de selección es O(1) sin recalculación geométrica.
 * 2. Precomputación trigonométrica (cos/sin del centroide) para eliminar llamadas trigonométricas en draw.
 * 3. Hit-testing normalizado rápido en gestos táctiles.
 * 4. Animación fluida de barrido angular a 60/120 fps.
 */
@Composable
fun D3RechartsPieChart(
    items: List<TypedExpenseShare>,
    totalAmount: Double,
    modifier: Modifier = Modifier,
    isDonut: Boolean = false,
    chartSize: Dp = 220.dp,
    showLegend: Boolean = true,
    showTooltipCard: Boolean = true,
    selectedIndex: Int? = null,
    onSelectIndex: (Int?) -> Unit = {}
) {
    if (items.isEmpty() || totalAmount <= 0) {
        EmptyPieChartPlaceholder()
        return
    }

    var animationPlayed by remember { mutableStateOf(false) }
    val sweepFactor by animateFloatAsState(
        targetValue = if (animationPlayed) 1f else 0f,
        animationSpec = tween(durationMillis = 850, easing = FastOutSlowInEasing),
        label = "d3PieSweep"
    )

    LaunchedEffect(items, isDonut) {
        animationPlayed = false
        animationPlayed = true
    }

    // Cálculo D3.pie memorizado estrictamente por datos (independiente del índice seleccionado)
    val sectors = remember(items, totalAmount) {
        calculateD3PieSectors(items, totalAmount)
    }

    val selectedSector = selectedIndex?.let { sectors.getOrNull(it) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .testTag("d3_recharts_pie_chart_container")
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Contenedor del Canvas y del Tooltip Flotante de Recharts
            Box(
                modifier = Modifier
                    .size(chartSize)
                    .testTag("d3_recharts_pie_canvas_box"),
                contentAlignment = Alignment.Center
            ) {
                val surfaceColor = MaterialTheme.colorScheme.surface

                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(sectors, isDonut) {
                            detectTapGestures { offset ->
                                val center = Offset(size.width / 2f, size.height / 2f)
                                val dx = offset.x - center.x
                                val dy = offset.y - center.y
                                val distance = sqrt(dx * dx + dy * dy)
                                val radius = minOf(size.width, size.height).toFloat() / 2f

                                val minTouchRadius = if (isDonut) radius * 0.45f else 0f
                                val maxTouchRadius = radius * 1.18f

                                if (distance in minTouchRadius..maxTouchRadius) {
                                    var touchAngle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                                    if (touchAngle < 0) touchAngle += 360f

                                    // Normalizar respecto a -90° (12 en punto)
                                    val relAngle = (touchAngle + 90f) % 360f

                                    var clickedIdx: Int? = null
                                    for (sector in sectors) {
                                        if (sector.containsNormalizedAngle(relAngle)) {
                                            clickedIdx = sector.index
                                            break
                                        }
                                    }

                                    onSelectIndex(if (selectedIndex == clickedIdx) null else clickedIdx)
                                } else {
                                    onSelectIndex(null)
                                }
                            }
                        }
                ) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val minDim = minOf(size.width, size.height)
                    val baseRadius = (minDim - 24.dp.toPx()) / 2f
                    val donutStrokeWidth = 32.dp.toPx()
                    val popDistanceSelected = 8.dp.toPx()
                    val extraRadiusSelected = 4.dp.toPx()
                    val hasSelection = selectedIndex != null

                    sectors.forEach { sector ->
                        val isSelected = selectedIndex == sector.index
                        val animatedSweep = sector.sweepAngleDeg * sweepFactor
                        if (animatedSweep <= 0.2f) return@forEach

                        // Pop-Out Recharts: traslación radial instantánea usando cosMid/sinMid precalculados
                        val popDistance = if (isSelected) popDistanceSelected else 0f
                        val popDx = sector.cosMid * popDistance
                        val popDy = sector.sinMid * popDistance

                        val sectorRadius = if (isSelected) baseRadius + extraRadiusSelected else baseRadius
                        val sectorCenter = Offset(center.x + popDx, center.y + popDy)

                        // D3 pad angle entre sectores
                        val effectivePad = if (sectors.size > 1) sector.padAngleDeg else 0f
                        val effectiveSweep = (animatedSweep - effectivePad).coerceAtLeast(0.1f)
                        val startAngle = sector.startAngleDeg + (effectivePad / 2f)

                        val alpha = if (!hasSelection || isSelected) 1.0f else 0.4f
                        val sectorColor = sector.data.color.copy(alpha = alpha)
                        val sectorDiameter = sectorRadius * 2
                        val sectorTopLeft = Offset(sectorCenter.x - sectorRadius, sectorCenter.y - sectorRadius)
                        val sectorSize = Size(sectorDiameter, sectorDiameter)

                        if (isDonut) {
                            // Donut Arc (useCenter = false)
                            val strokeW = if (isSelected) donutStrokeWidth + 6.dp.toPx() else donutStrokeWidth
                            drawArc(
                                color = sectorColor,
                                startAngle = startAngle,
                                sweepAngle = effectiveSweep,
                                useCenter = false,
                                topLeft = sectorTopLeft,
                                size = sectorSize,
                                style = Stroke(width = strokeW, cap = StrokeCap.Round)
                            )
                        } else {
                            // Solid Pie Wedge (useCenter = true)
                            drawArc(
                                color = sectorColor,
                                startAngle = startAngle,
                                sweepAngle = effectiveSweep,
                                useCenter = true,
                                topLeft = sectorTopLeft,
                                size = sectorSize,
                                style = Fill
                            )

                            // Borde de separación entre rebanadas
                            drawArc(
                                color = surfaceColor,
                                startAngle = startAngle,
                                sweepAngle = effectiveSweep,
                                useCenter = true,
                                topLeft = sectorTopLeft,
                                size = sectorSize,
                                style = Stroke(width = if (isSelected) 2.5.dp.toPx() else 1.5.dp.toPx())
                            )
                        }
                    }
                }

                // Centro del Donut: Telemetría central interactiva
                if (isDonut) {
                    DonutCenterTelemetry(
                        selectedSector = selectedSector,
                        totalAmount = totalAmount,
                        onClearSelection = { onSelectIndex(null) }
                    )
                }
            }

            // Tooltip flotante interactivo de Recharts al tocar un sector
            if (showTooltipCard && selectedSector != null) {
                RechartsInteractiveTooltip(
                    sector = selectedSector,
                    totalAmount = totalAmount,
                    onDismiss = { onSelectIndex(null) }
                )
            } else if (showTooltipCard && selectedSector == null && !isDonut) {
                // Hint sutil para el usuario
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.TouchApp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Toca cualquier sector para ver el desglose en detalle",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }

            // Leyenda interactiva sincronizada de Recharts
            if (showLegend) {
                RechartsInteractiveLegend(
                    sectors = sectors,
                    selectedIndex = selectedIndex,
                    onSelectIndex = onSelectIndex
                )
            }
        }
    }
}

/**
 * Algoritmo D3.pie para computar ángulos y centroides de sectores.
 * Precomputa razones trigonométricas y ángulos normalizados para máximo rendimiento.
 */
private fun calculateD3PieSectors(
    items: List<TypedExpenseShare>,
    totalAmount: Double
): List<D3PieArcSector> {
    var currentAngle = -90f // 12 en punto
    val totalPct = items.sumOf { it.percentage.toDouble() }.toFloat().coerceAtLeast(0.001f)

    return items.mapIndexed { index, item ->
        val rawSweep = (item.percentage / totalPct) * 360f
        val start = currentAngle
        val sweep = rawSweep
        val end = start + sweep
        val mid = start + (sweep / 2f)
        val midRad = (mid * PI / 180f).toFloat()

        val cosM = cos(midRad)
        val sinM = sin(midRad)
        val startNorm = (start + 90f + 360f) % 360f

        currentAngle += sweep

        D3PieArcSector(
            index = index,
            data = item,
            startAngleDeg = start,
            sweepAngleDeg = sweep,
            endAngleDeg = end,
            midAngleDeg = mid,
            midAngleRad = midRad,
            cosMid = cosM,
            sinMid = sinM,
            startNormDeg = startNorm,
            padAngleDeg = if (items.size > 1) 1.5f else 0f
        )
    }
}

/**
 * Tooltip interactivo estilo Recharts (<Tooltip content={<CustomTooltip />} />)
 * Muestra el color, icono, nombre de categoría/tipo, monto exacto y porcentaje.
 */
@Composable
fun RechartsShareTooltip(
    share: TypedExpenseShare,
    totalAmount: Double,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pctFormatted = "${String.format("%.1f", share.percentage * 100)}%"

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f)
        ),
        border = BorderStroke(1.5.dp, share.color),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .testTag("recharts_active_tooltip_card")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                // Insignia circular con color e icono
                Surface(
                    shape = CircleShape,
                    color = share.color.copy(alpha = 0.2f),
                    border = BorderStroke(1.5.dp, share.color),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = IconHelper.getIcon(share.iconKey),
                            contentDescription = null,
                            tint = share.color,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Column {
                    Text(
                        text = share.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (share.description.isNotEmpty()) {
                        Text(
                            text = share.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = CurrencyFormatter.format(share.amount),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = share.color
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = share.color.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = pctFormatted,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = share.color,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cerrar tooltip",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/**
 * Delegado de Tooltip interactivo estilo Recharts para sectores del gráfico D3 / Pie.
 */
@Composable
private fun RechartsInteractiveTooltip(
    sector: D3PieArcSector,
    totalAmount: Double,
    onDismiss: () -> Unit
) {
    RechartsShareTooltip(
        share = sector.data,
        totalAmount = totalAmount,
        onDismiss = onDismiss
    )
}

/**
 * Telemetría central interactiva del Donut (<DonutCenter />).
 */
@Composable
private fun DonutCenterTelemetry(
    selectedSector: D3PieArcSector?,
    totalAmount: Double,
    onClearSelection: () -> Unit
) {
    Column(
        modifier = Modifier
            .padding(18.dp)
            .clip(CircleShape)
            .clickable { onClearSelection() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (selectedSector != null) {
            val share = selectedSector.data
            Surface(
                shape = CircleShape,
                color = share.color.copy(alpha = 0.15f),
                modifier = Modifier.size(26.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = IconHelper.getIcon(share.iconKey),
                        contentDescription = null,
                        tint = share.color,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = share.name,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = CurrencyFormatter.format(share.amount),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold,
                color = share.color
            )

            Text(
                text = "${(share.percentage * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(
                text = "TOTAL GASTOS",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = CurrencyFormatter.format(totalAmount),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Toca un sector",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * Leyenda interactiva de Recharts (<Legend />) con badges circulares y chips de selección.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RechartsInteractiveLegend(
    sectors: List<D3PieArcSector>,
    selectedIndex: Int?,
    onSelectIndex: (Int?) -> Unit
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("recharts_interactive_legend")
    ) {
        sectors.forEach { sector ->
            val share = sector.data
            val isSelected = selectedIndex == sector.index
            val pct = (share.percentage * 100).toInt()

            FilterChip(
                selected = isSelected,
                onClick = {
                    onSelectIndex(if (isSelected) null else sector.index)
                },
                leadingIcon = {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(share.color)
                    )
                },
                label = {
                    Text(
                        text = "${share.name} ($pct%)",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = share.color.copy(alpha = 0.18f),
                    selectedLabelColor = MaterialTheme.colorScheme.onSurface
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = isSelected,
                    borderColor = if (isSelected) share.color else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
            )
        }
    }
}

@Composable
private fun EmptyPieChartPlaceholder() {
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
            text = "Sin datos de gastos para graficar",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
