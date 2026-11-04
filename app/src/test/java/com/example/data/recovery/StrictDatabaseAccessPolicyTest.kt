package com.example.data.recovery

import org.junit.Assert.assertEquals
import org.junit.Test

class StrictDatabaseAccessPolicyTest {

    @Test
    fun corruptOriginalNeverGetsNormalAccess() {
        val evidence = RecoveryEvidence(
            condition = DatabaseCondition.CORRUPT,
            phase = RecoveryPhase.SAFE_MODE,
            snapshotCertified = true,
            recoveryCompleted = true,
            recoveredCopyValidated = true,
            explicitAuthorization = true
        )

        assertEquals(DatabasePermit.NO_ACCESS, StrictDatabaseAccessPolicy.permit(evidence))
    }

    @Test
    fun futureSchemaIsDeniedEvenIfAuthorizationFlagsAreSet() {
        val evidence = RecoveryEvidence(
            condition = DatabaseCondition.FUTURE_VERSION,
            phase = RecoveryPhase.READY_FOR_AUTHORIZATION,
            snapshotCertified = true,
            recoveryCompleted = true,
            recoveredCopyValidated = true,
            explicitAuthorization = true
        )

        assertEquals(DatabasePermit.RECOVERY_COPY_ONLY, StrictDatabaseAccessPolicy.permit(evidence))
    }

    @Test
    fun snapshotInProgressAllowsOnlySnapshotOperation() {
        val evidence = RecoveryEvidence(
            condition = DatabaseCondition.UNKNOWN,
            phase = RecoveryPhase.SNAPSHOTTING
        )

        assertEquals(DatabasePermit.SNAPSHOT_ONLY, StrictDatabaseAccessPolicy.permit(evidence))
    }

    @Test
    fun missingAuthorizationFailsClosed() {
        val evidence = RecoveryEvidence(
            condition = DatabaseCondition.SUPPORTED,
            phase = RecoveryPhase.READY_FOR_AUTHORIZATION,
            snapshotCertified = true,
            recoveryCompleted = true,
            recoveredCopyValidated = true,
            explicitAuthorization = false
        )

        assertEquals(DatabasePermit.NO_ACCESS, StrictDatabaseAccessPolicy.permit(evidence))
    }

    @Test
    fun normalAccessRequiresAllIndependentConditions() {
        val evidence = RecoveryEvidence(
            condition = DatabaseCondition.SUPPORTED,
            phase = RecoveryPhase.READY_FOR_AUTHORIZATION,
            snapshotCertified = true,
            recoveryCompleted = true,
            recoveredCopyValidated = true,
            explicitAuthorization = true
        )

        assertEquals(DatabasePermit.NORMAL_ACCESS, StrictDatabaseAccessPolicy.permit(evidence))
    }

    @Test
    fun absentDatabaseIsNotAutomaticallyTreatedAsCleanInstall() {
        val evidence = RecoveryEvidence(
            condition = DatabaseCondition.ABSENT,
            phase = RecoveryPhase.PREFLIGHT,
            snapshotCertified = false,
            recoveryCompleted = false,
            recoveredCopyValidated = false,
            explicitAuthorization = false
        )

        assertEquals(DatabasePermit.NO_ACCESS, StrictDatabaseAccessPolicy.permit(evidence))
    }

    @Test
    fun unsupportedLegacySchemaCannotBeOpenedNormally() {
        val evidence = RecoveryEvidence(
            condition = DatabaseCondition.LEGACY_UNSUPPORTED,
            phase = RecoveryPhase.RECOVERY_REQUIRED
        )

        assertEquals(DatabasePermit.RECOVERY_COPY_ONLY, StrictDatabaseAccessPolicy.permit(evidence))
    }

    @Test
    fun ioFailureFailsClosed() {
        val evidence = RecoveryEvidence(
            condition = DatabaseCondition.IO_ERROR,
            phase = RecoveryPhase.PREFLIGHT
        )

        assertEquals(DatabasePermit.NO_ACCESS, StrictDatabaseAccessPolicy.permit(evidence))
    }
    @Test
    fun recognizedHeaderIsNotEnoughForNormalAccess() {
        val evidence = RecoveryEvidence(
            condition = DatabaseCondition.HEADER_RECOGNIZED,
            phase = RecoveryPhase.READY_FOR_AUTHORIZATION,
            snapshotCertified = true,
            recoveryCompleted = true,
            recoveredCopyValidated = true,
            explicitAuthorization = true
        )

        assertEquals(DatabasePermit.RECOVERY_COPY_ONLY, StrictDatabaseAccessPolicy.permit(evidence))
    }

}
