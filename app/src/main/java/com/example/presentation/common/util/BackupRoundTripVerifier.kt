package com.example.presentation.common.util

import java.math.BigDecimal
import org.json.JSONArray
import org.json.JSONObject

/**
 * Compares the persisted records in a staged database export with the source JSON.
 * Export timestamps and global preferences are intentionally excluded: preferences
 * are applied only after the user authorizes the validated database generation.
 */
object BackupRoundTripVerifier {

    private val requiredCollections = listOf(
        "accounts",
        "categories",
        "transactions",
        "reminders",
        "savingsGoals",
        "recurringExpenses",
        "businesses"
    )

    data class Result(
        val isExact: Boolean,
        val expectedRecordCount: Int,
        val actualRecordCount: Int,
        val mismatches: List<String>
    )

    fun verify(sourceJson: String, stagedExportJson: String): Result {
        val source = JSONObject(sourceJson)
        val staged = JSONObject(stagedExportJson)
        val mismatches = mutableListOf<String>()
        var expectedTotal = 0
        var actualTotal = 0

        requiredCollections.forEach { collection ->
            val expectedArray = source.opt(collection) as? JSONArray
            val actualArray = staged.opt(collection) as? JSONArray
            if (expectedArray == null) {
                mismatches += "$collection: colección ausente en el respaldo original"
                return@forEach
            }
            if (actualArray == null) {
                mismatches += "$collection: colección ausente en la exportación aislada"
                expectedTotal += expectedArray.length()
                return@forEach
            }

            expectedTotal += expectedArray.length()
            actualTotal += actualArray.length()

            val expectedById = indexById(expectedArray)
            val actualById = indexById(actualArray)
            if (expectedById == null || actualById == null) {
                mismatches += "$collection: contiene IDs ausentes o duplicados"
                return@forEach
            }

            val missingIds = expectedById.keys - actualById.keys
            val unexpectedIds = actualById.keys - expectedById.keys
            if (missingIds.isNotEmpty()) {
                mismatches += "$collection: faltan IDs ${missingIds.sorted().take(10)}"
            }
            if (unexpectedIds.isNotEmpty()) {
                mismatches += "$collection: aparecen IDs no esperados ${unexpectedIds.sorted().take(10)}"
            }

            (expectedById.keys intersect actualById.keys).sorted().forEach { id ->
                if (!jsonValuesEqual(expectedById.getValue(id), actualById.getValue(id))) {
                    mismatches += "$collection#$id: uno o más campos no coinciden"
                }
            }
        }

        return Result(
            isExact = mismatches.isEmpty() && expectedTotal == actualTotal,
            expectedRecordCount = expectedTotal,
            actualRecordCount = actualTotal,
            mismatches = mismatches
        )
    }

    private fun indexById(records: JSONArray): Map<Long, JSONObject>? {
        val indexed = linkedMapOf<Long, JSONObject>()
        for (index in 0 until records.length()) {
            val record = records.optJSONObject(index) ?: return null
            if (!record.has("id") || record.isNull("id")) return null
            val id = try {
                record.getLong("id")
            } catch (_: Exception) {
                return null
            }
            if (indexed.put(id, record) != null) return null
        }
        return indexed
    }

    /**
     * Structural JSON equality that does not depend on JSONObject.similar(),
     * which is not available in the Android org.json API used by this project.
     * Object key order is ignored; array order remains significant.
     */
    private fun jsonValuesEqual(expected: Any?, actual: Any?): Boolean {
        if (expected === JSONObject.NULL || actual === JSONObject.NULL) {
            return expected === actual
        }
        if (expected == null || actual == null) return expected == actual

        if (expected is JSONObject && actual is JSONObject) {
            val expectedKeys = expected.keys().asSequence().toSet()
            val actualKeys = actual.keys().asSequence().toSet()
            if (expectedKeys != actualKeys) return false
            return expectedKeys.all { key ->
                jsonValuesEqual(expected.opt(key), actual.opt(key))
            }
        }

        if (expected is JSONArray && actual is JSONArray) {
            if (expected.length() != actual.length()) return false
            return (0 until expected.length()).all { index ->
                jsonValuesEqual(expected.opt(index), actual.opt(index))
            }
        }

        if (expected is Number && actual is Number) {
            return try {
                BigDecimal(expected.toString()).compareTo(BigDecimal(actual.toString())) == 0
            } catch (_: NumberFormatException) {
                expected.toDouble() == actual.toDouble()
            }
        }

        return expected == actual
    }
}
