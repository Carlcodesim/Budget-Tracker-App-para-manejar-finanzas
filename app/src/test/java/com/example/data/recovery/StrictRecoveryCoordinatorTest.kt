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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class StrictRecoveryCoordinatorTest {

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
        StrictRecoveryCoordinator.resetForTests()
    }

    @Test
    fun laterLaunchRevalidatesThePreviouslyActivatedDatabaseInsteadOfStaleOriginal() {
        createRoomFixture(original)
        val originalHash = sha256(original)

        val firstPreparation = StrictRecoveryCoordinator.prepare(context)
        assertTrue("First preparation should be ready: $firstPreparation",
            firstPreparation is StrictRecoveryCoordinator.PreparationResult.ReadyForAuthorization)

        val firstAuthorization = StrictRecoveryCoordinator.authorizeAfterExplicitUserConfirmation(context)
        assertTrue("First authorization should pass: $firstAuthorization",
            firstAuthorization is StrictRecoveryCoordinator.AuthorizationResult.Authorized)
        val firstActiveFile = (firstAuthorization as StrictRecoveryCoordinator.AuthorizationResult.Authorized).databaseFile
        assertTrue(DatabaseAccessGate.isNormalAccessAllowed())

        // Simulate a financial record created while the app is using the active copy.
        val activeDb = SQLiteDatabase.openDatabase(firstActiveFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
        try {
            activeDb.execSQL("INSERT INTO accounts(id, name, type, initialBalance, colorHex) VALUES(1, 'Efectivo', 'CASH', 50, '#00AA00')")
            activeDb.execSQL("INSERT INTO categories(id, name, iconKey, isDefault, maxMonthlyBudget, colorHex, type) VALUES(1, 'Comida', 'food', 1, NULL, '#00AA00', 'EXPENSE')")
            activeDb.execSQL("INSERT INTO transactions(id, amount, type, categoryId, description, date, accountId, tags) VALUES(1, 12, 'EXPENSE', 1, 'Compra nueva', 1000, 1, NULL)")
        } finally {
            activeDb.close()
        }

        // A fresh process starts with no in-memory permit or pending authorization.
        DatabaseAccessGate.resetForTests()
        StrictRecoveryCoordinator.resetForTests()
        assertFalse(DatabaseAccessGate.isNormalAccessAllowed())

        val secondPreparation = StrictRecoveryCoordinator.prepare(context)
        assertTrue("Second preparation should revalidate the previous active copy: $secondPreparation",
            secondPreparation is StrictRecoveryCoordinator.PreparationResult.ReadyForAuthorization)
        val secondReady = secondPreparation as StrictRecoveryCoordinator.PreparationResult.ReadyForAuthorization
        assertEquals(3L, secondReady.recordsExamined)

        val secondAuthorization = StrictRecoveryCoordinator.restorePreviouslyAuthorizedCopy(context)
        assertTrue("Previously approved generation should restore after fresh validation: $secondAuthorization",
            secondAuthorization is StrictRecoveryCoordinator.AuthorizationResult.Authorized)
        val secondActiveFile = (secondAuthorization as StrictRecoveryCoordinator.AuthorizationResult.Authorized).databaseFile
        assertTrue(secondActiveFile.canonicalFile != firstActiveFile.canonicalFile)

        val verify = SQLiteDatabase.openDatabase(secondActiveFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        try {
            val transactionCount = verify.rawQuery("SELECT COUNT(*) FROM transactions", null).use {
                assertTrue(it.moveToFirst())
                it.getInt(0)
            }
            assertEquals(1, transactionCount)
        } finally {
            verify.close()
        }

        assertEquals("The original database must remain byte-identical across activations",
            originalHash, sha256(original))
        assertTrue(DatabaseAccessGate.isNormalAccessAllowed())
        val registered = ActiveDatabaseCopyRegistry.sourceForNextValidation(context)
        assertTrue(registered is ActiveDatabaseCopyRegistry.Source.PreviouslyActivatedCopy)
        assertEquals(secondActiveFile.canonicalFile,
            (registered as ActiveDatabaseCopyRegistry.Source.PreviouslyActivatedCopy).file)
    }

    @Test
    fun financialFindingsNeverProducePendingAuthorization() {
        createRoomFixture(original)
        val db = SQLiteDatabase.openDatabase(original.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
        try {
            db.execSQL("INSERT INTO savings_goals(id, name, targetAmount, currentAmount, deadline, createdAt, description, priority, category) VALUES(1, 'Meta', 100, 120, NULL, 1, '', 'Media', 'Otros')")
        } finally {
            db.close()
        }

        val preparation = StrictRecoveryCoordinator.prepare(context)

        assertTrue(preparation is StrictRecoveryCoordinator.PreparationResult.ManualReviewRequired)
        assertFalse(DatabaseAccessGate.isNormalAccessAllowed())
        assertEquals(
            StrictRecoveryCoordinator.AuthorizationResult.Rejected("NO_PENDING_AUTHORIZATION"),
            StrictRecoveryCoordinator.authorizeAfterExplicitUserConfirmation(context)
        )
    }

    @Test
    fun firstInstallCreatesOnlyAnIsolatedFreshDatabaseWithoutRecurringOrBusinessDefaults() {
        val preparation = StrictRecoveryCoordinator.prepare(context)
        System.err.println("FIRST_INSTALL_PREPARATION=$preparation")
        assertTrue("Expected fresh bootstrap validation: $preparation",
            preparation is StrictRecoveryCoordinator.PreparationResult.ReadyForAuthorization)
        assertFalse("The legacy database location must remain absent", original.exists())
        assertFalse(DatabaseAccessGate.isNormalAccessAllowed())

        val automaticRestore = StrictRecoveryCoordinator.restorePreviouslyAuthorizedCopy(context)
        assertEquals(
            StrictRecoveryCoordinator.AuthorizationResult.Rejected("PRIOR_EXPLICIT_AUTHORIZATION_REQUIRED"),
            automaticRestore
        )

        val authorization = StrictRecoveryCoordinator.authorizeAfterExplicitUserConfirmation(context)
        assertTrue("Fresh database should require and accept explicit confirmation: $authorization",
            authorization is StrictRecoveryCoordinator.AuthorizationResult.Authorized)
        val activeFile = (authorization as StrictRecoveryCoordinator.AuthorizationResult.Authorized).databaseFile

        val db = SQLiteDatabase.openDatabase(activeFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        try {
            fun count(table: String): Int = db.rawQuery("SELECT COUNT(*) FROM $table", null).use {
                assertTrue(it.moveToFirst())
                it.getInt(0)
            }
            assertEquals(10, count("categories"))
            assertEquals(4, count("accounts"))
            assertEquals(0, count("businesses"))
            assertEquals(0, count("recurring_expenses"))
        } finally {
            db.close()
        }
        assertFalse("Fresh bootstrap must not create the legacy path", original.exists())
        assertTrue(DatabaseAccessGate.isNormalAccessAllowed())
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
