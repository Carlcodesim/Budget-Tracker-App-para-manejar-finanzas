package com.example.presentation.common.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.presentation.common.util.CurrencyFormatter
import com.example.presentation.common.util.GoogleWalletManager

/**
 * Botón con estilo estándar de Google Wallet.
 */
@Composable
fun AddToGoogleWalletButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String = "Configurar Pase de Google Wallet",
    enabled: Boolean = true
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = Color(0xFF1F1F1F),
        contentColor = Color.White,
        border = BorderStroke(1.dp, Color(0xFF3C4043)),
        shadowElevation = 2.dp,
        modifier = modifier
            .height(48.dp)
            .fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            GoogleWalletLogoIcon(modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = text,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Dibuja el icono de 4 colores característico de Google Wallet.
 */
@Composable
fun GoogleWalletLogoIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Contorno de la tarjeta
        val path = Path().apply {
            moveTo(w * 0.15f, h * 0.25f)
            lineTo(w * 0.85f, h * 0.25f)
            quadraticTo(w * 0.95f, h * 0.25f, w * 0.95f, h * 0.35f)
            lineTo(w * 0.95f, h * 0.75f)
            quadraticTo(w * 0.95f, h * 0.85f, w * 0.85f, h * 0.85f)
            lineTo(w * 0.15f, h * 0.85f)
            quadraticTo(w * 0.05f, h * 0.85f, w * 0.05f, h * 0.75f)
            lineTo(w * 0.05f, h * 0.35f)
            quadraticTo(w * 0.05f, h * 0.25f, w * 0.15f, h * 0.25f)
            close()
        }

        drawPath(
            path = path,
            color = Color(0xFF202124),
            style = Stroke(width = w * 0.08f)
        )

        val strokeWidth = w * 0.12f

        // Línea azul
        drawLine(
            color = Color(0xFF4285F4),
            start = androidx.compose.ui.geometry.Offset(w * 0.2f, h * 0.45f),
            end = androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.45f),
            strokeWidth = strokeWidth
        )

        // Línea roja
        drawLine(
            color = Color(0xFFEA4335),
            start = androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.45f),
            end = androidx.compose.ui.geometry.Offset(w * 0.8f, h * 0.45f),
            strokeWidth = strokeWidth
        )

        // Línea amarilla
        drawLine(
            color = Color(0xFFFBBC05),
            start = androidx.compose.ui.geometry.Offset(w * 0.2f, h * 0.65f),
            end = androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.65f),
            strokeWidth = strokeWidth
        )

        // Línea verde
        drawLine(
            color = Color(0xFF34A853),
            start = androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.65f),
            end = androidx.compose.ui.geometry.Offset(w * 0.8f, h * 0.65f),
            strokeWidth = strokeWidth
        )
    }
}

/**
 * Tarjeta de vista previa que ilustra el pase digital para Google Wallet.
 */
@Composable
fun GoogleWalletPassCard(
    title: String,
    subtitle: String,
    amountSpent: Double,
    amountLimit: Double,
    cardColor: Color = Color(0xFF0A2540),
    onAddToWalletClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progress = if (amountLimit > 0) (amountSpent / amountLimit).coerceIn(0.0, 1.0).toFloat() else 0f
    val remaining = (amountLimit - amountSpent).coerceAtLeast(0.0)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp)),
                color = cardColor,
                shadowElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = title,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.35f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                GoogleWalletLogoIcon(modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    "Google Wallet Pass",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Text(
                        text = subtitle,
                        color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.labelSmall
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Gastado", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                            Text(
                                CurrencyFormatter.format(amountSpent),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Disponible", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                            Text(
                                CurrencyFormatter.format(remaining),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }

                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape),
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.25f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            AddToGoogleWalletButton(
                onClick = onAddToWalletClick,
                text = "Ver Ficha y Configurar Pase"
            )
        }
    }
}

/**
 * Diálogo modal interactivo que muestra la ficha digital, el estado de la integración de Wallet
 * y permite ver el JSON oficial o compartir el resumen.
 */
@Composable
fun GoogleWalletPassModalDialog(
    title: String,
    subtitle: String,
    amountSpent: Double,
    amountLimit: Double,
    isGoal: Boolean = false,
    goalPercent: Int = 0,
    onDismissRequest: () -> Unit,
    onAddToWallet: () -> Unit,
    onSharePass: () -> Unit,
    onOpenWalletApp: () -> Unit
) {
    val context = LocalContext.current
    val progress = if (amountLimit > 0) (amountSpent / amountLimit).coerceIn(0.0, 1.0).toFloat() else 0f
    val remaining = (amountLimit - amountSpent).coerceAtLeast(0.0)
    var showJsonViewer by remember { mutableStateOf(false) }
    val currentIssuerId = remember { GoogleWalletManager.getIssuerId(context) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            Button(
                onClick = onOpenWalletApp,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F1F1F))
            ) {
                GoogleWalletLogoIcon(modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Abrir App Wallet", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismissRequest,
                shape = CircleShape
            ) {
                Text("Cerrar")
            }
        },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GoogleWalletLogoIcon(modifier = Modifier.size(24.dp))
                Text(
                    text = if (isGoal) "Ficha Digital de Meta" else "Ficha Digital de Presupuesto",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Tarjeta réplica
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isGoal) Color(0xFF1B5E20) else Color(0xFF0A2540),
                    shadowElevation = 3.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = title,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Surface(
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.35f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    GoogleWalletLogoIcon(modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Wallet Pass", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Text(
                            text = subtitle,
                            color = Color.White.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.labelSmall
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (isGoal) "Ahorrado: ${CurrencyFormatter.format(amountSpent)}" else "Gastado: ${CurrencyFormatter.format(amountSpent)}",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "Meta: ${CurrencyFormatter.format(amountLimit)}",
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 12.sp
                            )
                        }

                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(CircleShape),
                            color = Color.White,
                            trackColor = Color.White.copy(alpha = 0.3f)
                        )
                    }
                }

                // Estado de integración con Google Wallet
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (currentIssuerId != null) "Issuer ID: $currentIssuerId" else "Requisito de Google Wallet",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = if (currentIssuerId != null) {
                                "Tu Issuer ID está configurado. Google Wallet requiere que la llamada esté firmada con la clave privada de tu cuenta de servicio de Google Cloud."
                            } else {
                                "Para que una app guarde pases directamente dentro de Google Wallet sin intervención manual, Google exige registrar un Issuer ID en Google Pay & Wallet Console."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (showJsonViewer) {
                    val passJson = remember {
                        GoogleWalletManager.generateGenericPassJson(
                            currentIssuerId ?: "EMISOR_DEMO",
                            amountLimit,
                            amountSpent
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF1E1E1E),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 140.dp)
                    ) {
                        Text(
                            text = passJson,
                            color = Color(0xFF4AF626),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }

                // Acciones rápidas
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            if (isGoal) {
                                val goalDummy = com.example.domain.model.SavingsGoal(
                                    id = 1,
                                    name = title,
                                    targetAmount = amountLimit,
                                    currentAmount = amountSpent,
                                    deadline = null,
                                    createdAt = java.util.Date(),
                                    category = subtitle
                                )
                                GoogleWalletManager.copyGoalPassToClipboard(context, goalDummy)
                            } else {
                                GoogleWalletManager.copyBudgetPassToClipboard(context, amountLimit, amountSpent)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copiar", fontSize = 11.sp)
                    }

                    OutlinedButton(
                        onClick = onSharePass,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Compartir", fontSize = 11.sp)
                    }

                    OutlinedButton(
                        onClick = { showJsonViewer = !showJsonViewer },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (showJsonViewer) "Ocultar" else "Ver JSON", fontSize = 11.sp)
                    }
                }
            }
        }
    )
}
