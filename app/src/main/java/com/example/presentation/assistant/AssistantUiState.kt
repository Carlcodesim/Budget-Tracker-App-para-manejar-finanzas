package com.example.presentation.assistant

import com.example.domain.model.Account

data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

data class AssistantUiState(
    val selectedTab: AssistantTab = AssistantTab.CHAT,
    val messages: List<ChatMessage> = listOf(
        ChatMessage(
            text = "¡Hola! Soy tu Asistente Financiero inteligente potenciado por Gemini. Puedo resolver consultas financieras interactivas, pronosticar gastos futuros, detectar anomalías de consumo y construir estrategias dinámicas de ahorro. ¿En qué te ayudo hoy?",
            isUser = false
        )
    ),
    val isLoading: Boolean = false,
    val error: String? = null,
    val isKeyConfigured: Boolean = true,
    val inputText: String = "",

    // Predicciones y anomalías (Punto 3)
    val anomalies: List<FinancialAnomaly> = emptyList(),
    val forecast: ExpenseForecast? = null,
    val isAnalyzingAnomalies: Boolean = false,
    val aiAnomalyAnalysis: String? = null,
    val simulatedDailyPace: Double? = null,
    val simulatedProjectedExpense: Double? = null,
    val simulatedProjectedSavings: Double? = null,
    val selectedPredictionDay: Int? = null,

    // Estrategia de ahorro (Punto 4)
    val savingsStrategies: List<SavingsGoalStrategy> = emptyList(),
    val isGeneratingStrategy: Boolean = false,
    val aiGlobalSavingsAdvice: String? = null,

    // Cuentas y ejecución de aportes directos
    val currentAccountsList: List<Account> = emptyList(),
    val showContributeDialogForGoal: SavingsGoalStrategy? = null,
    val contributionSuccessMessage: String? = null,

    // Simulador y Distribución Inteligente de Excedente
    val allocationMode: AllocationStrategyMode = AllocationStrategyMode.BALANCED,
    val simulatedSurplusToAllocate: Double? = null,
    val goalAllocations: List<GoalAllocationShare> = emptyList(),

    // Diagnóstico y Prueba de Estrés Financiero (Financial Health & Stress Simulator)
    val financialHealthReport: FinancialHealthReport? = null,
    val selectedStressScenario: Int = 0, // 0: Normal, 1: -25% Ingresos, 2: Emergencia $500, 3: Ahorro Hormiga -20%
    val stressTestResult: StressTestResult? = null
)
