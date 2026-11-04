package com.example.data.recovery

enum class DatabaseCondition {
    ABSENT,
    HEADER_RECOGNIZED,
    SUPPORTED,
    LEGACY_MIGRATION_CANDIDATE,
    LEGACY_UNSUPPORTED,
    FUTURE_VERSION,
    UNKNOWN,
    CORRUPT,
    INCONSISTENT,
    IO_ERROR
}

enum class RecoveryPhase {
    PREFLIGHT,
    BACKUP_REQUIRED,
    SNAPSHOTTING,
    VALIDATING_SNAPSHOT,
    SNAPSHOT_CERTIFIED,
    RECOVERY_REQUIRED,
    MIGRATION_REHEARSAL,
    READY_FOR_AUTHORIZATION,
    SAFE_MODE
}

enum class DatabasePermit {
    NO_ACCESS,
    SNAPSHOT_ONLY,
    RECOVERY_COPY_ONLY,
    NORMAL_ACCESS
}

data class RecoveryEvidence(
    val condition: DatabaseCondition,
    val phase: RecoveryPhase,
    val snapshotCertified: Boolean = false,
    val recoveryCompleted: Boolean = false,
    val recoveredCopyValidated: Boolean = false,
    val explicitAuthorization: Boolean = false
)

object StrictDatabaseAccessPolicy {
    /**
     * Normal access is granted only when every independent safety condition is
     * true. Header recognition is deliberately insufficient.
     */
    fun permit(evidence: RecoveryEvidence): DatabasePermit {
        if (evidence.condition != DatabaseCondition.SUPPORTED &&
            !(evidence.condition == DatabaseCondition.ABSENT &&
                evidence.phase == RecoveryPhase.READY_FOR_AUTHORIZATION)
        ) {
            return when (evidence.phase) {
                RecoveryPhase.SNAPSHOTTING,
                RecoveryPhase.VALIDATING_SNAPSHOT -> DatabasePermit.SNAPSHOT_ONLY
                RecoveryPhase.RECOVERY_REQUIRED,
                RecoveryPhase.MIGRATION_REHEARSAL,
                RecoveryPhase.READY_FOR_AUTHORIZATION -> DatabasePermit.RECOVERY_COPY_ONLY
                else -> DatabasePermit.NO_ACCESS
            }
        }

        val fullyAuthorized =
            evidence.snapshotCertified &&
                evidence.recoveryCompleted &&
                evidence.recoveredCopyValidated &&
                evidence.explicitAuthorization &&
                evidence.phase == RecoveryPhase.READY_FOR_AUTHORIZATION

        return if (fullyAuthorized) DatabasePermit.NORMAL_ACCESS else DatabasePermit.NO_ACCESS
    }
}
