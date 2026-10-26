package com.example.presentation.calculator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.Account
import com.example.domain.model.Transaction
import com.example.domain.model.TransactionType
import com.example.domain.usecase.account.GetAccountsUseCase
import com.example.domain.usecase.transaction.GetTransactionsUseCase
import com.example.presentation.analysis.ExpenseTypeClassifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.pow

data class AmortizationRow(
    val month: Int,
    val payment: Double,
    val principal: Double,
    val interest: Double,
    val remainingBalance: Double
)

data class YearlyGrowthPoint(
    val year: Int,
    val deposits: Double,
    val interestEarned: Double,
    val totalBalance: Double
)

enum class SafetyStatus {
    CRITICAL,      // < 1 mes
    BASIC,         // 1 - 3 meses
    RECOMMENDED,   // 3 - 6 meses
    OPTIMAL        // > 6 meses
}

data class CalculatorUiState(
    val selectedTab: Int = 0,
    // 50/30/20 Rule
    val incomeInput: String = "25000",
    val needs: Double = 12500.0,
    val wants: Double = 7500.0,
    val savings: Double = 5000.0,
    val actualIncome: Double = 0.0,
    val actualNeedsSpent: Double = 0.0,
    val actualWantsSpent: Double = 0.0,
    val actualSavingsSpent: Double = 0.0,
    val hasRealData: Boolean = false,
    val compareWithRealData: Boolean = false,

    // Compound Interest
    val initialDepositInput: String = "5000",
    val monthlyContributionInput: String = "1000",
    val annualRateInput: String = "8",
    val yearsInput: String = "10",
    val futureValue: Double = 0.0,
    val totalDeposits: Double = 0.0,
    val interestEarned: Double = 0.0,
    val yearlyBreakdown: List<YearlyGrowthPoint> = emptyList(),

    // Debt Payoff / Loan Amortization
    val debtAmountInput: String = "10000",
    val debtInterestRateInput: String = "16",
    val debtTermMonthsInput: String = "24",
    val extraMonthlyPaymentInput: String = "50",
    val regularMonthlyPayment: Double = 0.0,
    val totalInterestRegular: Double = 0.0,
    val totalPaidRegular: Double = 0.0,
    val acceleratedMonths: Int = 0,
    val totalInterestAccelerated: Double = 0.0,
    val interestSavings: Double = 0.0,
    val monthsSaved: Int = 0,
    val sampleAmortization: List<AmortizationRow> = emptyList(),

    // Emergency Fund Simulator
    val emergencyTargetMonths: Int = 6,
    val emergencyMonthlyExpenseInput: String = "15000",
    val emergencyCurrentSavingsInput: String = "30000",
    val emergencyMonthlySavingCapacityInput: String = "3000",
    val targetEmergencyFund: Double = 90000.0,
    val emergencyFundGap: Double = 60000.0,
    val runwayMonths: Double = 2.0,
    val emergencyFundProgress: Float = 0.33f,
    val monthsToCompleteEmergencyFund: Int = 20,
    val emergencySafetyStatus: SafetyStatus = SafetyStatus.BASIC,
    val actualTotalLiquidSavings: Double = 0.0,
    val actualMonthlyEssentialExpenses: Double = 0.0,
    val hasRealEmergencyData: Boolean = false
)

/**
 * ViewModel para gestionar el estado y la lógica de cálculos financieros en [CalculatorScreen].
 * Ofrece la regla 50/30/20 con sincronización de datos reales, interés compuesto, planificador de deudas
 * y simulador de Fondo de Emergencia con cálculo de pista financiera (runway).
 */
class CalculatorViewModel(
    private val getTransactionsUseCase: GetTransactionsUseCase? = null,
    private val getAccountsUseCase: GetAccountsUseCase? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(CalculatorUiState())
    val uiState: StateFlow<CalculatorUiState> = _uiState.asStateFlow()

    private var allTransactions: List<Transaction> = emptyList()
    private var allAccounts: List<Account> = emptyList()

    init {
        recalculate503020()
        recalculateCompoundInterest()
        recalculateDebtPayoff()
        recalculateEmergencyFund()

        viewModelScope.launch {
            getTransactionsUseCase?.invoke()?.collect { transactions ->
                allTransactions = transactions
                processRealFinancialData(transactions)
            }
        }

        viewModelScope.launch {
            getAccountsUseCase?.invoke()?.collect { accounts ->
                allAccounts = accounts
                processRealAccountsData(accounts)
            }
        }
    }

    private fun processRealFinancialData(transactions: List<Transaction>) {
        if (transactions.isEmpty()) return

        val cal = Calendar.getInstance()
        val currentMonth = cal.get(Calendar.MONTH)
        val currentYear = cal.get(Calendar.YEAR)

        val monthTransactions = transactions.filter {
            val tCal = Calendar.getInstance().apply { time = it.date }
            tCal.get(Calendar.MONTH) == currentMonth && tCal.get(Calendar.YEAR) == currentYear
        }

        val income = monthTransactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
        val expenses = monthTransactions.filter { it.type == TransactionType.EXPENSE }

        var actualNeeds = 0.0
        var actualWants = 0.0
        var actualSavings = 0.0

        for (tx in expenses) {
            when {
                ExpenseTypeClassifier.isNeedsCategory(tx.category.name, tx.category.iconKey) -> {
                    actualNeeds += tx.amount
                }
                ExpenseTypeClassifier.isWantsCategory(tx.category.name, tx.category.iconKey) -> {
                    actualWants += tx.amount
                }
                else -> {
                    actualSavings += tx.amount
                }
            }
        }

        val totalExpenses = expenses.sumOf { it.amount }
        val savingsCapacity = (income - totalExpenses).coerceAtLeast(0.0)

        _uiState.update {
            it.copy(
                actualIncome = income,
                actualNeedsSpent = actualNeeds,
                actualWantsSpent = actualWants,
                actualSavingsSpent = actualSavings,
                actualMonthlyEssentialExpenses = if (actualNeeds > 0) actualNeeds else totalExpenses,
                hasRealData = income > 0 || expenses.isNotEmpty(),
                hasRealEmergencyData = it.actualTotalLiquidSavings > 0 || totalExpenses > 0
            )
        }
    }

    private fun processRealAccountsData(accounts: List<Account>) {
        val liquidSavings = accounts.filter { it.type != "CREDIT" }.sumOf { it.currentBalance }.coerceAtLeast(0.0)
        _uiState.update {
            it.copy(
                actualTotalLiquidSavings = liquidSavings,
                hasRealEmergencyData = liquidSavings > 0 || it.actualMonthlyEssentialExpenses > 0
            )
        }
    }

    fun useRealIncomeData() {
        val income = _uiState.value.actualIncome
        if (income > 0) {
            _uiState.update {
                it.copy(
                    incomeInput = String.format(Locale.US, "%.0f", income),
                    compareWithRealData = true
                )
            }
            recalculate503020()
        }
    }

    fun toggleCompareWithRealData() {
        _uiState.update { it.copy(compareWithRealData = !it.compareWithRealData) }
    }

    fun setTab(tab: Int) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun setIncomeInput(input: String) {
        if (input.all { it.isDigit() || it == '.' }) {
            _uiState.update { it.copy(incomeInput = input) }
            recalculate503020()
        }
    }

    fun setInitialDeposit(input: String) {
        if (input.all { it.isDigit() || it == '.' }) {
            _uiState.update { it.copy(initialDepositInput = input) }
            recalculateCompoundInterest()
        }
    }

    fun setMonthlyContribution(input: String) {
        if (input.all { it.isDigit() || it == '.' }) {
            _uiState.update { it.copy(monthlyContributionInput = input) }
            recalculateCompoundInterest()
        }
    }

    fun setAnnualRate(input: String) {
        if (input.all { it.isDigit() || it == '.' }) {
            _uiState.update { it.copy(annualRateInput = input) }
            recalculateCompoundInterest()
        }
    }

    fun setYears(input: String) {
        if (input.all { it.isDigit() }) {
            _uiState.update { it.copy(yearsInput = input) }
            recalculateCompoundInterest()
        }
    }

    fun setDebtAmount(input: String) {
        if (input.all { it.isDigit() || it == '.' }) {
            _uiState.update { it.copy(debtAmountInput = input) }
            recalculateDebtPayoff()
        }
    }

    fun setDebtInterestRate(input: String) {
        if (input.all { it.isDigit() || it == '.' }) {
            _uiState.update { it.copy(debtInterestRateInput = input) }
            recalculateDebtPayoff()
        }
    }

    fun setDebtTermMonths(input: String) {
        if (input.all { it.isDigit() }) {
            _uiState.update { it.copy(debtTermMonthsInput = input) }
            recalculateDebtPayoff()
        }
    }

    fun setExtraMonthlyPayment(input: String) {
        if (input.all { it.isDigit() || it == '.' }) {
            _uiState.update { it.copy(extraMonthlyPaymentInput = input) }
            recalculateDebtPayoff()
        }
    }

    private fun recalculate503020() {
        val income = _uiState.value.incomeInput.toDoubleOrNull() ?: 0.0
        _uiState.update {
            it.copy(
                needs = income * 0.50,
                wants = income * 0.30,
                savings = income * 0.20
            )
        }
    }

    private fun recalculateCompoundInterest() {
        val initialDeposit = _uiState.value.initialDepositInput.toDoubleOrNull() ?: 0.0
        val monthlyContribution = _uiState.value.monthlyContributionInput.toDoubleOrNull() ?: 0.0
        val annualRate = (_uiState.value.annualRateInput.toDoubleOrNull() ?: 0.0) / 100.0
        val years = _uiState.value.yearsInput.toIntOrNull() ?: 0

        if (years <= 0) {
            _uiState.update {
                it.copy(
                    futureValue = initialDeposit,
                    totalDeposits = initialDeposit,
                    interestEarned = 0.0,
                    yearlyBreakdown = emptyList()
                )
            }
        } else {
            val n = 12
            val totalMonths = n * years
            val monthlyRate = annualRate / n
            val totalDeposits = initialDeposit + (monthlyContribution * totalMonths)
            val fvPrincipal = initialDeposit * (1 + monthlyRate).pow(totalMonths)
            val fvAnnuity = if (monthlyRate > 0) {
                monthlyContribution * (((1 + monthlyRate).pow(totalMonths) - 1) / monthlyRate)
            } else {
                monthlyContribution * totalMonths
            }
            val futureValue = fvPrincipal + fvAnnuity
            val interestEarned = maxOf(0.0, futureValue - totalDeposits)

            // Desglose de crecimiento por años
            val breakdown = mutableListOf<YearlyGrowthPoint>()
            val yearsToSample = listOf(1, 2, 3, 5, 10, 15, 20, 25, 30).filter { it <= years }
            val sampleSet = (yearsToSample + years).distinct().sorted()

            sampleSet.forEach { y ->
                val mCount = y * 12
                val dep = initialDeposit + (monthlyContribution * mCount)
                val fvP = initialDeposit * (1 + monthlyRate).pow(mCount)
                val fvA = if (monthlyRate > 0) {
                    monthlyContribution * (((1 + monthlyRate).pow(mCount) - 1) / monthlyRate)
                } else {
                    monthlyContribution * mCount
                }
                val tot = fvP + fvA
                val intEarned = (tot - dep).coerceAtLeast(0.0)
                breakdown.add(
                    YearlyGrowthPoint(
                        year = y,
                        deposits = dep,
                        interestEarned = intEarned,
                        totalBalance = tot
                    )
                )
            }

            _uiState.update {
                it.copy(
                    futureValue = futureValue,
                    totalDeposits = totalDeposits,
                    interestEarned = interestEarned,
                    yearlyBreakdown = breakdown
                )
            }
        }
    }

    private fun recalculateDebtPayoff() {
        val debt = _uiState.value.debtAmountInput.toDoubleOrNull() ?: 0.0
        val annualRate = (_uiState.value.debtInterestRateInput.toDoubleOrNull() ?: 0.0) / 100.0
        val termMonths = _uiState.value.debtTermMonthsInput.toIntOrNull() ?: 0
        val extraPayment = _uiState.value.extraMonthlyPaymentInput.toDoubleOrNull() ?: 0.0

        if (debt <= 0.0 || termMonths <= 0) {
            _uiState.update {
                it.copy(
                    regularMonthlyPayment = 0.0,
                    totalInterestRegular = 0.0,
                    totalPaidRegular = 0.0,
                    acceleratedMonths = 0,
                    totalInterestAccelerated = 0.0,
                    interestSavings = 0.0,
                    monthsSaved = 0,
                    sampleAmortization = emptyList()
                )
            }
            return
        }

        val monthlyRate = annualRate / 12.0
        val regularMonthlyPayment = if (monthlyRate > 0) {
            val factor = (1 + monthlyRate).pow(termMonths)
            debt * (monthlyRate * factor) / (factor - 1)
        } else {
            debt / termMonths
        }

        val totalPaidRegular = regularMonthlyPayment * termMonths
        val totalInterestRegular = (totalPaidRegular - debt).coerceAtLeast(0.0)

        // Simular con aporte extra voluntario
        val acceleratedMonthlyPayment = regularMonthlyPayment + extraPayment
        var balance = debt
        var currentMonth = 0
        var totalInterestAccelerated = 0.0
        val amortization = mutableListOf<AmortizationRow>()

        while (balance > 0.01 && currentMonth < 600) {
            currentMonth++
            val interestMonth = balance * monthlyRate
            totalInterestAccelerated += interestMonth
            val principalMonth = (acceleratedMonthlyPayment - interestMonth).coerceAtMost(balance).coerceAtLeast(0.0)
            balance = (balance - principalMonth).coerceAtLeast(0.0)

            if (currentMonth <= 6 || balance <= 0.01) {
                amortization.add(
                    AmortizationRow(
                        month = currentMonth,
                        payment = principalMonth + interestMonth,
                        principal = principalMonth,
                        interest = interestMonth,
                        remainingBalance = balance
                    )
                )
            }
        }

        val acceleratedMonths = currentMonth
        val monthsSaved = (termMonths - acceleratedMonths).coerceAtLeast(0)
        val interestSavings = (totalInterestRegular - totalInterestAccelerated).coerceAtLeast(0.0)

        _uiState.update {
            it.copy(
                regularMonthlyPayment = regularMonthlyPayment,
                totalInterestRegular = totalInterestRegular,
                totalPaidRegular = totalPaidRegular,
                acceleratedMonths = acceleratedMonths,
                totalInterestAccelerated = totalInterestAccelerated,
                interestSavings = interestSavings,
                monthsSaved = monthsSaved,
                sampleAmortization = amortization
            )
        }
    }

    fun setEmergencyTargetMonths(months: Int) {
        if (months > 0) {
            _uiState.update { it.copy(emergencyTargetMonths = months) }
            recalculateEmergencyFund()
        }
    }

    fun setEmergencyMonthlyExpense(input: String) {
        if (input.all { it.isDigit() || it == '.' }) {
            _uiState.update { it.copy(emergencyMonthlyExpenseInput = input) }
            recalculateEmergencyFund()
        }
    }

    fun setEmergencyCurrentSavings(input: String) {
        if (input.all { it.isDigit() || it == '.' }) {
            _uiState.update { it.copy(emergencyCurrentSavingsInput = input) }
            recalculateEmergencyFund()
        }
    }

    fun setEmergencyMonthlySavingCapacity(input: String) {
        if (input.all { it.isDigit() || it == '.' }) {
            _uiState.update { it.copy(emergencyMonthlySavingCapacityInput = input) }
            recalculateEmergencyFund()
        }
    }

    fun useRealEmergencyData() {
        val expense = _uiState.value.actualMonthlyEssentialExpenses
        val savings = _uiState.value.actualTotalLiquidSavings
        val income = _uiState.value.actualIncome
        val capacity = if (income > expense) income - expense else 0.0

        _uiState.update {
            it.copy(
                emergencyMonthlyExpenseInput = if (expense > 0) String.format(Locale.US, "%.0f", expense) else it.emergencyMonthlyExpenseInput,
                emergencyCurrentSavingsInput = if (savings > 0) String.format(Locale.US, "%.0f", savings) else it.emergencyCurrentSavingsInput,
                emergencyMonthlySavingCapacityInput = if (capacity > 0) String.format(Locale.US, "%.0f", capacity) else it.emergencyMonthlySavingCapacityInput
            )
        }
        recalculateEmergencyFund()
    }

    private fun recalculateEmergencyFund() {
        val targetMonths = _uiState.value.emergencyTargetMonths
        val monthlyExpense = _uiState.value.emergencyMonthlyExpenseInput.toDoubleOrNull() ?: 0.0
        val currentSavings = _uiState.value.emergencyCurrentSavingsInput.toDoubleOrNull() ?: 0.0
        val monthlySavingCapacity = _uiState.value.emergencyMonthlySavingCapacityInput.toDoubleOrNull() ?: 0.0

        val targetFund = monthlyExpense * targetMonths
        val gap = (targetFund - currentSavings).coerceAtLeast(0.0)
        val runway = if (monthlyExpense > 0.0) currentSavings / monthlyExpense else 0.0
        val progress = if (targetFund > 0.0) (currentSavings / targetFund).toFloat().coerceIn(0f, 1f) else 0f
        val monthsToComplete = if (gap > 0.0 && monthlySavingCapacity > 0.0) ceil(gap / monthlySavingCapacity).toInt() else 0

        val status = when {
            runway < 1.0 -> SafetyStatus.CRITICAL
            runway < 3.0 -> SafetyStatus.BASIC
            runway < 6.0 -> SafetyStatus.RECOMMENDED
            else -> SafetyStatus.OPTIMAL
        }

        _uiState.update {
            it.copy(
                targetEmergencyFund = targetFund,
                emergencyFundGap = gap,
                runwayMonths = runway,
                emergencyFundProgress = progress,
                monthsToCompleteEmergencyFund = monthsToComplete,
                emergencySafetyStatus = status
            )
        }
    }
}
