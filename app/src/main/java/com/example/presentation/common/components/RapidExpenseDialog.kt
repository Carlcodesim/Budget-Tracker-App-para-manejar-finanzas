package com.example.presentation.common.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.example.domain.model.Account
import com.example.domain.model.Category
import com.example.domain.model.Transaction
import com.example.domain.model.TransactionType
import com.example.presentation.common.util.DateFormatter
import com.example.presentation.common.util.IconHelper
import com.example.ui.theme.ExpenseRed
import java.util.Calendar
import java.util.Date

/**
 * Diálogo modal para la entrada rápida de nuevos gastos.
 * Permite ingresar el monto, seleccionar la categoría mediante chips e iconos,
 * ajustar la fecha rápidamente (Hoy, Ayer o Fecha personalizada) y la cuenta de origen.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RapidExpenseDialog(
    categories: List<Category>,
    accounts: List<Account>,
    onDismissRequest: () -> Unit,
    onSaveExpense: (amount: Double, category: Category, account: Account, dateEpoch: Long, note: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var amountText by remember { mutableStateOf("") }
    var amountError by remember { mutableStateOf<String?>(null) }

    var selectedCategory by remember { mutableStateOf(categories.firstOrNull()) }
    var categoryError by remember { mutableStateOf<String?>(null) }

    var selectedAccount by remember { mutableStateOf(accounts.firstOrNull() ?: Account(id = 1L, name = "Efectivo", type = "CASH", initialBalance = 0.0)) }

    var dateEpoch by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var selectedDateQuickOption by remember { mutableStateOf("TODAY") } // TODAY, YESTERDAY, CUSTOM

    var descriptionText by remember { mutableStateOf("") }
    var isDatePickerOpen by remember { mutableStateOf(false) }

    // Actualizar categoría o cuenta seleccionada cuando carguen las listas
    LaunchedEffect(categories) {
        if (selectedCategory == null && categories.isNotEmpty()) {
            selectedCategory = categories.first()
        }
    }
    LaunchedEffect(accounts) {
        if (accounts.isNotEmpty() && selectedAccount.id == 0L) {
            selectedAccount = accounts.first()
        }
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = modifier
            .fillMaxWidth(0.93f)
            .padding(vertical = 16.dp)
            .testTag("rapid_expense_dialog"),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = CircleShape,
                    color = ExpenseRed.copy(alpha = 0.15f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.FlashOn,
                            contentDescription = null,
                            tint = ExpenseRed,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Column {
                    Text(
                        text = "Registro Rápido de Gasto",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Añade un gasto en segundos",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Campo de Monto
                AmountTextField(
                    value = amountText,
                    onValueChange = { input ->
                        amountText = input
                        if (amountError != null) amountError = null
                    },
                    label = "Monto del Gasto",
                    isError = amountError != null,
                    errorMessage = amountError,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("rapid_expense_amount_input")
                )

                // Presets rápidos de monto ($5, $10, $20, $50)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf(5, 10, 20, 50, 100).forEach { preset ->
                        SuggestionChip(
                            onClick = {
                                val current = amountText.toDoubleOrNull() ?: 0.0
                                amountText = (current + preset).toString()
                                amountError = null
                            },
                            label = { Text("+$preset") },
                            modifier = Modifier.testTag("rapid_expense_preset_$preset")
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Selector de Categoría
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Categoría",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (categoryError != null) {
                            Text(
                                text = categoryError!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    if (categories.isEmpty()) {
                        Text(
                            text = "Cargando categorías...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        // Selector de Categorías en Cuadrícula de Chips
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("rapid_expense_category_chips")
                        ) {
                            categories.forEach { cat ->
                                val isSelected = selectedCategory?.id == cat.id
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        selectedCategory = cat
                                        categoryError = null
                                    },
                                    label = { Text(cat.name) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = IconHelper.getIcon(cat.iconKey),
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = ExpenseRed.copy(alpha = 0.2f),
                                        selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                                        selectedLeadingIconColor = ExpenseRed
                                    )
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Selector de Fecha Rápida
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Fecha del gasto",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedDateQuickOption == "TODAY",
                            onClick = {
                                selectedDateQuickOption = "TODAY"
                                dateEpoch = System.currentTimeMillis()
                            },
                            label = { Text("Hoy") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("rapid_expense_date_today")
                        )

                        FilterChip(
                            selected = selectedDateQuickOption == "YESTERDAY",
                            onClick = {
                                selectedDateQuickOption = "YESTERDAY"
                                val cal = Calendar.getInstance()
                                cal.add(Calendar.DAY_OF_YEAR, -1)
                                dateEpoch = cal.timeInMillis
                            },
                            label = { Text("Ayer") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("rapid_expense_date_yesterday")
                        )

                        FilterChip(
                            selected = selectedDateQuickOption == "CUSTOM",
                            onClick = {
                                isDatePickerOpen = true
                            },
                            label = {
                                Text(
                                    if (selectedDateQuickOption == "CUSTOM")
                                        DateFormatter.format(Date(dateEpoch))
                                    else "Elegir..."
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.CalendarToday,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            modifier = Modifier
                                .weight(1.3f)
                                .testTag("rapid_expense_date_custom")
                        )
                    }
                }

                // Selector de Cuenta de Origen
                if (accounts.size > 1) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Cuenta de Pago",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            accounts.forEach { acc ->
                                val isSelected = selectedAccount.id == acc.id
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedAccount = acc },
                                    label = { Text(acc.name) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.AccountBalanceWallet,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                )
                            }
                        }
                    }
                }

                // Nota u Observación (Opcional)
                OutlinedTextField(
                    value = descriptionText,
                    onValueChange = { descriptionText = it },
                    label = { Text("Nota / Descripción (opcional)") },
                    placeholder = { Text("Ej. Almuerzo rápido, Uber, café...") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("rapid_expense_note_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amountDouble = amountText.toDoubleOrNull()
                    var isValid = true

                    if (amountText.isBlank() || amountDouble == null || amountDouble <= 0) {
                        amountError = "Ingresa un monto válido mayor a 0"
                        isValid = false
                    }

                    if (selectedCategory == null) {
                        categoryError = "Selecciona una categoría"
                        isValid = false
                    }

                    if (isValid && amountDouble != null && selectedCategory != null) {
                        onSaveExpense(
                            amountDouble,
                            selectedCategory!!,
                            selectedAccount,
                            dateEpoch,
                            descriptionText.trim()
                        )
                    }
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ExpenseRed,
                    contentColor = Color.White
                ),
                modifier = Modifier.testTag("rapid_expense_save_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Guardar Gasto", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismissRequest,
                modifier = Modifier.testTag("rapid_expense_cancel_button")
            ) {
                Text("Cancelar")
            }
        }
    )

    // Modal DatePicker
    if (isDatePickerOpen) {
        val utcSelectedDate = dateEpoch + java.util.TimeZone.getDefault().getOffset(dateEpoch)
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = utcSelectedDate)

        DatePickerDialog(
            onDismissRequest = { isDatePickerOpen = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { utcMillis ->
                            val localMillis = utcMillis - java.util.TimeZone.getDefault().getOffset(utcMillis)
                            dateEpoch = localMillis
                            selectedDateQuickOption = "CUSTOM"
                        }
                        isDatePickerOpen = false
                    }
                ) {
                    Text("Aceptar")
                }
            },
            dismissButton = {
                TextButton(onClick = { isDatePickerOpen = false }) {
                    Text("Cancelar")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
