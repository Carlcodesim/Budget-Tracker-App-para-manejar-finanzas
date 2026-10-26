package com.example.domain.usecase.account

import com.example.domain.model.Category
import com.example.domain.model.Transaction
import com.example.domain.model.TransactionType
import com.example.domain.repository.AccountRepository
import com.example.domain.repository.CategoryRepository
import com.example.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.firstOrNull
import java.util.Date
import java.util.UUID

/**
 * Caso de uso para transferir fondos entre dos cuentas de manera atómica y trazable.
 * Genera dos movimientos espejo sincronizados con un identificador de correlación único.
 */
class TransferFundsUseCase(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository
) {
    suspend operator fun invoke(
        sourceAccountId: Long,
        targetAccountId: Long,
        amount: Double,
        note: String? = null,
        date: Date = Date()
    ): TransferResult {
        if (sourceAccountId == targetAccountId) {
            return TransferResult.Failure.SameAccount
        }

        if (amount <= 0.0) {
            return TransferResult.Failure.InvalidAmount
        }

        val sourceAccount = accountRepository.getAccountById(sourceAccountId)
            ?: return TransferResult.Failure.AccountNotFound(sourceAccountId)

        val targetAccount = accountRepository.getAccountById(targetAccountId)
            ?: return TransferResult.Failure.AccountNotFound(targetAccountId)

        // Buscar categoría de transferencia o usar una por defecto
        val categories = categoryRepository.getAllCategories().firstOrNull() ?: emptyList()
        val transferCategory = categories.find {
            it.name.contains("Transfer", ignoreCase = true) || it.iconKey == "swap_horiz"
        } ?: categories.find {
            it.name.contains("Otros", ignoreCase = true)
        } ?: Category(id = 10, name = "Transferencia", iconKey = "swap_horiz")

        // Generar identificador único de correlación para la transferencia
        val correlationId = "tx_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}"
        val noteSuffix = if (!note.isNullOrBlank()) " - ${note.trim()}" else ""

        // 1. Movimiento saliente (cuenta origen)
        val outTx = Transaction(
            amount = amount,
            type = TransactionType.TRANSFER,
            category = transferCategory,
            description = "Transferencia a ${targetAccount.name}$noteSuffix",
            date = date,
            accountId = sourceAccount.id,
            tags = "TRANSFER,OUT,CORR:$correlationId"
        )
        val outgoingId = transactionRepository.insertTransaction(outTx)

        // 2. Movimiento entrante (cuenta destino)
        val inTx = Transaction(
            amount = amount,
            type = TransactionType.TRANSFER,
            category = transferCategory,
            description = "Transferencia desde ${sourceAccount.name}$noteSuffix",
            date = date,
            accountId = targetAccount.id,
            tags = "TRANSFER,IN,CORR:$correlationId"
        )
        val incomingId = transactionRepository.insertTransaction(inTx)

        return TransferResult.Success(
            outgoingTxId = outgoingId,
            incomingTxId = incomingId,
            sourceAccountName = sourceAccount.name,
            targetAccountName = targetAccount.name,
            amount = amount,
            correlationId = correlationId
        )
    }
}

/**
 * Resultado formal de una operación de transferencia.
 */
sealed interface TransferResult {
    data class Success(
        val outgoingTxId: Long,
        val incomingTxId: Long,
        val sourceAccountName: String,
        val targetAccountName: String,
        val amount: Double,
        val correlationId: String
    ) : TransferResult

    sealed class Failure(val message: String) : TransferResult {
        data object SameAccount : Failure("No puedes transferir fondos hacia la misma cuenta.")
        data object InvalidAmount : Failure("El monto a transferir debe ser numérico y mayor a cero.")
        data class AccountNotFound(val accountId: Long) : Failure("Cuenta no encontrada (ID: $accountId).")
    }
}
