package com.example.data.recovery

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.channels.OverlappingFileLockException
import java.security.MessageDigest
import java.util.Properties
import java.util.UUID

/**
 * Makes a byte-preserving private copy of the legacy database and its durable
 * SQLite sidecars. It never opens SQLite or Room and never writes to the source.
 *
 * The result is a COPIED snapshot, not a certified or active database. WAL or
 * rollback-journal recovery, SQLite integrity checks, schema validation and
 * financial consistency checks must be performed against an isolated copy.
 */
object DatabaseSnapshotCopier {

    fun copyOriginalDatabase(context: Context): SnapshotCopyResult {
        val appContext = context.applicationContext
        val original = appContext.getDatabasePath(ORIGINAL_DATABASE_NAME)
        val recoveryRoot = File(appContext.filesDir, RECOVERY_DIRECTORY)

        return try {
            if (!recoveryRoot.exists() && !recoveryRoot.mkdirs()) {
                return SnapshotCopyResult.Rejected("RECOVERY_DIRECTORY_UNAVAILABLE")
            }
            if (!recoveryRoot.isDirectory) {
                return SnapshotCopyResult.Rejected("RECOVERY_DIRECTORY_NOT_A_DIRECTORY")
            }

            val privateRoot = appContext.filesDir.canonicalFile
            val canonicalRecoveryRoot = recoveryRoot.canonicalFile
            if (!canonicalRecoveryRoot.path.startsWith(privateRoot.path + File.separator)) {
                return SnapshotCopyResult.Rejected("RECOVERY_DIRECTORY_OUTSIDE_PRIVATE_STORAGE")
            }

            val lockFile = File(canonicalRecoveryRoot, LOCK_FILE_NAME)
            RandomAccessFile(lockFile, "rw").use { lockHandle ->
                val lock = try {
                    lockHandle.channel.tryLock()
                } catch (_: OverlappingFileLockException) {
                    null
                } ?: return SnapshotCopyResult.Rejected("SNAPSHOT_ALREADY_IN_PROGRESS")

                lock.use {
                    copyUnderLock(original, canonicalRecoveryRoot)
                }
            }
        } catch (_: SecurityException) {
            SnapshotCopyResult.Rejected("SNAPSHOT_ACCESS_DENIED")
        } catch (_: IOException) {
            SnapshotCopyResult.Rejected("SNAPSHOT_IO_FAILURE")
        } catch (_: Exception) {
            SnapshotCopyResult.Rejected("SNAPSHOT_FAILED_CLOSED")
        }
    }

    private fun copyUnderLock(original: File, recoveryRoot: File): SnapshotCopyResult {
        if (!original.isFile || !original.canRead()) {
            return SnapshotCopyResult.Rejected("ORIGINAL_DATABASE_NOT_READABLE")
        }

        val wal = File(original.path + "-wal")
        val journal = File(original.path + "-journal")
        val shm = File(original.path + "-shm")

        val snapshotId = UUID.randomUUID().toString()
        val snapshotDirectory = File(recoveryRoot, snapshotId)
        if (!snapshotDirectory.mkdir()) {
            return SnapshotCopyResult.Rejected("SNAPSHOT_DIRECTORY_CREATION_FAILED")
        }

        try {
            val sourceSetBefore = captureSourceSet(original, wal, journal, shm)
                ?: return rejectAndClean(snapshotDirectory, "SOURCE_SET_UNREADABLE")

            val snapshotDatabase = File(snapshotDirectory, SNAPSHOT_DATABASE_NAME)
            copyAndVerify(original, snapshotDatabase, sourceSetBefore.database)

            val snapshotWal = if (sourceSetBefore.wal.exists) {
                File(snapshotDirectory, SNAPSHOT_WAL_NAME).also {
                    copyAndVerify(wal, it, sourceSetBefore.wal)
                }
            } else null

            val snapshotJournal = if (sourceSetBefore.journal.exists) {
                File(snapshotDirectory, SNAPSHOT_JOURNAL_NAME).also {
                    copyAndVerify(journal, it, sourceSetBefore.journal)
                }
            } else null

            // SHM is transient WAL-index/locking state. It is recorded as observed
            // but not copied; SQLite must reconstruct it for the isolated copy.
            val sourceSetAfter = captureSourceSet(original, wal, journal, shm)
                ?: return rejectAndClean(snapshotDirectory, "SOURCE_SET_CHANGED_OR_UNREADABLE")

            if (!sourceSetBefore.sameDurableStateAs(sourceSetAfter)) {
                return rejectAndClean(snapshotDirectory, "SOURCE_CHANGED_DURING_SNAPSHOT")
            }

            verifyCopiedFile(sourceSetBefore.database, snapshotDatabase)
            if (snapshotWal != null) verifyCopiedFile(sourceSetBefore.wal, snapshotWal)
            if (snapshotJournal != null) verifyCopiedFile(sourceSetBefore.journal, snapshotJournal)

            val manifest = Properties().apply {
                setProperty("format", "INSERC_SQLITE_RAW_SNAPSHOT_1")
                setProperty("snapshot_id", snapshotId)
                setProperty("database_sha256", sourceSetBefore.database.sha256)
                setProperty("database_size", sourceSetBefore.database.length.toString())
                setProperty("database_last_modified", sourceSetBefore.database.lastModified.toString())
                setProperty("wal_present", sourceSetBefore.wal.exists.toString())
                if (sourceSetBefore.wal.exists) {
                    setProperty("wal_sha256", sourceSetBefore.wal.sha256)
                    setProperty("wal_size", sourceSetBefore.wal.length.toString())
                }
                setProperty("journal_present", sourceSetBefore.journal.exists.toString())
                if (sourceSetBefore.journal.exists) {
                    setProperty("journal_sha256", sourceSetBefore.journal.sha256)
                    setProperty("journal_size", sourceSetBefore.journal.length.toString())
                }
                setProperty("shm_observed", sourceSetBefore.shm.exists.toString())
                setProperty("shm_copied", "false")
                setProperty("integrity_certified", "false")
                setProperty("normal_access_authorized", "false")
            }

            val manifestTemp = File(snapshotDirectory, MANIFEST_TEMP_NAME)
            val manifestFile = File(snapshotDirectory, MANIFEST_NAME)
            FileOutputStream(manifestTemp).use { output ->
                manifest.store(output, "Raw copy metadata only; not an integrity certification")
                output.fd.sync()
            }
            if (!manifestTemp.renameTo(manifestFile)) {
                return rejectAndClean(snapshotDirectory, "SNAPSHOT_MANIFEST_PUBLISH_FAILED")
            }

            SnapshotCopyResult.Created(
                snapshotId = snapshotId,
                directory = snapshotDirectory,
                databaseFile = snapshotDatabase,
                walFile = snapshotWal,
                journalFile = snapshotJournal,
                manifestFile = manifestFile,
                sourceWalObserved = sourceSetBefore.wal.exists,
                sourceShmObserved = sourceSetBefore.shm.exists,
                sourceJournalObserved = sourceSetBefore.journal.exists,
                databaseSha256 = sourceSetBefore.database.sha256,
                walSha256 = sourceSetBefore.wal.sha256.takeIf { sourceSetBefore.wal.exists },
                journalSha256 = sourceSetBefore.journal.sha256.takeIf { sourceSetBefore.journal.exists },
                integrityCertified = false,
                authorizedForRoom = false
            )
        } catch (_: Exception) {
            return rejectAndClean(snapshotDirectory, "SNAPSHOT_COPY_OR_VERIFICATION_FAILED")
        }
    }

    private fun captureSourceSet(
        database: File,
        wal: File,
        journal: File,
        shm: File
    ): SourceSet? {
        val databaseState = capture(database, required = true) ?: return null
        val walState = capture(wal, required = false) ?: return null
        val journalState = capture(journal, required = false) ?: return null
        val shmState = captureMetadataOnly(shm) ?: return null
        return SourceSet(databaseState, walState, journalState, shmState)
    }

    private fun capture(file: File, required: Boolean): SourceFileState? {
        val exists = file.exists()
        if (!exists) return if (required) null else SourceFileState(false, 0L, 0L, "")
        if (!file.isFile || !file.canRead()) return null
        return SourceFileState(
            exists = true,
            length = file.length(),
            lastModified = file.lastModified(),
            sha256 = sha256(file)
        )
    }

    private fun captureMetadataOnly(file: File): SourceFileState? {
        val exists = file.exists()
        if (!exists) return SourceFileState(false, 0L, 0L, "")
        if (!file.isFile || !file.canRead()) return null
        return SourceFileState(
            exists = true,
            length = file.length(),
            lastModified = file.lastModified(),
            sha256 = ""
        )
    }

    private fun copyAndVerify(source: File, destination: File, expected: SourceFileState) {
        if (!source.isFile || !source.canRead()) throw IOException("Source file unavailable")
        if (!destination.createNewFile()) throw IOException("Destination already exists")

        FileInputStream(source).use { input ->
            FileOutputStream(destination).use { output ->
                val buffer = ByteArray(COPY_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                }
                output.flush()
                output.fd.sync()
            }
        }

        val destinationHash = sha256(destination)
        if (destinationHash != expected.sha256 || destination.length() != expected.length) {
            throw IOException("Copied snapshot file did not match source")
        }
    }

    private fun verifyCopiedFile(source: SourceFileState, copy: File) {
        if (!copy.isFile || copy.length() != source.length || sha256(copy) != source.sha256) {
            throw IOException("Snapshot copy verification failed")
        }
    }

    private fun rejectAndClean(directory: File, reason: String): SnapshotCopyResult.Rejected {
        deleteTree(directory)
        return SnapshotCopyResult.Rejected(reason)
    }

    private fun deleteTree(file: File) {
        if (file.isDirectory) file.listFiles()?.forEach(::deleteTree)
        file.delete()
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(COPY_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private data class SourceFileState(
        val exists: Boolean,
        val length: Long,
        val lastModified: Long,
        val sha256: String
    )

    private data class SourceSet(
        val database: SourceFileState,
        val wal: SourceFileState,
        val journal: SourceFileState,
        val shm: SourceFileState
    ) {
        fun sameDurableStateAs(other: SourceSet): Boolean =
            database == other.database &&
                wal == other.wal &&
                journal == other.journal &&
                shm.exists == other.shm.exists
    }

    private const val ORIGINAL_DATABASE_NAME = "budget_tracker_database"
    private const val RECOVERY_DIRECTORY = "recovery/snapshots"
    private const val LOCK_FILE_NAME = ".snapshot.lock"
    private const val SNAPSHOT_DATABASE_NAME = "database.sqlite"
    private const val SNAPSHOT_WAL_NAME = "database.sqlite-wal"
    private const val SNAPSHOT_JOURNAL_NAME = "database.sqlite-journal"
    private const val MANIFEST_NAME = "snapshot-manifest.properties"
    private const val MANIFEST_TEMP_NAME = "snapshot-manifest.properties.tmp"
    private const val COPY_BUFFER_SIZE = 64 * 1024
}

sealed interface SnapshotCopyResult {
    data class Created(
        val snapshotId: String,
        val directory: File,
        val databaseFile: File,
        val walFile: File?,
        val journalFile: File?,
        val manifestFile: File,
        val sourceWalObserved: Boolean,
        val sourceShmObserved: Boolean,
        val sourceJournalObserved: Boolean,
        val databaseSha256: String,
        val walSha256: String?,
        val journalSha256: String?,
        val integrityCertified: Boolean,
        val authorizedForRoom: Boolean
    ) : SnapshotCopyResult

    data class Rejected(val reasonCode: String) : SnapshotCopyResult
}
