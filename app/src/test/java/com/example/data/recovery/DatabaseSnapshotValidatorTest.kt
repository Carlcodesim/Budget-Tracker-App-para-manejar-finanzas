package com.example.data.recovery

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
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

    private fun createRoomFixture(
        file: File,
        version: Int,
        omitTransactionDateIndex: Boolean = false
    ) {
        file.parentFile?.mkdirs()
        val database = SQLiteDatabase.openOrCreateDatabase(file, null)
        try {
            database.execSQL(
                """CREATE TABLE transactions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    amount REAL NOT NULL,
                    type TEXT NOT NULL,
                    categoryId INTEGER NOT NULL,
                    description TEXT,
                    date INTEGER NOT NULL,
                    accountId INTEGER NOT NULL,
                    tags TEXT
                )"""
            )
            database.execSQL("CREATE INDEX index_transactions_categoryId ON transactions(categoryId)")
            if (!omitTransactionDateIndex) {
                database.execSQL("CREATE INDEX index_transactions_date ON transactions(date)")
            }
            database.execSQL("CREATE INDEX index_transactions_accountId ON transactions(accountId)")
            database.execSQL("CREATE INDEX index_transactions_type_date ON transactions(type, date)")
            database.execSQL("CREATE INDEX index_transactions_accountId_date ON transactions(accountId, date)")

            database.execSQL(
                """CREATE TABLE categories (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    name TEXT NOT NULL,
                    iconKey TEXT NOT NULL,
                    isDefault INTEGER NOT NULL,
                    maxMonthlyBudget REAL,
                    colorHex TEXT NOT NULL,
                    type TEXT NOT NULL
                )"""
            )
            database.execSQL(
                """CREATE TABLE accounts (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    name TEXT NOT NULL,
                    type TEXT NOT NULL,
                    initialBalance REAL NOT NULL,
                    colorHex TEXT NOT NULL
                )"""
            )
            database.execSQL(
                """CREATE TABLE savings_goals (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    name TEXT NOT NULL,
                    targetAmount REAL NOT NULL,
                    currentAmount REAL NOT NULL,
                    deadline INTEGER,
                    createdAt INTEGER NOT NULL,
                    description TEXT NOT NULL,
                    priority TEXT NOT NULL,
                    category TEXT NOT NULL
                )"""
            )
            database.execSQL(
                """CREATE TABLE reminders (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    type TEXT NOT NULL,
                    title TEXT NOT NULL,
                    description TEXT NOT NULL,
                    isEnabled INTEGER NOT NULL,
                    amount REAL,
                    dueDateDay INTEGER,
                    time TEXT,
                    notifyDaysBefore INTEGER NOT NULL,
                    notifyTime TEXT NOT NULL,
                    associatedGoalId INTEGER,
                    isCompletedThisMonth INTEGER NOT NULL
                )"""
            )
            database.execSQL(
                """CREATE TABLE recurring_expenses (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    title TEXT NOT NULL,
                    amount REAL NOT NULL,
                    categoryId INTEGER NOT NULL,
                    accountId INTEGER NOT NULL,
                    dayOfMonth INTEGER NOT NULL,
                    description TEXT,
                    tags TEXT,
                    isEnabled INTEGER NOT NULL,
                    lastExecutedMonth TEXT,
                    autoCreate INTEGER NOT NULL,
                    createdAt INTEGER NOT NULL,
                    frequency TEXT NOT NULL DEFAULT 'MONTHLY',
                    anchorDate INTEGER NOT NULL DEFAULT 0,
                    nextDueDate INTEGER NOT NULL DEFAULT 0
                )"""
            )
            database.execSQL("CREATE INDEX index_recurring_expenses_categoryId ON recurring_expenses(categoryId)")
            database.execSQL("CREATE INDEX index_recurring_expenses_accountId ON recurring_expenses(accountId)")
            database.execSQL("CREATE INDEX index_recurring_expenses_dayOfMonth ON recurring_expenses(dayOfMonth)")
            database.execSQL("CREATE INDEX index_recurring_expenses_isEnabled ON recurring_expenses(isEnabled)")

            database.execSQL(
                """CREATE TABLE businesses (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    name TEXT NOT NULL,
                    type TEXT NOT NULL,
                    description TEXT NOT NULL,
                    colorHex TEXT NOT NULL,
                    iconKey TEXT NOT NULL,
                    inventoryValue REAL NOT NULL,
                    createdAt INTEGER NOT NULL
                )"""
            )
            database.execSQL(
                "CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)"
            )
            database.execSQL(
                "INSERT INTO room_master_table(id, identity_hash) VALUES(42, 'test-room-schema-identity')"
            )
            database.version = version
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
