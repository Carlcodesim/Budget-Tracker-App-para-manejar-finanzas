package com.example.data.repository

import com.example.data.local.dao.AccountDao
import com.example.data.local.dao.TransactionDao
import com.example.data.mapper.toDomain
import com.example.data.mapper.toEntity
import com.example.domain.model.Account
import com.example.domain.repository.AccountRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

import kotlinx.coroutines.flow.firstOrNull

import com.example.data.local.entity.signedBalanceDelta

/**
 * Implementación del repositorio de cuentas utilizando Room.
 * Calcula dinámicamente los balances en tiempo real basándose en los movimientos (transacciones).
 */
class AccountRepositoryImpl(
    private val accountDao: AccountDao,
    private val transactionDao: TransactionDao
) : AccountRepository {

    override fun getAllAccounts(): Flow<List<Account>> {
        return combine(
            accountDao.getAllAccounts(),
            transactionDao.getAllTransactions()
        ) { accountEntities, transactionEntities ->
            accountEntities.map { entity ->
                val associatedTxs = transactionEntities.filter { it.accountId == entity.id }
                val currentBalance = entity.initialBalance + associatedTxs.sumOf { it.signedBalanceDelta() }
                entity.toDomain(currentBalance = currentBalance)
            }
        }
    }

    override suspend fun getAccountById(id: Long): Account? {
        val entity = accountDao.getAccountById(id) ?: return null
        val associatedTxs = transactionDao.getTransactionsByAccountIdSync(id)
        val currentBalance = entity.initialBalance + associatedTxs.sumOf { it.signedBalanceDelta() }
        return entity.toDomain(currentBalance = currentBalance)
    }


    override suspend fun insertAccount(account: Account): Long {
        return accountDao.insertAccount(account.toEntity())
    }

    override suspend fun updateAccount(account: Account) {
        accountDao.updateAccount(account.toEntity())
    }

    override suspend fun deleteAccount(account: Account) {
        accountDao.deleteAccount(account.toEntity())
    }
}
