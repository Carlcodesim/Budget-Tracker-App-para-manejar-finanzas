package com.example.presentation.recurring.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.domain.model.Account
import com.example.domain.model.Category
import com.example.domain.model.RecurringExpense
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RecurringExpenseFormSheet(
    isEditing: Boolean,
    initialExpense: RecurringExpense?,
    categories: List<Category>,
    accounts: List<Account>,
    onSave: (
        title: String,
        amount: Double,
        categoryId: Long,
        accountId: Long,
        dayOfMonth: Int,
        description: String?,
        autoCreate: Boolean
    ) -> Unit,
    onDismiss: () -> Unit
) {
    val expenseCategories = remember(categories) {
        categories.filter { it.type.equals("EXPENSE", ignoreCase = true) || it.type.equals("ALL", ignoreCase = true) }
    }

    var title by remember(initialExpense?.id) { mutableStateOf(initialExpense?.title ?: "") }
    var amountText by remember(initialExpense?.id) {
        mutableStateOf(initialExpense?.amount?.toString() ?: "")
    }
    var selectedCategoryId by remember(initialExpense?.id) {
        mutableLongStateOf(initialExpense?.category?.id ?: 0L)
    }
    var selectedAccountId by remember(initialExpense?.id) {
        mutableLongStateOf(initialExpense?.accountId ?: 0L)
    }
    var dayOfMonthSlider by remember(initialExpense?.id) {
        mutableFloatStateOf((initialExpense?.dayOfMonth ?: 1).toFloat())
    }
    var description by remember(initialExpense?.id) { mutableStateOf(initialExpense?.description ?: "") }
    var autoCreate by remember(initialExpense?.id) { mutableStateOf(initialExpense?.autoCreate ?: false) }
    var showAutoCreateConfirmation by remember { mutableStateOf(false) }

    var titleError by remember { mutableStateOf(false) }
    var amountError by remember { mutableStateOf(false) }
    var categoryError by remember { mutableStateOf(false) }
    var accountError by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val submitForm: (Boolean) -> Unit = { automaticCreationConfirmed ->
        val parsedAmount = amountText.toDoubleOrNull()
        var hasError = false

        if (title.isBlank()) {
            titleError = true
            hasError = true
        }
        if (parsedAmount == null || parsedAmount <= 0) {
            amountError = true
            hasError = true
        }
        if (expenseCategories.none { it.id == selectedCategoryId }) {
            categoryError = true
            hasError = true
        }
        if (accounts.none { it.id == selectedAccountId }) {
            accountError = true
            hasError = true
        }

        if (!hasError && parsedAmount != null) {
            val isEnablingAutomaticCreation = autoCreate && initialExpense?.autoCreate != true
            if (isEnablingAutomaticCreation && !automaticCreationConfirmed) {
                showAutoCreateConfirmation = true
            } else {
                onSave(
                    title.trim(),
                    parsedAmount,
                    selectedCategoryId,
                    selectedAccountId,
                    dayOfMonthSlider.roundToInt(),
                    description.takeIf { it.isNotBlank() },
                    autoCreate
                )
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (isEditing) "Editar Gasto Recurrente" else "Nuevo Gasto Recurrente",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Configura tu gasto periódico; tú decides cuándo registrar cada pago.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar")
                    }
                }

                // Campo: Título
                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        titleError = false
                    },
                    label = { Text("Nombre del gasto o suscripción") },
                    placeholder = { Text("Nombre del servicio o compromiso") },
                    leadingIcon = { Icon(Icons.Default.Title, contentDescription = null) },
                    isError = titleError,
                    supportingText = if (titleError) {
                        { Text("El nombre es requerido", color = MaterialTheme.colorScheme.error) }
                    } else null,
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_recurring_title")
                )

                // Campo: Monto
                OutlinedTextField(
                    value = amountText,
                    onValueChange = {
                        amountText = it
                        amountError = false
                    },
                    label = { Text("Monto mensual ($)") },
                    placeholder = { Text("0.00") },
                    leadingIcon = { Icon(Icons.Default.AttachMoney, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = amountError,
                    supportingText = if (amountError) {
                        { Text("Ingresa un monto válido mayor a 0", color = MaterialTheme.colorScheme.error) }
                    } else null,
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_recurring_amount")
                )

                // Selección de Día del Mes (1..31)
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
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
                                    imageVector = Icons.Default.CalendarMonth,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Día de vencimiento o pago",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "Día ${dayOfMonthSlider.roundToInt()}",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Slider(
                            value = dayOfMonthSlider,
                            onValueChange = { dayOfMonthSlider = it },
                            valueRange = 1f..31f,
                            steps = 29,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("slider_recurring_day")
                        )

                        Text(
                            text = "Fecha programada: día ${dayOfMonthSlider.roundToInt()} de cada mes.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Solo se permiten categorías de gasto en este formulario.
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Categoría del gasto",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (expenseCategories.isEmpty()) {
                        Text(
                            text = "No hay categorías de gastos disponibles. Crea una categoría de gasto antes de continuar.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            expenseCategories.forEach { category ->
                                FilterChip(
                                    selected = selectedCategoryId == category.id,
                                    onClick = {
                                        selectedCategoryId = category.id
                                        categoryError = false
                                    },
                                    label = { Text(category.name) },
                                    leadingIcon = if (selectedCategoryId == category.id) {
                                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                    } else null
                                )
                            }
                        }
                    }
                    if (categoryError) {
                        Text(
                            text = "Selecciona una categoría de gasto.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                // Selector de Cuenta de Débito
                if (accounts.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Cuenta de pago / débito",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            accounts.forEach { account ->
                                FilterChip(
                                    selected = selectedAccountId == account.id,
                                    onClick = { selectedAccountId = account.id },
                                    label = { Text(account.name) },
                                    leadingIcon = if (selectedAccountId == account.id) {
                                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                    } else {
                                        { Icon(Icons.Default.AccountBalance, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                    }
                                )
                            }
                        }
                    }
                    if (accountError) {
                        Text(
                            text = "Selecciona una cuenta válida.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                } else {
                    Text(
                        text = "No hay cuentas configuradas. Crea una cuenta antes de registrar este gasto.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                // Campo: Descripción
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Nota o descripción (opcional)") },
                    placeholder = { Text("Detalles que quieras recordar (opcional)") },
                    leadingIcon = { Icon(Icons.Default.Description, contentDescription = null) },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                // Toggle: Auto-Creación
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Crear transacción automáticamente",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "En la fecha indicada, se añadirá un gasto al historial sin verificar si el pago realmente se realizó.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Switch(
                            checked = autoCreate,
                            onCheckedChange = { autoCreate = it },
                            modifier = Modifier.testTag("switch_auto_create")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Botones de acción
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancelar")
                    }

                    Button(
                        onClick = { submitForm(false) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_save_recurring")
                    ) {
                        Text("Guardar")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (showAutoCreateConfirmation) {
        AlertDialog(
            onDismissRequest = { showAutoCreateConfirmation = false },
            title = { Text("Confirmar generación automática") },
            text = {
                Text(
                    "La aplicación registrará automáticamente un gasto de RD$${amountText} " +
                        "el día ${dayOfMonthSlider.roundToInt()} de cada mes, sin comprobar si el banco " +
                        "o proveedor realizó el cobro. Esto modificará tu historial y tus balances. " +
                        "Confirma solo si deseas que ese registro se cree automáticamente."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showAutoCreateConfirmation = false
                        submitForm(true)
                    }
                ) {
                    Text("Confirmar y guardar")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showAutoCreateConfirmation = false }) {
                    Text("Volver al formulario")
                }
            }
        )
    }

}