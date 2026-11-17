package com.example.data.recovery

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DatabaseAccessGateTest {

    @Before
    fun resetGate() {
        DatabaseAccessGate.resetForTests()
    }

    @After
    fun restoreFailClosedState() {
        DatabaseAccessGate.resetForTests()
    }

    @Test
    fun processStartsWithoutNormalDatabaseAccess() {
        assertEquals(DatabasePermit.NO_ACCESS, DatabaseAccessGate.currentPermit())
        assertFalse(DatabaseAccessGate.isNormalAccessAllowed())
    }

    @Test
    fun roomAccessIsRejectedUntilRecoveryIsFullyAuthorized() {
        val exception = assertThrows(IllegalStateException::class.java) {
            DatabaseAccessGate.requireNormalAccess()
        }

        assertTrue(exception.message.orEmpty().contains("Room access denied"))
    }

    @Test
    fun policyEvidenceWithoutAnAuthorizedRecoveryFileCannotEnableNormalAccess() {
        val permit = DatabaseAccessGate.applyEvidence(
            evidence = RecoveryEvidence(
                condition = DatabaseCondition.SUPPORTED,
                phase = RecoveryPhase.READY_FOR_AUTHORIZATION,
                snapshotCertified = true,
                recoveryCompleted = true,
                recoveredCopyValidated = true,
                explicitAuthorization = true
            ),
            activeDatabaseCopy = null,
            context = null
        )

        assertEquals(DatabasePermit.NO_ACCESS, permit)
        assertFalse(DatabaseAccessGate.isNormalAccessAllowed())
    }

    @Test
    fun incompleteEvidenceKeepsGateClosed() {
        val permit = DatabaseAccessGate.applyEvidence(
            evidence = RecoveryEvidence(
                condition = DatabaseCondition.SUPPORTED,
                phase = RecoveryPhase.READY_FOR_AUTHORIZATION,
                snapshotCertified = true,
                recoveryCompleted = true,
                recoveredCopyValidated = true,
                explicitAuthorization = false
            ),
            activeDatabaseCopy = null,
            context = null
        )

        assertEquals(DatabasePermit.NO_ACCESS, permit)
        assertFalse(DatabaseAccessGate.isNormalAccessAllowed())
        assertThrows(IllegalStateException::class.java) {
            DatabaseAccessGate.requireNormalAccess()
        }
    }
}
