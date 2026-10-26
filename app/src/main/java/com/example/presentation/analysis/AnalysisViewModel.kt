package com.example.presentation.analysis

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.Transaction
import com.example.domain.model.TransactionType
import com.example.domain.usecase.category.GetCategoriesUseCase
import com.example.domain.usecase.transaction.GetTransactionsUseCase
import com.example.util.BudgetCycleManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar

/**
 * ViewModel para realizar análisis financieros avanzados: distribuciones de gasto,
 * comparaciones intermensuales, tasas de ahorro, promedios diarios y recomendaciones,
 * sincronizado con el ciclo financiero de presupuesto configurado.
 */
class AnalysisViewModel(
    private val getTransactionsUseCase: GetTransactionsUseCase,
    private val getCategoriesUseCase: GetCategoriesUseCase
) : ViewModel() {

    val uiState: StateFlow<AnalysisUiState> = combine(
        getTransactionsUseCase(),
        getCategoriesUseCase(),
        BudgetCycleManager.startDayFlow
    ) { transactions, categories, startDay ->
        if (transactions.isEmpty()) {
            return@combine AnalysisUiState.Success(
                categoryExpenses = emptyList(),
                currentMonthIncome = 0.0,
                currentMonthExpense = 0.0,
                previousMonthIncome = 0.0,
                previousMonthExpense = 0.0,
                monthlyHistory = emptyList(),
                savingsTrend = SavingsTrendData(0.0, 0.0, 0f, 0f, 0f, true),
                topExpenseCategory = null,
                averageDailySpendingCurrent = 0.0,
                averageDailySpendingPrevious = 0.0,
                elapsedDaysCurrent = 1,
                elapsedDaysPrevious = 1,
                hasTransactions = false
            )
        }

        val curCycle = BudgetCycleManager.getCurrentCycleRange(startDay)
        val prevCycle = BudgetCycleManager.getPreviousCycleRange(curCycle, startDay)

        val currentMonthStart = curCycle.startDateMillis
        val currentMonthEnd = curCycle.endDateMillis
        val elapsedDaysCurrent = curCycle.elapsedDays

        val previousMonthStart = prevCycle.startDateMillis
        val previousMonthEnd = prevCycle.endDateMillis
        val elapsedDaysPrevious = prevCycle.totalDaysInCycle

        var currentMonthIncome = 0.0
        var currentMonthExpense = 0.0
        var previousMonthIncome = 0.0
        var previousMonthExpense = 0.0

        val currentCategoryExpenseMap = mutableMapOf<Long, Double>()
        val previousCategoryExpenseMap = mutableMapOf<Long, Double>()

        // 6 rangos de ciclos para histórico calculados de forma coherente con el día de inicio
        val monthNames = listOf("Ene", "Feb", "Mar", "Abr", "May", "Jun", "Jul", "Ago", "Sep", "Oct", "Nov", "Dic")
        data class MonthRange(val name: String, val year: Int, val start: Long, val end: Long, var expense: Double = 0.0, var income: Double = 0.0)

        val recentCycles = BudgetCycleManager.getRecentCycleRanges(count = 6, startDay = startDay)
        val historyRanges = recentCycles.map { c ->
            MonthRange(monthNames[c.month], c.year, c.startDateMillis, c.endDateMillis)
        }

        val curRange = historyRanges.last()
        val prevRange = historyRanges.getOrNull(4)

        // Agregación de alto rendimiento en un solo recorrido de transacciones
        for (tx in transactions) {
            val tTime = tx.date.time
            val amount = tx.amount

            // Vía rápida: mes actual
            if (tTime in currentMonthStart..currentMonthEnd) {
                if (tx.type == TransactionType.INCOME) {
                    currentMonthIncome += amount
                    curRange.income += amount
                } else if (tx.type == TransactionType.EXPENSE) {
                    currentMonthExpense += amount
                    curRange.expense += amount
                    currentCategoryExpenseMap[tx.category.id] = (currentCategoryExpenseMap[tx.category.id] ?: 0.0) + amount
                }
            } else if (tTime in previousMonthStart..previousMonthEnd) {
                // Vía rápida: mes anterior
                if (tx.type == TransactionType.INCOME) {
                    previousMonthIncome += amount
                    prevRange?.let { it.income += amount }
                } else if (tx.type == TransactionType.EXPENSE) {
                    previousMonthExpense += amount
                    prevRange?.let { it.expense += amount }
                    previousCategoryExpenseMap[tx.category.id] = (previousCategoryExpenseMap[tx.category.id] ?: 0.0) + amount
                }
            } else {
                // Meses históricos anteriores (índices 0 a 3)
                for (i in 0..3) {
                    val range = historyRanges[i]
                    if (tTime in range.start..range.end) {
                        if (tx.type == TransactionType.EXPENSE) {
                            range.expense += amount
                        } else if (tx.type == TransactionType.INCOME) {
                            range.income += amount
                        }
                        break
                    }
                }
            }
        }

        val categoryMapById = categories.associateBy { it.id }
        val currentTotalExpense = currentMonthExpense

        val categoryShareList = currentCategoryExpenseMap.map { (catId, sum) ->
            val category = categoryMapById[catId]
            val catName = category?.name ?: "Desconocido"
            val iconKey = category?.iconKey ?: "category"
            val percentage = if (currentTotalExpense > 0) (sum / currentTotalExpense).toFloat() else 0f
            CategoryExpenseShare(
                categoryId = catId,
                categoryName = catName,
                iconKey = iconKey,
                amount = sum,
                percentage = percentage
            )
        }.sortedByDescending { it.amount }

        val topCategory = categoryShareList.firstOrNull()

        // Tendencia de ahorro
        val currentMonthSavings = currentMonthIncome - currentMonthExpense
        val previousMonthSavings = previousMonthIncome - previousMonthExpense

        val savingsRateCurrent = if (currentMonthIncome > 0) (currentMonthSavings / currentMonthIncome).toFloat() else 0f
        val savingsRatePrevious = if (previousMonthIncome > 0) (previousMonthSavings / previousMonthIncome).toFloat() else 0f

        val savingsChange = currentMonthSavings - previousMonthSavings
        val savingsChangePercentage = if (previousMonthSavings != 0.0) {
            (savingsChange / kotlin.math.abs(previousMonthSavings)).toFloat() * 100f
        } else if (currentMonthSavings > 0) {
            100f
        } else {
            0f
        }
        val isPositiveTrend = currentMonthSavings >= previousMonthSavings

        val savingsTrend = SavingsTrendData(
            currentMonthSavings = currentMonthSavings,
            previousMonthSavings = previousMonthSavings,
            savingsRateCurrent = savingsRateCurrent,
            savingsRatePrevious = savingsRatePrevious,
            savingsChangePercentage = savingsChangePercentage,
            isPositiveTrend = isPositiveTrend
        )

        val monthlyHistory = historyRanges.map { range ->
            MonthlyExpenseData(
                monthName = range.name,
                year = range.year,
                totalExpense = range.expense,
                totalIncome = range.income
            )
        }

        val monthlyAggregates = historyRanges.mapIndexed { idx, range ->
            com.example.domain.model.MonthlySpendingAggregate(
                monthIndex = idx,
                monthName = range.name,
                year = range.year,
                totalExpense = range.expense,
                totalIncome = range.income,
                transactionCount = 0
            )
        }

        // Promedio de gastos diarios
        val averageDailySpendingCurrent = if (elapsedDaysCurrent > 0) currentMonthExpense / elapsedDaysCurrent else 0.0
        val averageDailySpendingPrevious = if (elapsedDaysPrevious > 0) previousMonthExpense / elapsedDaysPrevious else 0.0

        val hasTransactions = transactions.isNotEmpty()

        val allCategoryIds = (currentCategoryExpenseMap.keys + previousCategoryExpenseMap.keys)
        val categoryVelocities = allCategoryIds.mapNotNull { catId ->
            val category = categoryMapById[catId] ?: return@mapNotNull null
            val curAmt = currentCategoryExpenseMap[catId] ?: 0.0
            val prevAmt = previousCategoryExpenseMap[catId] ?: 0.0
            val diff = curAmt - prevAmt
            val pctChange = if (prevAmt > 0) {
                ((diff / prevAmt) * 100).toFloat()
            } else if (curAmt > 0) {
                100f
            } else {
                0f
            }
            CategorySpendingVelocity(
                categoryId = catId,
                categoryName = category.name,
                iconKey = category.iconKey,
                currentMonthAmount = curAmt,
                previousMonthAmount = prevAmt,
                percentageChange = pctChange,
                isIncrease = diff > 0
            )
        }.sortedByDescending { it.currentMonthAmount }

        // Cálculo de Regla Presupuestaria 50/30/20 (Necesidades, Deseos, Ahorro)
        var needsTotal = 0.0
        var wantsTotal = 0.0

        for ((catId, amount) in currentCategoryExpenseMap) {
            val catName = categoryMapById[catId]?.name?.lowercase() ?: ""
            val isNeed = catName.contains("comida") ||
                    catName.contains("supermercado") ||
                    catName.contains("transporte") ||
                    catName.contains("combustible") ||
                    catName.contains("gasolina") ||
                    catName.contains("servicio") ||
                    catName.contains("luz") ||
                    catName.contains("agua") ||
                    catName.contains("internet") ||
                    catName.contains("salud") ||
                    catName.contains("medicina") ||
                    catName.contains("farmacia") ||
                    catName.contains("educación") ||
                    catName.contains("colegio") ||
                    catName.contains("universidad") ||
                    catName.contains("vivienda") ||
                    catName.contains("alquiler") ||
                    catName.contains("renta")

            if (isNeed) {
                needsTotal += amount
            } else {
                wantsTotal += amount
            }
        }

        val netSavings = (currentMonthIncome - currentMonthExpense).coerceAtLeast(0.0)
        val incomeReference = if (currentMonthIncome > 0) currentMonthIncome else (needsTotal + wantsTotal).coerceAtLeast(1.0)

        val needsPct = ((needsTotal / incomeReference) * 100).toFloat().coerceIn(0f, 100f)
        val wantsPct = ((wantsTotal / incomeReference) * 100).toFloat().coerceIn(0f, 100f)
        val savingsPct = if (currentMonthIncome > 0) {
            ((netSavings / currentMonthIncome) * 100).toFloat().coerceIn(0f, 100f)
        } else {
            0f
        }

        val needsExceeded = needsPct > 55f
        val wantsExceeded = wantsPct > 35f
        val savingsOnTrack = savingsPct >= 20f

        val ruleStatusSummary = when {
            needsExceeded && wantsExceeded -> "Tus necesidades y deseos superan los límites del 50/30/20. Es aconsejable ajustar gastos variables."
            needsExceeded -> "Tus gastos básicos consumen más del 50% de tus ingresos. Evalúa recortar servicios o gastos fijos."
            wantsExceeded -> "Tus consumos de ocio y deseos superan el 30% recomendado. Modera compras discrecionales."
            savingsOnTrack -> "¡Excelente equilibrio financiero! Cumples la regla 50/30/20 y ahorras al menos el 20% de tus ingresos."
            else -> "Buen control de gastos generales. Incrementa ligeramente tu aporte a ahorros para alcanzar la meta del 20%."
        }

        val budgetRule503020 = BudgetRule503020(
            needsAmount = needsTotal,
            needsPercentage = needsPct,
            wantsAmount = wantsTotal,
            wantsPercentage = wantsPct,
            savingsAmount = netSavings,
            savingsPercentage = savingsPct,
            statusSummary = ruleStatusSummary,
            isBalanced = !needsExceeded && !wantsExceeded,
            needsExceeded = needsExceeded,
            wantsExceeded = wantsExceeded,
            savingsOnTrack = savingsOnTrack
        )

        AnalysisUiState.Success(
            categoryExpenses = categoryShareList,
            currentMonthIncome = currentMonthIncome,
            currentMonthExpense = currentMonthExpense,
            previousMonthIncome = previousMonthIncome,
            previousMonthExpense = previousMonthExpense,
            monthlyHistory = monthlyHistory,
            monthlyAggregates = monthlyAggregates,
            savingsTrend = savingsTrend,
            topExpenseCategory = topCategory,
            averageDailySpendingCurrent = averageDailySpendingCurrent,
            averageDailySpendingPrevious = averageDailySpendingPrevious,
            elapsedDaysCurrent = elapsedDaysCurrent,
            elapsedDaysPrevious = elapsedDaysPrevious,
            hasTransactions = hasTransactions,
            categoryVelocities = categoryVelocities,
            budgetRule503020 = budgetRule503020
        )
    }
    .flowOn(Dispatchers.Default)
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AnalysisUiState.Loading
    )
}
