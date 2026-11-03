package com.example.data.recovery

/**
 * Process-wide fail-closed gate for every Room database acquisition.
 *
 * The process starts with NO_ACCESS. Only the recovery/startup coordinator may
 * publish evidence, and normal access is enabled only by the strict policy.
 * This gate intentionally has no "open" default and does not persist permission
 * across process death.
 */
object DatabaseAccessGate {
    @Volatile
    private var permit: DatabasePermit = DatabasePermit.NO_ACCESS

    @Volatile
    private var denialReason: String = "Startup recovery evaluation has not completed"

    fun currentPermit(): DatabasePermit = permit

    fun isNormalAccessAllowed(): Boolean = permit == DatabasePermit.NORMAL_ACCESS

    fun requireNormalAccess() {
        check(isNormalAccessAllowed()) {
            "Room access denied by strict database quarantine: $denialReason"
        }
    }

    /**
     * Internal entry point reserved for the recovery coordinator.
     * Until a durable startup evaluator exists, no production code calls this.
     */
    @Synchronized
    internal fun applyEvidence(evidence: RecoveryEvidence): DatabasePermit {
        val nextPermit = StrictDatabaseAccessPolicy.permit(evidence)
        permit = nextPermit
        denialReason = if (nextPermit == DatabasePermit.NORMAL_ACCESS) {
            "Authorized recovery copy"
        } else {
            "Recovery state does not satisfy all authorization requirements"
        }
        return nextPermit
    }

    @Synchronized
    internal fun resetForTests() {
        permit = DatabasePermit.NO_ACCESS
        denialReason = "Test reset"
    }
}
