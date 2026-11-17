package com.example.presentation.common.util

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupRoundTripVerifierTest {

    private fun payload(transactionAmount: Double = 125.0, reverseAccounts: Boolean = false): String {
        val accounts = JSONArray().apply {
            val first = JSONObject().put("id", 1).put("name", "Efectivo")
            val second = JSONObject().put("id", 2).put("name", "Banco")
            if (reverseAccounts) {
                put(second)
                put(first)
            } else {
                put(first)
                put(second)
            }
        }
        return JSONObject().apply {
            put("version", 1)
            put("exportDateMillis", System.currentTimeMillis())
            put("accounts", accounts)
            put("categories", JSONArray().put(JSONObject().put("id", 3).put("name", "Comida")))
            put("transactions", JSONArray().put(
                JSONObject().put("id", 4).put("amount", transactionAmount).put("accountId", 1)
            ))
            put("reminders", JSONArray())
            put("savingsGoals", JSONArray())
            put("recurringExpenses", JSONArray())
            put("businesses", JSONArray())
            put("preferences", JSONObject().put("cycleStartDay", 30))
        }.toString()
    }

    @Test
    fun acceptsSameRecordsEvenWhenArrayOrderAndExportMetadataDiffer() {
        val source = payload(reverseAccounts = false)
        val staged = payload(reverseAccounts = true)

        val result = BackupRoundTripVerifier.verify(source, staged)

        assertTrue(result.isExact)
        assertEquals(4, result.expectedRecordCount)
        assertEquals(4, result.actualRecordCount)
        assertTrue(result.mismatches.isEmpty())
    }

    @Test
    fun rejectsChangedFinancialField() {
        val result = BackupRoundTripVerifier.verify(
            payload(transactionAmount = 125.0),
            payload(transactionAmount = 124.0)
        )

        assertFalse(result.isExact)
        assertTrue(result.mismatches.any { it == "transactions#4: uno o más campos no coinciden" })
    }

    @Test
    fun rejectsMissingRecord() {
        val source = JSONObject(payload())
        source.put("accounts", JSONArray().put(JSONObject().put("id", 1).put("name", "Efectivo"))
            .put(JSONObject().put("id", 2).put("name", "Banco")))
        val staged = JSONObject(payload())
        staged.put("accounts", JSONArray().put(JSONObject().put("id", 1).put("name", "Efectivo")))

        val result = BackupRoundTripVerifier.verify(source.toString(), staged.toString())

        assertFalse(result.isExact)
        assertTrue(result.mismatches.any { it.contains("faltan IDs [2]") })
    }
}
