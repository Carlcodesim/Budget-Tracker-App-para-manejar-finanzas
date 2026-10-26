package com.example.domain.usecase.transaction

import com.example.domain.model.Transaction
import com.example.domain.repository.TransactionRepository

/**
 * Caso de uso para agregar una nueva transacción.
 */
class AddTransactionUseCase(
    private val transactionRepository: TransactionRepository
) {
    suspend operator fun invoke(transaction: Transaction): Long {
        return transactionRepository.insertTransaction(transaction)
    }
}
