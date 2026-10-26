package com.example.presentation.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.Account
import com.example.domain.usecase.account.GetAccountsUseCase
import com.example.domain.usecase.account.UpdateAccountUseCase
import com.example.domain.usecase.account.AddAccountUseCase
import com.example.domain.usecase.account.DeleteAccountUseCase
import com.example.domain.usecase.transaction.GetTransactionsUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

import com.example.domain.model.Category
import com.example.domain.model.Transaction
import com.example.domain.model.TransactionType
import com.example.domain.usecase.category.GetCategoriesUseCase
import com.example.domain.usecase.transaction.AddTransactionUseCase
import java.util.Date

import com.example.domain.usecase.account.TransferFundsUseCase
import com.example.domain.usecase.account.TransferResult

/**
 * ViewModel para gestionar múltiples cuentas y ajustar sus saldos iniciales, además de realizar transferencias.
 */
class AccountsViewModel(
    private val getAccountsUseCase: GetAccountsUseCase,
    private val updateAccountUseCase: UpdateAccountUseCase,
    private val addAccountUseCase: AddAccountUseCase,
    private val deleteAccountUseCase: DeleteAccountUseCase,
    private val getTransactionsUseCase: GetTransactionsUseCase,
    private val transferFundsUseCase: TransferFundsUseCase,
    private val addTransactionUseCase: AddTransactionUseCase? = null,
    private val getCategoriesUseCase: GetCategoriesUseCase? = null
) : ViewModel() {

    private val _selectedAccountId = MutableStateFlow<Long?>(null)
    private val _uiState = MutableStateFlow(AccountsUiState())
    val uiState: StateFlow<AccountsUiState> = _uiState.asStateFlow()

    init {
        // Combinar reactivamente cuentas, movimientos y la cuenta seleccionada
        viewModelScope.launch {
            combine(
                getAccountsUseCase(),
                getTransactionsUseCase(),
                _selectedAccountId
            ) { accounts, transactions, selectedId ->
                val currentSelectedId = selectedId ?: accounts.firstOrNull()?.id ?: 1L
                val selectedAccount = accounts.find { it.id == currentSelectedId } ?: accounts.firstOrNull()
                val associatedTxs = transactions.filter { it.accountId == (selectedAccount?.id ?: -1L) }
                    .sortedByDescending { it.date }
                val totalWalletBalance = accounts.sumOf { it.currentBalance }

                _uiState.update { state ->
                    state.copy(
                        accounts = accounts,
                        selectedAccount = selectedAccount,
                        associatedTransactions = associatedTxs,
                        totalWalletBalance = totalWalletBalance,
                        isLoading = false
                    )
                }
            }.flowOn(Dispatchers.Default).collect {}
        }
    }

    fun selectAccount(account: Account) {
        _selectedAccountId.value = account.id
    }

    fun startEditingBalance() {
        val account = _uiState.value.selectedAccount ?: return
        _uiState.update {
            it.copy(
                isEditingInitialBalance = true,
                editBalanceAmount = account.initialBalance.toString(),
                editBalanceError = null
            )
        }
    }

    fun onBalanceAmountChanged(amount: String) {
        _uiState.update {
            it.copy(
                editBalanceAmount = amount,
                editBalanceError = if (amount.isNotBlank() && amount.toDoubleOrNull() == null) {
                    "Ingresa un valor numérico válido"
                } else null
            )
        }
    }

    fun dismissEditingBalance() {
        _uiState.update {
            it.copy(
                isEditingInitialBalance = false,
                editBalanceAmount = "",
                editBalanceError = null
            )
        }
    }

    fun saveInitialBalance() {
        val state = _uiState.value
        val amountDouble = state.editBalanceAmount.toDoubleOrNull()
        if (state.editBalanceAmount.isBlank() || amountDouble == null || amountDouble < 0) {
            _uiState.update { it.copy(editBalanceError = "El monto debe ser numérico y mayor o igual a 0") }
            return
        }

        val account = state.selectedAccount ?: return
        viewModelScope.launch {
            val updatedAccount = account.copy(initialBalance = amountDouble)
            updateAccountUseCase(updatedAccount)
            _uiState.update {
                it.copy(
                    isEditingInitialBalance = false
                )
            }
        }
    }

    fun startCreatingAccount() {
        _uiState.update {
            it.copy(
                isCreatingAccount = true,
                isEditingAccount = false,
                editingAccountId = null,
                newAccountName = "",
                newAccountType = "CASH",
                newAccountInitialBalance = "",
                newAccountColorHex = "#4CAF50",
                newAccountNameError = null,
                newAccountBalanceError = null
            )
        }
    }

    fun startEditingAccount(account: Account) {
        _uiState.update {
            it.copy(
                isCreatingAccount = true,
                isEditingAccount = true,
                editingAccountId = account.id,
                newAccountName = account.name,
                newAccountType = account.type,
                newAccountInitialBalance = account.initialBalance.toString(),
                newAccountColorHex = account.colorHex,
                newAccountNameError = null,
                newAccountBalanceError = null
            )
        }
    }

    fun onNewAccountNameChanged(name: String) {
        _uiState.update {
            it.copy(
                newAccountName = name,
                newAccountNameError = if (name.isBlank()) "El nombre no puede estar vacío" else null
            )
        }
    }

    fun onNewAccountTypeChanged(type: String) {
        _uiState.update { it.copy(newAccountType = type) }
    }

    fun onNewAccountInitialBalanceChanged(balance: String) {
        _uiState.update {
            it.copy(
                newAccountInitialBalance = balance,
                newAccountBalanceError = if (balance.isNotBlank() && balance.toDoubleOrNull() == null) {
                    "Ingresa un valor numérico válido"
                } else null
            )
        }
    }

    fun onNewAccountColorHexChanged(colorHex: String) {
        _uiState.update { it.copy(newAccountColorHex = colorHex) }
    }

    fun dismissCreatingAccount() {
        _uiState.update {
            it.copy(
                isCreatingAccount = false,
                isEditingAccount = false,
                editingAccountId = null,
                newAccountName = "",
                newAccountInitialBalance = "",
                newAccountNameError = null,
                newAccountBalanceError = null
            )
        }
    }

    fun deleteAccount(account: Account) {
        viewModelScope.launch {
            deleteAccountUseCase(account)
            dismissCreatingAccount()
            val remaining = _uiState.value.accounts.filter { it.id != account.id }
            if (remaining.isNotEmpty()) {
                _selectedAccountId.value = remaining.first().id
            }
        }
    }

    fun saveNewAccount() {
        val state = _uiState.value
        var hasError = false

        if (state.newAccountName.isBlank()) {
            _uiState.update { it.copy(newAccountNameError = "El nombre no puede estar vacío") }
            hasError = true
        }

        val balanceDouble = state.newAccountInitialBalance.toDoubleOrNull()
        if (state.newAccountInitialBalance.isBlank()) {
            _uiState.update { it.copy(newAccountBalanceError = "El saldo inicial no puede estar vacío") }
            hasError = true
        } else if (balanceDouble == null || balanceDouble < 0) {
            _uiState.update { it.copy(newAccountBalanceError = "Debe ser un número mayor o igual a 0") }
            hasError = true
        }

        if (hasError) return

        viewModelScope.launch {
            if (state.isEditingAccount && state.editingAccountId != null) {
                val updatedAccount = Account(
                    id = state.editingAccountId,
                    name = state.newAccountName.trim(),
                    type = state.newAccountType,
                    initialBalance = balanceDouble ?: 0.0,
                    colorHex = state.newAccountColorHex
                )
                updateAccountUseCase(updatedAccount)
                _uiState.update {
                    it.copy(
                        isCreatingAccount = false,
                        isEditingAccount = false,
                        editingAccountId = null
                    )
                }
            } else {
                val newAccount = Account(
                    name = state.newAccountName.trim(),
                    type = state.newAccountType,
                    initialBalance = balanceDouble ?: 0.0,
                    colorHex = state.newAccountColorHex
                )
                val newId = addAccountUseCase(newAccount)
                _selectedAccountId.value = newId
                _uiState.update {
                    it.copy(
                        isCreatingAccount = false
                    )
                }
            }
        }
    }

    // --- Funcionalidad de Transferencia entre Cuentas ---

    fun startTransfer(sourceAccount: Account? = null) {
        val accounts = _uiState.value.accounts
        if (accounts.size < 2) {
            _uiState.update {
                it.copy(
                    transferAccountError = "Necesitas al menos dos cuentas registradas para realizar una transferencia."
                )
            }
            return
        }

        val src = sourceAccount ?: _uiState.value.selectedAccount ?: accounts.first()
        val tgt = accounts.find { it.id != src.id } ?: accounts.last()

        _uiState.update {
            it.copy(
                isTransferringFunds = true,
                transferSourceAccount = src,
                transferTargetAccount = tgt,
                transferAmount = "",
                transferNote = "",
                transferAmountError = null,
                transferAccountError = null,
                transferSuccessMessage = null
            )
        }
    }

    fun onTransferSourceAccountChanged(account: Account) {
        val target = if (account.id == _uiState.value.transferTargetAccount?.id) {
            _uiState.value.accounts.find { it.id != account.id }
        } else {
            _uiState.value.transferTargetAccount
        }
        _uiState.update {
            it.copy(
                transferSourceAccount = account,
                transferTargetAccount = target,
                transferAccountError = null
            )
        }
    }

    fun onTransferTargetAccountChanged(account: Account) {
        _uiState.update {
            it.copy(
                transferTargetAccount = account,
                transferAccountError = null
            )
        }
    }

    fun onTransferAmountChanged(amount: String) {
        _uiState.update {
            it.copy(
                transferAmount = amount,
                transferAmountError = if (amount.isNotBlank() && amount.toDoubleOrNull() == null) {
                    "Monto numérico no válido"
                } else null
            )
        }
    }

    fun onTransferNoteChanged(note: String) {
        _uiState.update { it.copy(transferNote = note) }
    }

    fun dismissTransfer() {
        _uiState.update {
            it.copy(
                isTransferringFunds = false,
                transferAmount = "",
                transferNote = "",
                transferAmountError = null,
                transferAccountError = null
            )
        }
    }

    fun clearTransferSuccessMessage() {
        _uiState.update { it.copy(transferSuccessMessage = null) }
    }

    fun executeTransfer() {
        val state = _uiState.value
        val source = state.transferSourceAccount
        val target = state.transferTargetAccount
        val amount = state.transferAmount.toDoubleOrNull()

        if (source == null || target == null || source.id == target.id) {
            _uiState.update { it.copy(transferAccountError = "Selecciona dos cuentas distintas.") }
            return
        }

        if (amount == null || amount <= 0) {
            _uiState.update { it.copy(transferAmountError = "Ingresa un monto mayor a 0.") }
            return
        }

        viewModelScope.launch {
            val result = transferFundsUseCase(
                sourceAccountId = source.id,
                targetAccountId = target.id,
                amount = amount,
                note = state.transferNote.takeIf { it.isNotBlank() }
            )

            when (result) {
                is TransferResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isTransferringFunds = false,
                            transferAmount = "",
                            transferNote = "",
                            transferAmountError = null,
                            transferAccountError = null,
                            transferSuccessMessage = "¡Transferencia de \$${amount} de '${result.sourceAccountName}' a '${result.targetAccountName}' realizada con éxito!"
                        )
                    }
                }
                is TransferResult.Failure -> {
                    _uiState.update {
                        it.copy(transferAccountError = result.message)
                    }
                }
            }
        }
    }

}
