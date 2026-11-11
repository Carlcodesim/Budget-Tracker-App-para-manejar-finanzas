package com.example.data.recovery

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.room.Room
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
import java.util.Properties

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DatabaseFinancialConsistencyAuditorTest {

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
    fun validDataCompletesWithoutFindingsAndDoesNotChangeDatabase() {
        createRoomFixture(original)
        insertValidRows(original)
        val originalHash = sha256(original)
        val validated = validatedWorkspace()

        val result = DatabaseFinancialConsistencyAuditor.audit(context, validated)

        assertTrue("Expected completed audit, got $result", result is DatabaseFinancialConsistencyAuditor.Result.Completed)
        val completed = result as DatabaseFinancialConsistencyAuditor.Result.Completed
        assertTrue(completed.issues.isEmpty())
        assertTrue(completed.recordsExamined > 0)
        assertEquals(originalHash, sha256(original))
        assertEquals(completed.databaseSha256Before, completed.databaseSha256After)
        assertTrue(completed.databaseUnchanged)
        assertFalse(completed.roomAuthorized)
        assertFalse(DatabaseAccessGate.isNormalAccessAllowed())
        val report = Properties().apply { completed.reportFile.inputStream().use { load(it) } }
        assertEquals("true", report.getProperty("scan_completed"))
        assertEquals("true", report.getProperty("database_unchanged"))
        assertEquals("false", report.getProperty("normal_access_authorized"))
        assertEquals("false", report.getProperty("room_authorized"))
    }

    @Test
    fun classifiesFinancialAnomaliesWithoutCorrectingAnyStoredValue() {
        createRoomFixture(original)
        insertValidRows(original)
        val writeDb = SQLiteDatabase.openDatabase(original.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
        try {
            writeDb.execSQL(
                "INSERT INTO transactions(id, amount, type, categoryId, description, date, accountId, tags) VALUES(2, -3, 'EXPENSE', 77, 'invalid row', 2000, 77, NULL)"
            )
            writeDb.execSQL(
                "INSERT INTO transactions(id, amount, type, categoryId, description, date, accountId, tags) VALUES(3, 30, 'TRANSFER', 1, 'Transferencia a B', 3000, 1, 'TRANSFER,OUT,CORR:pair-1')"
            )
            writeDb.execSQL(
                "INSERT INTO savings_goals(id, name, targetAmount, currentAmount, deadline, createdAt, description, priority, category) VALUES(1, 'Meta', 100, 150, NULL, 1, '', 'Media', 'Otros')"
            )
            writeDb.execSQL(
                "INSERT INTO reminders(id, type, title, description, isEnabled, amount, dueDateDay, time, notifyDaysBefore, notifyTime, associatedGoalId, isCompletedThisMonth) VALUES(1, 'OTRO', 'Aviso', '', 1, -5, 32, NULL, -1, '09:00', 999, 0)"
            )
            writeDb.execSQL(
                "INSERT INTO recurring_expenses(id, title, amount, categoryId, accountId, dayOfMonth, description, tags, isEnabled, lastExecutedMonth, autoCreate, createdAt, frequency, anchorDate, nextDueDate) VALUES(1, 'Recurrente', 45, 1, 0, 31, NULL, NULL, 1, '2026-13', 0, 1, 'UNKNOWN', 0, 0)"
            )
            writeDb.execSQL(
                "UPDATE businesses SET type = 'Tecnología y Comercio', description = 'Negocio comercial principal', colorHex = '#2563EB', iconKey = 'storefront', inventoryValue = 0 WHERE id = 1"
            )
        } finally {
            writeDb.close()
        }

        val validated = validatedWorkspace()
        val validatedHashBeforeAudit = sha256(validated.validatedDatabaseFile)

        val result = DatabaseFinancialConsistencyAuditor.audit(context, validated)

        assertTrue("Expected completed audit, got $result", result is DatabaseFinancialConsistencyAuditor.Result.Completed)
        val completed = result as DatabaseFinancialConsistencyAuditor.Result.Completed
        val codes = completed.issues.map { it.code }.toSet()
        assertTrue("Missing invalid amount finding: $codes", "TRANSACTION_AMOUNT_NOT_POSITIVE_FINITE" in codes)
        assertTrue("Missing orphan account finding: $codes", "ORPHAN_TRANSACTION_ACCOUNT" in codes)
        assertTrue("Missing orphan category finding: $codes", "ORPHAN_TRANSACTION_CATEGORY" in codes)
        assertTrue("Missing transfer pairing finding: $codes", "TRANSFER_CORRELATION_PAIR_INCOMPLETE_OR_INCONSISTENT" in codes)
        assertTrue("Missing savings goal overshoot finding: $codes", "SAVINGS_GOAL_EXCEEDS_TARGET" in codes)
        assertTrue("Missing reminder reference finding: $codes", "REMINDER_GOAL_REFERENCE_MISSING" in codes)
        assertTrue("Missing recurring frequency finding: $codes", "RECURRING_FREQUENCY_UNKNOWN" in codes)
        assertTrue("Missing recurring month finding: $codes", "RECURRING_LAST_MONTH_INVALID" in codes)
        assertTrue("Missing ambiguous legacy business finding: $codes", "BUSINESS_POSSIBLE_LEGACY_PLACEHOLDER" in codes)

        assertEquals(validatedHashBeforeAudit, sha256(validated.validatedDatabaseFile))
        assertEquals(completed.databaseSha256Before, completed.databaseSha256After)
        assertFalse(completed.roomAuthorized)
        assertFalse(DatabaseAccessGate.isNormalAccessAllowed())

        val readBack = SQLiteDatabase.openDatabase(
            validated.validatedDatabaseFile.absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY
        )
        try {
            val goalAmount = readBack.rawQuery("SELECT currentAmount FROM savings_goals WHERE id = 1", null).use {
                assertTrue(it.moveToFirst())
                it.getDouble(0)
            }
            val transactionAmount = readBack.rawQuery("SELECT amount FROM transactions WHERE id = 2", null).use {
                assertTrue(it.moveToFirst())
                it.getDouble(0)
            }
            assertEquals(150.0, goalAmount, 0.0)
            assertEquals(-3.0, transactionAmount, 0.0)
        } finally {
            readBack.close()
        }
    }

    @Test
    fun changedValidationCopyIsRejectedBeforeAudit() {
        createRoomFixture(original)
        insertValidRows(original)
        val validated = validatedWorkspace()
        validated.validatedDatabaseFile.appendBytes(byteArrayOf(7, 8, 9))

        val result = DatabaseFinancialConsistencyAuditor.audit(context, validated)

        assertEquals(
            DatabaseFinancialConsistencyAuditor.Result.Rejected("VALIDATED_DATABASE_HASH_CHANGED"),
            result
        )
        assertFalse(DatabaseAccessGate.isNormalAccessAllowed())
    }

    private fun validatedWorkspace(): SnapshotValidationResult.Validated {
        val snapshot = DatabaseSnapshotCopier.copyOriginalDatabase(context)
        assertTrue("Raw snapshot failed: $snapshot", snapshot is SnapshotCopyResult.Created)
        val validation = DatabaseSnapshotValidator.validate(context, snapshot as SnapshotCopyResult.Created)
        assertTrue("SQLite/schema validation failed: $validation", validation is SnapshotValidationResult.Validated)
        return validation as SnapshotValidationResult.Validated
    }

    private fun insertValidRows(file: File) {
        val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
        try {
            db.execSQL("INSERT INTO accounts(id, name, type, initialBalance, colorHex) VALUES(1, 'Efectivo', 'CASH', 100, '#00AA00')")
            db.execSQL("INSERT INTO categories(id, name, iconKey, isDefault, maxMonthlyBudget, colorHex, type) VALUES(1, 'Comida', 'food', 1, NULL, '#00AA00', 'EXPENSE')")
            db.execSQL("INSERT INTO transactions(id, amount, type, categoryId, description, date, accountId, tags) VALUES(1, 20, 'EXPENSE', 1, 'Compra', 1000, 1, NULL)")
            db.execSQL("INSERT INTO businesses(id, name, type, description, colorHex, iconKey, inventoryValue, createdAt) VALUES(1, 'INSERC Store', 'Comercio', '', '#2563EB', 'storefront', 0, 1)")
        } finally {
            db.close()
        }
    }

    private fun createRoomFixture(file: File) {
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
