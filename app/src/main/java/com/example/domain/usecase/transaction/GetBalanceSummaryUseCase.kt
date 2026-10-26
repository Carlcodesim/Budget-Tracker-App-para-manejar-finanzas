package com.example.domain.usecase.transaction

import com.example.domain.model.BalanceSummary
import com.example.domain.model.TransactionType
import com.example.domain.repository.AccountRepository
import com.example.domain.repository.TransactionRepository
import com.example.util.BudgetCycleManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Caso de uso para obtener el resumen de balances (ingresos, gastos, balance mensual) de forma reactiva,
 * respetando el ciclo de presupuesto configurado por el usuario (ej. día 30, 15 o mes calendario).
 */
class GetBalanceSummaryUseCase(
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository
) {
    operator fun invoke(): Flow<BalanceSummary> {
        return combine(
            transactionRepository.getAllTransactions(),
            accountRepository.getAllAccounts(),
            BudgetCycleManager.startDayFlow
        ) { transactions, accounts, startDay ->
            var totalIncome = 0.0
            var totalExpense = 0.0
            var monthlyIncome = 0.0
            var monthlyExpense = 0.0

            val currentCycle = BudgetCycleManager.getCurrentCycleRange(startDay)
            val startMillis = currentCycle.startDateMillis
            val endMillis = currentCycle.endDateMillis

            val totalNetWorth = if (accounts.isNotEmpty()) {
                accounts.sumOf { it.currentBalance }
            } else {
                totalIncome - totalExpense
            }

            for (transaction in transactions) {
                val amount = transaction.amount
                val isCurrentMonth = transaction.date.time in startMillis..endMillis

                if (transaction.isIncome) {
                    totalIncome += amount
                    if (isCurrentMonth) {
                        monthlyIncome += amount
                    }
                } else if (transaction.isExpense) {
                    totalExpense += amount
                    if (isCurrentMonth) {
                        monthlyExpense += amount
                    }
                }
                // Las transferencias entre cuentas no son ni gastos ni ingresos globales
            }

            BalanceSummary(
                currentBalance = totalNetWorth,
                totalIncome = totalIncome,
                totalExpense = totalExpense,
                monthlyIncome = monthlyIncome,
                monthlyExpense = monthlyExpense
            )

        }
    }
}
