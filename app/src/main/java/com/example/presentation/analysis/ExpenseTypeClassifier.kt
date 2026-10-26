package com.example.presentation.analysis

import androidx.compose.ui.graphics.Color
import com.example.domain.model.Transaction
import com.example.domain.model.TransactionType

/**
 * Tipos de agrupación para clasificar gastos en gráficos Recharts/D3.
 */
enum class ExpenseGroupingType(
    val title: String,
    val shortLabel: String,
    val description: String
) {
    BY_CATEGORY(
        title = "Por Categoría",
        shortLabel = "Categorías",
        description = "Desglose por cada categoría individual de gasto"
    ),
    FIXED_VS_VARIABLE(
        title = "Fijos vs Variables",
        shortLabel = "Fijo / Variable",
        description = "Gastos fijos obligatorios frente a gastos variables discrecionales"
    ),
    RULE_50_30_20(
        title = "Regla 50 / 30 / 20",
        shortLabel = "50/30/20",
        description = "Necesidades básicas (50%), Deseos/Ocio (30%) y Ahorro (20%)"
    ),
    RECURRING_VS_OCCASIONAL(
        title = "Recurrentes vs Ocasionales",
        shortLabel = "Frecuencia",
        description = "Suscripciones y cobros fijos frente a compras ocasionales"
    )
}

/**
 * Representa una rebanada de gasto clasificada por tipo para gráficos D3 / Recharts.
 */
data class TypedExpenseShare(
    val id: String,
    val name: String,
    val typeGroup: ExpenseGroupingType,
    val amount: Double,
    val percentage: Float, // 0f..1f
    val color: Color,
    val iconKey: String,
    val description: String = "",
    val targetPercentage: Float? = null,
    val categoryCount: Int = 1
)

/**
 * Clasificador inteligente de gastos financieros según su naturaleza económica.
 */
object ExpenseTypeClassifier {

    // Paleta cualitativa D3 / Recharts para visualizaciones
    val D3_PALETTE = listOf(
        Color(0xFF3B82F6), // Blue
        Color(0xFF10B981), // Emerald
        Color(0xFFF59E0B), // Amber
        Color(0xFFEF4444), // Coral Red
        Color(0xFF8B5CF6), // Purple
        Color(0xFF06B6D4), // Cyan
        Color(0xFFEC4899), // Pink
        Color(0xFFF97316), // Orange
        Color(0xFF14B8A6), // Teal
        Color(0xFF6366F1), // Indigo
        Color(0xFF84CC16), // Lime
        Color(0xFF64748B)  // Slate
    )

    // Colores semánticos para Fijos vs Variables
    val COLOR_FIXED = Color(0xFF3B82F6)    // Azul estabilidad
    val COLOR_VARIABLE = Color(0xFFF59E0B) // Ámbar dinámico

    // Colores para Regla 50/30/20
    val COLOR_NEEDS = Color(0xFF10B981)    // Verde esmeralda (Necesidades)
    val COLOR_WANTS = Color(0xFF8B5CF6)    // Violeta (Deseos/Estilo de vida)
    val COLOR_SAVINGS = Color(0xFF06B6D4)  // Cian (Ahorro/Inversión)

    // Colores para Recurrentes vs Ocasionales
    val COLOR_RECURRING = Color(0xFF6366F1)  // Índigo suscripciones
    val COLOR_OCCASIONAL = Color(0xFFEC4899) // Rosa compras puntuales

    /**
     * Agrupa y calcula participaciones según el tipo de agrupación seleccionado.
     */
    fun classifyExpenses(
        groupingType: ExpenseGroupingType,
        categoryShares: List<CategoryExpenseShare>,
        transactions: List<Transaction> = emptyList(),
        totalExpense: Double
    ): List<TypedExpenseShare> {
        if (totalExpense <= 0 || categoryShares.isEmpty()) return emptyList()

        return when (groupingType) {
            ExpenseGroupingType.BY_CATEGORY -> {
                categoryShares.mapIndexed { index, share ->
                    TypedExpenseShare(
                        id = "cat_${share.categoryId}",
                        name = share.categoryName,
                        typeGroup = ExpenseGroupingType.BY_CATEGORY,
                        amount = share.amount,
                        percentage = share.percentage,
                        color = D3_PALETTE[index % D3_PALETTE.size],
                        iconKey = share.iconKey,
                        description = "${(share.percentage * 100).toInt()}% del gasto total",
                        categoryCount = 1
                    )
                }
            }

            ExpenseGroupingType.FIXED_VS_VARIABLE -> {
                classifyFixedVsVariable(categoryShares, totalExpense)
            }

            ExpenseGroupingType.RULE_50_30_20 -> {
                classifyRule503020(categoryShares, totalExpense)
            }

            ExpenseGroupingType.RECURRING_VS_OCCASIONAL -> {
                classifyRecurringVsOccasional(categoryShares, transactions, totalExpense)
            }
        }
    }

    /**
     * Clasificación: Gastos Fijos (Servicios, Salud, Educación, Vivienda) vs
     * Gastos Variables (Comida fuera, Ocio, Compras, Transporte, Otros).
     */
    private fun classifyFixedVsVariable(
        categoryShares: List<CategoryExpenseShare>,
        totalExpense: Double
    ): List<TypedExpenseShare> {
        var fixedAmount = 0.0
        var variableAmount = 0.0
        var fixedCount = 0
        var variableCount = 0

        for (share in categoryShares) {
            if (isFixedCategory(share.categoryName, share.iconKey)) {
                fixedAmount += share.amount
                fixedCount++
            } else {
                variableAmount += share.amount
                variableCount++
            }
        }

        val result = mutableListOf<TypedExpenseShare>()
        if (fixedAmount > 0) {
            val pct = (fixedAmount / totalExpense).toFloat()
            result.add(
                TypedExpenseShare(
                    id = "type_fixed",
                    name = "Gastos Fijos",
                    typeGroup = ExpenseGroupingType.FIXED_VS_VARIABLE,
                    amount = fixedAmount,
                    percentage = pct,
                    color = COLOR_FIXED,
                    iconKey = "receipt",
                    description = "Servicios, vivienda, salud, educación y compromisos fijos",
                    categoryCount = fixedCount
                )
            )
        }

        if (variableAmount > 0) {
            val pct = (variableAmount / totalExpense).toFloat()
            result.add(
                TypedExpenseShare(
                    id = "type_variable",
                    name = "Gastos Variables",
                    typeGroup = ExpenseGroupingType.FIXED_VS_VARIABLE,
                    amount = variableAmount,
                    percentage = pct,
                    color = COLOR_VARIABLE,
                    iconKey = "shopping_cart",
                    description = "Alimentación, ocio, transporte diario y compras flexibles",
                    categoryCount = variableCount
                )
            )
        }

        return result.sortedByDescending { it.amount }
    }

    /**
     * Clasificación: Regla 50/30/20
     * - Necesidades (50% target): Comida, Servicios, Salud, Transporte, Vivienda
     * - Deseos (30% target): Ocio, Entretenimiento, Compras personales, Salidas
     * - Ahorro/Otros (20% target): Ahorro, Educación, Transferencias, Otros
     */
    private fun classifyRule503020(
        categoryShares: List<CategoryExpenseShare>,
        totalExpense: Double
    ): List<TypedExpenseShare> {
        var needsAmount = 0.0
        var wantsAmount = 0.0
        var savingsOtherAmount = 0.0

        var needsCount = 0
        var wantsCount = 0
        var savingsCount = 0

        for (share in categoryShares) {
            when {
                isNeedsCategory(share.categoryName, share.iconKey) -> {
                    needsAmount += share.amount
                    needsCount++
                }
                isWantsCategory(share.categoryName, share.iconKey) -> {
                    wantsAmount += share.amount
                    wantsCount++
                }
                else -> {
                    savingsOtherAmount += share.amount
                    savingsCount++
                }
            }
        }

        val result = mutableListOf<TypedExpenseShare>()
        if (needsAmount > 0) {
            val pct = (needsAmount / totalExpense).toFloat()
            result.add(
                TypedExpenseShare(
                    id = "rule_needs",
                    name = "Necesidades Básicas",
                    typeGroup = ExpenseGroupingType.RULE_50_30_20,
                    amount = needsAmount,
                    percentage = pct,
                    color = COLOR_NEEDS,
                    iconKey = "home",
                    description = "Alimentación, servicios, salud y transporte vital",
                    targetPercentage = 0.50f,
                    categoryCount = needsCount
                )
            )
        }

        if (wantsAmount > 0) {
            val pct = (wantsAmount / totalExpense).toFloat()
            result.add(
                TypedExpenseShare(
                    id = "rule_wants",
                    name = "Deseos y Estilo de Vida",
                    typeGroup = ExpenseGroupingType.RULE_50_30_20,
                    amount = wantsAmount,
                    percentage = pct,
                    color = COLOR_WANTS,
                    iconKey = "sports_esports",
                    description = "Ocio, salidas, entretenimiento y compras personales",
                    targetPercentage = 0.30f,
                    categoryCount = wantsCount
                )
            )
        }

        if (savingsOtherAmount > 0) {
            val pct = (savingsOtherAmount / totalExpense).toFloat()
            result.add(
                TypedExpenseShare(
                    id = "rule_savings",
                    name = "Prioridades y Otros",
                    typeGroup = ExpenseGroupingType.RULE_50_30_20,
                    amount = savingsOtherAmount,
                    percentage = pct,
                    color = COLOR_SAVINGS,
                    iconKey = "school",
                    description = "Educación, transferencias, metas y gastos diversos",
                    targetPercentage = 0.20f,
                    categoryCount = savingsCount
                )
            )
        }

        return result.sortedByDescending { it.amount }
    }

    /**
     * Clasificación: Recurrentes vs Ocasionales.
     */
    private fun classifyRecurringVsOccasional(
        categoryShares: List<CategoryExpenseShare>,
        transactions: List<Transaction>,
        totalExpense: Double
    ): List<TypedExpenseShare> {
        // Categorías que habitualmente son cobros recurrentes fijos (Servicios, Educación, Alquiler)
        var recurringAmount = 0.0
        var occasionalAmount = 0.0
        var recurringCount = 0
        var occasionalCount = 0

        for (share in categoryShares) {
            val iconLower = share.iconKey.lowercase()
            val isLikelyRecurring = iconLower in RECURRING_ICONS || run {
                val lower = share.categoryName.lowercase()
                lower.contains("servicio") ||
                        lower.contains("suscrip") ||
                        lower.contains("educa") ||
                        lower.contains("alquiler") ||
                        lower.contains("cuota") ||
                        lower.contains("gym") ||
                        lower.contains("internet") ||
                        lower.contains("seguro")
            }

            if (isLikelyRecurring) {
                recurringAmount += share.amount
                recurringCount++
            } else {
                occasionalAmount += share.amount
                occasionalCount++
            }
        }

        val result = mutableListOf<TypedExpenseShare>()
        if (recurringAmount > 0) {
            val pct = (recurringAmount / totalExpense).toFloat()
            result.add(
                TypedExpenseShare(
                    id = "freq_recurring",
                    name = "Recurrentes y Frecuentes",
                    typeGroup = ExpenseGroupingType.RECURRING_VS_OCCASIONAL,
                    amount = recurringAmount,
                    percentage = pct,
                    color = COLOR_RECURRING,
                    iconKey = "repeat",
                    description = "Servicios mensuales, suscripciones y cuotas regulares",
                    categoryCount = recurringCount
                )
            )
        }

        if (occasionalAmount > 0) {
            val pct = (occasionalAmount / totalExpense).toFloat()
            result.add(
                TypedExpenseShare(
                    id = "freq_occasional",
                    name = "Puntuales y Ocasionales",
                    typeGroup = ExpenseGroupingType.RECURRING_VS_OCCASIONAL,
                    amount = occasionalAmount,
                    percentage = pct,
                    color = COLOR_OCCASIONAL,
                    iconKey = "flash_on",
                    description = "Compras espontáneas, salidas y gastos no programados",
                    categoryCount = occasionalCount
                )
            )
        }

        return result.sortedByDescending { it.amount }
    }

    private fun isFixedCategory(name: String, iconKey: String = ""): Boolean {
        val icon = iconKey.lowercase()
        if (icon in FIXED_ICONS) return true
        val lower = name.lowercase()
        return lower.contains("servicio") ||
                lower.contains("salud") ||
                lower.contains("educa") ||
                lower.contains("vivienda") ||
                lower.contains("alquiler") ||
                lower.contains("seguro") ||
                lower.contains("cuota") ||
                lower.contains("luz") ||
                lower.contains("agua") ||
                lower.contains("internet")
    }

    internal fun isNeedsCategory(name: String, iconKey: String = ""): Boolean {
        val icon = iconKey.lowercase()
        if (icon in NEEDS_ICONS) return true
        val lower = name.lowercase()
        return lower.contains("comida") ||
                lower.contains("alimenta") ||
                lower.contains("super") ||
                lower.contains("transporte") ||
                lower.contains("servicio") ||
                lower.contains("salud") ||
                lower.contains("farmacia") ||
                lower.contains("vivienda") ||
                lower.contains("alquiler")
    }

    internal fun isWantsCategory(name: String, iconKey: String = ""): Boolean {
        val icon = iconKey.lowercase()
        if (icon in WANTS_ICONS) return true
        val lower = name.lowercase()
        return lower.contains("ocio") ||
                lower.contains("entreteni") ||
                lower.contains("juego") ||
                lower.contains("restauran") ||
                lower.contains("café") ||
                lower.contains("cafe") ||
                lower.contains("viaje") ||
                lower.contains("compra") ||
                lower.contains("cine") ||
                lower.contains("bar")
    }

    private val FIXED_ICONS = setOf("home", "domain", "local_hospital", "school", "security", "receipt_long", "wifi")
    private val NEEDS_ICONS = setOf("home", "restaurant", "shopping_cart", "local_hospital", "directions_car", "commute", "receipt_long")
    private val WANTS_ICONS = setOf("sports_esports", "movie", "flight", "shopping_bag", "celebration", "pool", "palette")
    private val RECURRING_ICONS = setOf("repeat", "subscriptions", "calendar_month", "wifi", "bolt")
}
