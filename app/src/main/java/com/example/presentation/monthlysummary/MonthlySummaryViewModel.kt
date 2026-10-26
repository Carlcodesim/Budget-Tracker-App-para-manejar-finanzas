package com.example.presentation.monthlysummary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.Category
import com.example.domain.model.Transaction
import com.example.domain.model.TransactionType
import com.example.domain.repository.TransactionRepository
import com.example.util.BudgetCycleManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * ViewModel encargado de procesar y agregar los datos de gasto mensual
 * siguiendo el patrón MVVM + Repository, adaptándose reactivamente al ciclo
 * de corte financiero configurado (ej. quincenal o fin de mes).
 */
class MonthlySummaryViewModel(
    private val transactionRepository: TransactionRepository
) : ViewModel() {

    private val initialCycle = BudgetCycleManager.getCurrentCycleRange()
    private val _selectedMonthYear = MutableStateFlow(
        Pair(initialCycle.year, initialCycle.month)
    )

    private val monthNamesSpanish = listOf(
        "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
        "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"
    )

    private val dayOfWeekNames = listOf("Dom", "Lun", "Mar", "Mié", "Jue", "Vie", "Sáb")
    private val sdfDay = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val sdfShort = SimpleDateFormat("d MMM", Locale.forLanguageTag("es-ES"))

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<MonthlySummaryUiState> = combine(
        _selectedMonthYear,
        BudgetCycleManager.startDayFlow
    ) { (year, month), startDay ->
        Triple(year, month, startDay)
    }.flatMapLatest { (year, month, startDay) ->
        // Calcular inicio y fin del ciclo financiero seleccionado
        val cycleRange = BudgetCycleManager.getCycleRange(year, month, startDay)
        val startDate = cycleRange.startDateMillis
        val endDate = cycleRange.endDateMillis
        val daysInCycle = cycleRange.totalDaysInCycle
        val elapsedDays = cycleRange.elapsedDays
        val isCurrentCycle = cycleRange.isCurrentCycle

        // Ciclo anterior para cálculos comparativos
        val prevCycleRange = BudgetCycleManager.getPreviousCycleRange(cycleRange, startDay)
        val prevStartDate = prevCycleRange.startDateMillis
        val prevEndDate = prevCycleRange.endDateMillis

        // Combinar flujos reactivos desde el repositorio
        combine(
            transactionRepository.getTransactionsByDateRange(startDate, endDate),
            transactionRepository.getTotalExpenseByDateRange(prevStartDate, prevEndDate)
        ) { transactions, prevMonthExpenseTotal ->
            val expenseTransactions = transactions.filter { it.type == TransactionType.EXPENSE }
            val incomeTransactions = transactions.filter { it.type == TransactionType.INCOME }

            val totalExpense = expenseTransactions.sumOf { it.amount }
            val totalIncome = incomeTransactions.sumOf { it.amount }
            val netSavings = totalIncome - totalExpense
            val transactionCount = expenseTransactions.size

            val dailyAverage = if (elapsedDays > 0) totalExpense / elapsedDays else 0.0
            val projectedMonthEndExpense = if (isCurrentCycle && elapsedDays > 0) {
                dailyAverage * daysInCycle
            } else {
                totalExpense
            }

            // Comparación con el ciclo anterior
            val prevExpense = prevMonthExpenseTotal ?: 0.0
            val expenseDeltaPercentage = if (prevExpense > 0.0) {
                ((totalExpense - prevExpense) / prevExpense) * 100.0
            } else if (totalExpense > 0.0) {
                100.0
            } else {
                0.0
            }
            val isExpenseHigher = totalExpense > prevExpense

            // Agrupación por categoría
            val categoryBreakdown = expenseTransactions
                .groupBy { it.category.id }
                .map { (catId, txList) ->
                    val cat = txList.firstOrNull()?.category
                    val catName = cat?.name ?: "Otros"
                    val iconKey = cat?.iconKey ?: "category"
                    val sum = txList.sumOf { it.amount }
                    val count = txList.size
                    val pct = if (totalExpense > 0.0) (sum / totalExpense).toFloat() else 0f
                    val catDailyAvg = if (elapsedDays > 0) sum / elapsedDays else 0.0
                    CategoryMonthlyShare(
                        categoryId = catId,
                        categoryName = catName,
                        iconKey = iconKey,
                        totalAmount = sum,
                        transactionCount = count,
                        percentageOfTotal = pct,
                        dailyAverageForCategory = catDailyAvg
                    )
                }
                .sortedByDescending { it.totalAmount }

            // Agrupación diaria por fecha dentro del ciclo
            val dailyMap = mutableMapOf<String, Pair<Double, Int>>() // "yyyy-MM-dd" -> (sum, count)
            for (tx in expenseTransactions) {
                val dayKey = sdfDay.format(tx.date)
                val current = dailyMap[dayKey] ?: Pair(0.0, 0)
                dailyMap[dayKey] = Pair(current.first + tx.amount, current.second + 1)
            }

            var peakDay: Int? = null
            var peakAmount = 0.0

            val dailyHistory = (0 until daysInCycle).map { dayOffset ->
                val dayCal = Calendar.getInstance().apply {
                    timeInMillis = startDate
                    add(Calendar.DAY_OF_MONTH, dayOffset)
                }
                val dayKey = sdfDay.format(dayCal.time)
                val dayNum = dayCal.get(Calendar.DAY_OF_MONTH)
                val dow = dayOfWeekNames[dayCal.get(Calendar.DAY_OF_WEEK) - 1]
                val dayData = dailyMap[dayKey] ?: Pair(0.0, 0)
                val amount = dayData.first
                val count = dayData.second

                if (amount > peakAmount) {
                    peakAmount = amount
                    peakDay = dayNum
                }

                DailySpendingItem(
                    day = dayNum,
                    dayOfWeek = dow,
                    amount = amount,
                    transactionCount = count,
                    isPeakDay = false
                )
            }.map { item ->
                item.copy(isPeakDay = peakDay != null && item.day == peakDay && item.amount > 0.0)
            }

            // Agrupación semanal (bloques de 7 días dentro del ciclo)
            val weeklyHistory = mutableListOf<WeeklySpendingItem>()
            var weekIndex = 1
            var dayCursor = 0
            while (dayCursor < daysInCycle) {
                val weekEndOffset = minOf(dayCursor + 6, daysInCycle - 1)
                val startCal = Calendar.getInstance().apply {
                    timeInMillis = startDate
                    add(Calendar.DAY_OF_MONTH, dayCursor)
                }
                val endCal = Calendar.getInstance().apply {
                    timeInMillis = startDate
                    add(Calendar.DAY_OF_MONTH, weekEndOffset)
                }

                var weekSum = 0.0
                var weekCount = 0
                for (offset in dayCursor..weekEndOffset) {
                    val cCal = Calendar.getInstance().apply {
                        timeInMillis = startDate
                        add(Calendar.DAY_OF_MONTH, offset)
                    }
                    val k = sdfDay.format(cCal.time)
                    val dData = dailyMap[k]
                    if (dData != null) {
                        weekSum += dData.first
                        weekCount += dData.second
                    }
                }

                val pct = if (totalExpense > 0.0) (weekSum / totalExpense).toFloat() else 0f
                weeklyHistory.add(
                    WeeklySpendingItem(
                        weekNumber = weekIndex,
                        weekLabel = "Semana $weekIndex",
                        dateRangeLabel = "${sdfShort.format(startCal.time)} - ${sdfShort.format(endCal.time)}",
                        totalAmount = weekSum,
                        transactionCount = weekCount,
                        percentageOfTotal = pct
                    )
                )

                dayCursor += 7
                weekIndex++
            }

            // Desglose Quincenal (armonización de sueldos de 15 y 30)
            val midpointDays = daysInCycle / 2
            val midCal = Calendar.getInstance().apply {
                timeInMillis = startDate
                add(Calendar.DAY_OF_YEAR, midpointDays - 1)
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }
            val midMillis = midCal.timeInMillis

            // Quincena 1
            val q1Txs = transactions.filter { it.date.time in startDate..midMillis }
            val q1Income = q1Txs.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
            val q1Expense = q1Txs.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
            val q1RangeLabel = "${sdfShort.format(Date(startDate))} - ${sdfShort.format(midCal.time)}"

            // Quincena 2
            val q2Txs = transactions.filter { it.date.time in (midMillis + 1)..endDate }
            val q2Income = q2Txs.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
            val q2Expense = q2Txs.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
            val q2StartCal = Calendar.getInstance().apply {
                timeInMillis = midMillis
                add(Calendar.SECOND, 1)
            }
            val q2RangeLabel = "${sdfShort.format(q2StartCal.time)} - ${sdfShort.format(Date(endDate))}"

            val fortnightsList = listOf(
                FortnightSpendingItem(
                    fortnightNumber = 1,
                    title = "1ra Quincena",
                    dateRangeLabel = q1RangeLabel,
                    incomeAmount = q1Income,
                    expenseAmount = q1Expense,
                    netBalance = q1Income - q1Expense,
                    transactionCount = q1Txs.size
                ),
                FortnightSpendingItem(
                    fortnightNumber = 2,
                    title = "2da Quincena",
                    dateRangeLabel = q2RangeLabel,
                    incomeAmount = q2Income,
                    expenseAmount = q2Expense,
                    netBalance = q2Income - q2Expense,
                    transactionCount = q2Txs.size
                )
            )

            // Gastos más grandes del ciclo
            val topExpenses = expenseTransactions
                .sortedByDescending { it.amount }
                .take(6)

            // Promedio por transacción
            val avgPerTx = if (transactionCount > 0) totalExpense / transactionCount else 0.0

            // Tasa de ahorro
            val savingsRate = if (totalIncome > 0.0) {
                ((totalIncome - totalExpense) / totalIncome) * 100.0
            } else {
                0.0
            }

            // Velocidad de gasto
            val spendingVelocity = when {
                totalIncome > 0 && totalExpense > totalIncome * 0.9 -> SpendingVelocityStatus.ACCELERATED
                totalIncome > 0 && totalExpense > totalIncome * 0.6 -> SpendingVelocityStatus.MODERATE
                dailyAverage > 2500.0 -> SpendingVelocityStatus.ACCELERATED
                dailyAverage > 1000.0 -> SpendingVelocityStatus.MODERATE
                else -> SpendingVelocityStatus.OPTIMAL
            }

            val summary = MonthlySpendingSummary(
                year = year,
                month = month,
                monthName = cycleRange.cycleName,
                totalExpense = totalExpense,
                totalIncome = totalIncome,
                netSavings = netSavings,
                transactionCount = transactionCount,
                dailyAverage = dailyAverage,
                averagePerTransaction = avgPerTx,
                savingsRatePercentage = savingsRate,
                daysInMonth = daysInCycle,
                elapsedDays = elapsedDays,
                projectedMonthEndExpense = projectedMonthEndExpense,
                peakExpenseDay = peakDay,
                peakExpenseAmount = peakAmount,
                previousMonthExpense = prevExpense,
                expenseDeltaPercentage = expenseDeltaPercentage,
                isExpenseHigherThanPrevious = isExpenseHigher,
                categoryBreakdown = categoryBreakdown,
                dailySpendingHistory = dailyHistory,
                weeklySpendingHistory = weeklyHistory,
                fortnights = fortnightsList,
                topExpenses = topExpenses,
                allMonthExpenses = expenseTransactions,
                spendingVelocity = spendingVelocity,
                hasExpenses = expenseTransactions.isNotEmpty(),
                dateRangeLabel = cycleRange.dateRangeLabel,
                isCustomCycle = cycleRange.isCustomCycle
            )

            MonthlySummaryUiState.Success(
                summary = summary,
                selectedYear = year,
                selectedMonth = month
            )
        }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = MonthlySummaryUiState.Loading
        )

    fun previousMonth() {
        _selectedMonthYear.update { (year, month) ->
            if (month == 0) {
                Pair(year - 1, 11)
            } else {
                Pair(year, month - 1)
            }
        }
    }

    fun nextMonth() {
        _selectedMonthYear.update { (year, month) ->
            if (month == 11) {
                Pair(year + 1, 0)
            } else {
                Pair(year, month + 1)
            }
        }
    }

    fun setMonthYear(year: Int, month: Int) {
        _selectedMonthYear.update { Pair(year, month) }
    }

    fun resetToCurrentMonth() {
        val cur = BudgetCycleManager.getCurrentCycleRange()
        _selectedMonthYear.update { Pair(cur.year, cur.month) }
    }
}
