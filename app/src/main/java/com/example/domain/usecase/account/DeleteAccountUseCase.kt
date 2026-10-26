package com.example.domain.usecase.account

import com.example.domain.model.Account
import com.example.domain.repository.AccountRepository

/**
 * Caso de uso para eliminar una cuenta financiera de la base de datos.
 */
class DeleteAccountUseCase(
    private val accountRepository: AccountRepository
) {
    suspend operator fun invoke(account: Account) {
        accountRepository.deleteAccount(account)
    }
}
