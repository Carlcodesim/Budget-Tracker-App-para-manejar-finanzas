package com.example.domain.usecase.account

import com.example.domain.model.Account
import com.example.domain.repository.AccountRepository
import kotlinx.coroutines.flow.Flow

/**
 * Caso de uso para obtener todas las cuentas de forma reactiva, con su saldo actual actualizado.
 */
class GetAccountsUseCase(
    private val accountRepository: AccountRepository
) {
    operator fun invoke(): Flow<List<Account>> {
        return accountRepository.getAllAccounts()
    }
}
