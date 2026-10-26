package com.example.presentation.transactions.form

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.Category
import com.example.domain.model.ParsedReceipt
import com.example.domain.model.TransactionType
import com.example.presentation.common.components.AmountTextField
import com.example.presentation.common.components.LoadingIndicator
import com.example.presentation.common.util.CurrencyFormatter
import com.example.presentation.common.util.DateFormatter
import com.example.presentation.common.util.IconHelper
import com.example.presentation.common.util.ImageUtils
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.util.Date

/**
 * Pantalla para registrar o editar movimientos financieros.
 * Incorpora escaneo inteligente de recibos con Gemini AI (cámara en alta resolución y galería)
 * y visualización interactiva del presupuesto mensual por categoría con alertas de sobregiro.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionFormScreen(
    viewModel: TransactionFormViewModel,
    transactionId: Long?,
    initialType: TransactionType?,
    onNavigateBack: () -> Unit,
    autoScan: Boolean = false,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var isDatePickerOpen by remember { mutableStateOf(false) }
    var showFullReceiptDialog by remember { mutableStateOf(false) }

    // Uri temporal para captura de foto de alta resolución con FileProvider (persistente ante recreación de Activity)
    var currentPhotoUriString by rememberSaveable { mutableStateOf<String?>(null) }
    var hasAutoTriggeredScan by rememberSaveable { mutableStateOf(false) }

    // Launcher de Cámara (Full Resolution via FileProvider)
    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        val uriStr = currentPhotoUriString
        if (success && !uriStr.isNullOrBlank()) {
            val uri = Uri.parse(uriStr)
            val bitmap = ImageUtils.decodeUriToBitmap(context, uri)
            if (bitmap != null) {
                viewModel.scanReceipt(bitmap)
            } else {
                scope.launch {
                    snackbarHostState.showSnackbar("No se pudo decodificar la foto capturada.")
                }
            }
        }
    }

    // Launcher para solicitar permiso de cámara en tiempo de ejecución
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            try {
                val tempFile = ImageUtils.createTempImageFile(context)
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    tempFile
                )
                currentPhotoUriString = uri.toString()
                takePictureLauncher.launch(uri)
            } catch (e: Exception) {
                scope.launch {
                    snackbarHostState.showSnackbar("Error al inicializar la cámara: ${e.localizedMessage}")
                }
            }
        } else {
            scope.launch {
                snackbarHostState.showSnackbar("Permiso de cámara denegado. Puedes seleccionar el comprobante desde la galería.")
            }
        }
    }

    val launchCameraCapture: () -> Unit = {
        val permissionCheck = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        )
        if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
            try {
                val tempFile = ImageUtils.createTempImageFile(context)
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    tempFile
                )
                currentPhotoUriString = uri.toString()
                takePictureLauncher.launch(uri)
            } catch (e: Exception) {
                scope.launch {
                    snackbarHostState.showSnackbar("Error al abrir cámara: ${e.localizedMessage}")
                }
            }
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Auto-activación de la cámara si la pantalla fue abierta con intención directa de escanear recibo
    LaunchedEffect(autoScan) {
        if (autoScan && !hasAutoTriggeredScan && transactionId == null) {
            hasAutoTriggeredScan = true
            launchCameraCapture()
        }
    }

    // Launcher de Galería para seleccionar imagen existente
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val bitmap = ImageUtils.decodeUriToBitmap(context, uri)
                if (bitmap != null) {
                    viewModel.scanReceipt(bitmap)
                } else {
                    scope.launch {
                        snackbarHostState.showSnackbar("No se pudo cargar la imagen seleccionada.")
                    }
                }
            } catch (e: Exception) {
                scope.launch {
                    snackbarHostState.showSnackbar("Error al cargar la imagen: ${e.localizedMessage}")
                }
            }
        }
    }

    // Cargar datos en el inicio
    LaunchedEffect(transactionId, initialType) {
        if (transactionId != null) {
            viewModel.loadTransaction(transactionId)
        } else {
            viewModel.loadTransaction(0)
            initialType?.let { viewModel.onTypeChanged(it.name) }
        }
    }

    // Manejar mensajes de éxito o error
    LaunchedEffect(state.receiptScanSuccessMessage, state.errorMessage) {
        state.receiptScanSuccessMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessages()
        }
        state.errorMessage?.let { err ->
            snackbarHostState.showSnackbar(err)
            viewModel.clearMessages()
        }
    }

    // Regresar si se ha guardado correctamente
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
                        if (transactionId == null) "Agregar Movimiento" else "Editar Movimiento",
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { innerPadding ->
        if (!state.isLoadFinished && transactionId != null) {
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
                    // Tarjeta: Escaneo Inteligente de Recibos con Gemini AI (Solo para nuevos registros o gastos)
                    if (transactionId == null) {
                        if (state.scannedReceiptBitmap != null) {
                            ScannedReceiptPreviewCard(
                                bitmap = state.scannedReceiptBitmap!!,
                                parsedDetails = state.scannedReceiptDetails,
                                isScanning = state.isScanningReceipt,
                                onReScanCamera = launchCameraCapture,
                                onReScanGallery = { galleryLauncher.launch("image/*") },
                                onDeleteReceipt = { viewModel.clearScannedReceipt() },
                                onImageClick = { showFullReceiptDialog = true }
                            )
                        } else {
                            ReceiptScannerBanner(
                                isScanning = state.isScanningReceipt,
                                onTakePhotoClick = launchCameraCapture,
                                onPickGalleryClick = { galleryLauncher.launch("image/*") }
                            )
                        }
                    }

                    // Selector de Tipo: Ingreso o Gasto
                    Text(
                        text = "Tipo de movimiento",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val isIncome = state.type == "INCOME"
                        val isExpense = state.type == "EXPENSE"
                        val isTransfer = state.type == "TRANSFER"

                        Button(
                            onClick = { viewModel.onTypeChanged("EXPENSE") },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isExpense) ExpenseRed else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (isExpense) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("type_expense_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Gasto", fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { viewModel.onTypeChanged("INCOME") },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isIncome) IncomeGreen else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (isIncome) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("type_income_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Ingreso", fontWeight = FontWeight.Bold)
                        }

                        if (isTransfer) {
                            Button(
                                onClick = { viewModel.onTypeChanged("TRANSFER") },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF2563EB),
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Transfer.", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    // Monto con sugerencias rápidas
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AmountTextField(
                            value = state.amount,
                            onValueChange = { viewModel.onAmountChanged(it) },
                            label = "Monto",
                            isError = state.amountError != null,
                            errorMessage = state.amountError,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("amount_input")
                        )

                        // Chips de incremento rápido
                        val currentCurrency by CurrencyFormatter.currencyFlow.collectAsStateWithLifecycle()
                        val quickAddValues = remember(currentCurrency) {
                            CurrencyFormatter.getQuickAddSuggestions(currentCurrency)
                        }

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(quickAddValues) { addVal ->
                                val currentNum = state.amount.toDoubleOrNull() ?: 0.0
                                val label = "+${CurrencyFormatter.format(addVal)}"
                                SuggestionChip(
                                    onClick = {
                                        val newTotal = currentNum + addVal
                                        viewModel.onAmountChanged(
                                            if (newTotal % 1.0 == 0.0) newTotal.toLong().toString()
                                            else String.format(java.util.Locale.US, "%.2f", newTotal)
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    },
                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                        labelColor = MaterialTheme.colorScheme.primary
                                    ),
                                    border = SuggestionChipDefaults.suggestionChipBorder(
                                        enabled = true,
                                        borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                    )
                                )
                            }
                        }
                    }

                    // Selección de Categoría (Dropdown)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        CategoryDropdownSelector(
                            categories = state.categories,
                            selectedCategory = state.selectedCategory,
                            onCategorySelected = { viewModel.onCategoryChanged(it) },
                            isError = state.categoryError != null,
                            errorMessage = state.categoryError,
                            modifier = Modifier.testTag("category_dropdown")
                        )

                        // Visualización del Presupuesto Mensual de la Categoría y Alerta de Sobregiro
                        if (state.type == "EXPENSE" && state.categoryMonthlyLimit != null && state.categoryMonthlyLimit!! > 0) {
                            CategoryBudgetStatusCard(
                                limit = state.categoryMonthlyLimit!!,
                                currentSpent = state.categoryCurrentSpentThisMonth,
                                enteredAmount = state.amount.toDoubleOrNull() ?: 0.0,
                                isOverBudget = state.isOverBudgetWarning,
                                categoryName = state.selectedCategory?.name ?: ""
                            )
                        }
                    }

                    // Selección de Cuenta (Dropdown)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        AccountDropdownSelector(
                            accounts = state.accounts,
                            selectedAccount = state.selectedAccount,
                            onAccountSelected = { viewModel.onAccountChanged(it) },
                            isError = state.accountError != null,
                            errorMessage = state.accountError
                        )

                        // Hint de balance de la cuenta seleccionada
                        state.selectedAccount?.let { acc ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AccountBalance,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Saldo disponible en ${acc.name}: ${CurrencyFormatter.format(acc.currentBalance)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    // Fecha de Transacción
                    OutlinedTextField(
                        value = DateFormatter.format(Date(state.dateEpoch)),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Fecha") },
                        trailingIcon = {
                            IconButton(
                                onClick = { isDatePickerOpen = true },
                                modifier = Modifier.testTag("date_picker_button")
                            ) {
                                Icon(imageVector = Icons.Default.CalendarToday, contentDescription = "Seleccionar fecha")
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("date_input")
                    )

                    // Descripción / Nota
                    OutlinedTextField(
                        value = state.description,
                        onValueChange = { viewModel.onDescriptionChanged(it) },
                        label = { Text("Nota / Descripción (opcional)") },
                        placeholder = { Text("Ej. Supermercado La Sirena, almuerzo, factura...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("note_input"),
                        maxLines = 3
                    )

                    // Etiquetas personalizadas
                    OutlinedTextField(
                        value = state.tags,
                        onValueChange = { viewModel.onTagsChanged(it) },
                        label = { Text("Etiquetas (opcional)") },
                        placeholder = { Text("Ej. compras, mensualidad, trabajo") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("transaction_tags_input"),
                        maxLines = 2
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Botón Guardar
                    Button(
                        onClick = { viewModel.validateAndSave() },
                        enabled = state.isFormValid && !state.isSaving && !state.isScanningReceipt,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .testTag("save_transaction_button"),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        if (state.isSaving) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        } else {
                            Text(
                                text = if (transactionId == null) "Registrar Movimiento" else "Guardar Cambios",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }

    // Modal para visualizar el recibo capturado en grande
    if (showFullReceiptDialog && state.scannedReceiptBitmap != null) {
        Dialog(onDismissRequest = { showFullReceiptDialog = false }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Comprobante Capturado",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { showFullReceiptDialog = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Cerrar")
                        }
                    }

                    Image(
                        bitmap = state.scannedReceiptBitmap!!.asImageBitmap(),
                        contentDescription = "Recibo ampliado",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Fit
                    )

                    Button(
                        onClick = { showFullReceiptDialog = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Cerrar")
                    }
                }
            }
        }
    }

    // Modal DatePicker de Material 3
    if (isDatePickerOpen) {
        val utcSelectedDate = state.dateEpoch + java.util.TimeZone.getDefault().getOffset(state.dateEpoch)
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
                            viewModel.onDateChanged(localMillis)
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

/**
 * Banner superior para escanear recibos físicos o facturas con IA Gemini.
 */
@Composable
private fun ReceiptScannerBanner(
    isScanning: Boolean,
    onTakePhotoClick: () -> Unit,
    onPickGalleryClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .testTag("receipt_scanner_banner"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF0072C6), Color(0xFF8E24AA))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Escanear Recibo con IA (Gemini)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Text(
                        text = "Fotografía o selecciona tu comprobante para autocompletar monto, comercio y categoría",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                    )
                }
            }

            if (isScanning) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.5.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                text = "Analizando recibo con Gemini OCR...",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Extrayendo monto, comercio, fecha y categoría",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onTakePhotoClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("scan_receipt_camera_button")
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Cámara", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = onPickGalleryClick,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("scan_receipt_gallery_button")
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Galería", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Tarjeta de previsualización del recibo escaneado con los datos extraídos por Gemini OCR.
 */
@Composable
private fun ScannedReceiptPreviewCard(
    bitmap: Bitmap,
    parsedDetails: ParsedReceipt?,
    isScanning: Boolean,
    onReScanCamera: () -> Unit,
    onReScanGallery: () -> Unit,
    onDeleteReceipt: () -> Unit,
    onImageClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .testTag("scanned_receipt_preview_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
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
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = IncomeGreen,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = if (isScanning) "Procesando comprobante..." else "Comprobante Escaneado (Gemini OCR)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                IconButton(
                    onClick = onDeleteReceipt,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Quitar comprobante",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Miniatura de la imagen con botón para ampliar
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onImageClick() }
                ) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Recibo capturado",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.BottomEnd
                    ) {
                        Icon(
                            imageVector = Icons.Default.ZoomIn,
                            contentDescription = "Ampliar",
                            tint = Color.White,
                            modifier = Modifier
                                .padding(4.dp)
                                .size(16.dp)
                        )
                    }
                }

                // Datos detectados
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (isScanning) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        parsedDetails?.amount?.let { amt ->
                            Text(
                                text = "Monto: ${CurrencyFormatter.format(amt)}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        parsedDetails?.merchant?.let { merchant ->
                            Text(
                                text = "Comercio: $merchant",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        parsedDetails?.suggestedCategory?.let { cat ->
                            Text(
                                text = "Categoría: $cat",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        parsedDetails?.suggestedTags?.let { tags ->
                            Text(
                                text = "Etiquetas: $tags",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                }
            }

            // Opciones de acción
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onReScanCamera,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Re-capturar", style = MaterialTheme.typography.labelMedium)
                }

                OutlinedButton(
                    onClick = onReScanGallery,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Galería", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

/**
 * Tarjeta de Estado del Presupuesto por Categoría en el formulario.
 * Muestra el progreso mensual y alerta si el nuevo monto superará el límite fijado.
 */
@Composable
private fun CategoryBudgetStatusCard(
    limit: Double,
    currentSpent: Double,
    enteredAmount: Double,
    isOverBudget: Boolean,
    categoryName: String,
    modifier: Modifier = Modifier
) {
    val totalProjected = currentSpent + enteredAmount
    val percentage = if (limit > 0) (totalProjected / limit).toFloat() else 0f
    val progressColor = when {
        isOverBudget -> MaterialTheme.colorScheme.error
        percentage >= 0.8f -> Color(0xFFF57F17) // Orange warning
        else -> MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = modifier.fillMaxWidth().testTag("category_budget_status_card"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isOverBudget) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        border = BorderStroke(
            1.dp,
            if (isOverBudget) MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (isOverBudget) Icons.Default.Warning else Icons.Default.Info,
                        contentDescription = null,
                        tint = progressColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = if (isOverBudget) "¡Presupuesto Superado!" else "Presupuesto Mensual: $categoryName",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isOverBudget) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    )
                }

                Text(
                    text = "${CurrencyFormatter.format(totalProjected)} / ${CurrencyFormatter.format(limit)}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = progressColor
                )
            }

            LinearProgressIndicator(
                progress = { percentage.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = progressColor,
                trackColor = progressColor.copy(alpha = 0.15f)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Gastado este mes: ${CurrencyFormatter.format(currentSpent)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isOverBudget) {
                    Text(
                        text = "Exceso: ${CurrencyFormatter.format(totalProjected - limit)}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    Text(
                        text = "Restante: ${CurrencyFormatter.format((limit - totalProjected).coerceAtLeast(0.0))}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryDropdownSelector(
    categories: List<Category>,
    selectedCategory: Category?,
    onCategorySelected: (Category) -> Unit,
    isError: Boolean = false,
    errorMessage: String? = null,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = selectedCategory?.name ?: "Selecciona una categoría",
            onValueChange = {},
            readOnly = true,
            label = { Text("Categoría") },
            leadingIcon = selectedCategory?.let { cat ->
                {
                    Icon(
                        imageVector = IconHelper.getIcon(cat.iconKey),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            isError = isError,
            supportingText = {
                if (isError && errorMessage != null) {
                    Text(text = errorMessage)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            categories.forEach { category ->
                DropdownMenuItem(
                    leadingIcon = {
                        Icon(
                            imageVector = IconHelper.getIcon(category.iconKey),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    text = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(category.name)
                            category.maxMonthlyBudget?.let { budget ->
                                if (budget > 0) {
                                    Text(
                                        text = CurrencyFormatter.format(budget),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    },
                    onClick = {
                        onCategorySelected(category)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountDropdownSelector(
    accounts: List<com.example.domain.model.Account>,
    selectedAccount: com.example.domain.model.Account?,
    onAccountSelected: (com.example.domain.model.Account) -> Unit,
    isError: Boolean = false,
    errorMessage: String? = null,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = selectedAccount?.name ?: "Selecciona una cuenta",
            onValueChange = {},
            readOnly = true,
            label = { Text("Cuenta afectada") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            isError = isError,
            supportingText = {
                if (isError && errorMessage != null) {
                    Text(text = errorMessage)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                .testTag("transaction_form_account_dropdown")
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            accounts.forEach { account ->
                val typeLabel = when (account.type) {
                    "CASH" -> "Efectivo"
                    "BANK" -> "Banco"
                    "DEBIT" -> "Débito"
                    "CREDIT" -> "Crédito"
                    else -> "Wallet"
                }
                DropdownMenuItem(
                    text = { Text("${account.name} ($typeLabel)") },
                    onClick = {
                        onAccountSelected(account)
                        expanded = false
                    }
                )
            }
        }
    }
}

