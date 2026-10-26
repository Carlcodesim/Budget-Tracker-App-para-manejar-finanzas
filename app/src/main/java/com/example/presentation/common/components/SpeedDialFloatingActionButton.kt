package com.example.presentation.common.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.IncomeGreen

/**
 * Floating Action Button expandible (Speed Dial) con animación Material 3 para acceso
 * ultrarrápido a gastos frecuentes de 1 solo toque, modal rápido express, nuevo ingreso y asistente AI.
 */
@Composable
fun SpeedDialFloatingActionButton(
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onDismissRequest: () -> Unit,
    onFrequentExpensesClick: () -> Unit,
    onRapidExpenseClick: () -> Unit,
    onNewExpenseClick: () -> Unit,
    onNewIncomeClick: () -> Unit,
    onAssistantClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Animación de rotación del FAB principal (0° -> 45° = cruz de cierre)
    val rotationAngle by animateFloatAsState(
        targetValue = if (isExpanded) 45f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "fab_rotation"
    )

    val fabContainerColor by animateColorAsState(
        targetValue = if (isExpanded) MaterialTheme.colorScheme.errorContainer else ExpenseRed,
        animationSpec = tween(durationMillis = 250),
        label = "fab_container_color"
    )

    val fabContentColor by animateColorAsState(
        targetValue = if (isExpanded) MaterialTheme.colorScheme.onErrorContainer else Color.White,
        animationSpec = tween(durationMillis = 250),
        label = "fab_content_color"
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.BottomEnd
    ) {
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            // Sub-botones expandibles con animación escalonada
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn(animationSpec = tween(180)) + slideInVertically(
                    initialOffsetY = { it / 2 },
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                ) + expandVertically(expandFrom = Alignment.Bottom),
                exit = fadeOut(animationSpec = tween(140)) + slideOutVertically(
                    targetOffsetY = { it / 2 },
                    animationSpec = tween(140)
                ) + shrinkVertically(shrinkTowards = Alignment.Bottom)
            ) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 1. Asistente Inteligente / Escanear
                    SpeedDialMiniItem(
                        label = "Asistente AI / Escanear",
                        icon = Icons.Default.AutoAwesome,
                        containerColor = Color(0xFF7C4DFF),
                        contentColor = Color.White,
                        testTag = "speed_dial_assistant",
                        onClick = {
                            onDismissRequest()
                            onAssistantClick()
                        }
                    )

                    // 2. Nuevo Ingreso
                    SpeedDialMiniItem(
                        label = "Nuevo Ingreso",
                        icon = Icons.Default.AddCircle,
                        containerColor = IncomeGreen,
                        contentColor = Color.White,
                        testTag = "speed_dial_income",
                        onClick = {
                            onDismissRequest()
                            onNewIncomeClick()
                        }
                    )

                    // 3. Nuevo Gasto Detallado
                    SpeedDialMiniItem(
                        label = "Gasto Detallado",
                        icon = Icons.AutoMirrored.Filled.ReceiptLong,
                        containerColor = ExpenseRed,
                        contentColor = Color.White,
                        testTag = "speed_dial_detailed_expense",
                        onClick = {
                            onDismissRequest()
                            onNewExpenseClick()
                        }
                    )

                    // 4. Modal de Gasto Rápido Express
                    SpeedDialMiniItem(
                        label = "Gasto Rápido Express",
                        icon = Icons.Default.EditCalendar,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        testTag = "speed_dial_rapid_dialog",
                        onClick = {
                            onDismissRequest()
                            onRapidExpenseClick()
                        }
                    )

                    // 5. Destacado: Gastos Frecuentes (1 Toque)
                    SpeedDialHeroItem(
                        label = "Gastos Frecuentes (1 Toque)",
                        badge = "1 TOQUE",
                        icon = Icons.Default.FlashOn,
                        containerColor = Color(0xFFFF9F1C), // Vivid Orange
                        contentColor = Color.White,
                        testTag = "speed_dial_frequent_expenses",
                        onClick = {
                            onDismissRequest()
                            onFrequentExpensesClick()
                        }
                    )
                }
            }

            // FAB Principal
            FloatingActionButton(
                onClick = onToggleExpand,
                shape = CircleShape,
                containerColor = fabContainerColor,
                contentColor = fabContentColor,
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 6.dp,
                    pressedElevation = 10.dp
                ),
                modifier = Modifier
                    .size(60.dp)
                    .testTag("speed_dial_main_fab")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = if (isExpanded) "Cerrar menú rápido" else "Abrir acciones rápidas",
                    modifier = Modifier
                        .size(28.dp)
                        .rotate(rotationAngle)
                )
            }
        }
    }
}

/**
 * Sub-elemento destacado del Speed Dial con etiqueta distintiva de "1 Toque".
 */
@Composable
private fun SpeedDialHeroItem(
    label: String,
    badge: String,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .padding(end = 4.dp)
            .clickable(onClick = onClick)
    ) {
        // Etiqueta destacada
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp),
            shadowElevation = 4.dp,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                containerColor.copy(alpha = 0.5f)
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = containerColor.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = containerColor,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                        fontSize = 9.sp
                    )
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Mini FAB Destacado
        SmallFloatingActionButton(
            onClick = onClick,
            shape = CircleShape,
            containerColor = containerColor,
            contentColor = contentColor,
            elevation = FloatingActionButtonDefaults.elevation(4.dp),
            modifier = Modifier
                .size(48.dp)
                .testTag(testTag)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

/**
 * Sub-elemento estándar del Speed Dial con etiqueta y mini botón flotante.
 */
@Composable
private fun SpeedDialMiniItem(
    label: String,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .padding(end = 6.dp)
            .clickable(onClick = onClick)
    ) {
        // Etiqueta flotante
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
            shadowElevation = 3.dp
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
            )
        }

        // Mini Botón
        SmallFloatingActionButton(
            onClick = onClick,
            shape = CircleShape,
            containerColor = containerColor,
            contentColor = contentColor,
            elevation = FloatingActionButtonDefaults.elevation(3.dp),
            modifier = Modifier
                .size(44.dp)
                .testTag(testTag)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Capa de atenuación de fondo (Scrim) que se activa cuando el Speed Dial está desplegado.
 */
@Composable
fun SpeedDialBackdropScrim(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(200)),
        exit = fadeOut(animationSpec = tween(200)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
        )
    }
}
