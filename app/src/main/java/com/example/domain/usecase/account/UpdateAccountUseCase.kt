package com.example.domain.usecase.account

import com.example.domain.model.Account
import com.example.domain.repository.AccountRepository

/**
 * Caso de uso para actualizar los detalles de una cuenta (como el saldo inicial).
 */
class UpdateAccountUseCase(
    private val accountRepository: AccountRepository
) {
    suspend operator fun invoke(account: Account) {
        accountRepository.updateAccount(account)
    }
}
