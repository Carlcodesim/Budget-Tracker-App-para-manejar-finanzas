package com.example.presentation.accounts

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.domain.model.Account
import com.example.domain.model.Transaction
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.TransactionType
import com.example.presentation.common.util.CurrencyFormatter
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale

import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(
    viewModel: AccountsViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onNavigateBack)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val dateFormatter = remember { SimpleDateFormat("dd MMM yyyy", Locale.forLanguageTag("es-DO")) }
    var showDeleteConfirmation by remember { mutableStateOf<Account?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.transferSuccessMessage) {
        state.transferSuccessMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearTransferSuccessMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Mis Cuentas / Billetera",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("accounts_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Regresar"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.startTransfer() },
                        modifier = Modifier.testTag("transfer_topbar_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SwapHoriz,
                            contentDescription = "Transferir Fondos"
                        )
                    }
                    IconButton(
                        onClick = { viewModel.startCreatingAccount() },
                        modifier = Modifier.testTag("add_account_topbar_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Nueva Cuenta"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.startCreatingAccount() },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Nueva Cuenta") },
                modifier = Modifier.testTag("add_account_fab")
            )
        },
        modifier = modifier
    ) { innerPadding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.TopCenter
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 840.dp)
                        .testTag("accounts_list_container"),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                // Tarjeta Principal: Saldo Total de Billetera y Acciones
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp)
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
                                        imageVector = Icons.Default.Wallet,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = "Saldo Total Billetera",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)
                                ) {
                                    Text(
                                        text = "${state.accounts.size} Cuentas",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = CurrencyFormatter.format(state.totalWalletBalance),
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Button(
                                    onClick = { viewModel.startTransfer() },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("transfer_funds_button"),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SwapHoriz,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Transferir", fontWeight = FontWeight.Bold)
                                }

                                OutlinedButton(
                                    onClick = { viewModel.startCreatingAccount() },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("add_account_banner_button"),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.4f)
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Agregar", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                item {
                    Text(
                        text = "Selecciona una cuenta para ver sus movimientos o ajustar su saldo inicial:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }

                // Grid/Lista de Cuentas
                item {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        state.accounts.forEach { account ->
                            val isSelected = state.selectedAccount?.id == account.id
                            AccountCard(
                                account = account,
                                isSelected = isSelected,
                                onClick = { viewModel.selectAccount(account) },
                                onEditAccountClick = {
                                    viewModel.startEditingAccount(account)
                                }
                            )
                        }
                    }
                }

                // Historial de Movimientos de la Cuenta Seleccionada
                state.selectedAccount?.let { selected ->
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Movimientos de: ${selected.name}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            
                            // Botón para ajustar saldo inicial
                            TextButton(
                                onClick = { viewModel.startEditingBalance() },
                                modifier = Modifier.testTag("adjust_initial_balance_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Ajustar Saldo Inicial")
                            }
                        }
                    }

                    if (state.associatedTransactions.isEmpty()) {
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(40.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "No hay movimientos registrados en esta cuenta.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        textAlign = TextAlign.Center,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        items(state.associatedTransactions, key = { it.id }) { transaction ->
                            TransactionItem(
                                transaction = transaction,
                                dateFormatter = dateFormatter
                            )
                        }
                    }
                }
            }
        }
    }
    }

    // Dialog para editar el saldo inicial
    if (state.isEditingInitialBalance) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissEditingBalance() },
            title = {
                Text(
                    text = "Ajustar Saldo Inicial",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Modifica el saldo inicial para la cuenta '${state.selectedAccount?.name}'. Esto se usará como base para el cálculo automático de tu saldo actual.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    OutlinedTextField(
                        value = state.editBalanceAmount,
                        onValueChange = { viewModel.onBalanceAmountChanged(it) },
                        label = { Text("Saldo Inicial ($)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        isError = state.editBalanceError != null,
                        supportingText = state.editBalanceError?.let { { Text(it) } },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("initial_balance_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.saveInitialBalance() },
                    modifier = Modifier.testTag("save_initial_balance_button")
                ) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.dismissEditingBalance() },
                    modifier = Modifier.testTag("cancel_initial_balance_button")
                ) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Dialog para crear o editar cuenta
    if (state.isCreatingAccount) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissCreatingAccount() },
            title = {
                Text(
                    text = if (state.isEditingAccount) "Editar Cuenta Financiera" else "Nueva Cuenta Financiera",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (state.isEditingAccount) 
                            "Modifica los datos de la cuenta seleccionada. Ten en cuenta que los cambios se reflejarán en sus movimientos."
                            else "Registra una nueva cuenta para trackear tus movimientos.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Nombre de cuenta
                    OutlinedTextField(
                        value = state.newAccountName,
                        onValueChange = { viewModel.onNewAccountNameChanged(it) },
                        label = { Text("Nombre de la cuenta") },
                        singleLine = true,
                        isError = state.newAccountNameError != null,
                        supportingText = state.newAccountNameError?.let { { Text(it) } },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("new_account_name_input")
                    )

                    // Tipo de cuenta
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Tipo de Cuenta",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(vertical = 4.dp)
                        ) {
                            val types = listOf(
                                "CASH" to "Efectivo",
                                "BANK" to "Banco",
                                "DEBIT" to "Débito",
                                "CREDIT" to "Crédito"
                            )
                            types.forEach { (typeKey, label) ->
                                val isSelected = state.newAccountType == typeKey
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.surfaceVariant
                                        )
                                        .clickable { viewModel.onNewAccountTypeChanged(typeKey) }
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                        .testTag("account_type_chip_$typeKey"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }

                    // Saldo Inicial
                    OutlinedTextField(
                        value = state.newAccountInitialBalance,
                        onValueChange = { viewModel.onNewAccountInitialBalanceChanged(it) },
                        label = { Text("Saldo Inicial ($)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        isError = state.newAccountBalanceError != null,
                        supportingText = state.newAccountBalanceError?.let { { Text(it) } },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("new_account_balance_input")
                    )

                    // Color de cuenta
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Color de la Cuenta",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val presetColors = listOf(
                                "#4CAF50" to Color(0xFF4CAF50),
                                "#2196F3" to Color(0xFF2196F3),
                                "#9C27B0" to Color(0xFF9C27B0),
                                "#FF9800" to Color(0xFFFF9800),
                                "#009688" to Color(0xFF009688),
                                "#F44336" to Color(0xFFF44336),
                                "#3F51B5" to Color(0xFF3F51B5)
                            )
                            presetColors.forEach { (hex, colorVal) ->
                                val isSelectedColor = state.newAccountColorHex == hex
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(colorVal)
                                        .clickable { viewModel.onNewAccountColorHexChanged(hex) }
                                        .border(
                                            width = if (isSelectedColor) 3.dp else 0.dp,
                                            color = if (isSelectedColor) MaterialTheme.colorScheme.outline else Color.Transparent,
                                            shape = CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelectedColor) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Seleccionado",
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (state.isEditingAccount) {
                        TextButton(
                            onClick = {
                                showDeleteConfirmation = state.accounts.find { it.id == state.editingAccountId }
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.testTag("delete_account_dialog_button")
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Eliminar")
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(
                            onClick = { viewModel.dismissCreatingAccount() },
                            modifier = Modifier.testTag("cancel_new_account_button")
                        ) {
                            Text("Cancelar")
                        }
                        Button(
                            onClick = { viewModel.saveNewAccount() },
                            modifier = Modifier.testTag("save_new_account_button")
                        ) {
                            Text("Guardar")
                        }
                    }
                }
            }
        )
    }

    // Confirmación de eliminación
    if (showDeleteConfirmation != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = null },
            title = {
                Text(
                    text = "¿Eliminar Cuenta?",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Text(
                    text = "¿Estás seguro de que deseas eliminar la cuenta '${showDeleteConfirmation?.name}'? Todos sus movimientos asociados serán re-asignados o eliminados. Esta acción es irreversible.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmation?.let { viewModel.deleteAccount(it) }
                        showDeleteConfirmation = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_delete_account_button")
                ) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirmation = null },
                    modifier = Modifier.testTag("cancel_delete_account_button")
                ) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Modal de Transferencia entre Cuentas
    if (state.isTransferringFunds) {
        TransferFundsDialog(
            accounts = state.accounts,
            sourceAccount = state.transferSourceAccount,
            targetAccount = state.transferTargetAccount,
            amount = state.transferAmount,
            note = state.transferNote,
            amountError = state.transferAmountError,
            accountError = state.transferAccountError,
            onSourceAccountSelected = { viewModel.onTransferSourceAccountChanged(it) },
            onTargetAccountSelected = { viewModel.onTransferTargetAccountChanged(it) },
            onAmountChanged = { viewModel.onTransferAmountChanged(it) },
            onNoteChanged = { viewModel.onTransferNoteChanged(it) },
            onDismiss = { viewModel.dismissTransfer() },
            onConfirm = { viewModel.executeTransfer() }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferFundsDialog(
    accounts: List<Account>,
    sourceAccount: Account?,
    targetAccount: Account?,
    amount: String,
    note: String,
    amountError: String?,
    accountError: String?,
    onSourceAccountSelected: (Account) -> Unit,
    onTargetAccountSelected: (Account) -> Unit,
    onAmountChanged: (String) -> Unit,
    onNoteChanged: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    var sourceExpanded by remember { mutableStateOf(false) }
    var targetExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SwapHoriz,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Transferir entre Cuentas",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Transfiere saldo de una cuenta u origen a otra de forma automática.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (accountError != null) {
                    Text(
                        text = accountError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Cuenta Origen
                ExposedDropdownMenuBox(
                    expanded = sourceExpanded,
                    onExpandedChange = { sourceExpanded = !sourceExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = sourceAccount?.name ?: "Selecciona cuenta origen",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Cuenta Origen (Sale dinero)") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sourceExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                            .testTag("transfer_source_account_dropdown")
                    )

                    ExposedDropdownMenu(
                        expanded = sourceExpanded,
                        onDismissRequest = { sourceExpanded = false }
                    ) {
                        accounts.forEach { acc ->
                            DropdownMenuItem(
                                text = { Text("${acc.name} (${CurrencyFormatter.format(acc.currentBalance)})") },
                                onClick = {
                                    onSourceAccountSelected(acc)
                                    sourceExpanded = false
                                }
                            )
                        }
                    }
                }

                // Cuenta Destino
                ExposedDropdownMenuBox(
                    expanded = targetExpanded,
                    onExpandedChange = { targetExpanded = !targetExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = targetAccount?.name ?: "Selecciona cuenta destino",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Cuenta Destino (Entra dinero)") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = targetExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                            .testTag("transfer_target_account_dropdown")
                    )

                    ExposedDropdownMenu(
                        expanded = targetExpanded,
                        onDismissRequest = { targetExpanded = false }
                    ) {
                        accounts.filter { it.id != sourceAccount?.id }.forEach { acc ->
                            DropdownMenuItem(
                                text = { Text("${acc.name} (${CurrencyFormatter.format(acc.currentBalance)})") },
                                onClick = {
                                    onTargetAccountSelected(acc)
                                    targetExpanded = false
                                }
                            )
                        }
                    }
                }

                // Monto a transferir
                OutlinedTextField(
                    value = amount,
                    onValueChange = onAmountChanged,
                    label = { Text("Monto a transferir ($)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    isError = amountError != null,
                    supportingText = amountError?.let { { Text(it) } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("transfer_amount_input")
                )

                // Nota / Concepto
                OutlinedTextField(
                    value = note,
                    onValueChange = onNoteChanged,
                    label = { Text("Nota o concepto (Opcional)") },
                    placeholder = { Text("Ej. Pago de tarjeta, retiro de cajero") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("transfer_note_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                modifier = Modifier.testTag("confirm_transfer_button")
            ) {
                Text("Transferir")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_transfer_button")
            ) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
fun AccountCard(
    account: Account,
    isSelected: Boolean,
    onClick: () -> Unit,
    onEditAccountClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accountColor = try {
        Color(android.graphics.Color.parseColor(account.colorHex))
    } catch (e: Exception) {
        MaterialTheme.colorScheme.primary
    }

    val icon = when (account.type) {
        "CASH" -> Icons.Default.Payments
        "BANK" -> Icons.Default.AccountBalance
        "DEBIT" -> Icons.Default.CreditCard
        "CREDIT" -> Icons.Default.CreditCard
        else -> Icons.Default.Wallet
    }

    val typeLabel = when (account.type) {
        "CASH" -> "Efectivo"
        "BANK" -> "Cuenta Bancaria"
        "DEBIT" -> "Tarjeta de Débito"
        "CREDIT" -> "Tarjeta de Crédito"
        else -> "Wallet"
    }

    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .testTag("account_card_${account.id}"),
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) accountColor.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) accountColor else MaterialTheme.colorScheme.outlineVariant
        ),
        tonalElevation = if (isSelected) 4.dp else 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Ícono decorado con el color de la cuenta
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(accountColor.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accountColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Detalles de la cuenta
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = account.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = typeLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column {
                        Text(
                            text = "Saldo Actual",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = CurrencyFormatter.format(account.currentBalance),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (account.currentBalance >= 0) accountColor else MaterialTheme.colorScheme.error
                        )
                    }
                    Column {
                        Text(
                            text = "Saldo Inicial",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = CurrencyFormatter.format(account.initialBalance),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Botón de edición completa de cuenta
            IconButton(
                onClick = onEditAccountClick,
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .testTag("edit_account_icon_${account.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Editar Cuenta",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun TransactionItem(
    transaction: Transaction,
    dateFormatter: SimpleDateFormat,
    modifier: Modifier = Modifier
) {
    val isTransfer = transaction.type == TransactionType.TRANSFER
    val isIncomingTransfer = transaction.tags?.contains("IN", ignoreCase = true) == true ||
        transaction.description?.contains("desde", ignoreCase = true) == true
    val isExpense = transaction.type == TransactionType.EXPENSE

    val color = when {
        isTransfer -> Color(0xFF2563EB)
        isExpense -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }

    val sign = when {
        isTransfer -> if (isIncomingTransfer) "+ " else "- "
        isExpense -> "-"
        else -> "+"
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Icono de Categoría
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                if (isTransfer) {
                    Icon(
                        imageVector = Icons.Default.SwapHoriz,
                        contentDescription = "Transferencia",
                        tint = color,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    // Seleccionar icono basado en la categoría
                    val categoryIcon = when (transaction.category.iconKey) {
                        "attach_money" -> Icons.Default.AttachMoney
                        "restaurant" -> Icons.Default.Restaurant
                        "directions_car" -> Icons.Default.DirectionsCar
                        "sports_esports" -> Icons.Default.SportsEsports
                        "medical_services" -> Icons.Default.MedicalServices
                        "school" -> Icons.Default.School
                        "receipt" -> Icons.Default.Receipt
                        "swap_horiz" -> Icons.Default.SwapHoriz
                        else -> Icons.Default.Category
                    }
                    Icon(
                        imageVector = categoryIcon,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = transaction.description ?: transaction.category.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                val subtitle = if (isTransfer) {
                    if (isIncomingTransfer) "Transferencia Entrante" else "Transferencia Saliente"
                } else {
                    transaction.category.name
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = dateFormatter.format(transaction.date),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = "$sign${CurrencyFormatter.format(transaction.amount)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}
