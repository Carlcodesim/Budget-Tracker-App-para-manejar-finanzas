package com.example.presentation.common.util

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
                if (!expectedById.getValue(id).similar(actualById.getValue(id))) {
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
}
