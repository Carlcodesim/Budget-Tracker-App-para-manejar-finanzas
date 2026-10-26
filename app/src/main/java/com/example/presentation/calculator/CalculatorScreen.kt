package com.example.presentation.calculator

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.presentation.common.util.CurrencyFormatter
import com.example.ui.theme.BluePrimary
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.IncomeGreen
import com.example.ui.theme.NeutralOutline
import com.example.ui.theme.NeutralVariant
import com.example.ui.theme.OtherCategoryBrown
import com.example.ui.theme.SoftGreyBorder
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.PriceCheck
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.TrendingDown
import java.util.Locale
import kotlin.math.pow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculatorScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: CalculatorViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val tabs = listOf("Regla 50/30/20", "Interés Compuesto", "Plan de Deudas", "Fondo Emergencia")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Calculadora Financiera", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("calculator_back_to_home_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver al inicio"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Tab Selector Scrollable para que no se aprieten las 4 pestañas
            ScrollableTabRow(
                selectedTabIndex = uiState.selectedTab,
                edgePadding = 16.dp,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = BluePrimary,
                indicator = { tabPositions ->
                    if (uiState.selectedTab < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[uiState.selectedTab]),
                            color = BluePrimary
                        )
                    }
                }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = uiState.selectedTab == index,
                        onClick = { viewModel.setTab(index) },
                        text = {
                            Text(
                                text = title,
                                fontWeight = if (uiState.selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                color = if (uiState.selectedTab == index) BluePrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                when (uiState.selectedTab) {
                    0 -> {
                        Budget503020Calculator(
                            incomeInput = uiState.incomeInput,
                            needs = uiState.needs,
                            wants = uiState.wants,
                            savings = uiState.savings,
                            actualIncome = uiState.actualIncome,
                            actualNeedsSpent = uiState.actualNeedsSpent,
                            actualWantsSpent = uiState.actualWantsSpent,
                            actualSavingsSpent = uiState.actualSavingsSpent,
                            hasRealData = uiState.hasRealData,
                            compareWithRealData = uiState.compareWithRealData,
                            onIncomeChange = viewModel::setIncomeInput,
                            onUseRealData = viewModel::useRealIncomeData,
                            onToggleCompare = viewModel::toggleCompareWithRealData
                        )
                    }
                    1 -> {
                        CompoundInterestCalculator(
                            initialDepositInput = uiState.initialDepositInput,
                            monthlyContributionInput = uiState.monthlyContributionInput,
                            annualRateInput = uiState.annualRateInput,
                            yearsInput = uiState.yearsInput,
                            futureValue = uiState.futureValue,
                            totalDeposits = uiState.totalDeposits,
                            interestEarned = uiState.interestEarned,
                            yearlyBreakdown = uiState.yearlyBreakdown,
                            onInitialDepositChange = viewModel::setInitialDeposit,
                            onMonthlyContributionChange = viewModel::setMonthlyContribution,
                            onAnnualRateChange = viewModel::setAnnualRate,
                            onYearsChange = viewModel::setYears
                        )
                    }
                    2 -> {
                        DebtPayoffCalculator(
                            debtAmountInput = uiState.debtAmountInput,
                            debtInterestRateInput = uiState.debtInterestRateInput,
                            debtTermMonthsInput = uiState.debtTermMonthsInput,
                            extraMonthlyPaymentInput = uiState.extraMonthlyPaymentInput,
                            regularMonthlyPayment = uiState.regularMonthlyPayment,
                            totalInterestRegular = uiState.totalInterestRegular,
                            totalPaidRegular = uiState.totalPaidRegular,
                            acceleratedMonths = uiState.acceleratedMonths,
                            totalInterestAccelerated = uiState.totalInterestAccelerated,
                            interestSavings = uiState.interestSavings,
                            monthsSaved = uiState.monthsSaved,
                            sampleAmortization = uiState.sampleAmortization,
                            onDebtAmountChange = viewModel::setDebtAmount,
                            onDebtInterestRateChange = viewModel::setDebtInterestRate,
                            onDebtTermMonthsChange = viewModel::setDebtTermMonths,
                            onExtraMonthlyPaymentChange = viewModel::setExtraMonthlyPayment
                        )
                    }
                    else -> {
                        EmergencyFundCalculator(
                            targetMonths = uiState.emergencyTargetMonths,
                            monthlyExpenseInput = uiState.emergencyMonthlyExpenseInput,
                            currentSavingsInput = uiState.emergencyCurrentSavingsInput,
                            monthlySavingCapacityInput = uiState.emergencyMonthlySavingCapacityInput,
                            targetFund = uiState.targetEmergencyFund,
                            fundGap = uiState.emergencyFundGap,
                            runwayMonths = uiState.runwayMonths,
                            fundProgress = uiState.emergencyFundProgress,
                            monthsToComplete = uiState.monthsToCompleteEmergencyFund,
                            safetyStatus = uiState.emergencySafetyStatus,
                            hasRealEmergencyData = uiState.hasRealEmergencyData,
                            actualTotalLiquidSavings = uiState.actualTotalLiquidSavings,
                            actualMonthlyEssentialExpenses = uiState.actualMonthlyEssentialExpenses,
                            onTargetMonthsChange = viewModel::setEmergencyTargetMonths,
                            onMonthlyExpenseChange = viewModel::setEmergencyMonthlyExpense,
                            onCurrentSavingsChange = viewModel::setEmergencyCurrentSavings,
                            onMonthlySavingCapacityChange = viewModel::setEmergencyMonthlySavingCapacity,
                            onUseRealEmergencyData = viewModel::useRealEmergencyData
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun Budget503020Calculator(
    incomeInput: String,
    needs: Double,
    wants: Double,
    savings: Double,
    actualIncome: Double,
    actualNeedsSpent: Double,
    actualWantsSpent: Double,
    actualSavingsSpent: Double,
    hasRealData: Boolean,
    compareWithRealData: Boolean,
    onIncomeChange: (String) -> Unit,
    onUseRealData: () -> Unit,
    onToggleCompare: () -> Unit
) {
    val incomeValue = incomeInput.toDoubleOrNull() ?: 0.0

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 840.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Input Card
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, SoftGreyBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Divide tus ingresos con inteligencia",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = BluePrimary
                    )
                    Text(
                        text = "La regla 50/30/20 divide tu salario neto en tres categorías claras: necesidades básicas (50%), deseos personales (30%), y metas de ahorro o reducción de deuda (20%).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )

                    if (hasRealData && actualIncome > 0) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = BluePrimary.copy(alpha = 0.08f),
                            border = BorderStroke(1.dp, BluePrimary.copy(alpha = 0.2f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Sync,
                                        contentDescription = null,
                                        tint = BluePrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Ingresos del mes: ${CurrencyFormatter.format(actualIncome, includeDecimals = false)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        color = BluePrimary
                                    )
                                }
                                TextButton(
                                    onClick = onUseRealData,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "Usar cifra",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = BluePrimary
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    OutlinedTextField(
                        value = incomeInput,
                        onValueChange = onIncomeChange,
                        label = { Text("Ingreso Mensual Neto") },
                        prefix = { Text("$ ") },
                        placeholder = { Text("Ej. 30000") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BluePrimary,
                            unfocusedBorderColor = SoftGreyBorder,
                            focusedLabelColor = BluePrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (hasRealData && incomeValue > 0) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Comparar con gastos reales del mes",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Switch(
                                checked = compareWithRealData,
                                onCheckedChange = { onToggleCompare() }
                            )
                        }
                    }
                }
            }

            if (incomeValue > 0) {
                // Visual Allocation Bar
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, SoftGreyBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Distribución Visual del Presupuesto",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(24.dp)
                                .clip(RoundedCornerShape(12.dp))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .weight(0.5f)
                                    .background(BluePrimary)
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .weight(0.3f)
                                    .background(OtherCategoryBrown)
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .weight(0.2f)
                                    .background(IncomeGreen)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(BluePrimary))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("50% Nec.", style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, color = NeutralVariant)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(OtherCategoryBrown))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("30% Des.", style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, color = NeutralVariant)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(IncomeGreen))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("20% Aho.", style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, color = NeutralVariant)
                            }
                        }
                    }
                }

                // Real comparison summary card
                if (compareWithRealData && hasRealData) {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, SoftGreyBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Diagnóstico: Tus Gastos Reales vs Meta",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            RealComparisonRow(
                                title = "Necesidades (50%)",
                                target = needs,
                                actual = actualNeedsSpent,
                                color = BluePrimary
                            )

                            RealComparisonRow(
                                title = "Deseos y Ocio (30%)",
                                target = wants,
                                actual = actualWantsSpent,
                                color = OtherCategoryBrown
                            )

                            RealComparisonRow(
                                title = "Ahorro / Inversión (20%)",
                                target = savings,
                                actual = actualSavingsSpent,
                                color = IncomeGreen,
                                isSavings = true
                            )
                        }
                    }
                }

                // Category Details
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Needs Card
                    BudgetCategoryCard(
                        title = "Necesidades Obligatorias (50%)",
                        amount = CurrencyFormatter.format(needs, includeDecimals = false),
                        description = "Destinado a gastos indispensables para vivir: alquiler, hipoteca, servicios públicos, despensa básica, salud y transporte diario.",
                        color = BluePrimary,
                        icon = Icons.Filled.AccountBalance,
                        actualSpent = if (compareWithRealData) actualNeedsSpent else null,
                        targetAmount = needs
                    )

                    // Wants Card
                    BudgetCategoryCard(
                        title = "Deseos y Gustos (30%)",
                        amount = CurrencyFormatter.format(wants, includeDecimals = false),
                        description = "Dinero para actividades de ocio, salidas a comer, pasatiempos, suscripciones digitales (Netflix, Spotify), ropa de moda y compras personales.",
                        color = OtherCategoryBrown,
                        icon = Icons.Filled.Celebration,
                        actualSpent = if (compareWithRealData) actualWantsSpent else null,
                        targetAmount = wants
                    )

                    // Savings Card
                    BudgetCategoryCard(
                        title = "Ahorro, Inversiones o Deuda (20%)",
                        amount = CurrencyFormatter.format(savings, includeDecimals = false),
                        description = "Fondo de emergencia, aportes a planes de retiro, fondos indexados y pagos extraordinarios para amortizar capital de tus deudas.",
                        color = IncomeGreen,
                        icon = Icons.AutoMirrored.Filled.TrendingUp,
                        actualSpent = if (compareWithRealData) actualSavingsSpent else null,
                        targetAmount = savings,
                        isSavings = true
                    )
                }
            } else {
                // Empty state helper
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, SoftGreyBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Calculate,
                                contentDescription = null,
                                tint = BluePrimary,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = "Escribe un monto arriba para calcular la distribución de presupuesto idónea.",
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RealComparisonRow(
    title: String,
    target: Double,
    actual: Double,
    color: Color,
    isSavings: Boolean = false
) {
    val isOver = if (isSavings) actual < target else actual > target
    val progress = if (target > 0) (actual / target).toFloat().coerceIn(0f, 1.5f) else 0f

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = color
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${CurrencyFormatter.format(actual, includeDecimals = false)} / ${CurrencyFormatter.format(target, includeDecimals = false)}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isOver && !isSavings) ExpenseRed else MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = if (!isOver) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                    contentDescription = null,
                    tint = if (!isOver) IncomeGreen else ExpenseRed,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        LinearProgressIndicator(
            progress = { progress.coerceAtMost(1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = if (isOver && !isSavings) ExpenseRed else color,
            trackColor = SoftGreyBorder
        )
    }
}

@Composable
fun BudgetCategoryCard(
    title: String,
    amount: String,
    description: String,
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    actualSpent: Double? = null,
    targetAmount: Double? = null,
    isSavings: Boolean = false
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, SoftGreyBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = color
                    )
                }
                Text(
                    text = amount,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = color
                )
            }

            if (actualSpent != null && targetAmount != null && targetAmount > 0) {
                Spacer(modifier = Modifier.height(10.dp))
                val ratio = (actualSpent / targetAmount * 100).toInt()
                val isExceeded = if (isSavings) actualSpent < targetAmount else actualSpent > targetAmount

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isExceeded && !isSavings) ExpenseRed.copy(alpha = 0.08f) else IncomeGreen.copy(alpha = 0.08f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isSavings) {
                                if (actualSpent >= targetAmount) "¡Meta de ahorro alcanzada! ($ratio%)" else "Progreso de ahorro: $ratio%"
                            } else {
                                if (actualSpent > targetAmount) "Sobrepasado por ${CurrencyFormatter.format(actualSpent - targetAmount, includeDecimals = false)}" else "Dentro del presupuesto ($ratio% usado)"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = if (isExceeded && !isSavings) ExpenseRed else IncomeGreen
                        )
                        Text(
                            text = CurrencyFormatter.format(actualSpent, includeDecimals = false),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isExceeded && !isSavings) ExpenseRed else IncomeGreen
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
fun CompoundInterestCalculator(
    initialDepositInput: String,
    monthlyContributionInput: String,
    annualRateInput: String,
    yearsInput: String,
    futureValue: Double,
    totalDeposits: Double,
    interestEarned: Double,
    yearlyBreakdown: List<YearlyGrowthPoint>,
    onInitialDepositChange: (String) -> Unit,
    onMonthlyContributionChange: (String) -> Unit,
    onAnnualRateChange: (String) -> Unit,
    onYearsChange: (String) -> Unit
) {
    val initialDeposit = initialDepositInput.toDoubleOrNull() ?: 0.0
    val monthlyContribution = monthlyContributionInput.toDoubleOrNull() ?: 0.0
    val years = yearsInput.toIntOrNull() ?: 0

    val compoundResult = CalculatorResult(futureValue, totalDeposits, interestEarned)

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 840.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Inputs Card
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, SoftGreyBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "El poder del Interés Compuesto",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = BluePrimary
                    )
                    Text(
                        text = "Visualiza el crecimiento de tus ahorros e inversiones a largo plazo acumulando intereses mes tras mes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Initial Deposit Input
                    OutlinedTextField(
                        value = initialDepositInput,
                        onValueChange = onInitialDepositChange,
                        label = { Text("Depósito Inicial") },
                        prefix = { Text("$ ") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BluePrimary, unfocusedBorderColor = SoftGreyBorder, focusedLabelColor = BluePrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Monthly contribution Input
                    OutlinedTextField(
                        value = monthlyContributionInput,
                        onValueChange = onMonthlyContributionChange,
                        label = { Text("Aporte Mensual") },
                        prefix = { Text("$ ") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BluePrimary, unfocusedBorderColor = SoftGreyBorder, focusedLabelColor = BluePrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Rate Input
                        OutlinedTextField(
                            value = annualRateInput,
                            onValueChange = onAnnualRateChange,
                            label = { Text("Tasa Anual (%)") },
                            placeholder = { Text("Ej. 8") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BluePrimary, unfocusedBorderColor = SoftGreyBorder, focusedLabelColor = BluePrimary
                            ),
                            modifier = Modifier.weight(1f)
                        )

                        // Years Input
                        OutlinedTextField(
                            value = yearsInput,
                            onValueChange = onYearsChange,
                            label = { Text("Años") },
                            placeholder = { Text("Ej. 10") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BluePrimary, unfocusedBorderColor = SoftGreyBorder, focusedLabelColor = BluePrimary
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Output results
            if (years > 0 && (initialDeposit > 0 || monthlyContribution > 0)) {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, SoftGreyBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Summary Main Balance
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Balance Final Estimado",
                                style = MaterialTheme.typography.bodySmall,
                                color = NeutralVariant,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = CurrencyFormatter.format(compoundResult.futureValue, includeDecimals = false),
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = IncomeGreen
                            )
                        }

                        HorizontalDivider(color = SoftGreyBorder)

                        // Secondary stats
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Tus Aportes", style = MaterialTheme.typography.bodySmall, color = NeutralVariant)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = CurrencyFormatter.format(compoundResult.totalDeposits, includeDecimals = false),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                                Text("Interés Ganado", style = MaterialTheme.typography.bodySmall, color = NeutralVariant)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = CurrencyFormatter.format(compoundResult.interestEarned, includeDecimals = false),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = BluePrimary
                                )
                            }
                        }

                        // Comparative chart progress bar
                        if (compoundResult.futureValue > 0) {
                            val principalRatio = (compoundResult.totalDeposits / compoundResult.futureValue).toFloat()
                            val interestRatio = 1f - principalRatio

                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(14.dp)
                                        .clip(RoundedCornerShape(7.dp))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .weight(maxOf(0.01f, principalRatio))
                                            .background(BluePrimary.copy(alpha = 0.8f))
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .weight(maxOf(0.01f, interestRatio))
                                            .background(IncomeGreen)
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Aportado (${(principalRatio * 100).toInt()}%)",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontSize = 11.sp,
                                        color = NeutralVariant
                                    )
                                    Text(
                                        text = "Interés Ganado (${(interestRatio * 100).toInt()}%)",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontSize = 11.sp,
                                        color = NeutralVariant
                                    )
                                }
                            }
                        }

                        // Yearly projection breakdown
                        if (yearlyBreakdown.isNotEmpty()) {
                            HorizontalDivider(color = SoftGreyBorder)
                            Text(
                                text = "Proyección de Crecimiento Anual",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                // Filter display to show key milestone years (e.g. up to 10 points)
                                val displayedYears = if (yearlyBreakdown.size <= 10) yearlyBreakdown
                                else yearlyBreakdown.filterIndexed { idx, _ -> idx == 0 || (idx + 1) % (yearlyBreakdown.size / 6) == 0 || idx == yearlyBreakdown.lastIndex }

                                for (item in displayedYears) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Año ${item.year}",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                text = CurrencyFormatter.format(item.totalBalance, includeDecimals = false),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = IncomeGreen
                                            )
                                            Text(
                                                text = "Int: +${CurrencyFormatter.format(item.interestEarned, includeDecimals = false)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = BluePrimary
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Educational note card
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(BluePrimary.copy(alpha = 0.08f))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "💡 Sabías que: Si mantienes esta inversión por $years años con una tasa del ${annualRateInput}%, los intereses multiplicarán tu dinero de forma exponencial sin que tengas que hacer nada más. ¡La constancia mensual es clave!",
                                style = MaterialTheme.typography.bodySmall,
                                color = BluePrimary,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DebtPayoffCalculator(
    debtAmountInput: String,
    debtInterestRateInput: String,
    debtTermMonthsInput: String,
    extraMonthlyPaymentInput: String,
    regularMonthlyPayment: Double,
    totalInterestRegular: Double,
    totalPaidRegular: Double,
    acceleratedMonths: Int,
    totalInterestAccelerated: Double,
    interestSavings: Double,
    monthsSaved: Int,
    sampleAmortization: List<AmortizationRow>,
    onDebtAmountChange: (String) -> Unit,
    onDebtInterestRateChange: (String) -> Unit,
    onDebtTermMonthsChange: (String) -> Unit,
    onExtraMonthlyPaymentChange: (String) -> Unit
) {
    val debtAmount = debtAmountInput.toDoubleOrNull() ?: 0.0
    val termMonths = debtTermMonthsInput.toIntOrNull() ?: 0
    val extraPayment = extraMonthlyPaymentInput.toDoubleOrNull() ?: 0.0

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 840.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Inputs Card
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, SoftGreyBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Simulador de Salida de Deudas y Amortización",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = BluePrimary
                    )
                    Text(
                        text = "Descubre cuánto dinero y cuántos meses te ahorras al realizar pagos extraordinarios directos al capital de tu préstamo o tarjeta de crédito.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    OutlinedTextField(
                        value = debtAmountInput,
                        onValueChange = onDebtAmountChange,
                        label = { Text("Monto total adeudado") },
                        prefix = { Text("$ ") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BluePrimary, unfocusedBorderColor = SoftGreyBorder, focusedLabelColor = BluePrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = debtInterestRateInput,
                            onValueChange = onDebtInterestRateChange,
                            label = { Text("Tasa Anual (%)") },
                            placeholder = { Text("Ej. 18") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BluePrimary, unfocusedBorderColor = SoftGreyBorder, focusedLabelColor = BluePrimary
                            ),
                            modifier = Modifier.weight(1f)
                        )

                        OutlinedTextField(
                            value = debtTermMonthsInput,
                            onValueChange = onDebtTermMonthsChange,
                            label = { Text("Plazo (Meses)") },
                            placeholder = { Text("Ej. 24") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BluePrimary, unfocusedBorderColor = SoftGreyBorder, focusedLabelColor = BluePrimary
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    OutlinedTextField(
                        value = extraMonthlyPaymentInput,
                        onValueChange = onExtraMonthlyPaymentChange,
                        label = { Text("Aporte extra mensual voluntario") },
                        prefix = { Text("$ ") },
                        supportingText = { Text("Cada peso extra va 100% a amortizar capital") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = IncomeGreen, unfocusedBorderColor = SoftGreyBorder, focusedLabelColor = IncomeGreen
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Results Card
            if (debtAmount > 0 && termMonths > 0) {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, SoftGreyBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Impact Banner if Extra Payment
                        if (extraPayment > 0 && monthsSaved > 0) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = IncomeGreen.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, IncomeGreen.copy(alpha = 0.3f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Filled.CheckCircle,
                                            contentDescription = null,
                                            tint = IncomeGreen,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "¡Terminarás tu deuda $monthsSaved meses antes!",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = IncomeGreen
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Ahorras ${CurrencyFormatter.format(interestSavings, includeDecimals = false)} en intereses totales que no tendrás que pagarle al banco.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        // Cuota Regular
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Cuota Mensual Mínima", style = MaterialTheme.typography.bodySmall, color = NeutralVariant)
                                Text(
                                    text = CurrencyFormatter.format(regularMonthlyPayment, includeDecimals = false),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            if (extraPayment > 0) {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Pago Total Propuesto", style = MaterialTheme.typography.bodySmall, color = NeutralVariant)
                                    Text(
                                        text = CurrencyFormatter.format(regularMonthlyPayment + extraPayment, includeDecimals = false),
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = IncomeGreen
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = SoftGreyBorder)

                        // Comparative Table
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Plan Regular", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = NeutralVariant)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("Plazo: $termMonths meses", style = MaterialTheme.typography.bodySmall)
                                Text("Intereses: ${CurrencyFormatter.format(totalInterestRegular, includeDecimals = false)}", style = MaterialTheme.typography.bodySmall, color = ExpenseRed)
                                Text("Total pagado: ${CurrencyFormatter.format(totalPaidRegular, includeDecimals = false)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                            }

                            if (extraPayment > 0) {
                                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                                    Text("Plan Acelerado", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = IncomeGreen)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text("Plazo: $acceleratedMonths meses", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = IncomeGreen)
                                    Text("Intereses: ${CurrencyFormatter.format(totalInterestAccelerated, includeDecimals = false)}", style = MaterialTheme.typography.bodySmall, color = IncomeGreen)
                                    Text("Total pagado: ${CurrencyFormatter.format(debtAmount + totalInterestAccelerated, includeDecimals = false)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                }
                            }
                        }

                        // Sample Amortization Table
                        if (sampleAmortization.isNotEmpty()) {
                            HorizontalDivider(color = SoftGreyBorder)
                            Text(
                                text = "Tabla de Amortización (Primeros meses)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Mes", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.6f))
                                    Text("Cuota", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                                    Text("Capital", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                                    Text("Interés", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                                    Text("Saldo", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.2f), textAlign = TextAlign.End)
                                }

                                for (row in sampleAmortization) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("${row.month}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(0.6f))
                                        Text(CurrencyFormatter.format(row.payment, includeDecimals = false), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                                        Text(CurrencyFormatter.format(row.principal, includeDecimals = false), style = MaterialTheme.typography.bodySmall, color = IncomeGreen, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                                        Text(CurrencyFormatter.format(row.interest, includeDecimals = false), style = MaterialTheme.typography.bodySmall, color = ExpenseRed, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                                        Text(CurrencyFormatter.format(row.remainingBalance, includeDecimals = false), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1.2f), textAlign = TextAlign.End)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

data class CalculatorResult(
    val futureValue: Double,
    val totalDeposits: Double,
    val interestEarned: Double
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EmergencyFundCalculator(
    targetMonths: Int,
    monthlyExpenseInput: String,
    currentSavingsInput: String,
    monthlySavingCapacityInput: String,
    targetFund: Double,
    fundGap: Double,
    runwayMonths: Double,
    fundProgress: Float,
    monthsToComplete: Int,
    safetyStatus: SafetyStatus,
    hasRealEmergencyData: Boolean,
    actualTotalLiquidSavings: Double,
    actualMonthlyEssentialExpenses: Double,
    onTargetMonthsChange: (Int) -> Unit,
    onMonthlyExpenseChange: (String) -> Unit,
    onCurrentSavingsChange: (String) -> Unit,
    onMonthlySavingCapacityChange: (String) -> Unit,
    onUseRealEmergencyData: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    val statusColor = when (safetyStatus) {
        SafetyStatus.CRITICAL -> ExpenseRed
        SafetyStatus.BASIC -> Color(0xFFF59E0B)
        SafetyStatus.RECOMMENDED -> IncomeGreen
        SafetyStatus.OPTIMAL -> BluePrimary
    }

    val statusTitle = when (safetyStatus) {
        SafetyStatus.CRITICAL -> "Nivel de Cobertura Crítico"
        SafetyStatus.BASIC -> "Colchón Básico Inicial"
        SafetyStatus.RECOMMENDED -> "Fondo Recomendado y Saludable"
        SafetyStatus.OPTIMAL -> "Blindaje Financiero Óptimo"
    }

    val statusAdvice = when (safetyStatus) {
        SafetyStatus.CRITICAL -> "Tienes menos de 1 mes de gastos cubiertos. Ante un imprevisto tendrías que endeudarte. Se sugiere priorizar este fondo antes de gastos en deseos o inversiones volátiles."
        SafetyStatus.BASIC -> "Cuentas con un resguardo de 1 a 3 meses. Es un excelente comienzo para emergencias menores, continúa ahorrando mes a mes para llegar a los 6 meses de tranquilidad."
        SafetyStatus.RECOMMENDED -> "¡Gran trabajo! Tienes de 3 a 6 meses de gastos asegurados. Cumples el estándar dorado de educación financiera para amortiguar cualquier imprevisto."
        SafetyStatus.OPTIMAL -> "¡Excelente solidez! Tienes más de 6 meses de gastos cubiertos. Disfrutas de máxima autonomía y paz mental ante eventualidades o cambios profesionales."
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Tarjeta Explicativa Hero
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Savings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text(
                        text = "Simulador de Fondo de Emergencia",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = "El fondo de emergencia es tu escudo contra deudas: protege tu hogar frente a imprevistos médicos, averías o desempleo, manteniendo tu nivel de vida sin estrés.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Tarjeta de Sincronización con Datos Reales
        if (hasRealEmergencyData) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, BluePrimary.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = null,
                                tint = BluePrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Tus datos reales detectados",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = BluePrimary
                            )
                        }
                        Text(
                            text = "Gastos fijados: ${CurrencyFormatter.format(actualMonthlyEssentialExpenses, includeDecimals = false)} • Saldo en cuentas: ${CurrencyFormatter.format(actualTotalLiquidSavings, includeDecimals = false)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = onUseRealEmergencyData,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("Usar mis datos", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Selección de Meta en Meses (Chips interactivos)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Meses de cobertura deseados",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    3 to "3 meses",
                    6 to "6 meses (Ideal)",
                    9 to "9 meses",
                    12 to "1 año"
                ).forEach { (months, label) ->
                    val isSelected = targetMonths == months
                    FilterChip(
                        selected = isSelected,
                        onClick = { onTargetMonthsChange(months) },
                        label = {
                            Text(
                                text = label,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = BluePrimary.copy(alpha = 0.15f),
                            selectedLabelColor = BluePrimary
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Formulario de Inputs
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(1.dp, SoftGreyBorder)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Parámetros de tu Presupuesto",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = monthlyExpenseInput,
                    onValueChange = onMonthlyExpenseChange,
                    label = { Text("Gastos mensuales esenciales ($/mes)") },
                    placeholder = { Text("Ej. 15000") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = currentSavingsInput,
                    onValueChange = onCurrentSavingsChange,
                    label = { Text("Ahorro actual reservado para emergencias ($)") },
                    placeholder = { Text("Ej. 30000") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = monthlySavingCapacityInput,
                    onValueChange = onMonthlySavingCapacityChange,
                    label = { Text("Capacidad de ahorro mensual ($/mes)") },
                    placeholder = { Text("Ej. 3000") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }

        // Resultados del Simulador
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(1.5.dp, statusColor.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header de Estado de Seguridad
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "DIAGNÓSTICO DE PISTA FINANCIERA",
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                            fontWeight = FontWeight.Bold,
                            color = NeutralVariant
                        )
                        Text(
                            text = statusTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = statusColor
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = statusColor.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = String.format(Locale.getDefault(), "%.1f meses", runwayMonths),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = statusColor,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }

                Text(
                    text = statusAdvice,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                HorizontalDivider(color = SoftGreyBorder)

                // Progreso Visual del Fondo
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Meta del Fondo: ${CurrencyFormatter.format(targetFund, includeDecimals = false)}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${(fundProgress * 100).toInt()}% alcanzado",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = statusColor
                        )
                    }

                    LinearProgressIndicator(
                        progress = { fundProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(12.dp)
                            .clip(RoundedCornerShape(6.dp)),
                        color = statusColor,
                        trackColor = statusColor.copy(alpha = 0.15f)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val currentVal = currentSavingsInput.toDoubleOrNull() ?: 0.0
                        Text(
                            text = "Ahorrado: ${CurrencyFormatter.format(currentVal, includeDecimals = false)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = NeutralVariant
                        )
                        Text(
                            text = if (fundGap <= 0) "¡Fondo Completo!" else "Faltan: ${CurrencyFormatter.format(fundGap, includeDecimals = false)}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = if (fundGap <= 0) IncomeGreen else ExpenseRed
                        )
                    }
                }

                // Proyección de Tiempo Estimado
                if (fundGap > 0 && monthsToComplete > 0) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Timeline,
                                contentDescription = null,
                                tint = BluePrimary,
                                modifier = Modifier.size(24.dp)
                            )
                            Column {
                                Text(
                                    text = "Tiempo estimado para completar tu fondo",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Ahorrando ${CurrencyFormatter.format(monthlySavingCapacityInput.toDoubleOrNull() ?: 0.0, includeDecimals = false)} cada mes, alcanzarás tu meta en $monthsToComplete meses.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else if (fundGap <= 0) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = IncomeGreen.copy(alpha = 0.12f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Celebration,
                                contentDescription = null,
                                tint = IncomeGreen,
                                modifier = Modifier.size(24.dp)
                            )
                            Column {
                                Text(
                                    text = "¡Felicitaciones, tu fondo está al 100%!",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = IncomeGreen
                                )
                                Text(
                                    text = "Has cubierto tu objetivo de $targetMonths meses. Puedes destinar tus nuevos ahorros a metas a largo plazo, inversión o disfrute.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // Consejos Prácticos de Expertos
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(1.dp, SoftGreyBorder)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Reglas de oro del Fondo de Emergencia",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "• Alta liquidez: Guárdalo en una cuenta de ahorros de acceso inmediato, nunca en plazos fijos bloqueados o acciones.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "• Separado de tu cuenta de gastos diarios: Evita mezclarlo con tu dinero corriente para no gastarlo por impulso.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "• Úsalo solo para emergencias reales: Salud inesperada, pérdida temporal de ingresos o reparaciones urgentes del hogar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}


