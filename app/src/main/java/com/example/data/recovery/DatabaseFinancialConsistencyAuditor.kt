package com.example.data.recovery

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.example.data.local.AppDatabase
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Properties
import java.util.UUID

/**
 * Read-only financial/business invariant scan over a separately validated workspace.
 *
 * This class never opens the original database or raw snapshot. It refuses any
 * workspace without a matching successful SQLite/schema validation report.
 * Findings are classifications only: no row is corrected, deleted, merged or
 * otherwise changed. A completed scan never authorizes Room.
 */
object DatabaseFinancialConsistencyAuditor {

    private const val VALIDATION_FORMAT = "INSERC_SQLITE_VALIDATION_REPORT_1"
    private const val AUDIT_FORMAT = "INSERC_FINANCIAL_CONSISTENCY_REPORT_1"
    private const val DATABASE_NAME = "database.sqlite"
    private const val VALIDATION_REPORT = "validation-report.properties"
    private const val MAX_ISSUES = 10_000

    enum class Severity { ERROR, REVIEW }

    data class Issue(
        val table: String,
        val rowId: Long,
        val code: String,
        val severity: Severity,
        val explanation: String
    )

    sealed interface Result {
        data class Completed(
            val snapshotId: String,
            val reportFile: File,
            val recordsExamined: Long,
            val issues: List<Issue>,
            val databaseSha256Before: String,
            val databaseSha256After: String,
            val databaseUnchanged: Boolean,
            val roomAuthorized: Boolean
        ) : Result

        data class Rejected(val reasonCode: String) : Result
    }

    private data class TransferRow(
        val id: Long,
        val amount: Double,
        val date: Long,
        val accountId: Long,
        val tags: String?,
        val description: String?
    )

    fun audit(context: Context, validated: SnapshotValidationResult.Validated): Result {
        val appContext = context.applicationContext
        var database: SQLiteDatabase? = null

        return try {
            if (DatabaseAccessGate.isNormalAccessAllowed() || AppDatabase.hasInstance() || validated.authorizedForRoom) {
                return Result.Rejected("ROOM_ACCESS_MUST_REMAIN_DISABLED")
            }

            val privateRoot = appContext.filesDir.canonicalFile
            val workspace = validated.workspaceDirectory.canonicalFile
            val databaseFile = validated.validatedDatabaseFile.canonicalFile
            val validationReport = validated.reportFile.canonicalFile

            if (FilesSupport.isSymbolicLink(validated.workspaceDirectory) ||
                FilesSupport.isSymbolicLink(validated.validatedDatabaseFile) ||
                FilesSupport.isSymbolicLink(validated.reportFile)
            ) {
                return Result.Rejected("VALIDATION_WORKSPACE_SYMLINK_REJECTED")
            }
            val validationRoot = File(privateRoot, "recovery/validation").canonicalFile
            if (!workspace.path.startsWith(validationRoot.path + File.separator) ||
                workspace.name.isBlank() ||
                databaseFile.parentFile != workspace ||
                databaseFile.name != DATABASE_NAME ||
                validationReport.parentFile != workspace ||
                validationReport.name != VALIDATION_REPORT ||
                !databaseFile.isFile || !validationReport.isFile
            ) {
                return Result.Rejected("VALIDATION_WORKSPACE_PATH_INVALID")
            }

            val validationEvidence = Properties().apply {
                FileInputStream(validationReport).use(::load)
            }
            if (validationEvidence.getProperty("format") != VALIDATION_FORMAT ||
                validationEvidence.getProperty("snapshot_id") != validated.snapshotId ||
                validationEvidence.getProperty("workspace_id") != workspace.name ||
                validationEvidence.getProperty("schema_version") != "14" ||
                validationEvidence.getProperty("quick_check") != "ok" ||
                validationEvidence.getProperty("integrity_check") != "ok" ||
                validationEvidence.getProperty("schema_validated") != "true" ||
                validationEvidence.getProperty("sqlite_integrity_checks_passed") != "true" ||
                validationEvidence.getProperty("normal_access_authorized") != "false" ||
                validationEvidence.getProperty("financial_consistency_validated") != "false"
            ) {
                return Result.Rejected("VALIDATION_EVIDENCE_NOT_ELIGIBLE")
            }

            val expectedHash = validationEvidence.getProperty("database_sha256_after_validation")
                ?.takeIf { it.matches(Regex("[a-fA-F0-9]{64}")) }
                ?: return Result.Rejected("VALIDATION_DATABASE_HASH_MISSING")
            val beforeHash = sha256(databaseFile)
            if (!beforeHash.equals(expectedHash, ignoreCase = true)) {
                return Result.Rejected("VALIDATED_DATABASE_HASH_CHANGED")
            }

            val issues = mutableListOf<Issue>()
            var recordsExamined = 0L

            // The only SQLite handle in this class is explicitly read-only.
            database = SQLiteDatabase.openDatabase(
                databaseFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY
            )
            val db = checkNotNull(database)
            val accountIds = readIds(db, "accounts")
            val categoryTypes = readCategoryTypes(db)
            val goalIds = readIds(db, "savings_goals")

            recordsExamined += countRows(db, "transactions")
            recordsExamined += countRows(db, "categories")
            recordsExamined += countRows(db, "accounts")
            recordsExamined += countRows(db, "savings_goals")
            recordsExamined += countRows(db, "reminders")
            recordsExamined += countRows(db, "recurring_expenses")
            recordsExamined += countRows(db, "businesses")

            inspectTransactions(db, accountIds, categoryTypes, issues)
            inspectTransferPairs(db, issues)
            inspectCategories(db, issues)
            inspectGoals(db, issues)
            inspectReminders(db, goalIds, issues)
            inspectRecurringExpenses(db, accountIds, categoryTypes.keys, issues)
            inspectBusinesses(db, issues)

            db.close()
            database = null

            val afterHash = sha256(databaseFile)
            if (!beforeHash.equals(afterHash, ignoreCase = true)) {
                return Result.Rejected("DATABASE_CHANGED_DURING_READ_ONLY_AUDIT")
            }

            val reportId = UUID.randomUUID().toString()
            val report = Properties().apply {
                setProperty("format", AUDIT_FORMAT)
                setProperty("snapshot_id", validated.snapshotId)
                setProperty("workspace_id", workspace.name)
                setProperty("database_sha256_before", beforeHash)
                setProperty("database_sha256_after", afterHash)
                setProperty("records_examined", recordsExamined.toString())
                setProperty("issue_count", issues.size.toString())
                setProperty("error_count", issues.count { it.severity == Severity.ERROR }.toString())
                setProperty("review_count", issues.count { it.severity == Severity.REVIEW }.toString())
                setProperty("scan_completed", "true")
                setProperty("database_unchanged", "true")
                setProperty("financial_consistency_passed", issues.isEmpty().toString())
                setProperty("financial_consistency_scan_completed", "true")
                setProperty("financial_consistency_validated", issues.isEmpty().toString())
                setProperty("normal_access_authorized", "false")
                setProperty("room_authorized", "false")
                issues.forEachIndexed { index, issue ->
                    val prefix = "issue.${index.toString().padStart(5, '0')}."
                    setProperty(prefix + "table", issue.table)
                    setProperty(prefix + "row_id", issue.rowId.toString())
                    setProperty(prefix + "code", issue.code)
                    setProperty(prefix + "severity", issue.severity.name)
                    setProperty(prefix + "explanation", issue.explanation)
                }
            }
            val reportFile = File(workspace, "financial-consistency-report-$reportId.properties")
            val temporary = File(workspace, reportFile.name + ".tmp")
            FileOutputStream(temporary).use { output ->
                report.store(output, "Read-only audit findings; this report never authorizes Room")
                output.fd.sync()
            }
            if (!temporary.renameTo(reportFile)) {
                temporary.delete()
                return Result.Rejected("FINANCIAL_REPORT_PUBLISH_FAILED")
            }

            Result.Completed(
                snapshotId = validated.snapshotId,
                reportFile = reportFile,
                recordsExamined = recordsExamined,
                issues = issues.toList(),
                databaseSha256Before = beforeHash,
                databaseSha256After = afterHash,
                databaseUnchanged = true,
                roomAuthorized = false
            )
        } catch (_: Exception) {
            try {
                database?.close()
            } catch (_: Exception) {
                // Fail closed; the validated database is never changed here.
            }
            Result.Rejected("FINANCIAL_AUDIT_FAILED_CLOSED")
        }
    }

    private fun inspectTransactions(
        db: SQLiteDatabase,
        accountIds: Set<Long>,
        categoryTypes: Map<Long, String>,
        issues: MutableList<Issue>
    ) {
        db.rawQuery(
            "SELECT id, amount, type, categoryId, accountId FROM transactions",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                val amount = cursor.getDouble(1)
                val type = cursor.getString(2)
                val categoryId = cursor.getLong(3)
                val accountId = cursor.getLong(4)
                if (!amount.isFinite() || amount <= 0.0) {
                    add(issues, Issue("transactions", id, "TRANSACTION_AMOUNT_NOT_POSITIVE_FINITE",
                        Severity.ERROR, "El importe no es un número finito mayor que cero."))
                }
                if (type !in setOf("INCOME", "EXPENSE", "TRANSFER")) {
                    add(issues, Issue("transactions", id, "UNKNOWN_TRANSACTION_TYPE",
                        Severity.ERROR, "El tipo de transacción no coincide con los tipos admitidos."))
                }
                if (accountId !in accountIds) {
                    add(issues, Issue("transactions", id, "ORPHAN_TRANSACTION_ACCOUNT",
                        Severity.ERROR, "La transacción referencia una cuenta inexistente."))
                }
                val categoryType = categoryTypes[categoryId]
                if (categoryType == null) {
                    add(issues, Issue("transactions", id, "ORPHAN_TRANSACTION_CATEGORY",
                        Severity.REVIEW, "La categoría referenciada no existe; requiere revisión manual."))
                } else if (type == "INCOME" && categoryType == "EXPENSE" ||
                    type == "EXPENSE" && categoryType == "INCOME"
                ) {
                    add(issues, Issue("transactions", id, "TRANSACTION_CATEGORY_TYPE_MISMATCH",
                        Severity.REVIEW, "El tipo de categoría no coincide con el tipo del movimiento."))
                }
            }
        }
    }

    private fun inspectTransferPairs(db: SQLiteDatabase, issues: MutableList<Issue>) {
        val rows = mutableListOf<TransferRow>()
        db.rawQuery(
            """SELECT id, amount, date, accountId, tags, description
               FROM transactions
               WHERE type = 'TRANSFER'
                  OR upper(coalesce(tags, '')) LIKE '%TRANSFER%'
                  OR lower(coalesce(description, '')) LIKE 'transferencia a%'
                  OR lower(coalesce(description, '')) LIKE 'transferencia desde%'""".trimIndent(),
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                rows += TransferRow(
                    id = cursor.getLong(0),
                    amount = cursor.getDouble(1),
                    date = cursor.getLong(2),
                    accountId = cursor.getLong(3),
                    tags = if (cursor.isNull(4)) null else cursor.getString(4),
                    description = if (cursor.isNull(5)) null else cursor.getString(5)
                )
            }
        }

        val groups = linkedMapOf<String, MutableList<Pair<TransferRow, String?>>>()
        rows.forEach { row ->
            val tokens = row.tags.orEmpty().split(',')
                .map { it.trim() }
            val correlation = tokens.firstOrNull { it.startsWith("CORR:", ignoreCase = true) }
                ?.substringAfter(':')
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
            val direction = when {
                tokens.any { it.equals("OUT", ignoreCase = true) } -> "OUT"
                tokens.any { it.equals("IN", ignoreCase = true) } -> "IN"
                else -> null
            }
            if (correlation == null) {
                add(issues, Issue("transactions", row.id, "TRANSFER_WITHOUT_CORRELATION_ID",
                    Severity.REVIEW, "Movimiento de transferencia sin identificador de correlación verificable."))
            } else {
                groups.getOrPut(correlation) { mutableListOf() }.add(row to direction)
            }
        }

        groups.values.forEach { pair ->
            val outgoing = pair.filter { it.second == "OUT" }
            val incoming = pair.filter { it.second == "IN" }
            val validPair = pair.size == 2 &&
                outgoing.size == 1 && incoming.size == 1 &&
                outgoing.single().first.accountId != incoming.single().first.accountId &&
                outgoing.single().first.date == incoming.single().first.date &&
                outgoing.single().first.amount.isFinite() &&
                incoming.single().first.amount.isFinite() &&
                outgoing.single().first.amount == incoming.single().first.amount

            if (!validPair) {
                val rowId = pair.minOf { it.first.id }
                add(issues, Issue("transactions", rowId, "TRANSFER_CORRELATION_PAIR_INCOMPLETE_OR_INCONSISTENT",
                    Severity.REVIEW, "El grupo correlacionado no contiene exactamente un OUT y un IN compatibles."))
            }
        }
    }

    private fun inspectCategories(db: SQLiteDatabase, issues: MutableList<Issue>) {
        db.rawQuery("SELECT id, name, type, maxMonthlyBudget FROM categories", null).use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                val name = cursor.getString(1)
                val type = cursor.getString(2)
                if (name.isNullOrBlank()) {
                    add(issues, Issue("categories", id, "CATEGORY_NAME_EMPTY",
                        Severity.REVIEW, "La categoría no tiene un nombre visible."))
                }
                if (type !in setOf("EXPENSE", "INCOME", "ALL")) {
                    add(issues, Issue("categories", id, "UNKNOWN_CATEGORY_TYPE",
                        Severity.REVIEW, "El tipo de categoría no coincide con los valores conocidos."))
                }
                if (!cursor.isNull(3)) {
                    val budget = cursor.getDouble(3)
                    if (!budget.isFinite() || budget < 0.0) {
                        add(issues, Issue("categories", id, "CATEGORY_BUDGET_INVALID",
                            Severity.REVIEW, "El presupuesto mensual es negativo o no finito."))
                    }
                }
            }
        }
    }

    private fun inspectGoals(db: SQLiteDatabase, issues: MutableList<Issue>) {
        db.rawQuery("SELECT id, targetAmount, currentAmount FROM savings_goals", null).use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                val target = cursor.getDouble(1)
                val current = cursor.getDouble(2)
                if (!target.isFinite() || target <= 0.0) {
                    add(issues, Issue("savings_goals", id, "SAVINGS_GOAL_TARGET_INVALID",
                        Severity.ERROR, "El objetivo no es finito o no es mayor que cero."))
                }
                if (!current.isFinite() || current < 0.0) {
                    add(issues, Issue("savings_goals", id, "SAVINGS_GOAL_CURRENT_AMOUNT_INVALID",
                        Severity.ERROR, "El ahorro acumulado es negativo o no finito."))
                } else if (target.isFinite() && target > 0.0 && current > target) {
                    add(issues, Issue("savings_goals", id, "SAVINGS_GOAL_EXCEEDS_TARGET",
                        Severity.REVIEW, "El ahorro acumulado supera el objetivo; se conserva y no se ajusta."))
                }
            }
        }
    }

    private fun inspectReminders(
        db: SQLiteDatabase,
        goalIds: Set<Long>,
        issues: MutableList<Issue>
    ) {
        db.rawQuery(
            "SELECT id, dueDateDay, amount, notifyDaysBefore, associatedGoalId FROM reminders",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                if (!cursor.isNull(1) && cursor.getInt(1) !in 1..31) {
                    add(issues, Issue("reminders", id, "REMINDER_DUE_DAY_OUT_OF_RANGE",
                        Severity.REVIEW, "El día de vencimiento está fuera del rango 1..31."))
                }
                if (!cursor.isNull(2)) {
                    val amount = cursor.getDouble(2)
                    if (!amount.isFinite() || amount < 0.0) {
                        add(issues, Issue("reminders", id, "REMINDER_AMOUNT_INVALID",
                            Severity.REVIEW, "El importe del recordatorio es negativo o no finito."))
                    }
                }
                if (cursor.getInt(3) < 0) {
                    add(issues, Issue("reminders", id, "REMINDER_NOTICE_DAYS_NEGATIVE",
                        Severity.REVIEW, "Los días de aviso previos no pueden ser negativos."))
                }
                if (!cursor.isNull(4) && cursor.getLong(4) !in goalIds) {
                    add(issues, Issue("reminders", id, "REMINDER_GOAL_REFERENCE_MISSING",
                        Severity.REVIEW, "El recordatorio referencia una meta de ahorro inexistente."))
                }
            }
        }
    }

    private fun inspectRecurringExpenses(
        db: SQLiteDatabase,
        accountIds: Set<Long>,
        categoryIds: Set<Long>,
        issues: MutableList<Issue>
    ) {
        db.rawQuery(
            "SELECT id, amount, categoryId, accountId, dayOfMonth, frequency, lastExecutedMonth FROM recurring_expenses",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                val amount = cursor.getDouble(1)
                val categoryId = cursor.getLong(2)
                val accountId = cursor.getLong(3)
                val day = cursor.getInt(4)
                val frequency = cursor.getString(5)
                val lastExecutedMonth = if (cursor.isNull(6)) null else cursor.getString(6)

                if (!amount.isFinite() || amount <= 0.0) {
                    add(issues, Issue("recurring_expenses", id, "RECURRING_AMOUNT_NOT_POSITIVE_FINITE",
                        Severity.ERROR, "El importe recurrente no es finito o no es mayor que cero."))
                }
                if (categoryId !in categoryIds) {
                    add(issues, Issue("recurring_expenses", id, "RECURRING_CATEGORY_MISSING",
                        Severity.ERROR, "El gasto recurrente referencia una categoría inexistente."))
                }
                // accountId=0 is the entity's declared unassigned/default sentinel.
                if (accountId != 0L && accountId !in accountIds) {
                    add(issues, Issue("recurring_expenses", id, "RECURRING_ACCOUNT_MISSING",
                        Severity.REVIEW, "El gasto recurrente referencia una cuenta inexistente."))
                }
                if (day !in 1..31) {
                    add(issues, Issue("recurring_expenses", id, "RECURRING_DAY_OUT_OF_RANGE",
                        Severity.ERROR, "El día recurrente está fuera del rango 1..31."))
                }
                if (frequency !in setOf("WEEKLY", "BIWEEKLY", "MONTHLY", "YEARLY")) {
                    add(issues, Issue("recurring_expenses", id, "RECURRING_FREQUENCY_UNKNOWN",
                        Severity.REVIEW, "La frecuencia no es reconocida; la aplicación podría sustituirla por MONTHLY."))
                }
                if (lastExecutedMonth != null &&
                    !Regex("^\\d{4}-(0[1-9]|1[0-2])$").matches(lastExecutedMonth)
                ) {
                    add(issues, Issue("recurring_expenses", id, "RECURRING_LAST_MONTH_INVALID",
                        Severity.REVIEW, "El último mes ejecutado no tiene formato yyyy-MM válido."))
                }
            }
        }
    }

    private fun inspectBusinesses(db: SQLiteDatabase, issues: MutableList<Issue>) {
        db.rawQuery(
            "SELECT id, name, type, description, colorHex, iconKey, inventoryValue, createdAt FROM businesses",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                val name = cursor.getString(1)
                val type = cursor.getString(2)
                val description = cursor.getString(3)
                val colorHex = cursor.getString(4)
                val iconKey = cursor.getString(5)
                val inventoryValue = cursor.getDouble(6)

                if (name.isNullOrBlank()) {
                    add(issues, Issue("businesses", id, "BUSINESS_NAME_EMPTY",
                        Severity.REVIEW, "El negocio no tiene un nombre visible."))
                }
                if (!inventoryValue.isFinite() || inventoryValue < 0.0) {
                    add(issues, Issue("businesses", id, "BUSINESS_INVENTORY_VALUE_INVALID",
                        Severity.REVIEW, "El valor de inventario es negativo o no finito."))
                }

                // An older onOpen callback inserted this row with System.currentTimeMillis().
                // Its timestamp is not a reliable fingerprint, so preserve it and require
                // human review instead of deleting a potentially user-created business.
                val ambiguousLegacySeed =
                    id == 1L &&
                    name == "INSERC Store" &&
                    type == "Tecnología y Comercio" &&
                    description == "Negocio comercial principal" &&
                    colorHex == "#2563EB" &&
                    iconKey == "storefront" &&
                    inventoryValue == 0.0
                if (ambiguousLegacySeed) {
                    add(issues, Issue("businesses", id, "BUSINESS_POSSIBLE_LEGACY_PLACEHOLDER",
                        Severity.REVIEW,
                        "Este negocio coincide con un marcador antiguo de fecha variable. Se conserva y requiere confirmación manual."))
                }
            }
        }
    }

    private fun readIds(db: SQLiteDatabase, table: String): Set<Long> {
        val values = linkedSetOf<Long>()
        db.rawQuery("SELECT id FROM $table", null).use { cursor ->
            while (cursor.moveToNext()) values += cursor.getLong(0)
        }
        return values
    }

    private fun readCategoryTypes(db: SQLiteDatabase): Map<Long, String> {
        val values = linkedMapOf<Long, String>()
        db.rawQuery("SELECT id, type FROM categories", null).use { cursor ->
            while (cursor.moveToNext()) values[cursor.getLong(0)] = cursor.getString(1)
        }
        return values
    }

    private fun countRows(db: SQLiteDatabase, table: String): Long =
        db.rawQuery("SELECT COUNT(*) FROM $table", null).use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else 0L
        }

    private fun add(issues: MutableList<Issue>, issue: Issue) {
        if (issues.size < MAX_ISSUES) issues += issue
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private object FilesSupport {
        fun isSymbolicLink(file: File): Boolean = try {
            java.nio.file.Files.isSymbolicLink(file.toPath())
        } catch (_: Exception) {
            true
        }
    }
}
