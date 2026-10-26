package com.example.domain.usecase.account

import com.example.domain.model.Account
import com.example.domain.repository.AccountRepository

/**
 * Caso de uso para crear una nueva cuenta financiera.
 */
class AddAccountUseCase(
    private val accountRepository: AccountRepository
) {
    suspend operator fun invoke(account: Account): Long {
        return accountRepository.insertAccount(account)
    }
}
