package com.example.data.recovery

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class DatabaseMigrationRehearsalTest {

    private lateinit var context: Context
    private lateinit var original: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        original = context.getDatabasePath("budget_tracker_database")
        original.parentFile?.mkdirs()
        File(context.filesDir, "recovery").deleteRecursively()
        original.delete()
        File(original.path + "-wal").delete()
        File(original.path + "-shm").delete()
        File(original.path + "-journal").delete()
        DatabaseAccessGate.resetForTests()
    }

    @Test
    fun version9MigrationRunsOnlyOnCopiesAndThenPassesFullValidationAndAudit() {
        assertMigrationForVersion(9)
    }

    @Test
    fun versions10Through13AlsoPreserveDataAcrossTheFullRecoveryPipeline() {
        (10..13).forEach(::assertMigrationForVersion)
    }

    private fun assertMigrationForVersion(sourceVersion: Int) {
        File(context.filesDir, "recovery").deleteRecursively()
        original.delete()
        File(original.path + "-wal").delete()
        File(original.path + "-shm").delete()
        File(original.path + "-journal").delete()
        DatabaseAccessGate.resetForTests()

        createLegacyFixture(sourceVersion)
        val originalHash = sha256(original)

        val copyResult = DatabaseSnapshotCopier.copyOriginalDatabase(context)
        assertTrue("v$sourceVersion raw evidence snapshot should be created: $copyResult",
            copyResult is SnapshotCopyResult.Created)
        val raw = copyResult as SnapshotCopyResult.Created
        val rawHash = sha256(raw.databaseFile)

        val rehearsal = DatabaseMigrationRehearsal.rehearse(context, raw)
        assertTrue("v$sourceVersion migration rehearsal should succeed: $rehearsal",
            rehearsal is DatabaseMigrationRehearsal.Result.Completed)
        val migrated = (rehearsal as DatabaseMigrationRehearsal.Result.Completed).migration
        assertEquals(sourceVersion, migrated.sourceSchemaVersion)

        val migratedDb = SQLiteDatabase.openDatabase(
            migrated.databaseFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY
        )
        try {
            assertEquals(14, migratedDb.version)
        } finally {
            migratedDb.close()
        }

        val validation = DatabaseSnapshotValidator.validateMigrated(context, migrated)
        assertTrue("v$sourceVersion migrated copy should be independently validated: $validation",
            validation is SnapshotValidationResult.Validated)
        val validated = validation as SnapshotValidationResult.Validated
        assertEquals(14, validated.schemaVersion)
        assertTrue(validated.sqliteIntegrityChecksPassed)
        assertFalse(validated.authorizedForRoom)

        val audit = DatabaseFinancialConsistencyAuditor.audit(context, validated)
        assertTrue("v$sourceVersion financial scan should complete: $audit",
            audit is DatabaseFinancialConsistencyAuditor.Result.Completed)
        val completed = audit as DatabaseFinancialConsistencyAuditor.Result.Completed
        assertTrue("v$sourceVersion unexpected financial findings: ${completed.issues}",
            completed.issues.isEmpty())
        assertFalse(completed.roomAuthorized)

        val verify = SQLiteDatabase.openDatabase(
            validated.validatedDatabaseFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY
        )
        try {
            verify.rawQuery(
                "SELECT amount, description FROM transactions WHERE id = 91", null
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(123.45, cursor.getDouble(0), 0.001)
                assertEquals("Legacy expense", cursor.getString(1))
            }
            verify.rawQuery(
                "SELECT title, amount, frequency, anchorDate, nextDueDate FROM recurring_expenses WHERE id = 77",
                null
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Legacy monthly", cursor.getString(0))
                assertEquals(850.0, cursor.getDouble(1), 0.001)
                assertEquals("MONTHLY", cursor.getString(2))
                assertEquals(0L, cursor.getLong(3))
                assertEquals(0L, cursor.getLong(4))
            }
            verify.rawQuery("SELECT COUNT(*) FROM businesses", null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(
                    "v$sourceVersion should have no migrated placeholder; only a v12/v13 custom business survives",
                    if (sourceVersion >= 12) 1 else 0,
                    cursor.getInt(0)
                )
            }
            if (sourceVersion >= 12) {
                verify.rawQuery("SELECT name, inventoryValue FROM businesses WHERE id = 2", null).use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("My custom business", cursor.getString(0))
                    assertEquals(1234.5, cursor.getDouble(1), 0.001)
                }
            }
        } finally {
            verify.close()
        }

        // Neither the original database nor the raw evidence snapshot may be changed.
        assertEquals("Original v$sourceVersion bytes changed", originalHash, sha256(original))
        assertEquals("Raw v$sourceVersion snapshot bytes changed", rawHash, sha256(raw.databaseFile))
        assertFalse(DatabaseAccessGate.isNormalAccessAllowed())
    }

    private fun createLegacyFixture(sourceVersion: Int) {
        require(sourceVersion in 9..13)
        val current = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            original.absolutePath
        ).allowMainThreadQueries().build()
        try {
            val db = current.openHelper.writableDatabase
            db.execSQL(
                "INSERT INTO accounts(id, name, type, initialBalance, colorHex) VALUES(1, 'Cash', 'CASH', 100.0, '#00AA00')"
            )
            db.execSQL(
                "INSERT INTO categories(id, name, iconKey, isDefault, maxMonthlyBudget, colorHex, type) VALUES(41, 'Food', 'food', 0, NULL, '#00AA00', 'EXPENSE')"
            )
            db.execSQL(
                "INSERT INTO transactions(id, amount, type, categoryId, description, date, accountId, tags) VALUES(91, 123.45, 'EXPENSE', 41, 'Legacy expense', 1700000000000, 1, NULL)"
            )
            db.execSQL(
                "INSERT INTO recurring_expenses(id, title, amount, categoryId, accountId, dayOfMonth, description, tags, isEnabled, lastExecutedMonth, autoCreate, createdAt) VALUES(77, 'Legacy monthly', 850.0, 41, 1, 12, 'Preserve this', NULL, 1, NULL, 0, 1700000000000)"
            )
            db.execSQL(
                """
                INSERT INTO businesses
                (id, name, type, description, colorHex, iconKey, inventoryValue, createdAt)
                VALUES (1, 'INSERC Store', 'Tecnología y Comercio',
                        'Negocio principal de servicios y venta comercial',
                        '#2563EB', 'storefront', 0.0, 1704067200000)
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO businesses
                (id, name, type, description, colorHex, iconKey, inventoryValue, createdAt)
                VALUES (2, 'My custom business', 'Retail', 'User-created business',
                        '#10B981', 'storefront', 1234.5, 1704067200001)
                """.trimIndent()
            )
        } finally {
            current.close()
        }

        val legacy = SQLiteDatabase.openDatabase(
            original.absolutePath, null, SQLiteDatabase.OPEN_READWRITE
        )
        try {
            // Before v13, recurrence frequency and anchored-date fields did not exist.
            if (sourceVersion < 13) {
                legacy.execSQL("DROP TABLE recurring_expenses")
                legacy.execSQL(
                    """
                    CREATE TABLE recurring_expenses (
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
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                legacy.execSQL("CREATE INDEX index_recurring_expenses_categoryId ON recurring_expenses(categoryId)")
                legacy.execSQL("CREATE INDEX index_recurring_expenses_accountId ON recurring_expenses(accountId)")
                legacy.execSQL("CREATE INDEX index_recurring_expenses_dayOfMonth ON recurring_expenses(dayOfMonth)")
                legacy.execSQL("CREATE INDEX index_recurring_expenses_isEnabled ON recurring_expenses(isEnabled)")
                legacy.execSQL(
                    """
                    INSERT INTO recurring_expenses
                    (id, title, amount, categoryId, accountId, dayOfMonth, description, tags, isEnabled, lastExecutedMonth, autoCreate, createdAt)
                    VALUES (77, 'Legacy monthly', 850.0, 41, 1, 12, 'Preserve this', NULL, 1, NULL, 0, 1700000000000)
                    """.trimIndent()
                )
            }

            // The businesses table was introduced in 11 -> 12.
            if (sourceVersion < 12) {
                legacy.execSQL("DROP TABLE businesses")
            }

            // These indexes were introduced in 10 -> 11.
            if (sourceVersion < 11) {
                legacy.execSQL("DROP INDEX IF EXISTS index_transactions_type_date")
                legacy.execSQL("DROP INDEX IF EXISTS index_transactions_accountId_date")
            }

            // Category color/type fields were introduced in 9 -> 10.
            if (sourceVersion < 10) {
                legacy.execSQL(
                    """
                    CREATE TABLE categories_legacy (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        iconKey TEXT NOT NULL,
                        isDefault INTEGER NOT NULL,
                        maxMonthlyBudget REAL
                    )
                    """.trimIndent()
                )
                legacy.execSQL(
                    """
                    INSERT INTO categories_legacy (id, name, iconKey, isDefault, maxMonthlyBudget)
                    SELECT id, name, iconKey, isDefault, maxMonthlyBudget FROM categories
                    """.trimIndent()
                )
                legacy.execSQL("DROP TABLE categories")
                legacy.execSQL("ALTER TABLE categories_legacy RENAME TO categories")
            }
            legacy.version = sourceVersion
        } finally {
            legacy.close()
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
