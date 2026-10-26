package com.example.presentation.common.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CurrencyFormatterTest {

    @Test
    fun testCurrencyFormattingDefaults() {
        val formattedDop = CurrencyFormatter.format(1250.50, currencyCode = "DOP")
        assertTrue(formattedDop.contains("RD$"))
        assertTrue(formattedDop.contains("1,250.50"))

        val formattedUsd = CurrencyFormatter.format(1250.50, currencyCode = "USD")
        assertEquals("$1,250.50", formattedUsd)

        val formattedEur = CurrencyFormatter.format(1250.50, currencyCode = "EUR")
        assertTrue(formattedEur.contains("€"))
    }

    @Test
    fun testNegativeCurrencyFormatting() {
        val negativeDop = CurrencyFormatter.format(-500.0, currencyCode = "DOP")
        assertTrue(negativeDop.startsWith("-RD$"))

        val negativeUsd = CurrencyFormatter.format(-500.0, currencyCode = "USD")
        assertEquals("-$500.00", negativeUsd)
    }

    @Test
    fun testSignedCurrencyFormatting() {
        val positiveSigned = CurrencyFormatter.formatSigned(100.0, currencyCode = "USD", explicitPositive = true)
        assertEquals("+$100.00", positiveSigned)

        val negativeSigned = CurrencyFormatter.formatSigned(-100.0, currencyCode = "USD", explicitPositive = true)
        assertEquals("-$100.00", negativeSigned)
    }

    @Test
    fun testCompactCurrencyFormatting() {
        val compactThousand = CurrencyFormatter.formatCompact(1500.0, currencyCode = "USD")
        assertEquals("$1.5K", compactThousand)

        val compactMillion = CurrencyFormatter.formatCompact(2500000.0, currencyCode = "USD")
        assertEquals("$2.5M", compactMillion)

        val compactSmall = CurrencyFormatter.formatCompact(250.0, currencyCode = "USD")
        assertEquals("$250.00", compactSmall)
    }

    @Test
    fun testAmountParsing() {
        assertEquals(1250.50, CurrencyFormatter.parseAmount("1250.50")!!, 0.001)
        assertEquals(1250.50, CurrencyFormatter.parseAmount("1,250.50")!!, 0.001)
        assertEquals(1250.50, CurrencyFormatter.parseAmount("RD$ 1,250.50")!!, 0.001)
        assertEquals(1250.50, CurrencyFormatter.parseAmount("$1,250.50")!!, 0.001)
        assertEquals(-75.25, CurrencyFormatter.parseAmount("-75.25")!!, 0.001)
        assertNull(CurrencyFormatter.parseAmount(""))
        assertNull(CurrencyFormatter.parseAmount("abc"))
    }

    @Test
    fun testSanitizeDecimalInput() {
        assertEquals("123.45", CurrencyFormatter.sanitizeDecimalInput("123.45", maxDecimals = 2))
        assertEquals("123.45", CurrencyFormatter.sanitizeDecimalInput("123,45", maxDecimals = 2))
        assertEquals("123.", CurrencyFormatter.sanitizeDecimalInput("123..", maxDecimals = 2))
    }

    @Test
    fun testFormatParts() {
        val parts = CurrencyFormatter.formatParts(1250.75, currencyCode = "USD")
        assertEquals("$", parts.symbol)
        assertEquals("1,250", parts.wholePart)
        assertEquals("75", parts.decimalPart)
        assertEquals(false, parts.isNegative)
    }

    @Test
    fun testQuickAddSuggestions() {
        val usdSuggestions = CurrencyFormatter.getQuickAddSuggestions("USD")
        assertTrue(usdSuggestions.contains(5.0))
        assertTrue(usdSuggestions.contains(100.0))

        val dopSuggestions = CurrencyFormatter.getQuickAddSuggestions("DOP")
        assertTrue(dopSuggestions.contains(100.0))
        assertTrue(dopSuggestions.contains(1000.0))
    }
}
