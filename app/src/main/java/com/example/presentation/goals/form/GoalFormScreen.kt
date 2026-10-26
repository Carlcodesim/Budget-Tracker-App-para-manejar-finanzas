package com.example.presentation.goals.form

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.presentation.common.components.AmountTextField
import com.example.presentation.common.components.LoadingIndicator
import com.example.presentation.common.util.DateFormatter
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalFormScreen(
    viewModel: GoalFormViewModel,
    goalId: Long?,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var isDatePickerOpen by remember { mutableStateOf(false) }

    LaunchedEffect(goalId) {
        if (goalId != null) {
            viewModel.loadGoal(goalId)
        } else {
            viewModel.loadGoal(0)
        }
    }

    LaunchedEffect(state.isSaveFinished) {
        if (state.isSaveFinished) {
            onNavigateBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (goalId == null) "Crear Meta de Ahorro" else "Editar Meta de Ahorro",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Regresar"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        if (!state.isLoadFinished && goalId != null) {
            LoadingIndicator()
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 640.dp)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                // Nombre de la Meta
                val nameError = state.nameError
                OutlinedTextField(
                    value = state.name,
                    onValueChange = { viewModel.onNameChanged(it) },
                    label = { Text("Nombre de la meta") },
                    placeholder = { Text("Ej. Vacaciones de verano") },
                    isError = nameError != null,
                    supportingText = {
                        if (nameError != null) {
                            Text(text = nameError)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Monto Objetivo
                AmountTextField(
                    value = state.targetAmount,
                    onValueChange = { viewModel.onTargetAmountChanged(it) },
                    label = "Monto Objetivo (Meta)",
                    isError = state.targetAmountError != null,
                    errorMessage = state.targetAmountError,
                    modifier = Modifier.fillMaxWidth()
                )

                // Categoría de la Meta
                var expandedCategory by remember { mutableStateOf(false) }
                val goalCategories = listOf("Viajes", "Fondo de Emergencia", "Hogar", "Vehículo", "Educación", "Tecnología", "Salud", "Regalos", "Otros")
                ExposedDropdownMenuBox(
                    expanded = expandedCategory,
                    onExpandedChange = { expandedCategory = !expandedCategory },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = state.category,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Categoría de la meta") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCategory) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                    )
                    ExposedDropdownMenu(
                        expanded = expandedCategory,
                        onDismissRequest = { expandedCategory = false }
                    ) {
                        goalCategories.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat) },
                                onClick = {
                                    viewModel.onCategoryChanged(cat)
                                    expandedCategory = false
                                }
                            )
                        }
                    }
                }

                // Prioridad
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Prioridad de la meta",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("Baja", "Media", "Alta").forEach { priority ->
                            val isSelected = state.priority == priority
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.onPriorityChanged(priority) },
                                label = { Text(priority) },
                                modifier = Modifier.weight(1f),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = when (priority) {
                                        "Alta" -> MaterialTheme.colorScheme.errorContainer
                                        "Media" -> MaterialTheme.colorScheme.primaryContainer
                                        else -> MaterialTheme.colorScheme.secondaryContainer
                                    },
                                    selectedLabelColor = when (priority) {
                                        "Alta" -> MaterialTheme.colorScheme.onErrorContainer
                                        "Media" -> MaterialTheme.colorScheme.onPrimaryContainer
                                        else -> MaterialTheme.colorScheme.onSecondaryContainer
                                    }
                                )
                            )
                        }
                    }
                }

                // Descripción
                OutlinedTextField(
                    value = state.description,
                    onValueChange = { viewModel.onDescriptionChanged(it) },
                    label = { Text("Descripción (opcional)") },
                    placeholder = { Text("Ej. Ahorro para mis vacaciones en la playa") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )

                // Fecha Límite (Opcional)
                OutlinedTextField(
                    value = state.deadlineEpoch?.let { DateFormatter.format(Date(it)) } ?: "Sin fecha límite",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Fecha límite (opcional)") },
                    trailingIcon = {
                        IconButton(onClick = { isDatePickerOpen = true }) {
                            Icon(imageVector = Icons.Default.CalendarToday, contentDescription = "Seleccionar fecha límite")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                // Botón Limpiar fecha si ya está elegida
                if (state.deadlineEpoch != null) {
                    TextButton(
                        onClick = { viewModel.onDeadlineChanged(null) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Quitar fecha límite")
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Botón Guardar
                Button(
                    onClick = { viewModel.validateAndSave() },
                    enabled = state.isFormValid && !state.isSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    if (state.isSaving) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Text(
                            text = if (goalId == null) "Crear Meta" else "Guardar Cambios",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

    // Modal DatePicker
    if (isDatePickerOpen) {
        val initialEpoch = state.deadlineEpoch ?: System.currentTimeMillis()
        val utcSelectedDate = initialEpoch + java.util.TimeZone.getDefault().getOffset(initialEpoch)
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = utcSelectedDate
        )
        DatePickerDialog(
            onDismissRequest = { isDatePickerOpen = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { utcMillis ->
                            val localMillis = utcMillis - java.util.TimeZone.getDefault().getOffset(utcMillis)
                            viewModel.onDeadlineChanged(localMillis)
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
