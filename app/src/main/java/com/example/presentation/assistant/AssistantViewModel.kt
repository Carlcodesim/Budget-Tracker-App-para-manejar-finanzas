package com.example.presentation.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.Account
import com.example.domain.model.SavingsGoal
import com.example.domain.model.Transaction
import com.example.domain.model.TransactionType
import com.example.domain.repository.GeminiRepository
import com.example.domain.usecase.account.GetAccountsUseCase
import com.example.domain.usecase.goal.GetSavingsGoalsUseCase
import com.example.domain.usecase.transaction.GetTransactionsUseCase
import com.example.domain.model.Category
import com.example.domain.usecase.category.GetCategoriesUseCase
import com.example.domain.usecase.goal.ContributeToGoalUseCase
import com.example.domain.usecase.transaction.AddTransactionUseCase
import com.example.presentation.common.util.CurrencyFormatter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Date
import java.util.Locale

class AssistantViewModel(
    private val geminiRepository: GeminiRepository,
    private val getTransactionsUseCase: GetTransactionsUseCase,
    private val getAccountsUseCase: GetAccountsUseCase,
    private val getSavingsGoalsUseCase: GetSavingsGoalsUseCase,
    private val getCategoriesUseCase: GetCategoriesUseCase? = null,
    private val contributeToGoalUseCase: ContributeToGoalUseCase? = null,
    private val addTransactionUseCase: AddTransactionUseCase? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _uiState = MutableStateFlow(AssistantUiState())
    val uiState: StateFlow<AssistantUiState> = _uiState.asStateFlow()

    private var currentAccounts: List<Account> = emptyList()
    private var currentTransactions: List<Transaction> = emptyList()
    private var currentGoals: List<SavingsGoal> = emptyList()
    private var currentCategories: List<Category> = emptyList()

    init {
        viewModelScope.launch {
            getAccountsUseCase().collect { accounts ->
                currentAccounts = accounts
                _uiState.update { it.copy(currentAccountsList = accounts) }
                calculateFinancialHealthReportLocal()
            }
        }
        viewModelScope.launch {
            getTransactionsUseCase().collect { transactions ->
                currentTransactions = transactions
                calculateForecastAndAnomaliesLocal()
                calculateFinancialHealthReportLocal()
            }
        }
        viewModelScope.launch {
            getSavingsGoalsUseCase().collect { goals ->
                currentGoals = goals
                calculateSavingsStrategiesLocal()
            }
        }
        viewModelScope.launch {
            getCategoriesUseCase?.invoke()?.collect { categories ->
                currentCategories = categories
                calculateForecastAndAnomaliesLocal()
                calculateFinancialHealthReportLocal()
            }
        }
    }

    fun onTabSelected(tab: AssistantTab) {
        _uiState.update { it.copy(selectedTab = tab) }
        when (tab) {
            AssistantTab.PREDICTIONS -> {
                if (_uiState.value.forecast == null) {
                    calculateForecastAndAnomaliesLocal()
                }
            }
            AssistantTab.SAVINGS_STRATEGY -> {
                if (_uiState.value.savingsStrategies.isEmpty() && currentGoals.isNotEmpty()) {
                    calculateSavingsStrategiesLocal()
                }
            }
            AssistantTab.CHAT -> { /* no-op */ }
        }
    }

    fun onInputTextChanged(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    // -------------------------------------------------------------
    // GESTIÓN DE APORTES DIRECTOS Y SIMULADOR DE EXCEDENTE
    // -------------------------------------------------------------
    fun setAllocationMode(mode: AllocationStrategyMode) {
        _uiState.update { it.copy(allocationMode = mode) }
        calculateGoalAllocations()
    }

    fun setSimulatedSurplus(amount: Double) {
        val clamped = amount.coerceAtLeast(0.0)
        _uiState.update { it.copy(simulatedSurplusToAllocate = clamped) }
        calculateGoalAllocations()
    }

    fun openContributeDialog(goal: SavingsGoalStrategy) {
        _uiState.update { it.copy(showContributeDialogForGoal = goal) }
    }

    fun dismissContributeDialog() {
        _uiState.update { it.copy(showContributeDialogForGoal = null) }
    }

    fun dismissContributionMessage() {
        _uiState.update { it.copy(contributionSuccessMessage = null) }
    }

    fun contributeToGoal(goalId: Long, amount: Double, accountId: Long) {
        if (amount <= 0.0) return
        viewModelScope.launch {
            contributeToGoalUseCase?.invoke(goalId, amount)

            val goal = currentGoals.find { it.id == goalId }
            val categories = currentCategories
            val category = categories.find {
                it.name.contains("Ahorro", ignoreCase = true) || it.name.contains("Meta", ignoreCase = true)
            } ?: categories.firstOrNull()

            if (category != null && goal != null) {
                addTransactionUseCase?.invoke(
                    Transaction(
                        amount = amount,
                        type = TransactionType.EXPENSE,
                        category = category,
                        description = "Aporte a meta: ${goal.name}",
                        date = Date(),
                        accountId = accountId,
                        tags = "ahorro, metas, asistente"
                    )
                )
            }

            _uiState.update {
                it.copy(
                    showContributeDialogForGoal = null,
                    contributionSuccessMessage = "¡Aporte de ${CurrencyFormatter.format(amount)} registrado exitosamente en '${goal?.name ?: "Meta"}'!"
                )
            }
        }
    }

    fun selectStressScenario(scenarioIndex: Int) {
        _uiState.update { it.copy(selectedStressScenario = scenarioIndex) }
        calculateStressTestResult(scenarioIndex)
    }

    // -------------------------------------------------------------
    // 1. CONVERSATIONAL AI (CHAT)
    // -------------------------------------------------------------
    fun sendMessage(text: String) {
        if (text.isBlank()) return

        val userMessage = ChatMessage(text = text, isUser = true)
        val updatedMessages = _uiState.value.messages + userMessage

        _uiState.update {
            it.copy(
                messages = updatedMessages,
                inputText = "",
                isLoading = true,
                error = null
            )
        }

        viewModelScope.launch {
            try {
                val response = withContext(ioDispatcher) {
                    val systemPrompt = buildSystemPrompt()
                    val chatHistoryPrompt = buildChatHistoryPrompt(updatedMessages.takeLast(10))
                    geminiRepository.generateResponse(systemPrompt, chatHistoryPrompt)
                }

                _uiState.update {
                    it.copy(
                        messages = it.messages + ChatMessage(text = response, isUser = false),
                        isLoading = false
                    )
                }
            } catch (e: IllegalStateException) {
                if (e.message == "API_KEY_NOT_CONFIGURED") {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isKeyConfigured = false,
                            error = "La clave API de Gemini no está configurada. Por favor, añádela en el panel de Secretos en AI Studio."
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = e.message ?: "Ocurrió un error inesperado."
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Ocurrió un error al comunicarse con el asistente."
                    )
                }
            }
        }
    }

    // -------------------------------------------------------------
    // 3. PREDICTIONS & ANOMALY DETECTION
    // -------------------------------------------------------------
    fun runDeepAnomalyAndForecastAnalysis() {
        _uiState.update { it.copy(isAnalyzingAnomalies = true, error = null) }

        viewModelScope.launch {
            calculateForecastAndAnomaliesLocal()
            try {
                val response = withContext(ioDispatcher) {
                    val systemPrompt = """
                        Eres un analista financiero de riesgo y predicción de presupuestos para una aplicación móvil.
                        Tu tarea es analizar el historial de gastos del mes, comparar con tendencias anteriores, pronosticar el cierre de mes e identificar anomalías o picos sospechosos de gasto.
                        Sé muy claro, directo, estructurado y ofrece acciones concretas preventivas.
                    """.trimIndent()

                    val prompt = buildAnomalyPrompt()
                    geminiRepository.generateResponse(systemPrompt, prompt)
                }
                _uiState.update {
                    it.copy(
                        aiAnomalyAnalysis = response,
                        isAnalyzingAnomalies = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isAnalyzingAnomalies = false,
                        error = e.message
                    )
                }
            }
        }
    }

    fun onSimulatedDailyPaceChanged(pace: Double) {
        val currentForecast = _uiState.value.forecast ?: return
        val daysRemaining = currentForecast.daysRemaining
        val currentSpent = currentForecast.currentSpent
        val totalIncome = currentForecast.totalIncome

        val simExpense = currentSpent + (pace * daysRemaining)
        val simSavings = totalIncome - simExpense

        _uiState.update {
            it.copy(
                simulatedDailyPace = pace,
                simulatedProjectedExpense = simExpense,
                simulatedProjectedSavings = simSavings
            )
        }
    }

    fun onSelectPredictionDay(day: Int?) {
        _uiState.update { it.copy(selectedPredictionDay = day) }
    }

    fun resetSimulation() {
        val currentForecast = _uiState.value.forecast
        _uiState.update {
            it.copy(
                simulatedDailyPace = currentForecast?.averageDailySpent,
                simulatedProjectedExpense = currentForecast?.expectedMonthEndExpense,
                simulatedProjectedSavings = currentForecast?.projectedSavings,
                selectedPredictionDay = null
            )
        }
    }

    private fun calculateForecastAndAnomaliesLocal() {
        val curCycle = com.example.util.BudgetCycleManager.getCurrentCycleRange()
        val prevCycle = com.example.util.BudgetCycleManager.getPreviousCycleRange(curCycle)
        val currentDay = curCycle.elapsedDays
        val maxDays = curCycle.totalDaysInCycle
        val daysRemaining = (maxDays - currentDay).coerceAtLeast(0)

        // Rango de ciclo actual
        val currentMonthStart = curCycle.startDateMillis
        val currentMonthEnd = curCycle.endDateMillis

        // Rango ciclo anterior
        val prevMonthStart = prevCycle.startDateMillis
        val prevMonthEnd = prevCycle.endDateMillis

        val currentMonthExpenses = currentTransactions.filter {
            it.type == TransactionType.EXPENSE && it.date.time in currentMonthStart until currentMonthEnd
        }
        val prevMonthExpenses = currentTransactions.filter {
            it.type == TransactionType.EXPENSE && it.date.time in prevMonthStart until prevMonthEnd
        }
        val currentMonthIncomes = currentTransactions.filter {
            it.type == TransactionType.INCOME && it.date.time in currentMonthStart until currentMonthEnd
        }

        val currentTotalSpent = currentMonthExpenses.sumOf { it.amount }
        val currentTotalIncome = currentMonthIncomes.sumOf { it.amount }
        val prevTotalSpent = prevMonthExpenses.sumOf { it.amount }

        val avgDailySpent = if (currentDay > 0) currentTotalSpent / currentDay else 0.0
        val projectedEndExpense = avgDailySpent * maxDays
        val projectedSavings = currentTotalIncome - projectedEndExpense

        // Presupuesto mensual total configurado en categorías
        val totalBudgetFromCategories = currentCategories.mapNotNull { it.maxMonthlyBudget }.sum()
        val totalMonthlyBudget = if (totalBudgetFromCategories > 0) totalBudgetFromCategories else null

        // Techo de gasto mensual de referencia (Presupuesto o Ingresos o histórico anterior)
        val targetCeiling = when {
            totalMonthlyBudget != null && totalMonthlyBudget > 0 -> totalMonthlyBudget
            currentTotalIncome > 0 -> currentTotalIncome
            prevTotalSpent > 0 -> prevTotalSpent * 1.05
            else -> (currentTotalSpent * 1.25).coerceAtLeast(1000.0)
        }

        // Gasto diario seguro restante para no superar el techo mensual
        val remainingAllowedBudget = (targetCeiling - currentTotalSpent).coerceAtLeast(0.0)
        val safeDailyRemainingBudget = if (daysRemaining > 0) remainingAllowedBudget / daysRemaining else 0.0
        val dailyPaceDifference = avgDailySpent - safeDailyRemainingBudget

        // Día estimado en que se agotará el presupuesto al ritmo actual
        val burnoutDayOfMonth = if (avgDailySpent > 0 && targetCeiling > 0) {
            val rawBurnoutDay = (targetCeiling / avgDailySpent).toInt()
            if (rawBurnoutDay in 1..maxDays) rawBurnoutDay else null
        } else null
        val daysUntilBurnout = if (burnoutDayOfMonth != null) (burnoutDayOfMonth - currentDay).coerceAtLeast(0) else null

        val trendStatus = when {
            burnoutDayOfMonth != null && burnoutDayOfMonth <= maxDays -> "INCREASING"
            projectedSavings >= 0 && projectedEndExpense <= targetCeiling -> "SAVING_ON_TRACK"
            projectedSavings < 0 -> "INCREASING"
            else -> "STABLE"
        }

        // Velocidad y alertas por categoría
        val categoryVelocities = currentCategories.mapNotNull { cat ->
            val catExpenses = currentMonthExpenses.filter { it.category.id == cat.id }
            val spent = catExpenses.sumOf { it.amount }
            if (spent == 0.0 && cat.maxMonthlyBudget == null) return@mapNotNull null

            val catDailyAvg = if (currentDay > 0) spent / currentDay else 0.0
            val catProjectedEnd = catDailyAvg * maxDays
            val catBudget = cat.maxMonthlyBudget
            val isOverBudgetRisk = catBudget != null && catProjectedEnd > catBudget
            val safeDailyAllowance = if (catBudget != null && daysRemaining > 0) {
                ((catBudget - spent).coerceAtLeast(0.0) / daysRemaining)
            } else null

            val riskSeverity = when {
                catBudget != null && spent >= catBudget -> AnomalySeverity.HIGH
                catBudget != null && catProjectedEnd > catBudget * 1.15 -> AnomalySeverity.HIGH
                catBudget != null && catProjectedEnd > catBudget -> AnomalySeverity.MEDIUM
                catDailyAvg > (avgDailySpent * 0.4) -> AnomalySeverity.INFO
                else -> AnomalySeverity.INFO
            }

            CategorySpendingVelocity(
                categoryId = cat.id,
                categoryName = cat.name,
                iconKey = cat.iconKey,
                currentSpent = spent,
                dailyAverage = catDailyAvg,
                safeDailyAllowance = safeDailyAllowance,
                projectedMonthEnd = catProjectedEnd,
                budgetLimit = catBudget,
                isOverBudgetRisk = isOverBudgetRisk,
                riskSeverity = riskSeverity
            )
        }.sortedByDescending { it.dailyAverage }

        // Historial diario y curva de proyección acumulada (Día 1 a fin de mes)
        val dailyMap = mutableMapOf<Int, Double>()
        val txCal = Calendar.getInstance()
        currentMonthExpenses.forEach { tx ->
            txCal.time = tx.date
            val day = txCal.get(Calendar.DAY_OF_MONTH)
            dailyMap[day] = (dailyMap[day] ?: 0.0) + tx.amount
        }

        var runningActualSum = 0.0
        val dailyCumulativeHistory = (1..maxDays).map { day ->
            val idealCumulative = if (targetCeiling > 0) (targetCeiling / maxDays) * day else null
            if (day <= currentDay) {
                runningActualSum += (dailyMap[day] ?: 0.0)
                DailyCumulativePoint(
                    day = day,
                    actualCumulative = runningActualSum,
                    projectedCumulative = runningActualSum,
                    idealBudgetCumulative = idealCumulative,
                    isToday = (day == currentDay)
                )
            } else {
                val futureDays = day - currentDay
                val projectedSum = currentTotalSpent + (avgDailySpent * futureDays)
                DailyCumulativePoint(
                    day = day,
                    actualCumulative = null,
                    projectedCumulative = projectedSum,
                    idealBudgetCumulative = idealCumulative,
                    isToday = false
                )
            }
        }

        // Escenarios predictivos de cierre de mes
        val predictiveScenarios = listOf(
            PredictiveScenario(
                id = "current_pace",
                title = "Ritmo Actual (Tendencia)",
                description = "Manteniendo el ritmo promedio observado hasta hoy.",
                dailyPace = avgDailySpent,
                projectedTotalExpense = projectedEndExpense,
                projectedBalance = projectedSavings,
                isCurrentPace = true
            ),
            PredictiveScenario(
                id = "moderate_saving",
                title = "Ritmo Optimizado (-15%)",
                description = "Ajustando salidas y gastos variables en días restantes.",
                dailyPace = avgDailySpent * 0.85,
                projectedTotalExpense = currentTotalSpent + (avgDailySpent * 0.85 * daysRemaining),
                projectedBalance = currentTotalIncome - (currentTotalSpent + (avgDailySpent * 0.85 * daysRemaining)),
                isCurrentPace = false
            ),
            PredictiveScenario(
                id = "safe_allowance",
                title = "Ritmo Seguro Disponible",
                description = "Límite diario recomendado para asegurar superávit.",
                dailyPace = safeDailyRemainingBudget,
                projectedTotalExpense = currentTotalSpent + (safeDailyRemainingBudget * daysRemaining),
                projectedBalance = currentTotalIncome - (currentTotalSpent + (safeDailyRemainingBudget * daysRemaining)),
                isCurrentPace = false
            )
        )

        // Tips financieros accionables
        val actionableTips = mutableListOf<ActionableFinancialTip>()

        if (dailyPaceDifference > 0 && daysRemaining > 0) {
            actionableTips.add(
                ActionableFinancialTip(
                    id = "tip_pace_excess",
                    title = "Ritmo diario por encima del límite seguro",
                    recommendation = "Tu ritmo diario (${CurrencyFormatter.format(avgDailySpent)}/día) excede el ritmo seguro de ${CurrencyFormatter.format(safeDailyRemainingBudget)}/día en ${CurrencyFormatter.format(dailyPaceDifference)}/día. Moderar tus consumos durante los próximos $daysRemaining días te ahorrará ${CurrencyFormatter.format(dailyPaceDifference * daysRemaining)}.",
                    potentialMonthlySaving = dailyPaceDifference * daysRemaining,
                    iconKey = "speed",
                    priority = AnomalySeverity.HIGH
                )
            )
        } else if (safeDailyRemainingBudget > 0 && daysRemaining > 0) {
            actionableTips.add(
                ActionableFinancialTip(
                    id = "tip_pace_safe",
                    title = "Presupuesto diario disponible saludable",
                    recommendation = "Cuentas con un margen diario seguro de hasta ${CurrencyFormatter.format(safeDailyRemainingBudget)}/día para los $daysRemaining días restantes sin comprometer tu meta mensual.",
                    potentialMonthlySaving = projectedSavings.coerceAtLeast(0.0),
                    iconKey = "verified",
                    priority = AnomalySeverity.INFO
                )
            )
        }

        burnoutDayOfMonth?.let { bDay ->
            if (bDay <= maxDays) {
                actionableTips.add(
                    ActionableFinancialTip(
                        id = "tip_burnout_alert",
                        title = "Alerta de agotamiento de presupuesto",
                        recommendation = "Al ritmo de gasto actual, agotarás el presupuesto disponible el día $bDay del mes (en ${daysUntilBurnout ?: 0} días).",
                        iconKey = "warning",
                        priority = AnomalySeverity.HIGH
                    )
                )
            }
        }

        categoryVelocities.firstOrNull { it.isOverBudgetRisk }?.let { cat ->
            actionableTips.add(
                ActionableFinancialTip(
                    id = "tip_cat_overbudget_${cat.categoryId}",
                    title = "Desviación en ${cat.categoryName}",
                    recommendation = "Se proyecta en ${CurrencyFormatter.format(cat.projectedMonthEnd)}, excediendo su presupuesto de ${CurrencyFormatter.format(cat.budgetLimit ?: 0.0)}. Límite diario sugerido: ${CurrencyFormatter.format(cat.safeDailyAllowance ?: 0.0)}/día.",
                    potentialMonthlySaving = (cat.projectedMonthEnd - (cat.budgetLimit ?: 0.0)).coerceAtLeast(0.0),
                    iconKey = cat.iconKey,
                    priority = AnomalySeverity.MEDIUM
                )
            )
        }

        val summaryInsight = when {
            burnoutDayOfMonth != null && burnoutDayOfMonth <= maxDays ->
                "Atención: A ${CurrencyFormatter.format(avgDailySpent)}/día agotarás tu presupuesto el día $burnoutDayOfMonth. Para evitarlo, reduce a máximo ${CurrencyFormatter.format(safeDailyRemainingBudget)}/día durante los $daysRemaining días restantes."
            projectedSavings >= 0 ->
                "Al ritmo de gasto actual de ${CurrencyFormatter.format(avgDailySpent)}/día, proyectas un ahorro de ${CurrencyFormatter.format(projectedSavings)} al cierre de mes."
            else ->
                "Alerta: Al ritmo de gasto actual proyectas un déficit de ${CurrencyFormatter.format(kotlin.math.abs(projectedSavings))} respecto a tus ingresos. Tu ritmo seguro restante es de ${CurrencyFormatter.format(safeDailyRemainingBudget)}/día."
        }

        val forecast = ExpenseForecast(
            expectedMonthEndExpense = projectedEndExpense,
            currentSpent = currentTotalSpent,
            averageDailySpent = avgDailySpent,
            daysElapsed = currentDay,
            daysRemaining = daysRemaining,
            projectedSavings = projectedSavings,
            trendStatus = trendStatus,
            summaryInsight = summaryInsight,
            totalIncome = currentTotalIncome,
            totalMonthlyBudget = totalMonthlyBudget,
            targetCeiling = targetCeiling,
            safeDailyRemainingBudget = safeDailyRemainingBudget,
            dailyPaceDifference = dailyPaceDifference,
            burnoutDayOfMonth = burnoutDayOfMonth,
            daysUntilBurnout = daysUntilBurnout,
            categoryVelocities = categoryVelocities,
            dailyCumulativeHistory = dailyCumulativeHistory,
            predictiveScenarios = predictiveScenarios,
            actionableTips = actionableTips
        )

        // Detección de anomalías locales
        val anomalies = mutableListOf<FinancialAnomaly>()

        // 1. Detección de gastos atípicos (outliers mayores al 40% del total gastado o mayores al promedio * 2.5)
        val expenseThreshold = if (currentMonthExpenses.size > 2) (avgDailySpent * 2.5).coerceAtLeast(100.0) else 500.0
        currentMonthExpenses.filter { it.amount >= expenseThreshold }.forEachIndexed { idx, tx ->
            anomalies.add(
                FinancialAnomaly(
                    id = "anomaly_large_${tx.id}_$idx",
                    title = "Gasto significativo: ${tx.category.name}",
                    description = "Gasto de ${CurrencyFormatter.format(tx.amount)} en '${tx.description ?: tx.category.name}' supera ampliamente el promedio diario habitual.",
                    severity = AnomalySeverity.HIGH,
                    recommendation = "Revisa si este gasto es recurrente o puntual para no descalabrar el presupuesto mensual.",
                    category = tx.category.name,
                    amountDiff = tx.amount
                )
            )
        }

        // 2. Aceleración de gasto comparado al mes anterior
        if (prevTotalSpent > 0 && projectedEndExpense > prevTotalSpent * 1.25) {
            val increase = projectedEndExpense - prevTotalSpent
            anomalies.add(
                FinancialAnomaly(
                    id = "anomaly_spending_acceleration",
                    title = "Aceleración de gastos (+${String.format(Locale.US, "%.0f", (projectedEndExpense / prevTotalSpent - 1) * 100)}%)",
                    description = "Tu proyección de gastos (${CurrencyFormatter.format(projectedEndExpense)}) supera el gasto total del mes anterior (${CurrencyFormatter.format(prevTotalSpent)}).",
                    severity = AnomalySeverity.MEDIUM,
                    recommendation = "Limita los gastos no esenciales durante los ${daysRemaining} días que restan del mes.",
                    amountDiff = increase
                )
            )
        }

        // 3. Alerta de saldo bajo en cuentas
        currentAccounts.filter { it.currentBalance < 50.0 }.forEach { acc ->
            anomalies.add(
                FinancialAnomaly(
                    id = "anomaly_low_balance_${acc.id}",
                    title = "Saldo bajo en ${acc.name}",
                    description = "El saldo disponible actual es de tan solo ${CurrencyFormatter.format(acc.currentBalance)}.",
                    severity = AnomalySeverity.MEDIUM,
                    recommendation = "Considera fondear esta cuenta o limitar cobros automáticos para evitar penalizaciones.",
                    category = acc.name
                )
            )
        }

        _uiState.update {
            it.copy(
                forecast = forecast,
                anomalies = anomalies,
                simulatedDailyPace = it.simulatedDailyPace ?: avgDailySpent,
                simulatedProjectedExpense = it.simulatedProjectedExpense ?: projectedEndExpense,
                simulatedProjectedSavings = it.simulatedProjectedSavings ?: projectedSavings
            )
        }
    }

    private fun buildAnomalyPrompt(): String {
        val sb = StringBuilder()
        sb.append("Analiza los siguientes datos financieros reales de este mes y su análisis predictivo:\n\n")
        val forecast = _uiState.value.forecast
        if (forecast != null) {
            sb.append("- Gasto acumulado en el mes: ${CurrencyFormatter.format(forecast.currentSpent)}\n")
            sb.append("- Ritmo diario actual: ${CurrencyFormatter.format(forecast.averageDailySpent)}/día\n")
            sb.append("- Ritmo seguro restante recomendado: ${CurrencyFormatter.format(forecast.safeDailyRemainingBudget)}/día\n")
            sb.append("- Diferencia de ritmo: ${CurrencyFormatter.format(forecast.dailyPaceDifference)}/día\n")
            sb.append("- Techo de gasto presupuestado: ${CurrencyFormatter.format(forecast.targetCeiling)}\n")
            sb.append("- Proyección total al cierre: ${CurrencyFormatter.format(forecast.expectedMonthEndExpense)}\n")
            sb.append("- Días transcurridos: ${forecast.daysElapsed}, Días restantes: ${forecast.daysRemaining}\n")
            if (forecast.burnoutDayOfMonth != null) {
                sb.append("- Día estimado de quiebre de presupuesto: Día ${forecast.burnoutDayOfMonth} del mes (en ${forecast.daysUntilBurnout} días)\n")
            }
            if (forecast.categoryVelocities.isNotEmpty()) {
                sb.append("\nVelocidad por categorías principales:\n")
                forecast.categoryVelocities.take(4).forEach { cat ->
                    sb.append("  * ${cat.categoryName}: ${CurrencyFormatter.format(cat.dailyAverage)}/día (Gastado: ${CurrencyFormatter.format(cat.currentSpent)}, Proyectado: ${CurrencyFormatter.format(cat.projectedMonthEnd)}")
                    if (cat.budgetLimit != null) {
                        sb.append(", Límite: ${CurrencyFormatter.format(cat.budgetLimit)}, Ritmo seguro: ${CurrencyFormatter.format(cat.safeDailyAllowance ?: 0.0)}/día")
                    }
                    sb.append(")\n")
                }
            }
        }

        sb.append("\nTransacciones recientes del mes:\n")
        currentTransactions.take(15).forEach { tx ->
            sb.append("- ${tx.type}: ${CurrencyFormatter.format(tx.amount)} en ${tx.category.name} (${tx.description ?: "Sin descripción"})\n")
        }

        sb.append("\nPor favor proporciona:\n")
        sb.append("1. Diagnóstico del ritmo de gasto actual vs el ritmo seguro restante.\n")
        sb.append("2. Detección de patrones de fuga en categorías clave.\n")
        sb.append("3. 3 recomendaciones altamente accionables para proteger el superávit en los días restantes del mes.\n")
        return sb.toString()
    }

    // -------------------------------------------------------------
    // 4. SAVINGS STRATEGY (ESTRATEGIA DINÁMICA DE AHORRO)
    // -------------------------------------------------------------
    fun generateIntelligentSavingsStrategy() {
        _uiState.update { it.copy(isGeneratingStrategy = true, error = null) }

        viewModelScope.launch {
            calculateSavingsStrategiesLocal()
            try {
                val response = withContext(ioDispatcher) {
                    val systemPrompt = """
                        Eres un asesor patrimonial experto en optimización del ahorro personal y cumplimiento de metas financieras.
                        Analiza las metas de ahorro del usuario, su liquidez disponible y sus patrones de gasto para generar una estrategia inteligente, viable y altamente motivadora.
                        Prioriza con la regla 50/30/20 y técnicas de efecto bola de nieve o avalancha según corresponda.
                    """.trimIndent()

                    val prompt = buildSavingsStrategyPrompt()
                    geminiRepository.generateResponse(systemPrompt, prompt)
                }

                _uiState.update {
                    it.copy(
                        aiGlobalSavingsAdvice = response,
                        isGeneratingStrategy = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isGeneratingStrategy = false,
                        error = e.message
                    )
                }
            }
        }
    }

    private fun calculateSavingsStrategiesLocal() {
        if (currentGoals.isEmpty()) {
            _uiState.update { it.copy(savingsStrategies = emptyList(), goalAllocations = emptyList()) }
            return
        }

        val strategies = currentGoals.map { goal ->
            val remaining = goal.remainingAmount
            val monthlyNeeded = goal.recommendedMonthlySaving ?: (if (goal.daysRemaining != null && goal.daysRemaining!! > 0) remaining / (goal.daysRemaining!! / 30.0).coerceAtLeast(1.0) else remaining / 6.0)

            val actions = mutableListOf<String>()
            if (goal.progressPercentage >= 0.8f) {
                actions.add("¡Estás en la recta final! Solo faltan ${CurrencyFormatter.format(remaining)}.")
                actions.add("Destina pequeños ingresos extras para completar la meta este mes.")
            } else if (goal.daysRemaining != null && goal.daysRemaining!! < 30) {
                actions.add("El plazo vence pronto (${goal.daysRemaining} días). Revisa si es posible acelerar aportes.")
            } else {
                actions.add("Aporte mensual sugerido: ${CurrencyFormatter.format(monthlyNeeded)}.")
                actions.add("Automatiza transferencias a inicio de mes hacia la cuenta de ahorro.")
            }

            val insight = when (goal.priority.lowercase()) {
                "alta" -> "Meta de alta prioridad: Proteger y fondear antes que cualquier gasto no esencial."
                "media" -> "Meta equilibrada: Mantén el ritmo periódico constante."
                else -> "Meta flexible: Si hay sobrantes a fin de mes, dirígelos aquí."
            }

            // Cálculo de hitos (25%, 50%, 75%, 100%)
            val milestonePercentages = listOf(25, 50, 75, 100)
            val milestones = milestonePercentages.map { pct ->
                val targetAmountForMilestone = goal.targetAmount * (pct / 100.0)
                val isReached = goal.currentAmount >= targetAmountForMilestone
                val remainingToMilestone = (targetAmountForMilestone - goal.currentAmount).coerceAtLeast(0.0)
                GoalMilestone(
                    percentage = pct,
                    targetAmount = targetAmountForMilestone,
                    isReached = isReached,
                    remainingToMilestone = remainingToMilestone
                )
            }
            val nextMilestone = milestones.firstOrNull { !it.isReached }

            SavingsGoalStrategy(
                goalId = goal.id,
                goalName = goal.name,
                currentAmount = goal.currentAmount,
                targetAmount = goal.targetAmount,
                progressPercentage = goal.progressPercentage,
                daysRemaining = goal.daysRemaining,
                recommendedMonthlyRate = monthlyNeeded,
                aiInsight = insight,
                actionItems = actions,
                priority = goal.priority,
                remainingAmount = remaining,
                category = goal.category,
                milestones = milestones,
                nextMilestone = nextMilestone
            )
        }

        _uiState.update {
            it.copy(savingsStrategies = strategies)
        }
        calculateGoalAllocations()
        calculateFinancialHealthReportLocal()
    }

    private fun calculateGoalAllocations() {
        val strategies = _uiState.value.savingsStrategies
        if (strategies.isEmpty()) {
            _uiState.update { it.copy(goalAllocations = emptyList()) }
            return
        }

        val totalSurplus = _uiState.value.simulatedSurplusToAllocate ?: run {
            val income = _uiState.value.forecast?.totalIncome ?: 0.0
            val spent = _uiState.value.forecast?.currentSpent ?: 0.0
            val diff = income - spent
            if (diff > 0) diff else 200.0
        }

        val mode = _uiState.value.allocationMode
        val allocations = mutableListOf<GoalAllocationShare>()

        when (mode) {
            AllocationStrategyMode.BALANCED -> {
                val weights = strategies.map { s ->
                    val priorityWeight = when (s.priority.lowercase()) {
                        "alta" -> 3.0
                        "media" -> 2.0
                        else -> 1.0
                    }
                    val urgencyWeight = when {
                        s.daysRemaining == null -> 1.0
                        s.daysRemaining <= 30 -> 2.2
                        s.daysRemaining <= 90 -> 1.5
                        else -> 1.0
                    }
                    val activeWeight = if (s.remainingAmount > 0) 1.0 else 0.0
                    s to (priorityWeight * urgencyWeight * activeWeight)
                }
                val totalWeight = weights.sumOf { it.second }.coerceAtLeast(0.001)

                strategies.forEach { s ->
                    val weight = weights.find { it.first.goalId == s.goalId }?.second ?: 0.0
                    val allocated = (totalSurplus * (weight / totalWeight)).coerceAtMost(s.remainingAmount)
                    val newCurrent = s.currentAmount + allocated
                    val newProgress = if (s.targetAmount > 0) (newCurrent / s.targetAmount).toFloat().coerceIn(0f, 1f) else 1f
                    val monthlyRate = s.recommendedMonthlyRate ?: 1.0
                    val monthsSaved = if (monthlyRate > 0) allocated / monthlyRate else 0.0
                    val newRemaining = (s.targetAmount - newCurrent).coerceAtLeast(0.0)
                    val newEstimatedMonths = if (monthlyRate > 0) newRemaining / monthlyRate else null

                    allocations.add(
                        GoalAllocationShare(
                            goalId = s.goalId,
                            goalName = s.goalName,
                            allocatedAmount = allocated,
                            newProgressPercentage = newProgress,
                            newEstimatedMonths = newEstimatedMonths,
                            monthsSaved = monthsSaved
                        )
                    )
                }
            }
            AllocationStrategyMode.SNOWBALL -> {
                val sorted = strategies.filter { it.remainingAmount > 0 }.sortedBy { it.remainingAmount }
                var remainingPool = totalSurplus
                val mapAllocated = mutableMapOf<Long, Double>()

                for (s in sorted) {
                    if (remainingPool <= 0.0) {
                        mapAllocated[s.goalId] = 0.0
                    } else {
                        val toGive = remainingPool.coerceAtMost(s.remainingAmount)
                        mapAllocated[s.goalId] = toGive
                        remainingPool -= toGive
                    }
                }

                strategies.forEach { s ->
                    val allocated = mapAllocated[s.goalId] ?: 0.0
                    val newCurrent = s.currentAmount + allocated
                    val newProgress = if (s.targetAmount > 0) (newCurrent / s.targetAmount).toFloat().coerceIn(0f, 1f) else 1f
                    val monthlyRate = s.recommendedMonthlyRate ?: 1.0
                    val monthsSaved = if (monthlyRate > 0) allocated / monthlyRate else 0.0
                    val newRemaining = (s.targetAmount - newCurrent).coerceAtLeast(0.0)
                    val newEstimatedMonths = if (monthlyRate > 0) newRemaining / monthlyRate else null

                    allocations.add(
                        GoalAllocationShare(
                            goalId = s.goalId,
                            goalName = s.goalName,
                            allocatedAmount = allocated,
                            newProgressPercentage = newProgress,
                            newEstimatedMonths = newEstimatedMonths,
                            monthsSaved = monthsSaved
                        )
                    )
                }
            }
            AllocationStrategyMode.AVALANCHE -> {
                val sorted = strategies.filter { it.remainingAmount > 0 }.sortedWith(
                    compareByDescending<SavingsGoalStrategy> {
                        when (it.priority.lowercase()) {
                            "alta" -> 3
                            "media" -> 2
                            else -> 1
                        }
                    }.thenBy { it.daysRemaining ?: 9999L }
                )
                var remainingPool = totalSurplus
                val mapAllocated = mutableMapOf<Long, Double>()

                for (s in sorted) {
                    if (remainingPool <= 0.0) {
                        mapAllocated[s.goalId] = 0.0
                    } else {
                        val toGive = remainingPool.coerceAtMost(s.remainingAmount)
                        mapAllocated[s.goalId] = toGive
                        remainingPool -= toGive
                    }
                }

                strategies.forEach { s ->
                    val allocated = mapAllocated[s.goalId] ?: 0.0
                    val newCurrent = s.currentAmount + allocated
                    val newProgress = if (s.targetAmount > 0) (newCurrent / s.targetAmount).toFloat().coerceIn(0f, 1f) else 1f
                    val monthlyRate = s.recommendedMonthlyRate ?: 1.0
                    val monthsSaved = if (monthlyRate > 0) allocated / monthlyRate else 0.0
                    val newRemaining = (s.targetAmount - newCurrent).coerceAtLeast(0.0)
                    val newEstimatedMonths = if (monthlyRate > 0) newRemaining / monthlyRate else null

                    allocations.add(
                        GoalAllocationShare(
                            goalId = s.goalId,
                            goalName = s.goalName,
                            allocatedAmount = allocated,
                            newProgressPercentage = newProgress,
                            newEstimatedMonths = newEstimatedMonths,
                            monthsSaved = monthsSaved
                        )
                    )
                }
            }
        }

        _uiState.update {
            it.copy(
                simulatedSurplusToAllocate = totalSurplus,
                goalAllocations = allocations
            )
        }
    }

    private fun calculateFinancialHealthReportLocal() {
        val totalLiquid = currentAccounts.sumOf { it.currentBalance }.coerceAtLeast(0.0)

        val cal = Calendar.getInstance()
        val currentMonth = cal.get(Calendar.MONTH)
        val currentYear = cal.get(Calendar.YEAR)

        val currentMonthTransactions = currentTransactions.filter {
            val tCal = Calendar.getInstance().apply { time = it.date }
            tCal.get(Calendar.MONTH) == currentMonth && tCal.get(Calendar.YEAR) == currentYear
        }

        val totalIncome = currentMonthTransactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
        val totalExpense = currentMonthTransactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }

        val currentDay = cal.get(Calendar.DAY_OF_MONTH).coerceAtLeast(1)
        val dailyExpense = (totalExpense / currentDay).coerceAtLeast(1.0)
        val monthlyEstimatedExpense = dailyExpense * 30.0

        // 1. Tasa de Ahorro: (Income - Expense) / Income
        val savingsRate = if (totalIncome > 0) ((totalIncome - totalExpense) / totalIncome * 100.0).coerceIn(-100.0, 100.0) else 0.0
        val savingsScore = when {
            savingsRate >= 25.0 -> 100
            savingsRate >= 15.0 -> 80
            savingsRate >= 5.0 -> 60
            savingsRate >= 0.0 -> 40
            else -> 15
        }
        val savingsStatus = when {
            savingsScore >= 80 -> "Excelente"
            savingsScore >= 60 -> "Aceptable"
            else -> "Crítico"
        }

        // 2. Fondo de Emergencia / Runway
        val runwayMonths = if (monthlyEstimatedExpense > 0) totalLiquid / monthlyEstimatedExpense else 0.0
        val runwayScore = when {
            runwayMonths >= 6.0 -> 100
            runwayMonths >= 3.0 -> 85
            runwayMonths >= 1.5 -> 65
            runwayMonths >= 0.5 -> 40
            else -> 20
        }
        val runwayStatus = when {
            runwayScore >= 80 -> "Robusto"
            runwayScore >= 60 -> "En Construcción"
            else -> "Vulnerable"
        }

        // 3. Ratio de Gastos Fijos vs Ingresos (ej. recurrentes o categorías esenciales)
        val fixedCategories = listOf("servicios", "renta", "alquiler", "hogar", "educación", "salud", "transporte")
        val fixedSpent = currentMonthTransactions
            .filter { it.type == TransactionType.EXPENSE && fixedCategories.any { fix -> it.category.name.lowercase().contains(fix) } }
            .sumOf { it.amount }
        val fixedRatio = if (totalIncome > 0) (fixedSpent / totalIncome * 100.0) else 50.0
        val fixedScore = when {
            fixedRatio <= 40.0 -> 95
            fixedRatio <= 55.0 -> 80
            fixedRatio <= 70.0 -> 55
            else -> 30
        }
        val fixedStatus = when {
            fixedScore >= 80 -> "Equilibrado"
            fixedScore >= 55 -> "Aceptable"
            else -> "Alto"
        }

        // 4. Cumplimiento de Presupuesto por Categoría
        val budgetedCategories = currentCategories.filter { it.maxMonthlyBudget != null && it.maxMonthlyBudget > 0 }
        val adherencePct = if (budgetedCategories.isNotEmpty()) {
            val respectedCount = budgetedCategories.count { cat ->
                val spentInCat = currentMonthTransactions
                    .filter { it.type == TransactionType.EXPENSE && it.category.id == cat.id }
                    .sumOf { it.amount }
                spentInCat <= (cat.maxMonthlyBudget ?: 0.0)
            }
            (respectedCount.toDouble() / budgetedCategories.size * 100.0)
        } else {
            85.0
        }
        val budgetScore = adherencePct.toInt().coerceIn(10, 100)
        val budgetStatus = if (budgetScore >= 80) "Óptimo" else if (budgetScore >= 50) "Regular" else "Desviado"

        // Overall Score (Ponderado)
        val overall = (savingsScore * 0.35 + runwayScore * 0.30 + fixedScore * 0.20 + budgetScore * 0.15).toInt().coerceIn(0, 100)
        val grade = when {
            overall >= 85 -> "Excelente"
            overall >= 70 -> "Saludable"
            overall >= 50 -> "Atención"
            else -> "Vulnerable"
        }
        val summary = when {
            overall >= 85 -> "Tu salud financiera es sobresaliente. Mantienes un colchón sólido y ritmo de ahorro constante."
            overall >= 70 -> "Buen control de tus finanzas. Puedes optimizar tu fondo de emergencia o acelerar tus metas."
            overall >= 50 -> "Finanzas en zona de atención. Ajustar gastos hormiga te dará margen para no comprometer tu tranquilidad."
            else -> "Finanzas vulnerables. Enfócate de inmediato en recortar gastos no esenciales y construir un fondo de emergencia."
        }

        val metrics = listOf(
            FinancialHealthMetric(
                name = "Tasa de Ahorro",
                score = savingsScore,
                weight = 0.35,
                status = savingsStatus,
                description = "${String.format(Locale.US, "%.1f", savingsRate)}% de tus ingresos ahorrados este mes (Meta: >=20%)",
                iconKey = "savings"
            ),
            FinancialHealthMetric(
                name = "Colchón de Emergencia",
                score = runwayScore,
                weight = 0.30,
                status = runwayStatus,
                description = "${String.format(Locale.US, "%.1f", runwayMonths)} meses de supervivencia cubiertos con tu liquidez actual",
                iconKey = "shield"
            ),
            FinancialHealthMetric(
                name = "Compromisos Fijos",
                score = fixedScore,
                weight = 0.20,
                status = fixedStatus,
                description = "${String.format(Locale.US, "%.1f", fixedRatio)}% de tus ingresos destinados a gastos indispensables (Regla 50/30/20)",
                iconKey = "lock"
            ),
            FinancialHealthMetric(
                name = "Disciplina Presupuestaria",
                score = budgetScore,
                weight = 0.15,
                status = budgetStatus,
                description = "${budgetScore}% de cumplimiento en categorías con límite",
                iconKey = "check"
            )
        )

        val report = FinancialHealthReport(
            overallScore = overall,
            grade = grade,
            summary = summary,
            metrics = metrics,
            emergencyRunwayMonths = runwayMonths,
            savingsRatePercentage = savingsRate,
            fixedExpensesRatioPercentage = fixedRatio,
            budgetAdherencePercentage = adherencePct
        )

        _uiState.update { it.copy(financialHealthReport = report) }
        calculateStressTestResult(_uiState.value.selectedStressScenario)
    }

    private fun calculateStressTestResult(scenarioIndex: Int) {
        val report = _uiState.value.financialHealthReport ?: return
        val currentRunway = report.emergencyRunwayMonths
        val baseIncome = _uiState.value.forecast?.totalIncome ?: 0.0
        val baseExpense = _uiState.value.forecast?.expectedMonthEndExpense ?: (_uiState.value.forecast?.currentSpent ?: 0.0)

        val result = when (scenarioIndex) {
            1 -> {
                val simIncome = baseIncome * 0.75
                val simSurplus = simIncome - baseExpense
                val simRunway = (currentRunway * 0.75).coerceAtLeast(0.1)
                StressTestResult(
                    scenarioName = "Caída de Ingresos (-25%)",
                    simulatedRunwayMonths = simRunway,
                    monthlyDeficitOrSurplus = simSurplus,
                    monthsDifference = simRunway - currentRunway,
                    impactSeverity = if (simSurplus < 0) AnomalySeverity.HIGH else AnomalySeverity.MEDIUM,
                    advice = if (simSurplus < 0) {
                        "Entrarías en déficit mensual de ${CurrencyFormatter.format(kotlin.math.abs(simSurplus))}. Suspende compras prescindibles de inmediato."
                    } else {
                        "Mantendrías superávit mensual de ${CurrencyFormatter.format(simSurplus)}. Resistirías ${String.format(Locale.US, "%.1f", simRunway)} meses."
                    }
                )
            }
            2 -> {
                val emergencyCost = 500.0
                val totalLiquid = currentAccounts.sumOf { it.currentBalance }.coerceAtLeast(0.0)
                val newLiquid = (totalLiquid - emergencyCost).coerceAtLeast(0.0)
                val monthlyExp = if (baseExpense > 0) baseExpense else 1.0
                val newRunway = newLiquid / monthlyExp
                val deficit = (baseIncome - (baseExpense + emergencyCost))
                StressTestResult(
                    scenarioName = "Emergencia Imprevista ($500)",
                    simulatedRunwayMonths = newRunway,
                    monthlyDeficitOrSurplus = deficit,
                    monthsDifference = newRunway - currentRunway,
                    impactSeverity = if (newLiquid < 200.0) AnomalySeverity.HIGH else AnomalySeverity.MEDIUM,
                    advice = if (newLiquid < 200.0) {
                        "Tus reservas quedarían vulnerables (${CurrencyFormatter.format(newLiquid)}). Repón el fondo de emergencia como prioridad #1."
                    } else {
                        "Absorberías la emergencia conservando ${String.format(Locale.US, "%.1f", newRunway)} meses de liquidez de respaldo."
                    }
                )
            }
            3 -> {
                val simExpense = baseExpense * 0.80
                val simSurplus = baseIncome - simExpense
                val monthlySaved = baseExpense * 0.20
                val annualGain = monthlySaved * 12.0
                val simRunway = if (simExpense > 0) (currentAccounts.sumOf { it.currentBalance } / simExpense) else currentRunway
                StressTestResult(
                    scenarioName = "Recorte de Gastos Hormiga (-20%)",
                    simulatedRunwayMonths = simRunway,
                    monthlyDeficitOrSurplus = simSurplus,
                    monthsDifference = simRunway - currentRunway,
                    impactSeverity = AnomalySeverity.INFO,
                    advice = "¡Liberarías ${CurrencyFormatter.format(monthlySaved)} extra al mes (${CurrencyFormatter.format(annualGain)} al año) para acelerar tus metas!"
                )
            }
            else -> {
                StressTestResult(
                    scenarioName = "Condiciones Actuales (Línea Base)",
                    simulatedRunwayMonths = currentRunway,
                    monthlyDeficitOrSurplus = baseIncome - baseExpense,
                    monthsDifference = 0.0,
                    impactSeverity = AnomalySeverity.INFO,
                    advice = "Rendimiento normal de tus finanzas según tus hábitos actuales de ingreso y gasto del mes."
                )
            }
        }

        _uiState.update { it.copy(stressTestResult = result) }
    }

    private fun buildSavingsStrategyPrompt(): String {
        val sb = StringBuilder()
        sb.append("Metas de ahorro del usuario:\n")
        currentGoals.forEach { g ->
            sb.append("- ${g.name} (Prioridad: ${g.priority}): Meta de ${CurrencyFormatter.format(g.targetAmount)}, acumulado ${CurrencyFormatter.format(g.currentAmount)} (${String.format(Locale.US, "%.1f", g.progressPercentage * 100)}%). ")
            g.daysRemaining?.let { sb.append("Plazo: $it días restantes. ") }
            sb.append("\n")
        }

        sb.append("\nCuentas disponibles:\n")
        currentAccounts.forEach { a ->
            sb.append("- ${a.name}: ${CurrencyFormatter.format(a.currentBalance)}\n")
        }

        sb.append("\nPor favor crea un Plan Estratégico de Ahorro personalizado:\n")
        sb.append("1. Distribución recomendada del dinero entre las diferentes metas según su prioridad y plazo.\n")
        sb.append("2. Consejos prácticos para reducir micro-gastos y destinarlos al ahorro.\n")
        sb.append("3. Cronograma sugerido de aportaciones para maximizar el éxito.\n")
        return sb.toString()
    }

    // -------------------------------------------------------------
    // HELPERS SYSTEM PROMPT
    // -------------------------------------------------------------
    private fun buildSystemPrompt(): String {
        val sb = StringBuilder()
        sb.append("Eres un Asistente Financiero inteligente, experto, profesional y muy amigable en español para una aplicación de control de presupuesto.\n\n")
        sb.append("Tu tarea es analizar los datos reales del usuario y proporcionarle recomendaciones personalizadas, responder a sus consultas financieras, identificar patrones de gasto y motivarlo a alcanzar sus metas de ahorro.\n\n")
        sb.append("Aquí está el estado financiero actual del usuario en tiempo real:\n\n")

        // 1. Cuentas
        sb.append("### CUENTAS FINANCIERAS:\n")
        if (currentAccounts.isEmpty()) {
            sb.append("- No hay cuentas registradas.\n")
        } else {
            currentAccounts.forEach { account ->
                sb.append("- ${account.name} (Tipo: ${account.type}): Saldo Actual ${CurrencyFormatter.format(account.currentBalance)} (Saldo inicial: ${CurrencyFormatter.format(account.initialBalance)})\n")
            }
        }
        sb.append("\n")

        // 2. Metas de ahorro
        sb.append("### METAS DE AHORRO:\n")
        if (currentGoals.isEmpty()) {
            sb.append("- No hay metas de ahorro registradas.\n")
        } else {
            currentGoals.forEach { goal ->
                val remaining = goal.remainingAmount
                sb.append("- ${goal.name} (Categoría: ${goal.category}, Prioridad: ${goal.priority}): ")
                sb.append("Progreso: ${CurrencyFormatter.format(goal.currentAmount)} de ${CurrencyFormatter.format(goal.targetAmount)} (${String.format(Locale.US, "%.1f", goal.progressPercentage * 100)}%). ")
                sb.append("Faltan: ${CurrencyFormatter.format(remaining)}. ")
                goal.daysRemaining?.let { sb.append("Días restantes: $it. ") }
                goal.recommendedMonthlySaving?.let { sb.append("Ahorro mensual recomendado: ${CurrencyFormatter.format(it)}.") }
                sb.append("\n")
            }
        }
        sb.append("\n")

        // 3. Transacciones recientes
        sb.append("### TRANSACCIONES RECIENTES (ÚLTIMAS 20):\n")
        if (currentTransactions.isEmpty()) {
            sb.append("- No hay transacciones registradas.\n")
        } else {
            val sorted = currentTransactions.sortedByDescending { it.date }.take(20)
            sorted.forEach { tx ->
                val typeStr = when (tx.type) {
                    TransactionType.INCOME -> "INGRESO"
                    TransactionType.EXPENSE -> "GASTO"
                    TransactionType.TRANSFER -> "TRANSFERENCIA ENTRE CUENTAS"
                }
                sb.append("- $typeStr de ${CurrencyFormatter.format(tx.amount)} en la categoría '${tx.category.name}' - Descripción: ${tx.description ?: "Sin descripción"} - Fecha: ${tx.date}\n")
            }
        }
        sb.append("\n")

        sb.append("### INSTRUCCIONES DE RESPUESTA:\n")
        sb.append("1. Da respuestas concisas pero muy informativas y accionables en español.\n")
        sb.append("2. Usa formato Markdown (negritas, listas con viñetas, saltos de línea) para que la respuesta sea visualmente agradable y estructurada.\n")
        sb.append("3. Sé proactivo: si el usuario tiene gastos elevados, sugiérele formas de reducirlos basándote en sus categorías reales. Si está cerca de cumplir una meta, motívalo.\n")
        sb.append("4. Siempre mantén un tono profesional, empático y alentador.\n")
        sb.append("5. Si te preguntan sobre cosas fuera del ámbito financiero, amablemente redirige la conversación a sus finanzas personales.\n")

        return sb.toString()
    }

    private fun buildChatHistoryPrompt(messages: List<ChatMessage>): String {
        val sb = StringBuilder()
        messages.forEach { message ->
            val role = if (message.isUser) "Usuario" else "Asistente"
            sb.append("$role: ${message.text}\n\n")
        }
        return sb.toString()
    }

    fun clearChat() {
        _uiState.update {
            it.copy(
                messages = listOf(
                    ChatMessage(
                        text = "Historial limpiado. ¡Hola de nuevo! Estoy listo para analizar tus cuentas y metas. ¿Qué te gustaría saber?",
                        isUser = false
                    )
                ),
                isLoading = false,
                error = null
            )
        }
    }
}
