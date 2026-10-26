package com.example.presentation.accounts

import com.example.domain.model.Account
import com.example.domain.model.Transaction

/**
 * Estado de la UI para la pantalla de gestión de cuentas financieras.
 */
data class AccountsUiState(
    val accounts: List<Account> = emptyList(),
    val selectedAccount: Account? = null,
    val associatedTransactions: List<Transaction> = emptyList(),
    val totalWalletBalance: Double = 0.0,
    val isLoading: Boolean = true,
    
    // Estado para la edición del saldo inicial
    val isEditingInitialBalance: Boolean = false,
    val editBalanceAmount: String = "",
    val editBalanceError: String? = null,

    // Estado para la creación o edición de una cuenta
    val isCreatingAccount: Boolean = false,
    val isEditingAccount: Boolean = false,
    val editingAccountId: Long? = null,
    val newAccountName: String = "",
    val newAccountType: String = "CASH", // "CASH", "BANK", "DEBIT", "CREDIT"
    val newAccountInitialBalance: String = "",
    val newAccountColorHex: String = "#4CAF50", // Color por defecto (verde)
    val newAccountNameError: String? = null,
    val newAccountBalanceError: String? = null,

    // Estado para transferencia entre cuentas
    val isTransferringFunds: Boolean = false,
    val transferSourceAccount: Account? = null,
    val transferTargetAccount: Account? = null,
    val transferAmount: String = "",
    val transferNote: String = "",
    val transferAmountError: String? = null,
    val transferAccountError: String? = null,
    val transferSuccessMessage: String? = null
)

