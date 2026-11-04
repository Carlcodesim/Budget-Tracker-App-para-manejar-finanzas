package com.example.data.recovery

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.Properties
import java.util.UUID

/**
 * Validates a private WORKING COPY derived from a raw snapshot.
 *
 * SQLite is opened read-write only on the disposable validation workspace so
 * SQLite can recover a copied WAL or rollback journal. The raw snapshot and
 * original database are never opened or modified by this validator.
 *
 * A successful result is validation evidence only. It does NOT register an
 * active database, grant DatabaseAccessGate permission, or authorize Room.
 */
object DatabaseSnapshotValidator {

    private const val SNAPSHOT_ROOT = "recovery/snapshots"
    private const val VALIDATION_ROOT = "recovery/validation"
    private const val SNAPSHOT_FORMAT = "INSERC_SQLITE_RAW_SNAPSHOT_1"
    private const val CURRENT_SCHEMA_VERSION = 14
    private const val DATABASE_NAME = "database.sqlite"
    private const val WAL_NAME = "database.sqlite-wal"
    private const val JOURNAL_NAME = "database.sqlite-journal"
    private const val MANIFEST_NAME = "snapshot-manifest.properties"
    private const val BUFFER_SIZE = 64 * 1024

    private data class ColumnSpec(
        val name: String,
        val type: String,
        val notNull: Boolean,
        val primaryKey: Boolean = false,
        val defaultValue: String? = null
    )

    private data class TableSpec(
        val name: String,
        val columns: List<ColumnSpec>,
        val indices: Map<String, List<String>> = emptyMap()
    )

    private val expectedTables = listOf(
        TableSpec(
            "transactions",
            listOf(
                ColumnSpec("id", "INTEGER", true, true),
                ColumnSpec("amount", "REAL", true),
                ColumnSpec("type", "TEXT", true),
                ColumnSpec("categoryId", "INTEGER", true),
                ColumnSpec("description", "TEXT", false),
                ColumnSpec("date", "INTEGER", true),
                ColumnSpec("accountId", "INTEGER", true),
                ColumnSpec("tags", "TEXT", false)
            ),
            mapOf(
                "index_transactions_categoryId" to listOf("categoryId"),
                "index_transactions_date" to listOf("date"),
                "index_transactions_accountId" to listOf("accountId"),
                "index_transactions_type_date" to listOf("type", "date"),
                "index_transactions_accountId_date" to listOf("accountId", "date")
            )
        ),
        TableSpec(
            "categories",
            listOf(
                ColumnSpec("id", "INTEGER", true, true),
                ColumnSpec("name", "TEXT", true),
                ColumnSpec("iconKey", "TEXT", true),
                ColumnSpec("isDefault", "INTEGER", true),
                ColumnSpec("maxMonthlyBudget", "REAL", false),
                ColumnSpec("colorHex", "TEXT", true),
                ColumnSpec("type", "TEXT", true)
            )
        ),
        TableSpec(
            "accounts",
            listOf(
                ColumnSpec("id", "INTEGER", true, true),
                ColumnSpec("name", "TEXT", true),
                ColumnSpec("type", "TEXT", true),
                ColumnSpec("initialBalance", "REAL", true),
                ColumnSpec("colorHex", "TEXT", true)
            )
        ),
        TableSpec(
            "savings_goals",
            listOf(
                ColumnSpec("id", "INTEGER", true, true),
                ColumnSpec("name", "TEXT", true),
                ColumnSpec("targetAmount", "REAL", true),
                ColumnSpec("currentAmount", "REAL", true),
                ColumnSpec("deadline", "INTEGER", false),
                ColumnSpec("createdAt", "INTEGER", true),
                ColumnSpec("description", "TEXT", true),
                ColumnSpec("priority", "TEXT", true),
                ColumnSpec("category", "TEXT", true)
            )
        ),
        TableSpec(
            "reminders",
            listOf(
                ColumnSpec("id", "INTEGER", true, true),
                ColumnSpec("type", "TEXT", true),
                ColumnSpec("title", "TEXT", true),
                ColumnSpec("description", "TEXT", true),
                ColumnSpec("isEnabled", "INTEGER", true),
                ColumnSpec("amount", "REAL", false),
                ColumnSpec("dueDateDay", "INTEGER", false),
                ColumnSpec("time", "TEXT", false),
                ColumnSpec("notifyDaysBefore", "INTEGER", true),
                ColumnSpec("notifyTime", "TEXT", true),
                ColumnSpec("associatedGoalId", "INTEGER", false),
                ColumnSpec("isCompletedThisMonth", "INTEGER", true)
            )
        ),
        TableSpec(
            "recurring_expenses",
            listOf(
                ColumnSpec("id", "INTEGER", true, true),
                ColumnSpec("title", "TEXT", true),
                ColumnSpec("amount", "REAL", true),
                ColumnSpec("categoryId", "INTEGER", true),
                ColumnSpec("accountId", "INTEGER", true),
                ColumnSpec("dayOfMonth", "INTEGER", true),
                ColumnSpec("description", "TEXT", false),
                ColumnSpec("tags", "TEXT", false),
                ColumnSpec("isEnabled", "INTEGER", true),
                ColumnSpec("lastExecutedMonth", "TEXT", false),
                ColumnSpec("autoCreate", "INTEGER", true),
                ColumnSpec("createdAt", "INTEGER", true),
                ColumnSpec("frequency", "TEXT", true, defaultValue = "'MONTHLY'"),
                ColumnSpec("anchorDate", "INTEGER", true, defaultValue = "0"),
                ColumnSpec("nextDueDate", "INTEGER", true, defaultValue = "0")
            ),
            mapOf(
                "index_recurring_expenses_categoryId" to listOf("categoryId"),
                "index_recurring_expenses_accountId" to listOf("accountId"),
                "index_recurring_expenses_dayOfMonth" to listOf("dayOfMonth"),
                "index_recurring_expenses_isEnabled" to listOf("isEnabled")
            )
        ),
        TableSpec(
            "businesses",
            listOf(
                ColumnSpec("id", "INTEGER", true, true),
                ColumnSpec("name", "TEXT", true),
                ColumnSpec("type", "TEXT", true),
                ColumnSpec("description", "TEXT", true),
                ColumnSpec("colorHex", "TEXT", true),
                ColumnSpec("iconKey", "TEXT", true),
                ColumnSpec("inventoryValue", "REAL", true),
                ColumnSpec("createdAt", "INTEGER", true)
            )
        )
    )

    fun validate(
        context: Context,
        snapshot: SnapshotCopyResult.Created
    ): SnapshotValidationResult {
        val appContext = context.applicationContext
        if (DatabaseAccessGate.isNormalAccessAllowed() || com.example.data.local.AppDatabase.hasInstance()) {
            return SnapshotValidationResult.Rejected("ROOM_ACCESS_NOT_QUIESCED")
        }

        val validationRoot = File(appContext.filesDir, VALIDATION_ROOT)
        return try {
            val rawSnapshot = verifyRawSnapshot(appContext, snapshot)
                ?: return SnapshotValidationResult.Rejected("RAW_SNAPSHOT_MANIFEST_OR_HASH_INVALID")

            if (!validationRoot.exists() && !validationRoot.mkdirs()) {
                return SnapshotValidationResult.Rejected("VALIDATION_DIRECTORY_UNAVAILABLE")
            }
            if (!validationRoot.isDirectory) {
                return SnapshotValidationResult.Rejected("VALIDATION_PATH_NOT_A_DIRECTORY")
            }

            val canonicalPrivateRoot = appContext.filesDir.canonicalFile
            val canonicalValidationRoot = validationRoot.canonicalFile
            if (!canonicalValidationRoot.path.startsWith(canonicalPrivateRoot.path + File.separator)) {
                return SnapshotValidationResult.Rejected("VALIDATION_PATH_OUTSIDE_PRIVATE_STORAGE")
            }

            val workspace = File(canonicalValidationRoot, UUID.randomUUID().toString())
            if (!workspace.mkdir()) {
                return SnapshotValidationResult.Rejected("VALIDATION_WORKSPACE_CREATION_FAILED")
            }

            validateWorkspace(rawSnapshot, workspace)
        } catch (_: SecurityException) {
            SnapshotValidationResult.Rejected("VALIDATION_ACCESS_DENIED")
        } catch (_: IOException) {
            SnapshotValidationResult.Rejected("VALIDATION_IO_FAILURE")
        } catch (_: Exception) {
            SnapshotValidationResult.Rejected("VALIDATION_FAILED_CLOSED")
        }
    }

    private fun verifyRawSnapshot(
        context: Context,
        snapshot: SnapshotCopyResult.Created
    ): RawSnapshot? {
        val snapshotRoot = File(context.filesDir, SNAPSHOT_ROOT).canonicalFile
        val directory = snapshot.directory.canonicalFile
        if (!directory.path.startsWith(snapshotRoot.path + File.separator)) return null

        val expectedDatabase = File(directory, DATABASE_NAME).canonicalFile
        val expectedWal = File(directory, WAL_NAME).canonicalFile
        val expectedJournal = File(directory, JOURNAL_NAME).canonicalFile
        val expectedManifest = File(directory, MANIFEST_NAME).canonicalFile

        if (snapshot.databaseFile.canonicalFile != expectedDatabase) return null
        if (snapshot.manifestFile.canonicalFile != expectedManifest) return null
        if (snapshot.walFile?.canonicalFile != if (expectedWal.isFile) expectedWal else null) return null
        if (snapshot.journalFile?.canonicalFile != if (expectedJournal.isFile) expectedJournal else null)
            return null

        val manifest = Properties()
        FileInputStream(expectedManifest).use { manifest.load(it) }

        if (manifest.getProperty("format") != SNAPSHOT_FORMAT) return null
        if (manifest.getProperty("snapshot_id") != snapshot.snapshotId) return null
        if (manifest.getProperty("integrity_certified") != "false") return null
        if (manifest.getProperty("normal_access_authorized") != "false") return null

        val databaseHash = manifest.getProperty("database_sha256") ?: return null
        if (!expectedDatabase.isFile || sha256(expectedDatabase) != databaseHash) return null
        if (expectedDatabase.length() != manifest.getProperty("database_size")?.toLongOrNull()) return null

        val walPresent = manifest.getProperty("wal_present")?.toBooleanStrictOrNull() ?: return null
        if (walPresent != expectedWal.isFile) return null
        if (walPresent) {
            val walHash = manifest.getProperty("wal_sha256") ?: return null
            if (sha256(expectedWal) != walHash) return null
        }

        val journalPresent = manifest.getProperty("journal_present")?.toBooleanStrictOrNull() ?: return null
        if (journalPresent != expectedJournal.isFile) return null
        if (journalPresent) {
            val journalHash = manifest.getProperty("journal_sha256") ?: return null
            if (sha256(expectedJournal) != journalHash) return null
        }

        return RawSnapshot(
            snapshotId = snapshot.snapshotId,
            databaseFile = expectedDatabase,
            walFile = expectedWal.takeIf { walPresent },
            journalFile = expectedJournal.takeIf { journalPresent },
            databaseHash = databaseHash,
            walHash = manifest.getProperty("wal_sha256"),
            journalHash = manifest.getProperty("journal_sha256")
        )
    }

    private fun validateWorkspace(
        raw: RawSnapshot,
        workspace: File
    ): SnapshotValidationResult {
        val databaseCopy = File(workspace, DATABASE_NAME)
        val walCopy = raw.walFile?.let { File(workspace, WAL_NAME) }
        val journalCopy = raw.journalFile?.let { File(workspace, JOURNAL_NAME) }

        copyAndSync(raw.databaseFile, databaseCopy)
        if (walCopy != null) copyAndSync(checkNotNull(raw.walFile), walCopy)
        if (journalCopy != null) copyAndSync(checkNotNull(raw.journalFile), journalCopy)

        // Re-hash the immutable raw evidence after copying to detect a concurrent change.
        if (sha256(raw.databaseFile) != raw.databaseHash ||
            sha256(databaseCopy) != raw.databaseHash
        ) {
            return rejectAndClean(workspace, "DATABASE_COPY_HASH_MISMATCH")
        }
        if (walCopy != null) {
            val expected = raw.walHash ?: return rejectAndClean(workspace, "WAL_MANIFEST_HASH_MISSING")
            if (sha256(checkNotNull(raw.walFile)) != expected || sha256(walCopy) != expected) {
                return rejectAndClean(workspace, "WAL_COPY_HASH_MISMATCH")
            }
        }
        if (journalCopy != null) {
            val expected = raw.journalHash ?: return rejectAndClean(workspace, "JOURNAL_MANIFEST_HASH_MISSING")
            if (sha256(checkNotNull(raw.journalFile)) != expected || sha256(journalCopy) != expected) {
                return rejectAndClean(workspace, "JOURNAL_COPY_HASH_MISMATCH")
            }
        }

        var sqlite: SQLiteDatabase? = null
        try {
            // OPEN_READWRITE is intentional: SQLite may need to replay a copied
            // hot journal or create a private WAL index. CREATE_IF_NECESSARY is
            // deliberately omitted, so a missing copy cannot become an empty DB.
            sqlite = SQLiteDatabase.openDatabase(
                databaseCopy.absolutePath,
                null,
                SQLiteDatabase.OPEN_READWRITE
            )

            val quickCheck = readPragmaRows(sqlite, "PRAGMA quick_check(1)")
            if (quickCheck != listOf("ok")) {
                return rejectAndClean(workspace, "SQLITE_QUICK_CHECK_FAILED")
            }

            val integrityCheck = readPragmaRows(sqlite, "PRAGMA integrity_check")
            if (integrityCheck != listOf("ok")) {
                return rejectAndClean(workspace, "SQLITE_INTEGRITY_CHECK_FAILED")
            }

            val schemaVersion = sqlite.version
            if (schemaVersion != CURRENT_SCHEMA_VERSION) {
                return rejectAndClean(
                    workspace,
                    "UNSUPPORTED_SCHEMA_VERSION_$schemaVersion"
                )
            }

            val schemaFailure = validateRoomSchema(sqlite)
            if (schemaFailure != null) {
                return rejectAndClean(workspace, schemaFailure)
            }

            val identityHash = readRoomIdentityHash(sqlite)
                ?: return rejectAndClean(workspace, "ROOM_IDENTITY_MISSING")

            // Validation may have replayed a journal or WAL in this workspace.
            // Close SQLite before publishing evidence so recovery writes are flushed.
            sqlite.close()
            sqlite = null

            val postValidationHash = sha256(databaseCopy)
            val report = Properties().apply {
                setProperty("format", "INSERC_SQLITE_VALIDATION_REPORT_1")
                setProperty("snapshot_id", raw.snapshotId)
                setProperty("workspace_id", workspace.name)
                setProperty("schema_version", schemaVersion.toString())
                setProperty("quick_check", "ok")
                setProperty("integrity_check", "ok")
                setProperty("room_identity_hash", identityHash)
                setProperty("database_sha256_before_validation", raw.databaseHash)
                setProperty("database_sha256_after_validation", postValidationHash)
                setProperty("wal_recovery_input_present", (raw.walFile != null).toString())
                setProperty("journal_recovery_input_present", (raw.journalFile != null).toString())
                setProperty("schema_validated", "true")
                setProperty("integrity_certified", "true")
                setProperty("normal_access_authorized", "false")
            }

            val tempReport = File(workspace, "validation-report.properties.tmp")
            val finalReport = File(workspace, "validation-report.properties")
            FileOutputStream(tempReport).use { output ->
                report.store(output, "Validation evidence only; Room activation is not authorized")
                output.fd.sync()
            }
            if (!tempReport.renameTo(finalReport)) {
                return rejectAndClean(workspace, "VALIDATION_REPORT_PUBLISH_FAILED")
            }

            return SnapshotValidationResult.Validated(
                snapshotId = raw.snapshotId,
                workspaceDirectory = workspace,
                validatedDatabaseFile = databaseCopy,
                reportFile = finalReport,
                schemaVersion = schemaVersion,
                roomIdentityHash = identityHash,
                quickCheckPassed = true,
                integrityCheckPassed = true,
                schemaValidated = true,
                integrityCertified = true,
                authorizedForRoom = false
            )
        } catch (_: Exception) {
            return rejectAndClean(workspace, "SQLITE_OPEN_OR_VALIDATION_FAILED")
        } finally {
            try {
                sqlite?.close()
            } catch (_: Exception) {
                // The workspace remains isolated and is not promoted on failure.
            }
        }
    }

    private fun validateRoomSchema(database: SQLiteDatabase): String? {
        for (table in expectedTables) {
            val actualColumns = linkedMapOf<String, ColumnSpec>()
            database.rawQuery("PRAGMA table_info('${table.name}')", null).use { cursor ->
                while (cursor.moveToNext()) {
                    val name = cursor.getString(1)
                    val type = cursor.getString(2).uppercase()
                    val notNull = cursor.getInt(3) == 1
                    val defaultValue = if (cursor.isNull(4)) null else cursor.getString(4)
                    val primaryKey = cursor.getInt(5) > 0
                    actualColumns[name] = ColumnSpec(
                        name = name,
                        type = type,
                        notNull = notNull,
                        primaryKey = primaryKey,
                        defaultValue = defaultValue
                    )
                }
            }

            val expectedByName = table.columns.associateBy { it.name }
            if (actualColumns.keys != expectedByName.keys) {
                return "ROOM_SCHEMA_COLUMNS_MISMATCH_${table.name}"
            }

            for (expected in table.columns) {
                val actual = actualColumns[expected.name] ?: return "ROOM_SCHEMA_COLUMN_MISSING_${table.name}"
                if (actual.type != expected.type ||
                    actual.notNull != expected.notNull ||
                    actual.primaryKey != expected.primaryKey ||
                    (expected.defaultValue != null && actual.defaultValue != expected.defaultValue)
                ) {
                    return "ROOM_SCHEMA_COLUMN_DEFINITION_MISMATCH_${table.name}_${expected.name}"
                }
            }

            val actualIndexNames = linkedSetOf<String>()
            database.rawQuery("PRAGMA index_list('${table.name}')", null).use { cursor ->
                while (cursor.moveToNext()) {
                    actualIndexNames += cursor.getString(1)
                }
            }
            if (actualIndexNames != table.indices.keys) {
                return "ROOM_SCHEMA_INDEX_SET_MISMATCH_${table.name}"
            }

            for ((indexName, expectedColumns) in table.indices) {
                val actualIndexColumns = mutableListOf<String>()
                database.rawQuery("PRAGMA index_info('$indexName')", null).use { cursor ->
                    while (cursor.moveToNext()) {
                        actualIndexColumns += cursor.getString(2)
                    }
                }
                if (actualIndexColumns != expectedColumns) {
                    return "ROOM_SCHEMA_INDEX_COLUMNS_MISMATCH_$indexName"
                }
            }
        }
        return null
    }

    private fun readRoomIdentityHash(database: SQLiteDatabase): String? {
        return try {
            database.rawQuery(
                "SELECT identity_hash FROM room_master_table WHERE id = 42 LIMIT 1",
                null
            ).use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0)?.takeIf { it.isNotBlank() } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun readPragmaRows(database: SQLiteDatabase, sql: String): List<String> {
        val rows = mutableListOf<String>()
        database.rawQuery(sql, null).use { cursor ->
            while (cursor.moveToNext()) {
                rows += cursor.getString(0)
            }
        }
        return rows
    }

    private fun copyAndSync(source: File, destination: File) {
        if (!source.isFile || !source.canRead()) throw IOException("Snapshot component unavailable")
        if (!destination.createNewFile()) throw IOException("Validation destination already exists")

        FileInputStream(source).use { input ->
            FileOutputStream(destination).use { output ->
                input.copyTo(output, BUFFER_SIZE)
                output.flush()
                output.fd.sync()
            }
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun rejectAndClean(workspace: File, reason: String): SnapshotValidationResult.Rejected {
        deleteTree(workspace)
        return SnapshotValidationResult.Rejected(reason)
    }

    private fun deleteTree(file: File) {
        if (file.isDirectory) file.listFiles()?.forEach(::deleteTree)
        file.delete()
    }

    private data class RawSnapshot(
        val snapshotId: String,
        val databaseFile: File,
        val walFile: File?,
        val journalFile: File?,
        val databaseHash: String,
        val walHash: String?,
        val journalHash: String?
    )
}

sealed interface SnapshotValidationResult {
    data class Validated(
        val snapshotId: String,
        val workspaceDirectory: File,
        val validatedDatabaseFile: File,
        val reportFile: File,
        val schemaVersion: Int,
        val roomIdentityHash: String,
        val quickCheckPassed: Boolean,
        val integrityCheckPassed: Boolean,
        val schemaValidated: Boolean,
        val integrityCertified: Boolean,
        val authorizedForRoom: Boolean
    ) : SnapshotValidationResult

    data class Rejected(val reasonCode: String) : SnapshotValidationResult
}
