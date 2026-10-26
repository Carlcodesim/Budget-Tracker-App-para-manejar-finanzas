package com.example.presentation.common.util

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * Metadata descriptor for each supported currency.
 */
data class CurrencyOption(
    val code: String,
    val name: String,
    val symbol: String,
    val flag: String,
    val localeTag: String,
    val standardFractionDigits: Int = 2,
    val isSymbolPrefix: Boolean = true
)

/**
 * Structured parts of a formatted currency value for custom UI typography/layouts.
 */
data class FormattedCurrencyParts(
    val symbol: String,
    val wholePart: String,
    val decimalPart: String,
    val isNegative: Boolean,
    val fullFormatted: String
)

/**
 * System-wide currency formatting utility ensuring consistent, high-performance,
 * thread-safe, and locale-aware display of monetary values across the entire application.
 */
object CurrencyFormatter {

    const val DEFAULT_CURRENCY_CODE = "DOP"
    private const val PREFS_NAME = "currency_prefs"
    private const val KEY_SELECTED_CURRENCY = "selected_currency"

    val availableCurrencies: List<CurrencyOption> = listOf(
        CurrencyOption("DOP", "Peso Dominicano", "RD$", "🇩🇴", "es-DO", 2, true),
        CurrencyOption("USD", "Dólar Estadounidense", "$", "🇺🇸", "en-US", 2, true),
        CurrencyOption("EUR", "Euro", "€", "🇪🇺", "es-ES", 2, false),
        CurrencyOption("MXN", "Peso Mexicano", "$", "🇲🇽", "es-MX", 2, true),
        CurrencyOption("COP", "Peso Colombiano", "$", "🇨🇴", "es-CO", 0, true),
        CurrencyOption("ARS", "Peso Argentino", "$", "🇦🇷", "es-AR", 2, true),
        CurrencyOption("BRL", "Real Brasileño", "R$", "🇧🇷", "pt-BR", 2, true),
        CurrencyOption("GBP", "Libra Esterlina", "£", "🇬🇧", "en-GB", 2, true),
        CurrencyOption("CAD", "Dólar Canadiense", "CA$", "🇨🇦", "en-CA", 2, true),
        CurrencyOption("CLP", "Peso Chileno", "$", "🇨🇱", "es-CL", 0, true),
        CurrencyOption("PEN", "Sol Peruano", "S/", "🇵🇪", "es-PE", 2, true),
        CurrencyOption("UYU", "Peso Uruguayo", "\$U", "🇺🇾", "es-UY", 2, true),
        CurrencyOption("CRC", "Colón Costarricense", "₡", "🇨🇷", "es-CR", 0, true),
        CurrencyOption("GTQ", "Quetzal Guatemalteco", "Q", "🇬🇹", "es-GT", 2, true),
        CurrencyOption("JPY", "Yen Japonés", "¥", "🇯🇵", "ja-JP", 0, true),
        CurrencyOption("CHF", "Franco Suizo", "CHF", "🇨🇭", "de-CH", 2, true),
        CurrencyOption("AUD", "Dólar Australiano", "A$", "🇦🇺", "en-AU", 2, true)
    )

    private val currencyOptionsByCode: Map<String, CurrencyOption> =
        availableCurrencies.associateBy { it.code }

    private var currentCurrency: String = DEFAULT_CURRENCY_CODE
    private val _currencyFlow = MutableStateFlow(DEFAULT_CURRENCY_CODE)
    val currencyFlow: StateFlow<String> = _currencyFlow.asStateFlow()

    private val _currencyOptionFlow = MutableStateFlow(
        currencyOptionsByCode[DEFAULT_CURRENCY_CODE] ?: availableCurrencies.first()
    )
    val currencyOptionFlow: StateFlow<CurrencyOption> = _currencyOptionFlow.asStateFlow()

    // Formatters cache by currency code to guarantee maximum speed and zero memory thrashing
    private val standardFormatters = ConcurrentHashMap<String, DecimalFormat>()
    private val noDecimalFormatters = ConcurrentHashMap<String, DecimalFormat>()

    /**
     * Initializes the currency preferences from Android SharedPreferences.
     */
    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_SELECTED_CURRENCY, DEFAULT_CURRENCY_CODE) ?: DEFAULT_CURRENCY_CODE
        val validCode = if (currencyOptionsByCode.containsKey(saved)) saved else DEFAULT_CURRENCY_CODE
        currentCurrency = validCode
        _currencyFlow.value = validCode
        _currencyOptionFlow.value = getCurrencyOption(validCode)
    }

    /**
     * Updates the active system-wide currency preference and persists it.
     */
    fun setCurrency(context: Context, currencyCode: String) {
        val validCode = if (currencyOptionsByCode.containsKey(currencyCode)) currencyCode else DEFAULT_CURRENCY_CODE
        currentCurrency = validCode
        _currencyFlow.value = validCode
        _currencyOptionFlow.value = getCurrencyOption(validCode)

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_SELECTED_CURRENCY, validCode).apply()
    }

    /**
     * Returns the currently active currency code (e.g., "DOP", "USD").
     */
    fun getCurrency(): String = currentCurrency

    /**
     * Returns the currency option for the current or specified currency code.
     */
    fun getCurrencyOption(currencyCode: String? = null): CurrencyOption {
        val code = currencyCode ?: currentCurrency
        return currencyOptionsByCode[code] ?: availableCurrencies.first()
    }

    /**
     * Returns the active currency symbol (e.g. "RD$", "$", "€", "£").
     */
    fun getCurrencySymbol(currencyCode: String? = null): String {
        return getCurrencyOption(currencyCode).symbol
    }

    /**
     * Formats a monetary amount into a clean, localized string using the current or specified currency.
     *
     * Example outputs:
     * - DOP: "RD$ 1,250.00" / "-RD$ 1,250.00"
     * - USD: "$1,250.00" / "-$1,250.00"
     * - EUR: "1.250,00 €" / "-1.250,00 €"
     */
    fun format(
        amount: Double,
        currencyCode: String? = null,
        includeDecimals: Boolean = true
    ): String {
        val option = getCurrencyOption(currencyCode)
        val isNeg = amount < 0
        val absAmount = abs(amount)

        val fractionDigits = if (includeDecimals) option.standardFractionDigits else 0
        val formatter = getOrCreateFormatter(option, fractionDigits)

        val formattedNumber = synchronized(formatter) {
            formatter.format(absAmount)
        }

        val formattedWithSymbol = if (option.isSymbolPrefix) {
            val separator = if (option.symbol.length > 1) " " else ""
            "${option.symbol}$separator$formattedNumber"
        } else {
            "$formattedNumber ${option.symbol}"
        }

        return if (isNeg) "-$formattedWithSymbol" else formattedWithSymbol
    }

    /**
     * Formats a monetary amount with an explicit positive (+) or negative (-) prefix.
     */
    fun formatSigned(
        amount: Double,
        currencyCode: String? = null,
        explicitPositive: Boolean = true
    ): String {
        val option = getCurrencyOption(currencyCode)
        val isNeg = amount < 0
        val formatted = format(abs(amount), currencyCode, true)
        return when {
            isNeg -> "-$formatted"
            explicitPositive && amount > 0 -> "+$formatted"
            else -> formatted
        }
    }

    /**
     * Formats large monetary values compactly (e.g., "$1.2K", "RD$ 3.5M", "€10.4B").
     */
    fun formatCompact(
        amount: Double,
        currencyCode: String? = null
    ): String {
        val option = getCurrencyOption(currencyCode)
        val isNeg = amount < 0
        val absVal = abs(amount)

        val (scaledValue, suffix) = when {
            absVal >= 1_000_000_000 -> Pair(absVal / 1_000_000_000.0, "B")
            absVal >= 1_000_000 -> Pair(absVal / 1_000_000.0, "M")
            absVal >= 1_000 -> Pair(absVal / 1_000.0, "K")
            else -> Pair(absVal, "")
        }

        val formattedNumber = if (suffix.isEmpty()) {
            format(absVal, currencyCode, option.standardFractionDigits > 0)
        } else {
            val numStr = String.format(Locale.US, "%.1f", scaledValue).removeSuffix(".0")
            if (option.isSymbolPrefix) {
                val sep = if (option.symbol.length > 1) " " else ""
                "${option.symbol}$sep$numStr$suffix"
            } else {
                "$numStr$suffix ${option.symbol}"
            }
        }

        return if (isNeg && !formattedNumber.startsWith("-")) "-$formattedNumber" else formattedNumber
    }

    /**
     * Formats whole units without decimals regardless of currency configuration.
     */
    fun formatWithoutDecimals(
        amount: Double,
        currencyCode: String? = null
    ): String {
        return format(amount, currencyCode, includeDecimals = false)
    }

    /**
     * Deconstructs a monetary value into parts (symbol, whole number, decimals) for rich UI typography.
     */
    fun formatParts(
        amount: Double,
        currencyCode: String? = null
    ): FormattedCurrencyParts {
        val option = getCurrencyOption(currencyCode)
        val isNeg = amount < 0
        val absAmount = abs(amount)
        val formatter = getOrCreateFormatter(option, option.standardFractionDigits)

        val formattedNumber = synchronized(formatter) {
            formatter.format(absAmount)
        }

        val decimalSeparator = formatter.decimalFormatSymbols.decimalSeparator
        val parts = formattedNumber.split(decimalSeparator)
        val whole = parts.getOrNull(0) ?: "0"
        val fraction = if (parts.size > 1) parts[1] else ""

        val full = format(amount, currencyCode)
        return FormattedCurrencyParts(
            symbol = option.symbol,
            wholePart = whole,
            decimalPart = fraction,
            isNegative = isNeg,
            fullFormatted = full
        )
    }

    /**
     * Parses a string representation of money entered by a user into a nullable Double.
     * Sanitizes currency symbols, handles comma vs. dot decimal conventions automatically.
     */
    fun parseAmount(input: String?): Double? {
        if (input.isNullOrBlank()) return null

        var cleaned = input.trim()
        // Remove common currency symbols and spaces
        cleaned = cleaned.replace(Regex("[^0-9.,\\-]"), "")
        if (cleaned.isEmpty() || cleaned == "-") return null

        val isNeg = cleaned.startsWith("-")
        if (isNeg) {
            cleaned = cleaned.removePrefix("-")
        }

        // Determine decimal separator: if both comma and dot exist
        val lastDot = cleaned.lastIndexOf('.')
        val lastComma = cleaned.lastIndexOf(',')

        val normalized = when {
            lastDot != -1 && lastComma != -1 -> {
                if (lastDot > lastComma) {
                    // 1,234.56 -> remove commas
                    cleaned.replace(",", "")
                } else {
                    // 1.234,56 -> remove dots, replace comma with dot
                    cleaned.replace(".", "").replace(',', '.')
                }
            }
            lastComma != -1 && lastDot == -1 -> {
                // If comma is followed by 1 or 2 digits at end, treat as decimal separator: 1250,50 -> 1250.50
                val afterComma = cleaned.substring(lastComma + 1)
                if (afterComma.length <= 2 && cleaned.count { it == ',' } == 1) {
                    cleaned.replace(',', '.')
                } else {
                    // Thousands separator: 1,250,000 -> 1250000
                    cleaned.replace(",", "")
                }
            }
            else -> cleaned
        }

        return try {
            val result = normalized.toDouble()
            if (isNeg) -result else result
        } catch (e: NumberFormatException) {
            null
        }
    }

    /**
     * Sanitizes raw text field input to ensure only numbers and a single valid decimal separator are typed.
     */
    fun sanitizeDecimalInput(input: String, maxDecimals: Int = 2): String {
        if (input.isEmpty()) return ""
        val normalized = input.replace(',', '.')
        val dotCount = normalized.count { it == '.' }
        if (dotCount > 1) return input.dropLast(1)

        val regex = if (maxDecimals > 0) "^\\d*\\.?\\d{0,$maxDecimals}$" else "^\\d*$"
        return if (normalized.matches(Regex(regex))) normalized else input.dropLast(1)
    }

    /**
     * Returns curated quick-add suggestions tailored to the active currency scale.
     */
    fun getQuickAddSuggestions(currencyCode: String? = null): List<Double> {
        val code = currencyCode ?: currentCurrency
        return when (code) {
            "CLP", "COP", "JPY", "CRC" -> listOf(1000.0, 5000.0, 10000.0, 20000.0, 50000.0)
            "USD", "EUR", "GBP", "CAD", "CHF", "AUD" -> listOf(5.0, 10.0, 20.0, 50.0, 100.0)
            else -> listOf(100.0, 500.0, 1000.0, 2000.0, 5000.0)
        }
    }

    private fun getOrCreateFormatter(option: CurrencyOption, fractionDigits: Int): DecimalFormat {
        val cacheKey = "${option.code}_$fractionDigits"
        return standardFormatters.computeIfAbsent(cacheKey) {
            val locale = Locale.forLanguageTag(option.localeTag)
            val symbols = DecimalFormatSymbols(locale)
            
            // For custom symbol placement & clean typography:
            val pattern = if (fractionDigits > 0) {
                "#,##0." + "0".repeat(fractionDigits)
            } else {
                "#,##0"
            }
            DecimalFormat(pattern, symbols).apply {
                minimumFractionDigits = fractionDigits
                maximumFractionDigits = fractionDigits
            }
        }
    }

    // ==================== COMPOSE REACTIVE HELPERS ====================

    @Composable
    fun rememberCurrentCurrency(): State<String> {
        return currencyFlow.collectAsStateWithLifecycle()
    }

    @Composable
    fun rememberCurrentCurrencyOption(): State<CurrencyOption> {
        return currencyOptionFlow.collectAsStateWithLifecycle()
    }

    @Composable
    fun rememberCurrencySymbol(): State<String> {
        val optionState = currencyOptionFlow.collectAsStateWithLifecycle()
        return androidx.compose.runtime.derivedStateOf { optionState.value.symbol }
    }
}
