package com.example.data.recovery

import android.content.Context
import java.io.File
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AppDatabaseQuarantineTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        DatabaseAccessGate.resetForTests()
        context = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        DatabaseAccessGate.resetForTests()
    }

    @Test
    fun deniedRoomAcquisitionDoesNotCreateDatabaseFile() {
        val databaseFile = context.getDatabasePath("budget_tracker_database")
        databaseFile.parentFile?.mkdirs()
        assertFalse(databaseFile.exists())

        assertThrows(IllegalStateException::class.java) {
            AppDatabase.getDatabase(
                context,
                CoroutineScope(SupervisorJob() + Dispatchers.IO)
            )
        }

        assertFalse("Room must not create the original database path while quarantined", databaseFile.exists())
    }
    @Test
    fun fullyAuthorizedPolicyCannotActivateTheOriginalDatabasePath() {
        val originalFile = context.getDatabasePath("budget_tracker_database")
        originalFile.parentFile?.mkdirs()
        originalFile.writeBytes(byteArrayOf(1, 2, 3))

        val permit = DatabaseAccessGate.applyEvidence(
            evidence = RecoveryEvidence(
                condition = DatabaseCondition.SUPPORTED,
                phase = RecoveryPhase.READY_FOR_AUTHORIZATION,
                snapshotCertified = true,
                recoveryCompleted = true,
                recoveredCopyValidated = true,
                explicitAuthorization = true
            ),
            activeDatabaseCopy = originalFile,
            context = context
        )

        assertFalse(permit == DatabasePermit.NORMAL_ACCESS)
        assertFalse(DatabaseAccessGate.isNormalAccessAllowed())
    }

    @Test
    fun nonEmptyPrivateRecoveryCopyCanBeSelectedByTheGate() {
        val recoveryCopy = File(context.filesDir, "recovery/active-copy.db").apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(1, 2, 3, 4))
        }

        val permit = DatabaseAccessGate.applyEvidence(
            evidence = RecoveryEvidence(
                condition = DatabaseCondition.SUPPORTED,
                phase = RecoveryPhase.READY_FOR_AUTHORIZATION,
                snapshotCertified = true,
                recoveryCompleted = true,
                recoveredCopyValidated = true,
                explicitAuthorization = true
            ),
            activeDatabaseCopy = recoveryCopy,
            context = context
        )

        assertTrue(permit == DatabasePermit.NORMAL_ACCESS)
        assertEquals(recoveryCopy.canonicalFile, DatabaseAccessGate.requireAuthorizedDatabaseFile(context))
    }

}
