package com.example.domain.usecase.transaction

import androidx.paging.PagingData
import com.example.domain.model.Transaction
import com.example.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow

/**
 * Caso de uso para obtener la lista paginada de transacciones mediante Paging 3 (lazy loading).
 */
class GetPagedTransactionsUseCase(
    private val transactionRepository: TransactionRepository
) {
    operator fun invoke(): Flow<PagingData<Transaction>> {
        return transactionRepository.getPagedTransactions()
    }
}
