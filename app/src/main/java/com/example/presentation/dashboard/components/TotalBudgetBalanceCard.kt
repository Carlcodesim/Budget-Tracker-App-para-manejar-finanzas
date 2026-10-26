package com.example.presentation.dashboard.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.BalanceSummary
import com.example.presentation.common.util.CurrencyFormatter

/**
 * Modern, high-craft Dashboard UI component designed to display the current total budget balance,
 * financial performance metrics, spending ratio, and interactive privacy toggle.
 */
@Composable
fun TotalBudgetBalanceCard(
    summary: BalanceSummary,
    modifier: Modifier = Modifier,
    onWalletClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var isBalanceHidden by remember { mutableStateOf(false) }

    val formattedBalance = remember(summary.currentBalance) {
        CurrencyFormatter.format(summary.currentBalance)
    }
    val formattedIncome = remember(summary.monthlyIncome) {
        CurrencyFormatter.format(summary.monthlyIncome)
    }
    val formattedExpense = remember(summary.monthlyExpense) {
        CurrencyFormatter.format(summary.monthlyExpense)
    }

    // Calcular ratio de consumo del presupuesto (Gasto / Ingreso)
    val expenseRatio = remember(summary.monthlyIncome, summary.monthlyExpense) {
        if (summary.monthlyIncome > 0) {
            (summary.monthlyExpense / summary.monthlyIncome).toFloat().coerceIn(0f, 1f)
        } else if (summary.monthlyExpense > 0) {
            1f
        } else {
            0f
        }
    }

    val animatedProgress by animateFloatAsState(
        targetValue = expenseRatio,
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "budgetProgressAnimation"
    )

    // Tasa de ahorro calculada
    val savingsRate = remember(summary.monthlyIncome, summary.monthlyExpense) {
        if (summary.monthlyIncome > 0) {
            val saved = summary.monthlyIncome - summary.monthlyExpense
            ((saved / summary.monthlyIncome) * 100).toInt()
        } else {
            0
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .testTag("total_budget_balance_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF1E3C72), // Vivid Navy-Sapphire
                            Color(0xFF0F2027), // Deep Obsidian Abyss
                            Color(0xFF05131E)  // Dark Slate Contrast
                        ),
                        center = Offset(0f, 0f),
                        radius = 900f
                    )
                )
                .drawBehind {
                    // Decorative ambient glow effect
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFF00C6FF).copy(alpha = 0.25f), Color.Transparent),
                            center = Offset(size.width * 0.85f, size.height * 0.15f),
                            radius = size.width * 0.45f
                        )
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFF0072C6).copy(alpha = 0.15f), Color.Transparent),
                            center = Offset(size.width * 0.15f, size.height * 0.9f),
                            radius = size.width * 0.5f
                        )
                    )
                }
        ) {
            val isCompact = maxWidth < 340.dp
            val isExpanded = maxWidth >= 600.dp

            val contentPadding = when {
                isCompact -> 12.dp
                isExpanded -> 22.dp
                else -> 16.dp
            }

            val balanceFontSize = when {
                isCompact -> 24.sp
                isExpanded -> 34.sp
                else -> 28.sp
            }

            val subCardPadding = if (isCompact) 8.dp else 10.dp

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(contentPadding)
            ) {
                // Header: Título, Privacy Toggle y Google Wallet
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF00C6FF).copy(alpha = 0.18f),
                            modifier = Modifier.size(if (isCompact) 22.dp else 26.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Box(
                                    modifier = Modifier
                                        .size(if (isCompact) 6.dp else 8.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF00C6FF))
                                )
                            }
                        }

                        Text(
                            text = "BALANCE TOTAL",
                            style = MaterialTheme.typography.labelSmall.copy(
                                letterSpacing = if (isCompact) 0.8.sp else 1.2.sp,
                                fontWeight = FontWeight.Bold,
                                fontSize = if (isCompact) 9.sp else 11.sp
                            ),
                            color = Color.White.copy(alpha = 0.75f)
                        )

                        IconButton(
                            onClick = { isBalanceHidden = !isBalanceHidden },
                            modifier = Modifier
                                .size(if (isCompact) 24.dp else 28.dp)
                                .testTag("balance_visibility_toggle")
                        ) {
                            Icon(
                                imageVector = if (isBalanceHidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (isBalanceHidden) "Mostrar saldo" else "Ocultar saldo",
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(if (isCompact) 14.dp else 16.dp)
                            )
                        }
                    }

                    // Badge del Ciclo Financiero
                    val cycleRange = remember { com.example.util.BudgetCycleManager.getCurrentCycleRange() }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color.White.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = null,
                                tint = Color(0xFF00C6FF),
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = if (cycleRange.isCustomCycle) cycleRange.dateRangeLabel else cycleRange.cycleName,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = if (isCompact) 9.sp else 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = Color.White.copy(alpha = 0.95f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Balance Principal con Animación
                AnimatedContent(
                    targetState = isBalanceHidden,
                    transitionSpec = {
                        (slideInVertically { it / 2 } + fadeIn(tween(250))) togetherWith
                                (slideOutVertically { -it / 2 } + fadeOut(tween(250)))
                    },
                    label = "balanceVisibilityAnimation"
                ) { hidden ->
                    if (hidden) {
                        Text(
                            text = "••••••••",
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontSize = balanceFontSize,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp
                            ),
                            color = Color.White
                        )
                    } else {
                        Text(
                            text = formattedBalance,
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontSize = balanceFontSize,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.5).sp
                            ),
                            color = Color.White
                        )
                    }
                }

                // Badge de Rendimiento / Tasa de Ahorro
                if (savingsRate > 0 && summary.monthlyIncome > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF2ECC71).copy(alpha = 0.18f),
                        border = BorderStroke(1.dp, Color(0xFF2ECC71).copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                                contentDescription = null,
                                tint = Color(0xFF2ECC71),
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "$savingsRate% de ahorro este mes",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = if (isCompact) 9.sp else 10.sp),
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2ECC71)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(if (isCompact) 10.dp else 12.dp))

                // Fila de Tarjetas Vidriadas: Ingresos vs Gastos
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(if (isCompact) 8.dp else 10.dp)
                ) {
                    // Tarjeta Ingresos
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                            .padding(subCardPadding)
                    ) {
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(if (isCompact) 16.dp else 18.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF2ECC71).copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                                        contentDescription = null,
                                        tint = Color(0xFF2ECC71),
                                        modifier = Modifier.size(if (isCompact) 8.dp else 10.dp)
                                    )
                                }
                                Text(
                                    text = "INGRESOS",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = if (isCompact) 9.sp else 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.8.sp
                                    ),
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = if (isBalanceHidden) "••••" else "+$formattedIncome",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontSize = if (isCompact) 12.sp else 14.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = Color(0xFF2ECC71),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Tarjeta Gastos
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                            .padding(subCardPadding)
                    ) {
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(if (isCompact) 16.dp else 18.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFFF7675).copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.TrendingDown,
                                        contentDescription = null,
                                        tint = Color(0xFFFF7675),
                                        modifier = Modifier.size(if (isCompact) 8.dp else 10.dp)
                                    )
                                }
                                Text(
                                    text = "GASTOS",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = if (isCompact) 9.sp else 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.8.sp
                                    ),
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = if (isBalanceHidden) "••••" else "-$formattedExpense",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontSize = if (isCompact) 12.sp else 14.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = Color(0xFFFF7675),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Barra de Progreso de Consumo de Presupuesto
                if (summary.monthlyIncome > 0) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Presupuesto utilizado",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = Color.White.copy(alpha = 0.65f)
                            )
                            Text(
                                text = "${(animatedProgress * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                fontWeight = FontWeight.Bold,
                                color = if (animatedProgress > 0.85f) Color(0xFFFF7675) else Color.White
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = if (animatedProgress > 0.85f) Color(0xFFFF7675) else Color(0xFF00C6FF),
                            trackColor = Color.White.copy(alpha = 0.15f),
                            strokeCap = StrokeCap.Round
                        )
                    }
                }
            }
        }
    }
}

// Extensión para simplificar border modifier en Box
private fun Modifier.border(width: androidx.compose.ui.unit.Dp, color: Color, shape: androidx.compose.ui.graphics.Shape): Modifier =
    this.then(Modifier.background(Color.Transparent, shape)).then(
        androidx.compose.foundation.BorderStroke(width, color).let {
            Modifier.clip(shape)
        }
    )
