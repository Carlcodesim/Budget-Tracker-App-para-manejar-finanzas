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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import com.example.domain.model.Account
import com.example.domain.model.Category
import com.example.domain.model.RecurringExpense
import com.example.domain.model.RecurrenceFrequency

private fun startOfTodayMillis(): Long =
    Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

private fun toDatePickerUtcMillis(localMillis: Long): Long {
    val local = Calendar.getInstance().apply { timeInMillis = localMillis }
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
    }.timeInMillis
}

private fun fromDatePickerUtcMillis(utcMillis: Long): Long {
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMillis }
    return Calendar.getInstance().apply {
        clear()
        set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
    }.timeInMillis
}

private fun formatRecurringDate(value: Long): String =
    SimpleDateFormat("EEEE, d 'de' MMMM yyyy", Locale("es", "DO")).format(Date(value))

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
        autoCreate: Boolean,
        frequency: RecurrenceFrequency,
        nextDueDate: Long
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
    var selectedFrequency by remember(initialExpense?.id) {
        mutableStateOf(initialExpense?.frequency ?: RecurrenceFrequency.MONTHLY)
    }
    var nextDueDateMillis by remember(initialExpense?.id) {
        mutableLongStateOf(initialExpense?.resolvedNextDueDate() ?: startOfTodayMillis())
    }
    var showDatePicker by remember { mutableStateOf(false) }
    var description by remember(initialExpense?.id) { mutableStateOf(initialExpense?.description ?: "") }
    var autoCreate by remember(initialExpense?.id) { mutableStateOf(initialExpense?.autoCreate ?: false) }
    var showAutoCreateConfirmation by remember { mutableStateOf(false) }

    var titleError by remember { mutableStateOf(false) }
    var amountError by remember { mutableStateOf(false) }
    var categoryError by remember { mutableStateOf(false) }
    var accountError by remember { mutableStateOf(false) }
    var dueDateError by remember { mutableStateOf(false) }

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
        if (nextDueDateMillis < startOfTodayMillis()) {
            dueDateError = true
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
                    Calendar.getInstance().apply { timeInMillis = nextDueDateMillis }.get(Calendar.DAY_OF_MONTH),
                    description.takeIf { it.isNotBlank() },
                    autoCreate,
                    selectedFrequency,
                    nextDueDateMillis
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
                    label = { Text("Monto por periodo (RD$)") },
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

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Frecuencia del gasto",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        RecurrenceFrequency.values().forEach { option ->
                            FilterChip(
                                selected = selectedFrequency == option,
                                onClick = { selectedFrequency = option },
                                label = { Text(option.label) }
                            )
                        }
                    }
                    Text(
                        text = when (selectedFrequency) {
                            RecurrenceFrequency.WEEKLY -> "Se repetirá cada 7 días."
                            RecurrenceFrequency.BIWEEKLY -> "Se repetirá cada 15 días."
                            RecurrenceFrequency.MONTHLY -> "Se repetirá cada mes en la fecha seleccionada."
                            RecurrenceFrequency.YEARLY -> "Se repetirá cada año en la fecha seleccionada."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = { showDatePicker = true },
                        modifier = Modifier.fillMaxWidth().testTag("btn_recurring_due_date")
                    ) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Primer vencimiento: " + formatRecurringDate(nextDueDateMillis))
                    }
                    if (dueDateError) {
                        Text(
                            text = "El primer vencimiento no puede estar en el pasado.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
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
                                    onClick = {
                                        selectedAccountId = account.id
                                        accountError = false
                                    },
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
                    "La aplicación registrará automáticamente RD$" + amountText +
                        " por periodo con frecuencia " + selectedFrequency.label +
                        ", a partir del " + formatRecurringDate(nextDueDateMillis) +
                        ", sin comprobar si el banco o proveedor realizó el cobro. Esto modificará tu historial y tus balances. " +
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