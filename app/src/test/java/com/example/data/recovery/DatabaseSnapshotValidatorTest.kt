package com.example.data.recovery

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.room.Room
import com.example.data.local.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DatabaseSnapshotValidatorTest {

    private lateinit var context: Context
    private lateinit var original: File
    private lateinit var snapshotRoot: File
    private lateinit var validationRoot: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        original = context.getDatabasePath("budget_tracker_database")
        original.parentFile?.mkdirs()
        snapshotRoot = File(context.filesDir, "recovery/snapshots")
        validationRoot = File(context.filesDir, "recovery/validation")
        snapshotRoot.deleteRecursively()
        validationRoot.deleteRecursively()
        original.delete()
        File(original.path + "-wal").delete()
        File(original.path + "-shm").delete()
        File(original.path + "-journal").delete()
        DatabaseAccessGate.resetForTests()
    }

    @Test
    fun validRoomDatabasePassesChecksOnSeparateWorkspaceOnly() {
        createRoomFixture(original, version = 14)
        val originalHash = sha256(original)

        val snapshot = DatabaseSnapshotCopier.copyOriginalDatabase(context)
        assertTrue("Raw snapshot should be created", snapshot is SnapshotCopyResult.Created)
        val raw = snapshot as SnapshotCopyResult.Created
        val rawSnapshotHash = sha256(raw.databaseFile)

        val result = DatabaseSnapshotValidator.validate(context, raw)

        assertTrue("Validation should pass: $result", result is SnapshotValidationResult.Validated)
        val validated = result as SnapshotValidationResult.Validated
        assertEquals(14, validated.schemaVersion)
        assertTrue(validated.quickCheckPassed)
        assertTrue(validated.integrityCheckPassed)
        assertTrue(validated.schemaValidated)
        assertTrue(validated.sqliteIntegrityChecksPassed)
        assertFalse(validated.authorizedForRoom)
        assertNotEquals(raw.databaseFile.canonicalFile, validated.validatedDatabaseFile.canonicalFile)
        assertTrue(validated.workspaceDirectory.canonicalPath.startsWith(validationRoot.canonicalPath))
        assertTrue(validated.reportFile.isFile)

        // Neither the original file nor the raw evidence snapshot is opened or rewritten.
        assertEquals(originalHash, sha256(original))
        assertEquals(rawSnapshotHash, sha256(raw.databaseFile))
        assertFalse(DatabaseAccessGate.isNormalAccessAllowed())
        assertEquals(DatabasePermit.NO_ACCESS, DatabaseAccessGate.currentPermit())
    }

    @Test
    fun corruptedSqliteCopyIsRejectedAndOriginalRemainsByteIdentical() {
        val corruptBytes = ByteArray(1024) { ((it * 13) % 251).toByte() }
        original.writeBytes(corruptBytes)
        val originalHash = sha256(original)

        val snapshot = DatabaseSnapshotCopier.copyOriginalDatabase(context) as SnapshotCopyResult.Created
        val result = DatabaseSnapshotValidator.validate(context, snapshot)

        assertTrue(result is SnapshotValidationResult.Rejected)
        assertEquals(originalHash, sha256(original))
        assertFalse(DatabaseAccessGate.isNormalAccessAllowed())
    }

    @Test
    fun missingRequiredRoomIndexRejectsOtherwiseReadableDatabase() {
        createRoomFixture(original, version = 14, omitTransactionDateIndex = true)
        val originalHash = sha256(original)
        val snapshot = DatabaseSnapshotCopier.copyOriginalDatabase(context) as SnapshotCopyResult.Created

        val result = DatabaseSnapshotValidator.validate(context, snapshot)

        assertTrue(result is SnapshotValidationResult.Rejected)
        assertEquals(
            SnapshotValidationResult.Rejected("ROOM_SCHEMA_INDEX_SET_MISMATCH_transactions"),
            result
        )
        assertEquals(originalHash, sha256(original))
    }

    @Test
    fun futureSchemaVersionIsNotAcceptedAsCurrentRoomDatabase() {
        createRoomFixture(original, version = 15)
        val snapshot = DatabaseSnapshotCopier.copyOriginalDatabase(context) as SnapshotCopyResult.Created

        val result = DatabaseSnapshotValidator.validate(context, snapshot)

        assertEquals(
            SnapshotValidationResult.Rejected("UNSUPPORTED_SCHEMA_VERSION_15"),
            result
        )
        assertFalse(DatabaseAccessGate.isNormalAccessAllowed())
    }

    @Test
    fun roomIdentityHashMustMatchTheGeneratedV14Schema() {
        createRoomFixture(original, version = 14)
        val database = SQLiteDatabase.openDatabase(original.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
        try {
            database.execSQL("UPDATE room_master_table SET identity_hash = 'different-identity' WHERE id = 42")
        } finally {
            database.close()
        }
        val snapshot = DatabaseSnapshotCopier.copyOriginalDatabase(context) as SnapshotCopyResult.Created

        val result = DatabaseSnapshotValidator.validate(context, snapshot)

        assertEquals(SnapshotValidationResult.Rejected("ROOM_IDENTITY_HASH_MISMATCH"), result)
        assertFalse(DatabaseAccessGate.isNormalAccessAllowed())
    }

    @Test
    fun tamperedRawSnapshotIsRejectedBeforeSqliteIsOpened() {
        createRoomFixture(original, version = 14)
        val snapshot = DatabaseSnapshotCopier.copyOriginalDatabase(context) as SnapshotCopyResult.Created
        snapshot.databaseFile.appendBytes(byteArrayOf(1, 2, 3, 4))

        val result = DatabaseSnapshotValidator.validate(context, snapshot)

        assertEquals(
            SnapshotValidationResult.Rejected("RAW_SNAPSHOT_MANIFEST_OR_HASH_INVALID"),
            result
        )
        assertFalse(DatabaseAccessGate.isNormalAccessAllowed())
    }

    @Test
    fun committedWalChangesAreValidatedFromIsolatedWorkspace() {
        createRoomFixture(original, version = 14)
        val sourceDatabase = SQLiteDatabase.openDatabase(
            original.absolutePath,
            null,
            SQLiteDatabase.OPEN_READWRITE
        )
        try {
            val mode = sourceDatabase.rawQuery("PRAGMA journal_mode=WAL", null).use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else ""
            }
            assertEquals("wal", mode.lowercase())

            sourceDatabase.execSQL(
                "INSERT INTO accounts(name, type, initialBalance, colorHex) VALUES(?, ?, ?, ?)",
                arrayOf("WAL-VALIDATION-TEST", "CASH", 125.0, "#123456")
            )

            val sourceWal = File(original.path + "-wal")
            assertTrue("SQLite should have a WAL file with committed data", sourceWal.isFile)
            val snapshot = DatabaseSnapshotCopier.copyOriginalDatabase(context)
            assertTrue(snapshot is SnapshotCopyResult.Created)
            val raw = snapshot as SnapshotCopyResult.Created
            assertTrue(raw.sourceWalObserved)

            val originalHashBeforeValidation = sha256(original)
            val originalWalHashBeforeValidation = sha256(sourceWal)
            val rawSnapshotHashBeforeValidation = sha256(raw.databaseFile)
            val rawWalHashBeforeValidation = sha256(raw.walFile!!)

            val result = DatabaseSnapshotValidator.validate(context, raw)

            assertTrue("WAL-backed snapshot validation should pass: $result", result is SnapshotValidationResult.Validated)
            val validated = result as SnapshotValidationResult.Validated
            assertTrue(validated.sqliteIntegrityChecksPassed)
            assertFalse(validated.authorizedForRoom)

            // Validation/checkpointing operates only on a second-generation workspace.
            assertEquals(originalHashBeforeValidation, sha256(original))
            assertEquals(originalWalHashBeforeValidation, sha256(sourceWal))
            assertEquals(rawSnapshotHashBeforeValidation, sha256(raw.databaseFile))
            assertEquals(rawWalHashBeforeValidation, sha256(raw.walFile!!))

            val verifiedDatabase = SQLiteDatabase.openDatabase(
                validated.validatedDatabaseFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY
            )
            try {
                val count = verifiedDatabase.rawQuery(
                    "SELECT COUNT(*) FROM accounts WHERE name = 'WAL-VALIDATION-TEST'",
                    null
                ).use { cursor ->
                    if (cursor.moveToFirst()) cursor.getInt(0) else 0
                }
                assertEquals(1, count)
            } finally {
                verifiedDatabase.close()
            }
        } finally {
            sourceDatabase.close()
        }
    }

    private fun createRoomFixture(
        file: File,
        version: Int,
        omitTransactionDateIndex: Boolean = false
    ) {
        file.parentFile?.mkdirs()
        val room = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            file.absolutePath
        ).allowMainThreadQueries().build()
        try {
            room.openHelper.writableDatabase
        } finally {
            room.close()
        }

        val database = SQLiteDatabase.openDatabase(
            file.absolutePath,
            null,
            SQLiteDatabase.OPEN_READWRITE
        )
        try {
            database.version = version
            if (omitTransactionDateIndex) {
                database.execSQL("DROP INDEX IF EXISTS index_transactions_date")
            }
        } finally {
            database.close()
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(4096)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
