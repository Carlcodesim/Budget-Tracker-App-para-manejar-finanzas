package com.example.data.recovery

import android.content.Context
import java.io.File

/**
 * Process-wide fail-closed gate for every Room database acquisition.
 *
 * Normal access requires both a fully satisfied recovery policy and an explicit
 * active-copy file located below the app-private files directory. The legacy
 * database path under Context.getDatabasePath() is never an eligible active path.
 */
object DatabaseAccessGate {
    @Volatile
    private var permit: DatabasePermit = DatabasePermit.NO_ACCESS

    @Volatile
    private var denialReason: String = "Startup recovery evaluation has not completed"

    @Volatile
    private var authorizedDatabaseFile: File? = null

    fun currentPermit(): DatabasePermit = permit

    fun isNormalAccessAllowed(): Boolean = permit == DatabasePermit.NORMAL_ACCESS

    fun requireNormalAccess() {
        check(isNormalAccessAllowed()) {
            "Room access denied by strict database quarantine: $denialReason"
        }
    }

    /**
     * Internal entry point reserved for the recovery coordinator.
     *
     * A policy decision alone cannot authorize Room. A concrete, non-empty
     * recovered copy under filesDir must also be supplied. This prevents a future
     * caller from accidentally re-authorizing the immutable original database.
     */
    @Synchronized
    internal fun applyEvidence(
        evidence: RecoveryEvidence,
        activeDatabaseCopy: File?,
        context: Context?
    ): DatabasePermit {
        val policyPermit = StrictDatabaseAccessPolicy.permit(evidence)
        if (policyPermit != DatabasePermit.NORMAL_ACCESS) {
            authorizedDatabaseFile = null
            permit = policyPermit
            denialReason = "Recovery state does not satisfy all authorization requirements"
            return permit
        }

        val candidate = activeDatabaseCopy?.canonicalFile
        val privateRoot = context?.filesDir?.canonicalFile
        val originalDatabase = context?.getDatabasePath("budget_tracker_database")?.canonicalFile

        val isPrivateRecoveredCopy =
            candidate != null &&
                privateRoot != null &&
                candidate.path.startsWith(privateRoot.path + File.separator) &&
                candidate != originalDatabase &&
                candidate.isFile &&
                candidate.length() > 0L

        if (!isPrivateRecoveredCopy) {
            authorizedDatabaseFile = null
            permit = DatabasePermit.NO_ACCESS
            denialReason = "No validated active recovery copy is registered in app-private storage"
            return permit
        }

        authorizedDatabaseFile = candidate
        permit = DatabasePermit.NORMAL_ACCESS
        denialReason = "Authorized recovered database copy"
        return permit
    }

    /**
     * Returns the exact authorized recovery-copy path after rechecking that it
     * still exists and remains inside filesDir. No caller may substitute the
     * original database filename.
     */
    @Synchronized
    fun requireAuthorizedDatabaseFile(context: Context): File {
        requireNormalAccess()

        val candidate = authorizedDatabaseFile?.canonicalFile
        val privateRoot = context.applicationContext.filesDir.canonicalFile
        val originalDatabase = context.applicationContext
            .getDatabasePath("budget_tracker_database")
            .canonicalFile

        val stillValid =
            candidate != null &&
                candidate.path.startsWith(privateRoot.path + File.separator) &&
                candidate != originalDatabase &&
                candidate.isFile &&
                candidate.length() > 0L

        if (!stillValid) {
            authorizedDatabaseFile = null
            permit = DatabasePermit.NO_ACCESS
            denialReason = "Authorized recovery copy is missing or no longer valid"
            error("Room access revoked: the authorized recovery copy is unavailable")
        }

        return candidate
    }

    @Synchronized
    internal fun resetForTests() {
        authorizedDatabaseFile = null
        permit = DatabasePermit.NO_ACCESS
        denialReason = "Test reset"
    }
}
