package com.example.data.recovery

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import com.example.data.local.AppDatabase
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.security.MessageDigest
import java.util.Properties
import java.util.UUID

/**
 * Rehearses the registered v9-v13 -> v14 Room migrations on a private copy
 * derived from an immutable raw snapshot. The original and raw snapshot are
 * never opened by Room and are never modified.
 *
 * A successful rehearsal is not activation evidence. Its output must pass
 * DatabaseSnapshotValidator.validateMigrated and the financial auditor.
 */
object DatabaseMigrationRehearsal {

    private const val SNAPSHOT_ROOT = "recovery/snapshots"
    private const val MIGRATION_ROOT = "recovery/migrations"
    private const val SNAPSHOT_FORMAT = "INSERC_SQLITE_RAW_SNAPSHOT_1"
    private const val REPORT_FORMAT = "INSERC_ROOM_MIGRATION_REHEARSAL_1"
    private const val DATABASE_NAME = "database.sqlite"
    private const val WAL_NAME = "database.sqlite-wal"
    private const val JOURNAL_NAME = "database.sqlite-journal"
    private const val MANIFEST_NAME = "snapshot-manifest.properties"
    private const val REPORT_NAME = "migration-report.properties"
    private const val CURRENT_SCHEMA_VERSION = 14

    data class Migrated(
        val snapshotId: String,
        val sourceSchemaVersion: Int,
        val workspaceDirectory: File,
        val databaseFile: File,
        val reportFile: File,
        val walFile: File?,
        val journalFile: File?,
        val databaseSha256: String,
        val walSha256: String?,
        val journalSha256: String?
    )

    sealed interface Result {
        data class Completed(val migration: Migrated) : Result
        data class Rejected(val reasonCode: String) : Result
    }

    private data class RawEvidence(
        val directory: File,
        val database: File,
        val wal: File?,
        val journal: File?,
        val databaseHash: String,
        val walHash: String?,
        val journalHash: String?
    )

    fun rehearse(context: Context, snapshot: SnapshotCopyResult.Created): Result {
        val appContext = context.applicationContext
        if (DatabaseAccessGate.isNormalAccessAllowed() || AppDatabase.hasInstance() ||
            snapshot.integrityCertified || snapshot.authorizedForRoom
        ) {
            return Result.Rejected("MIGRATION_REHEARSAL_REQUIRES_QUARANTINE")
        }

        var workspace: File? = null
        return try {
            val raw = verifyRawSnapshot(appContext, snapshot)
                ?: return Result.Rejected("MIGRATION_RAW_SNAPSHOT_INVALID")

            val preflight = DatabasePreflightInspector.inspect(raw.database)
            val sidecarSnapshot = preflight.reasonCode in setOf(
                "WAL_SIDECAR_REQUIRES_CONSISTENT_SNAPSHOT",
                "ROLLBACK_JOURNAL_PRESENT"
            )
            val sourceVersion = preflight.schemaVersion
            if (sourceVersion == null || sourceVersion !in 9..13 ||
                (preflight.condition != DatabaseCondition.LEGACY_MIGRATION_CANDIDATE && !sidecarSnapshot)
            ) {
                return Result.Rejected("LEGACY_SCHEMA_NOT_ELIGIBLE_FOR_REHEARSAL")
            }

            val migrationRoot = File(appContext.filesDir, MIGRATION_ROOT)
            if (!migrationRoot.exists() && !migrationRoot.mkdirs()) {
                return Result.Rejected("MIGRATION_WORKSPACE_ROOT_UNAVAILABLE")
            }
            val privateRoot = appContext.filesDir.canonicalFile
            val canonicalRoot = migrationRoot.canonicalFile
            if (!canonicalRoot.path.startsWith(privateRoot.path + File.separator) ||
                !canonicalRoot.isDirectory
            ) {
                return Result.Rejected("MIGRATION_WORKSPACE_ROOT_INVALID")
            }

            val created = File(canonicalRoot, UUID.randomUUID().toString())
            if (!created.mkdir()) return Result.Rejected("MIGRATION_WORKSPACE_CREATE_FAILED")
            workspace = created

            val migratedDatabase = File(created, DATABASE_NAME)
            val migratedWal = raw.wal?.let { File(created, WAL_NAME) }
            val migratedJournal = raw.journal?.let { File(created, JOURNAL_NAME) }
            copyAndSync(raw.database, migratedDatabase)
            if (migratedWal != null) copyAndSync(checkNotNull(raw.wal), migratedWal)
            if (migratedJournal != null) copyAndSync(checkNotNull(raw.journal), migratedJournal)

            if (sha256(raw.database) != raw.databaseHash ||
                sha256(migratedDatabase) != raw.databaseHash
            ) return Result.Rejected("MIGRATION_DATABASE_COPY_HASH_MISMATCH")
            if (migratedWal != null) {
                val expected = raw.walHash ?: return Result.Rejected("MIGRATION_WAL_HASH_MISSING")
                if (sha256(checkNotNull(raw.wal)) != expected || sha256(migratedWal) != expected) {
                    return Result.Rejected("MIGRATION_WAL_COPY_HASH_MISMATCH")
                }
            }
            if (migratedJournal != null) {
                val expected = raw.journalHash ?: return Result.Rejected("MIGRATION_JOURNAL_HASH_MISSING")
                if (sha256(checkNotNull(raw.journal)) != expected || sha256(migratedJournal) != expected) {
                    return Result.Rejected("MIGRATION_JOURNAL_COPY_HASH_MISMATCH")
                }
            }

            // Room is permitted to migrate only this second-generation disposable copy.
            val room = Room.databaseBuilder(
                appContext,
                AppDatabase::class.java,
                migratedDatabase.absolutePath
            ).addMigrations(
                AppDatabase.MIGRATION_7_8,
                AppDatabase.MIGRATION_9_10,
                AppDatabase.MIGRATION_10_11,
                AppDatabase.MIGRATION_11_12,
                AppDatabase.MIGRATION_12_13,
                AppDatabase.MIGRATION_13_14
            ).build()
            try {
                room.openHelper.writableDatabase
            } finally {
                room.close()
            }

            // Verify the migrated copy independently after Room has closed.
            val verification = SQLiteDatabase.openDatabase(
                migratedDatabase.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY
            )
            val quickCheck: List<String>
            val integrityCheck: List<String>
            val finalVersion: Int
            val identityHash: String?
            try {
                finalVersion = verification.version
                quickCheck = readRows(verification, "PRAGMA quick_check(1)")
                integrityCheck = readRows(verification, "PRAGMA integrity_check")
                identityHash = verification.rawQuery(
                    "SELECT identity_hash FROM room_master_table WHERE id = 42 LIMIT 1",
                    null
                ).use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0)?.takeIf { it.isNotBlank() } else null
                }
            } finally {
                verification.close()
            }
            if (finalVersion != CURRENT_SCHEMA_VERSION) {
                return Result.Rejected("MIGRATION_RESULT_SCHEMA_VERSION_UNEXPECTED")
            }
            if (quickCheck != listOf("ok") || integrityCheck != listOf("ok")) {
                return Result.Rejected("MIGRATION_RESULT_SQLITE_INTEGRITY_FAILED")
            }
            if (identityHash.isNullOrBlank()) {
                return Result.Rejected("MIGRATION_RESULT_ROOM_IDENTITY_MISSING")
            }

            // Ensure the immutable raw evidence stayed byte-identical throughout the rehearsal.
            if (sha256(raw.database) != raw.databaseHash ||
                (raw.wal != null && sha256(raw.wal) != raw.walHash) ||
                (raw.journal != null && sha256(raw.journal) != raw.journalHash)
            ) {
                return Result.Rejected("RAW_SNAPSHOT_CHANGED_DURING_MIGRATION")
            }

            val walAfter = File(migratedDatabase.path + "-wal").takeIf { it.isFile && it.length() > 32L }
            val journalAfter = File(migratedDatabase.path + "-journal").takeIf { it.isFile && it.length() > 0L }
            val databaseHash = sha256(migratedDatabase)
            val walHash = walAfter?.let(::sha256)
            val journalHash = journalAfter?.let(::sha256)

            val report = Properties().apply {
                setProperty("format", REPORT_FORMAT)
                setProperty("snapshot_id", snapshot.snapshotId)
                setProperty("workspace_id", created.name)
                setProperty("source_schema_version", sourceVersion.toString())
                setProperty("migrated_schema_version", CURRENT_SCHEMA_VERSION.toString())
                setProperty("raw_database_sha256", raw.databaseHash)
                raw.walHash?.let { setProperty("raw_wal_sha256", it) }
                raw.journalHash?.let { setProperty("raw_journal_sha256", it) }
                setProperty("migrated_database_sha256", databaseHash)
                walHash?.let { setProperty("migrated_wal_sha256", it) }
                journalHash?.let { setProperty("migrated_journal_sha256", it) }
                setProperty("room_identity_hash", identityHash)
                setProperty("quick_check", "ok")
                setProperty("integrity_check", "ok")
                setProperty("raw_snapshot_unchanged", "true")
                setProperty("migration_completed", "true")
                setProperty("normal_access_authorized", "false")
                setProperty("room_authorized", "false")
            }
            val reportFile = File(created, REPORT_NAME)
            val temporary = File(created, "$REPORT_NAME.tmp")
            FileOutputStream(temporary).use { output ->
                report.store(output, "Migration rehearsal evidence only; this copy is not active")
                output.fd.sync()
            }
            if (!temporary.renameTo(reportFile)) {
                temporary.delete()
                return Result.Rejected("MIGRATION_REPORT_PUBLISH_FAILED")
            }

            Result.Completed(
                Migrated(
                    snapshotId = snapshot.snapshotId,
                    sourceSchemaVersion = sourceVersion,
                    workspaceDirectory = created,
                    databaseFile = migratedDatabase,
                    reportFile = reportFile,
                    walFile = walAfter,
                    journalFile = journalAfter,
                    databaseSha256 = databaseHash,
                    walSha256 = walHash,
                    journalSha256 = journalHash
                )
            )
        } catch (_: SecurityException) {
            Result.Rejected("MIGRATION_REHEARSAL_ACCESS_DENIED")
        } catch (_: IOException) {
            Result.Rejected("MIGRATION_REHEARSAL_IO_FAILED")
        } catch (_: Exception) {
            // Preserve any partial rehearsal workspace for inspection; never touch the source.
            Result.Rejected("MIGRATION_REHEARSAL_FAILED_CLOSED")
        }
    }

    private fun verifyRawSnapshot(context: Context, snapshot: SnapshotCopyResult.Created): RawEvidence? {
        val root = File(context.filesDir, SNAPSHOT_ROOT).canonicalFile
        val directoryRaw = snapshot.directory
        if (Files.isSymbolicLink(directoryRaw.toPath())) return null
        val directory = directoryRaw.canonicalFile
        if (!directory.path.startsWith(root.path + File.separator)) return null

        val databaseRaw = File(directory, DATABASE_NAME)
        val walRaw = File(directory, WAL_NAME)
        val journalRaw = File(directory, JOURNAL_NAME)
        val manifestRaw = File(directory, MANIFEST_NAME)
        val shmRaw = File(directory, "$DATABASE_NAME-shm")
        if (listOf(databaseRaw, walRaw, journalRaw, manifestRaw, shmRaw).any {
                Files.isSymbolicLink(it.toPath())
            } || shmRaw.exists()
        ) return null

        val database = databaseRaw.canonicalFile
        val wal = walRaw.takeIf { it.isFile }?.canonicalFile
        val journal = journalRaw.takeIf { it.isFile }?.canonicalFile
        val manifestFile = manifestRaw.canonicalFile
        if (database.parentFile != directory || manifestFile.parentFile != directory) return null
        if (snapshot.databaseFile.canonicalFile != database ||
            snapshot.manifestFile.canonicalFile != manifestFile ||
            snapshot.walFile?.canonicalFile != wal ||
            snapshot.journalFile?.canonicalFile != journal
        ) return null

        val manifest = Properties().apply { FileInputStream(manifestFile).use(::load) }
        if (manifest.getProperty("format") != SNAPSHOT_FORMAT ||
            manifest.getProperty("snapshot_id") != snapshot.snapshotId ||
            manifest.getProperty("integrity_certified") != "false" ||
            manifest.getProperty("normal_access_authorized") != "false" ||
            manifest.getProperty("shm_copied") != "false" ||
            snapshot.integrityCertified || snapshot.authorizedForRoom
        ) return null

        val databaseHash = manifest.getProperty("database_sha256") ?: return null
        if (databaseHash != snapshot.databaseSha256 || sha256(database) != databaseHash ||
            database.length() != manifest.getProperty("database_size")?.toLongOrNull()
        ) return null

        val walPresent = manifest.getProperty("wal_present")?.toBooleanStrictOrNull() ?: return null
        val journalPresent = manifest.getProperty("journal_present")?.toBooleanStrictOrNull() ?: return null
        if (walPresent != (wal != null) || journalPresent != (journal != null) ||
            walPresent != snapshot.sourceWalObserved ||
            journalPresent != snapshot.sourceJournalObserved
        ) return null

        val walHash = if (walPresent) {
            val expected = manifest.getProperty("wal_sha256") ?: return null
            if (expected != snapshot.walSha256 || sha256(checkNotNull(wal)) != expected) return null
            expected
        } else null
        val journalHash = if (journalPresent) {
            val expected = manifest.getProperty("journal_sha256") ?: return null
            if (expected != snapshot.journalSha256 || sha256(checkNotNull(journal)) != expected) return null
            expected
        } else null

        val shmObserved = manifest.getProperty("shm_observed")?.toBooleanStrictOrNull() ?: return null
        if (shmObserved != snapshot.sourceShmObserved) return null

        return RawEvidence(directory, database, wal, journal, databaseHash, walHash, journalHash)
    }

    private fun copyAndSync(source: File, destination: File) {
        if (!source.isFile || !source.canRead() || !destination.createNewFile()) {
            throw IOException("Migration copy source or destination is invalid")
        }
        FileInputStream(source).use { input ->
            FileOutputStream(destination).use { output ->
                input.copyTo(output, 64 * 1024)
                output.flush()
                output.fd.sync()
            }
        }
    }

    private fun readRows(database: SQLiteDatabase, sql: String): List<String> =
        database.rawQuery(sql, null).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
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
}
