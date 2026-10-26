package com.example.presentation.dashboard.components

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
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.StackedBarChart
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.CustomCycleBalancePrediction
import com.example.domain.model.CycleDayDataPoint
import com.example.presentation.common.util.CurrencyFormatter
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.IncomeGreen
import kotlin.math.max
import kotlin.math.min

/**
 * Modo de visualización del Dashboard de Predicción y Balance.
 */
enum class PredictionChartMode {
    D3_TIMELINE_AREA,    // Curva interactiva de proyección D3 con interpolación y tooltips Recharts
    RECHARTS_BAR_COMPARISON // Comparativa directa de barras de ingresos vs balances estilo Recharts
}

/**
 * Panel de Resumen Dashboard con gráficos estilo Recharts y D3
 * para visualizar el balance real versus el ingreso previsto para el período de presupuesto personalizado activo.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CyclePredictionDashboardCard(
    prediction: CustomCycleBalancePrediction,
    onUpdatePredictedIncome: (Double?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var chartMode by remember { mutableStateOf(PredictionChartMode.D3_TIMELINE_AREA) }
    var selectedDayIndex by remember {
        mutableIntStateOf(prediction.daysElapsed.coerceIn(1, prediction.daysTotal.coerceAtLeast(1)))
    }
    var showEditTargetDialog by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("cycle_prediction_dashboard_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Encabezado principal: Título, Badge del Ciclo Personalizado y Botón de Ajuste
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    // Badge del Ciclo Financiero
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                        modifier = Modifier.testTag("cycle_range_badge")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = prediction.cycleRange.displayTitle,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Balance vs Ingreso Previsto",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (prediction.isCustomCycle) {
                            "Ciclo personalizado (corte día ${prediction.cycleRange.startDayOfMonth}) con motor D3 / Recharts"
                        } else {
                            "Ciclo mensual calendario con motor D3 / Recharts"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Botón para editar meta prevista
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier
                        .clickable { showEditTargetDialog = true }
                        .testTag("edit_predicted_income_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Editar meta de ingreso",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Meta",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // 2. Tira de Métricas KPI Clave (Estilo Recharts Telemetry Strip)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Balance Real del Ciclo
                KpiMetricCard(
                    modifier = Modifier.weight(1f),
                    title = "Balance Real",
                    amount = CurrencyFormatter.format(prediction.actualBalance),
                    caption = if (prediction.actualBalance >= 0) "Superávit neto" else "Déficit neto",
                    isPositive = prediction.actualBalance >= 0,
                    testTag = "kpi_actual_balance"
                )

                // Ingreso Previsto
                KpiMetricCard(
                    modifier = Modifier.weight(1f),
                    title = "Ingreso Previsto",
                    amount = CurrencyFormatter.format(prediction.predictedIncome),
                    caption = if (prediction.isAutoEstimated) "Estimado auto" else "Meta fijada",
                    isPositive = null,
                    testTag = "kpi_predicted_income"
                )

                // Progreso de Ingreso
                KpiMetricCard(
                    modifier = Modifier.weight(1f),
                    title = "Recibido",
                    amount = "${(prediction.incomeProgressPercent * 100).toInt()}%",
                    caption = "${CurrencyFormatter.format(prediction.actualIncome)} de meta",
                    isPositive = prediction.incomeProgressPercent >= 0.5f,
                    testTag = "kpi_income_progress"
                )
            }

            // 3. Barra de Selección de Modo Gráfico (Recharts vs D3 Area)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = chartMode == PredictionChartMode.D3_TIMELINE_AREA,
                        onClick = { chartMode = PredictionChartMode.D3_TIMELINE_AREA },
                        label = { Text("Proyección D3", fontSize = 12.sp) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ShowChart,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.testTag("chip_mode_d3_timeline")
                    )

                    FilterChip(
                        selected = chartMode == PredictionChartMode.RECHARTS_BAR_COMPARISON,
                        onClick = { chartMode = PredictionChartMode.RECHARTS_BAR_COMPARISON },
                        label = { Text("Barras Recharts", fontSize = 12.sp) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.StackedBarChart,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.testTag("chip_mode_recharts_bars")
                    )
                }

                // Días restantes en el ciclo
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Text(
                        text = "Día ${prediction.daysElapsed}/${prediction.daysTotal}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // 4. Área Gráfica Principal según el Modo Seleccionado
            when (chartMode) {
                PredictionChartMode.D3_TIMELINE_AREA -> {
                    D3TimelineProjectionChart(
                        prediction = prediction,
                        selectedDayIndex = selectedDayIndex,
                        onDaySelected = { selectedDayIndex = it }
                    )
                }
                PredictionChartMode.RECHARTS_BAR_COMPARISON -> {
                    RechartsComparativeBarChart(prediction = prediction)
                }
            }

            // 5. Explicación contextual de armonización del ciclo
            if (prediction.isCustomCycle) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Tus nóminas y gastos están armonizados dentro del corte ${prediction.cycleRange.dateRangeLabel}. Evita solapamientos entre las quincenas del día 15 y 30.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }

    // Modal para editar la meta de ingreso previsto
    if (showEditTargetDialog) {
        EditPredictedIncomeDialog(
            currentValue = prediction.predictedIncome,
            isAuto = prediction.isAutoEstimated,
            onDismiss = { showEditTargetDialog = false },
            onConfirm = { newAmount ->
                onUpdatePredictedIncome(newAmount)
                showEditTargetDialog = false
            }
        )
    }
}

/**
 * Gráfico interactivo de Proyección Temporal estilo D3 (d3.area + d3.line) y Recharts Tooltip.
 * Dibuja la curva continua del balance real acumulado, la meta de ingreso previsto y la proyección futura.
 */
@Composable
private fun D3TimelineProjectionChart(
    prediction: CustomCycleBalancePrediction,
    selectedDayIndex: Int,
    onDaySelected: (Int) -> Unit
) {
    val points = prediction.timelineDataPoints
    if (points.isEmpty()) return

    val selectedPoint = points.find { it.dayIndex == selectedDayIndex } ?: points.lastOrNull()

    // Escala Y máxima (asegurando margen para la meta de ingreso previsto y el balance real)
    val maxVal = remember(prediction, points) {
        val peakBalance = points.maxOfOrNull { it.accumulatedBalance } ?: 0.0
        val peakIncome = points.maxOfOrNull { it.accumulatedIncome } ?: 0.0
        max(prediction.predictedIncome, max(peakBalance, peakIncome)) * 1.15
    }.coerceAtLeast(100.0)

    val minVal = remember(points) {
        val lowestBalance = points.minOfOrNull { it.accumulatedBalance } ?: 0.0
        min(0.0, lowestBalance) * 1.15
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("d3_timeline_projection_chart"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Tooltip Flotante / Anclado de Recharts para el día seleccionado
        if (selectedPoint != null) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("recharts_timeline_tooltip")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Día ${selectedPoint.dayIndex} de ${prediction.daysTotal} • ${selectedPoint.dateLabel}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Balance: ${CurrencyFormatter.format(selectedPoint.accumulatedBalance)}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = if (selectedPoint.accumulatedBalance >= 0) IncomeGreen else ExpenseRed
                            )
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.bodySmall,
                                color = onSurfaceVariant
                            )
                            Text(
                                text = "Ingresos: ${CurrencyFormatter.format(selectedPoint.accumulatedIncome)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = onSurfaceVariant
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    ) {
                        val pct = if (selectedPoint.predictedIncomeBaseline > 0) {
                            ((selectedPoint.accumulatedIncome / selectedPoint.predictedIncomeBaseline) * 100).toInt()
                        } else 0
                        Text(
                            text = "$pct% de meta",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        // Canvas interactivo con D3 curves, baseline, grid y touch gestures
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f))
        ) {
            val canvasWidth = constraints.maxWidth.toFloat()
            val canvasHeight = constraints.maxHeight.toFloat()
            val totalDays = prediction.daysTotal

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(points, totalDays) {
                        detectTapGestures { offset ->
                            val touchedFraction = (offset.x / size.width).coerceIn(0f, 1f)
                            val dayIdx = (touchedFraction * totalDays).toInt().coerceIn(1, totalDays)
                            onDaySelected(dayIdx)
                        }
                    }
                    .pointerInput(points, totalDays) {
                        detectDragGestures { change, _ ->
                            change.consume()
                            val touchedFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                            val dayIdx = (touchedFraction * totalDays).toInt().coerceIn(1, totalDays)
                            onDaySelected(dayIdx)
                        }
                    }
            ) {
                val paddingLeft = 12.dp.toPx()
                val paddingRight = 12.dp.toPx()
                val paddingTop = 24.dp.toPx()
                val paddingBottom = 28.dp.toPx()

                val chartW = size.width - paddingLeft - paddingRight
                val chartH = size.height - paddingTop - paddingBottom
                val valRange = (maxVal - minVal).coerceAtLeast(1.0)

                fun xForDay(dayIndex: Int): Float {
                    val frac = (dayIndex - 1).toFloat() / (totalDays - 1).coerceAtLeast(1)
                    return paddingLeft + frac * chartW
                }

                fun yForValue(value: Double): Float {
                    val normalized = ((value - minVal) / valRange).toFloat()
                    return paddingTop + chartH * (1f - normalized)
                }

                // 1. Cuadrícula horizontal estilo Recharts (25%, 50%, 75%, 100%)
                val gridSteps = 4
                val dashEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)

                for (i in 0..gridSteps) {
                    val frac = i.toFloat() / gridSteps
                    val y = paddingTop + chartH * frac
                    drawLine(
                        color = gridColor,
                        start = Offset(paddingLeft, y),
                        end = Offset(paddingLeft + chartW, y),
                        strokeWidth = 1f,
                        pathEffect = dashEffect
                    )
                }

                // 2. Línea de Referencia: Ingreso Previsto (Target Line)
                val targetY = yForValue(prediction.predictedIncome)
                drawLine(
                    color = Color(0xFF00B4D8), // Cyan moderno de Recharts
                    start = Offset(paddingLeft, targetY),
                    end = Offset(paddingLeft + chartW, targetY),
                    strokeWidth = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                )

                // 3. Trayectoria de avance D3 hacia la meta (Dotted progression guide)
                val startX = xForDay(1)
                val startY = yForValue(0.0)
                val endX = xForDay(totalDays)
                val endY = yForValue(prediction.predictedIncome)
                drawLine(
                    color = primaryColor.copy(alpha = 0.25f),
                    start = Offset(startX, startY),
                    end = Offset(endX, endY),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f), 0f)
                )

                // 4. Construcción de curvas de Balance Real e Ingresos Acumulados
                val pastPoints = points.filter { it.isPastOrToday }
                if (pastPoints.isNotEmpty()) {
                    val balancePath = Path()
                    val areaPath = Path()
                    val incomePath = Path()

                    val firstX = xForDay(pastPoints.first().dayIndex)
                    val firstY = yForValue(pastPoints.first().accumulatedBalance)
                    val firstIncomeY = yForValue(pastPoints.first().accumulatedIncome)
                    val zeroY = yForValue(0.0)

                    balancePath.moveTo(firstX, firstY)
                    incomePath.moveTo(firstX, firstIncomeY)
                    areaPath.moveTo(firstX, zeroY)
                    areaPath.lineTo(firstX, firstY)

                    for (i in 1 until pastPoints.size) {
                        val prev = pastPoints[i - 1]
                        val cur = pastPoints[i]

                        val pX = xForDay(prev.dayIndex)
                        val pY = yForValue(prev.accumulatedBalance)
                        val cX = xForDay(cur.dayIndex)
                        val cY = yForValue(cur.accumulatedBalance)

                        // Curva de Bezier cúbica suave estilo D3
                        val midX = (pX + cX) / 2f
                        balancePath.cubicTo(midX, pY, midX, cY, cX, cY)
                        areaPath.cubicTo(midX, pY, midX, cY, cX, cY)

                        // Curva de ingresos
                        val pIncY = yForValue(prev.accumulatedIncome)
                        val cIncY = yForValue(cur.accumulatedIncome)
                        incomePath.cubicTo(midX, pIncY, midX, cIncY, cX, cIncY)
                    }

                    val lastX = xForDay(pastPoints.last().dayIndex)
                    areaPath.lineTo(lastX, zeroY)
                    areaPath.close()

                    // Relleno degradado estilo Recharts AreaChart
                    drawPath(
                        path = areaPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                primaryColor.copy(alpha = 0.35f),
                                primaryColor.copy(alpha = 0.05f)
                            ),
                            startY = paddingTop,
                            endY = zeroY
                        )
                    )

                    // Trazo de Ingresos acumulados (Azul cian)
                    drawPath(
                        path = incomePath,
                        color = Color(0xFF38BDF8),
                        style = Stroke(
                            width = 2.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    )

                    // Trazo de Balance Real (Verde esmeralda o rojo)
                    val strokeColor = if (prediction.actualBalance >= 0) IncomeGreen else ExpenseRed
                    drawPath(
                        path = balancePath,
                        color = strokeColor,
                        style = Stroke(
                            width = 3.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    )
                }

                // 5. Proyección futura hacia el fin del ciclo (Línea discontinua)
                val futurePoints = points.filter { !it.isPastOrToday }
                if (futurePoints.isNotEmpty() && pastPoints.isNotEmpty()) {
                    val lastPast = pastPoints.last()
                    val lastX = xForDay(lastPast.dayIndex)
                    val lastY = yForValue(lastPast.accumulatedBalance)
                    val endProjectionX = xForDay(totalDays)
                    val endProjectionY = yForValue(prediction.predictedIncome)

                    drawLine(
                        color = primaryColor.copy(alpha = 0.5f),
                        start = Offset(lastX, lastY),
                        end = Offset(endProjectionX, endProjectionY),
                        strokeWidth = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                    )
                }

                // 6. Línea vertical del cursor y punto focal interactivo
                val currentSelectedPt = points.find { it.dayIndex == selectedDayIndex }
                if (currentSelectedPt != null) {
                    val focalX = xForDay(currentSelectedPt.dayIndex)
                    val focalY = yForValue(currentSelectedPt.accumulatedBalance)

                    // Cursor vertical de Recharts
                    drawLine(
                        color = primaryColor.copy(alpha = 0.6f),
                        start = Offset(focalX, paddingTop),
                        end = Offset(focalX, size.height - paddingBottom),
                        strokeWidth = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f)
                    )

                    // Anillo exterior brillante
                    drawCircle(
                        color = primaryColor.copy(alpha = 0.3f),
                        radius = 9.dp.toPx(),
                        center = Offset(focalX, focalY)
                    )
                    // Punto central relleno
                    drawCircle(
                        color = if (currentSelectedPt.accumulatedBalance >= 0) IncomeGreen else ExpenseRed,
                        radius = 4.5.dp.toPx(),
                        center = Offset(focalX, focalY)
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 2.dp.toPx(),
                        center = Offset(focalX, focalY)
                    )
                }
            }
        }

        // Leyendas de referencia estilo Recharts Legend
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LegendItem(
                color = if (prediction.actualBalance >= 0) IncomeGreen else ExpenseRed,
                label = "Balance Real"
            )
            LegendItem(
                color = Color(0xFF38BDF8),
                label = "Ingresos Recibidos"
            )
            LegendItem(
                color = Color(0xFF00B4D8),
                label = "Meta Prevista (${CurrencyFormatter.format(prediction.predictedIncome)})"
            )
        }
    }
}

/**
 * Gráfico Comparativo de Barras Agrupadas estilo Recharts (<BarChart />).
 */
@Composable
private fun RechartsComparativeBarChart(prediction: CustomCycleBalancePrediction) {
    val maxBarAmount = remember(prediction) {
        max(
            prediction.predictedIncome,
            max(prediction.actualIncome, max(prediction.actualExpense, prediction.actualBalance.coerceAtLeast(0.0)))
        ).coerceAtLeast(1.0)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("recharts_comparative_bar_chart"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Barra 1: Ingreso Previsto vs Ingreso Real Recibido
        ComparativeBarRow(
            title = "Ingreso Previsto vs Recibido",
            currentAmount = prediction.actualIncome,
            targetAmount = prediction.predictedIncome,
            maxReference = maxBarAmount,
            barColor = Color(0xFF0284C7),
            badgeText = "${(prediction.incomeProgressPercent * 100).toInt()}% recibido",
            caption = if (prediction.remainingPredictedIncome > 0) {
                "Faltan ${CurrencyFormatter.format(prediction.remainingPredictedIncome)} para cumplir la meta"
            } else {
                "¡Meta de ingresos del ciclo cumplida con éxito!"
            }
        )

        // Barra 2: Balance Neto del Período
        ComparativeBarRow(
            title = "Balance Neto Real",
            currentAmount = prediction.actualBalance.coerceAtLeast(0.0),
            targetAmount = prediction.predictedIncome,
            maxReference = maxBarAmount,
            barColor = if (prediction.actualBalance >= 0) IncomeGreen else ExpenseRed,
            badgeText = if (prediction.actualBalance >= 0) "Superávit" else "Déficit",
            caption = "Margen de ahorro del ciclo: ${(prediction.balanceVsPredictedRatio * 100).toInt()}%"
        )

        // Barra 3: Gastos del Período sobre la Meta de Ingresos
        val expensePercent = if (prediction.predictedIncome > 0) {
            (prediction.actualExpense / prediction.predictedIncome * 100).toInt()
        } else 0

        ComparativeBarRow(
            title = "Gastos Totales Incurridos",
            currentAmount = prediction.actualExpense,
            targetAmount = prediction.predictedIncome,
            maxReference = maxBarAmount,
            barColor = ExpenseRed,
            badgeText = "$expensePercent% de ingresos consumidos",
            caption = "${CurrencyFormatter.format(prediction.actualExpense)} gastados en el ciclo"
        )
    }
}

@Composable
private fun ComparativeBarRow(
    title: String,
    currentAmount: Double,
    targetAmount: Double,
    maxReference: Double,
    barColor: Color,
    badgeText: String,
    caption: String
) {
    val progress = (currentAmount / maxReference).coerceIn(0.0, 1.0).toFloat()
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "bar_anim"
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = barColor.copy(alpha = 0.15f)
            ) {
                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = barColor,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        // Barra con Canvas de pista y valor redondeado
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(18.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val filledWidth = size.width * animatedProgress
                if (filledWidth > 0) {
                    drawRoundRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(barColor.copy(alpha = 0.8f), barColor)
                        ),
                        size = Size(filledWidth, size.height),
                        cornerRadius = CornerRadius(18f, 18f)
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = caption,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = CurrencyFormatter.format(currentAmount),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun KpiMetricCard(
    title: String,
    amount: String,
    caption: String,
    isPositive: Boolean?,
    modifier: Modifier = Modifier,
    testTag: String = ""
) {
    Surface(
        modifier = modifier.testTag(testTag),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = amount,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold,
                color = when (isPositive) {
                    true -> IncomeGreen
                    false -> ExpenseRed
                    null -> MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = caption,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = color,
            modifier = Modifier.size(8.dp)
        ) {}
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Diálogo interactivo para personalizar la meta de ingreso previsto para el ciclo.
 */
@Composable
private fun EditPredictedIncomeDialog(
    currentValue: Double,
    isAuto: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Double?) -> Unit
) {
    var textValue by remember {
        mutableStateOf(if (isAuto) currentValue.toInt().toString() else currentValue.toInt().toString())
    }
    val presets = listOf(1500.0, 2000.0, 2500.0, 3000.0, 4000.0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Meta de Ingreso Previsto",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = "Ajusta el monto total de ingresos que esperas recibir en este ciclo contable (ej. suma de dos nóminas del 15 y 30).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = textValue,
                    onValueChange = { input ->
                        if (input.all { it.isDigit() || it == '.' }) {
                            textValue = input
                        }
                    },
                    label = { Text("Ingreso Previsto ($)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Montos rápidos sugeridos:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    presets.forEach { preset ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { textValue = preset.toInt().toString() }
                        ) {
                            Text(
                                text = "$${preset.toInt()}",
                                style = MaterialTheme.typography.labelSmall,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = textValue.toDoubleOrNull()
                    onConfirm(amount)
                }
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { onConfirm(null) }) {
                    Text("Auto")
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancelar")
                }
            }
        }
    )
}
