package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FinancialHealthCalculatorTest {

    @Test
    fun testHealthyFinancesGivesHighScore() {
        val result = FinancialHealthCalculator.calculate(
            currentMonthIncome = 5000.0,
            currentMonthExpense = 2000.0,
            totalBudgetLimit = 3500.0,
            currentBalance = 15000.0
        )

        assertNotNull(result)
        assertTrue("Health score should be >= 80 for low expense ratio and high balance", result.score >= 80)
        assertEquals("Excelente", result.statusLabel)
        assertTrue("Daily recommended budget should be positive", result.dailyRecommendedBudget > 0)
        assertTrue("Days left should be >= 1", result.daysLeftInMonth >= 1)
        assertTrue("Savings rate should be positive", result.savingsRate > 0)
    }

    @Test
    fun testDeficitGivesAttentionRequired() {
        val result = FinancialHealthCalculator.calculate(
            currentMonthIncome = 2000.0,
            currentMonthExpense = 4500.0,
            totalBudgetLimit = 2500.0,
            currentBalance = -500.0
        )

        assertNotNull(result)
        assertTrue("Health score should be <= 50 for severe deficit", result.score <= 50)
        assertEquals("Atención Requerida", result.statusLabel)
        assertTrue("Savings rate should be negative", result.savingsRate < 0)
    }
}
