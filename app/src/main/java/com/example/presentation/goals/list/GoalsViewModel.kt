package com.example.presentation.goals.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.SavingsGoal
import com.example.domain.model.Transaction
import com.example.domain.model.TransactionType
import com.example.domain.usecase.goal.AddSavingsGoalUseCase
import com.example.domain.usecase.goal.ContributeToGoalUseCase
import com.example.domain.usecase.goal.DeleteSavingsGoalUseCase
import com.example.domain.usecase.goal.GetSavingsGoalsUseCase
import com.example.domain.usecase.account.GetAccountsUseCase
import com.example.domain.usecase.category.GetCategoriesUseCase
import com.example.domain.usecase.transaction.AddTransactionUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.util.Date

/**
 * ViewModel para la gestión de las metas de ahorro.
 * Ofrece flujos de datos en tiempo real y permite aportes monetarios directos de forma segura.
 */
class GoalsViewModel(
    private val getSavingsGoalsUseCase: GetSavingsGoalsUseCase,
    private val deleteSavingsGoalUseCase: DeleteSavingsGoalUseCase,
    private val contributeToGoalUseCase: ContributeToGoalUseCase,
    private val addSavingsGoalUseCase: AddSavingsGoalUseCase,
    private val getAccountsUseCase: GetAccountsUseCase,
    private val getCategoriesUseCase: GetCategoriesUseCase,
    private val addTransactionUseCase: AddTransactionUseCase
) : ViewModel() {

    private var lastDeletedGoal: SavingsGoal? = null

    val uiState: StateFlow<GoalsUiState> = combine(
        getSavingsGoalsUseCase(),
        getAccountsUseCase()
    ) { goals, accounts ->
        GoalsUiState.Success(goals, accounts) as GoalsUiState
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = GoalsUiState.Loading
    )

    fun contribute(goalId: Long, amount: Double, accountId: Long) {
        viewModelScope.launch {
            contributeToGoalUseCase(goalId, amount)
            
            // Log as transaction from the specific account
            val goal = getSavingsGoalsUseCase().firstOrNull()?.find { it.id == goalId }
            val categories = getCategoriesUseCase().firstOrNull()
            
            // Try to find an "Ahorro" or "Transferencia" category, or use the first one available
            val category = categories?.find { it.name.contains("Ahorro", ignoreCase = true) || it.name.contains("Meta", ignoreCase = true) }
                ?: categories?.firstOrNull()
                
            if (category != null && goal != null) {
                addTransactionUseCase(
                    Transaction(
                        amount = amount,
                        type = TransactionType.EXPENSE, // Expense from the perspective of the account
                        category = category,
                        description = "Aporte a meta: ${goal.name}",
                        date = Date(),
                        accountId = accountId,
                        tags = "ahorro, metas"
                    )
                )
            }
        }
    }

    fun deleteGoal(goal: SavingsGoal) {
        lastDeletedGoal = goal
        viewModelScope.launch {
            deleteSavingsGoalUseCase(goal)
        }
    }

    fun undoDelete() {
        val goal = lastDeletedGoal ?: return
        viewModelScope.launch {
            addSavingsGoalUseCase(goal)
            lastDeletedGoal = null
        }
    }
}
