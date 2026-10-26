package com.example.presentation.assistant

enum class AnomalySeverity {
    HIGH,
    MEDIUM,
    INFO
}

data class FinancialAnomaly(
    val id: String,
    val title: String,
    val description: String,
    val severity: AnomalySeverity,
    val recommendation: String,
    val category: String? = null,
    val amountDiff: Double? = null
)

data class GoalMilestone(
    val percentage: Int, // 25, 50, 75, 100
    val targetAmount: Double,
    val isReached: Boolean,
    val remainingToMilestone: Double
)

enum class AllocationStrategyMode(val label: String, val description: String) {
    BALANCED("Equilibrado", "Pondera por prioridad y proximidad del plazo"),
    SNOWBALL("Bola de Nieve", "Prioriza liquidar primero las metas más cercanas"),
    AVALANCHE("Avalancha", "Fondea agresivamente las metas de mayor prioridad o urgencia")
}

data class GoalAllocationShare(
    val goalId: Long,
    val goalName: String,
    val allocatedAmount: Double,
    val newProgressPercentage: Float,
    val newEstimatedMonths: Double?,
    val monthsSaved: Double
)

data class FinancialHealthMetric(
    val name: String,
    val score: Int, // 0-100
    val weight: Double,
    val status: String,
    val description: String,
    val iconKey: String = "shield"
)

data class FinancialHealthReport(
    val overallScore: Int, // 0-100
    val grade: String, // "Excelente", "Saludable", "Atención", "Vulnerable"
    val summary: String,
    val metrics: List<FinancialHealthMetric>,
    val emergencyRunwayMonths: Double,
    val savingsRatePercentage: Double,
    val fixedExpensesRatioPercentage: Double,
    val budgetAdherencePercentage: Double
)

data class StressTestResult(
    val scenarioName: String,
    val simulatedRunwayMonths: Double,
    val monthlyDeficitOrSurplus: Double,
    val monthsDifference: Double,
    val impactSeverity: AnomalySeverity,
    val advice: String
)

data class SavingsGoalStrategy(
    val goalId: Long,
    val goalName: String,
    val currentAmount: Double,
    val targetAmount: Double,
    val progressPercentage: Float,
    val daysRemaining: Long?,
    val recommendedMonthlyRate: Double?,
    val aiInsight: String,
    val actionItems: List<String>,
    val priority: String = "Media",
    val remainingAmount: Double = 0.0,
    val category: String = "Otros",
    val milestones: List<GoalMilestone> = emptyList(),
    val nextMilestone: GoalMilestone? = null
)

data class CategorySpendingVelocity(
    val categoryId: Long,
    val categoryName: String,
    val iconKey: String,
    val currentSpent: Double,
    val dailyAverage: Double,
    val safeDailyAllowance: Double?,
    val projectedMonthEnd: Double,
    val budgetLimit: Double?,
    val isOverBudgetRisk: Boolean,
    val riskSeverity: AnomalySeverity = AnomalySeverity.INFO
)

data class DailyCumulativePoint(
    val day: Int,
    val actualCumulative: Double?,
    val projectedCumulative: Double,
    val idealBudgetCumulative: Double?,
    val isToday: Boolean
)

data class PredictiveScenario(
    val id: String,
    val title: String,
    val description: String,
    val dailyPace: Double,
    val projectedTotalExpense: Double,
    val projectedBalance: Double,
    val isCurrentPace: Boolean
)

data class ActionableFinancialTip(
    val id: String,
    val title: String,
    val recommendation: String,
    val potentialMonthlySaving: Double? = null,
    val iconKey: String = "lightbulb",
    val priority: AnomalySeverity = AnomalySeverity.INFO
)

data class ExpenseForecast(
    val expectedMonthEndExpense: Double,
    val currentSpent: Double,
    val averageDailySpent: Double,
    val daysElapsed: Int,
    val daysRemaining: Int,
    val projectedSavings: Double,
    val trendStatus: String, // "STABLE", "INCREASING", "SAVING_ON_TRACK"
    val summaryInsight: String,
    val totalIncome: Double = 0.0,
    val totalMonthlyBudget: Double? = null,
    val targetCeiling: Double = 0.0,
    val safeDailyRemainingBudget: Double = 0.0,
    val dailyPaceDifference: Double = 0.0, // averageDailySpent - safeDailyRemainingBudget
    val burnoutDayOfMonth: Int? = null, // Día estimado en que se agotará el presupuesto
    val daysUntilBurnout: Int? = null, // Días faltantes para agotar presupuesto
    val categoryVelocities: List<CategorySpendingVelocity> = emptyList(),
    val dailyCumulativeHistory: List<DailyCumulativePoint> = emptyList(),
    val predictiveScenarios: List<PredictiveScenario> = emptyList(),
    val actionableTips: List<ActionableFinancialTip> = emptyList()
)

enum class AssistantTab {
    CHAT,
    PREDICTIONS,
    SAVINGS_STRATEGY
}
