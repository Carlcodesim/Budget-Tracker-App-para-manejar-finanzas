package com.example.presentation.store.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.Business
import com.example.presentation.common.util.CurrencyFormatter
import com.example.presentation.store.BusinessFinancialSummary
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.IncomeGreen

private val BusinessColorPresets = listOf(
    "#2563EB", // Azul Real
    "#10B981", // Verde Esmeralda
    "#8B5CF6", // Violeta Moderno
    "#F59E0B", // Ámbar Dorado
    "#EF4444", // Rojo Coral
    "#06B6D4", // Cian
    "#4F46E5", // Índigo
    "#EC4899"  // Rosa Fucsia
)

private val BusinessTypePresets = listOf(
    "Comercio / Ventas",
    "Servicios Profesionales",
    "Restaurante / Alimentos",
    "Tecnología / Digital",
    "Salud y Belleza",
    "Taller / Reparaciones",
    "Otro Emprendimiento"
)

/**
 * Diálogo para la creación o edición de un negocio comercial independiente.
 */
@Composable
fun CreateOrEditBusinessDialog(
    businessToEdit: Business? = null,
    onDismiss: () -> Unit,
    onSave: (name: String, type: String, description: String, colorHex: String, inventoryValue: Double) -> Unit,
    onDelete: ((Long) -> Unit)? = null
) {
    var name by remember { mutableStateOf(businessToEdit?.name ?: "") }
    var type by remember { mutableStateOf(businessToEdit?.type ?: BusinessTypePresets.first()) }
    var description by remember { mutableStateOf(businessToEdit?.description ?: "") }
    var selectedColorHex by remember { mutableStateOf(businessToEdit?.colorHex ?: BusinessColorPresets.first()) }
    var inventoryStr by remember { mutableStateOf(businessToEdit?.inventoryValue?.let { if (it > 0) it.toString() else "" } ?: "") }
    var nameError by remember { mutableStateOf(false) }

    val isEditing = businessToEdit != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (isEditing) Icons.Default.Edit else Icons.Default.AddBusiness,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (isEditing) "Editar Negocio" else "Nuevo Negocio",
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Configura los datos del negocio para registrar ventas, gastos e inventario de manera independiente.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Nombre
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        if (it.isNotBlank()) nameError = false
                    },
                    label = { Text("Nombre del Negocio *") },
                    placeholder = { Text("Ej. INSERC Store, Cafetería Luna...") },
                    isError = nameError,
                    supportingText = if (nameError) { { Text("El nombre es requerido") } } else null,
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("business_name_input")
                )

                // Rubro / Tipo de Negocio (Chips seleccionables)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Rubro o Sector Comercial",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(BusinessTypePresets) { preset ->
                            FilterChip(
                                selected = type == preset,
                                onClick = { type = preset },
                                label = { Text(preset, fontSize = 11.sp) }
                            )
                        }
                    }
                }

                // Descripción
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Descripción o notas (opcional)") },
                    placeholder = { Text("Ej. Venta de accesorios y servicios técnicos") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // Selector de Color
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Color Distintivo",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BusinessColorPresets.forEach { hex ->
                            val color = try {
                                Color(android.graphics.Color.parseColor(hex))
                            } catch (e: Exception) {
                                MaterialTheme.colorScheme.primary
                            }
                            val isSelected = selectedColorHex.equals(hex, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .clickable { selectedColorHex = hex }
                                    .then(
                                        if (isSelected) {
                                            Modifier.border(2.5.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                        } else {
                                            Modifier
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Valor inicial de Inventario
                OutlinedTextField(
                    value = inventoryStr,
                    onValueChange = { if (it.isEmpty() || it.all { ch -> ch.isDigit() || ch == '.' }) inventoryStr = it },
                    label = { Text("Valor de Inventario / Mercadería") },
                    placeholder = { Text("0.00") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank()) {
                        nameError = true
                        return@Button
                    }
                    val invVal = inventoryStr.toDoubleOrNull() ?: 0.0
                    onSave(name.trim(), type, description.trim(), selectedColorHex, invVal)
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("save_business_button")
            ) {
                Text(if (isEditing) "Actualizar" else "Crear Negocio")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Cancelar")
            }
        }
    )
}

/**
 * Diálogo para confirmar la eliminación de un negocio.
 */
@Composable
fun ConfirmDeleteBusinessDialog(
    business: Business,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.DeleteOutline,
                contentDescription = null,
                tint = ExpenseRed,
                modifier = Modifier.size(28.dp)
            )
        },
        title = {
            Text(
                text = "Eliminar Negocio",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = "¿Estás seguro de que deseas eliminar el negocio '${business.name}'? Sus operaciones seguirán registradas en tu historial pero no aparecerán agrupadas bajo este negocio."
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = ExpenseRed),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Eliminar Negocio", color = Color.White)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Cancelar")
            }
        }
    )
}

/**
 * Barra superior de conmutación de negocios: permite alternar entre vista consolidada o individual.
 */
@Composable
fun BusinessSwitcherBar(
    businesses: List<Business>,
    selectedBusinessId: Long?,
    onSelectBusiness: (Long?) -> Unit,
    onAddNewBusiness: () -> Unit,
    onEditSelectedBusiness: () -> Unit,
    onDeleteSelectedBusiness: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = modifier.fillMaxWidth().testTag("business_switcher_card")
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
                    Icon(
                        imageVector = Icons.Default.Storefront,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Mis Negocios (${businesses.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (selectedBusinessId != null) {
                        IconButton(
                            onClick = onEditSelectedBusiness,
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Editar negocio",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        IconButton(
                            onClick = onDeleteSelectedBusiness,
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Eliminar negocio",
                                tint = ExpenseRed,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onAddNewBusiness,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(30.dp).testTag("add_business_chip_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("+ Negocio", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // Chips horizontales de selección
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Chip Consolidado / Todos
                item {
                    FilterChip(
                        selected = selectedBusinessId == null,
                        onClick = { onSelectBusiness(null) },
                        label = { Text("Consolidado (Todos)", fontWeight = FontWeight.SemiBold, fontSize = 12.sp) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Dashboard,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }

                // Chips para cada negocio individual
                items(businesses, key = { it.id }) { biz ->
                    val isSelected = selectedBusinessId == biz.id
                    val bizColor = try {
                        Color(android.graphics.Color.parseColor(biz.colorHex))
                    } catch (e: Exception) {
                        MaterialTheme.colorScheme.primary
                    }
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectBusiness(biz.id) },
                        label = {
                            Text(
                                text = biz.name,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 12.sp
                            )
                        },
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(bizColor)
                            )
                        }
                    )
                }
            }
        }
    }
}

/**
 * Tarjeta de desglose comparativo de todos los negocios (visible en vista Consolidada).
 */
@Composable
fun BusinessConsolidatedComparisonCard(
    summaries: List<BusinessFinancialSummary>,
    onSelectBusiness: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Rendimiento por Negocio",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Ingresos y utilidades de cada emprendimiento",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                ) {
                    Text(
                        text = "${summaries.size} negocios",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            summaries.forEach { item ->
                val biz = item.business
                val bizColor = try {
                    Color(android.graphics.Color.parseColor(biz.colorHex))
                } catch (e: Exception) {
                    MaterialTheme.colorScheme.primary
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectBusiness(biz.id) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(bizColor.copy(alpha = 0.18f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Storefront,
                                    contentDescription = null,
                                    tint = bizColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = biz.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${biz.type} • ${item.salesCount} ventas",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = CurrencyFormatter.format(item.sales),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = IncomeGreen
                            )
                            Text(
                                text = "Utilidad: ${CurrencyFormatter.format(item.netProfit)}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = if (item.netProfit >= 0) MaterialTheme.colorScheme.onSurface else ExpenseRed,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
