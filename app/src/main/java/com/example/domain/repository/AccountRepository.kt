package com.example.domain.repository

import com.example.domain.model.Account
import kotlinx.coroutines.flow.Flow

/**
 * Interfaz de repositorio para operaciones con cuentas financieras.
 */
interface AccountRepository {
    fun getAllAccounts(): Flow<List<Account>>
    suspend fun getAccountById(id: Long): Account?
    suspend fun insertAccount(account: Account): Long
    suspend fun updateAccount(account: Account)
    suspend fun deleteAccount(account: Account)
}
