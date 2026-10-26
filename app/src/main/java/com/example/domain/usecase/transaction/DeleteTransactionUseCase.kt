package com.example.domain.usecase.transaction

import com.example.domain.model.Transaction
import com.example.domain.repository.TransactionRepository

/**
 * Caso de uso para eliminar transacciones de forma segura y consistente.
 * Si se elimina una transferencia vinculada a otra cuenta, limpia automáticamente
 * la contraparte sincronizada para mantener los balances perfectamente cuadrados.
 */
class DeleteTransactionUseCase(
    private val transactionRepository: TransactionRepository
) {
    suspend operator fun invoke(transaction: Transaction, deleteLinkedTransfer: Boolean = true) {
        val corrId = transaction.transferCorrelationId
        if (deleteLinkedTransfer && corrId != null) {
            transactionRepository.deleteTransactionsByCorrelationId(corrId)
        } else {
            transactionRepository.deleteTransaction(transaction)
        }
    }

    suspend operator fun invoke(transactions: List<Transaction>, deleteLinkedTransfers: Boolean = true) {
        if (transactions.isEmpty()) return

        if (!deleteLinkedTransfers) {
            transactionRepository.deleteTransactions(transactions)
            return
        }

        // Procesar transferencias con identificador de correlación
        val handledCorrelationIds = mutableSetOf<String>()
        val directDeletions = mutableListOf<Transaction>()

        for (tx in transactions) {
            val corrId = tx.transferCorrelationId
            if (corrId != null) {
                if (handledCorrelationIds.add(corrId)) {
                    transactionRepository.deleteTransactionsByCorrelationId(corrId)
                }
            } else {
                directDeletions.add(tx)
            }
        }

        if (directDeletions.isNotEmpty()) {
            transactionRepository.deleteTransactions(directDeletions)
        }
    }
}

