package com.example.presentation.navigation

/**
 * Representa las pantallas y rutas de navegación de la aplicación.
 */
sealed class Screen(val route: String) {
    object Dashboard : Screen("dashboard")
    object Transactions : Screen("transactions")
    object Goals : Screen("goals")
    object Settings : Screen("settings")
    object Calculator : Screen("calculator")
    object Analysis : Screen("analysis")
    object Reminders : Screen("reminders")
    object Accounts : Screen("accounts")
    object Assistant : Screen("assistant")
    object Store : Screen("store")
    object MonthlySummary : Screen("monthly_summary")
    object RecurringExpenses : Screen("recurring_expenses")
    object Categories : Screen("categories")

    object TransactionForm : Screen("transaction_form?transactionId={transactionId}&initialType={initialType}&autoScan={autoScan}") {
        fun createRoute(
            transactionId: Long? = null,
            initialType: String? = null,
            autoScan: Boolean = false
        ): String {
            val tId = transactionId?.toString() ?: ""
            val type = initialType ?: ""
            return "transaction_form?transactionId=$tId&initialType=$type&autoScan=$autoScan"
        }
    }

    object GoalForm : Screen("goal_form?goalId={goalId}") {
        fun createRoute(goalId: Long? = null): String {
            val gId = goalId?.toString() ?: ""
            return "goal_form?goalId=$gId"
        }
    }
}
