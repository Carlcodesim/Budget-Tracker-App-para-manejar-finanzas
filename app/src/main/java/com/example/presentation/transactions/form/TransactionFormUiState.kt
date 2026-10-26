package com.example.presentation.transactions.form

import com.example.domain.model.Category
import com.example.domain.model.Account

/**
 * Estado de la interfaz de usuario para el formulario de transacciones (Agregar/Editar).
 * Incluye campos para albergar la validación inline, control de presupuestos y escaneo OCR de recibos.
 */
data class TransactionFormUiState(
    val id: Long = 0L,
    val amount: String = "",
    val type: String = "EXPENSE", // "EXPENSE" o "INCOME"
    val selectedCategory: Category? = null,
    val selectedAccount: Account? = null,
    val description: String = "",
    val dateEpoch: Long = System.currentTimeMillis(),
    val tags: String = "",
    val categories: List<Category> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val isSaving: Boolean = false,
    val isLoadFinished: Boolean = false,
    val isSaveFinished: Boolean = false,

    // Escaneo de recibos con Gemini IA
    val isScanningReceipt: Boolean = false,
    val receiptScanSuccessMessage: String? = null,
    val scannedReceiptBitmap: android.graphics.Bitmap? = null,
    val scannedReceiptDetails: com.example.domain.model.ParsedReceipt? = null,

    // Seguimiento del presupuesto mensual de la categoría seleccionada
    val categoryCurrentSpentThisMonth: Double = 0.0,
    val categoryMonthlyLimit: Double? = null,
    val isOverBudgetWarning: Boolean = false,

    // Mensajes de error para validaciones inline
    val amountError: String? = null,
    val categoryError: String? = null,
    val accountError: String? = null,
    val errorMessage: String? = null
) {
    val isFormValid: Boolean
        get() = amount.isNotBlank() &&
                amount.toDoubleOrNull() != null &&
                (amount.toDoubleOrNull() ?: 0.0) > 0.0 &&
                selectedCategory != null &&
                selectedAccount != null
}
