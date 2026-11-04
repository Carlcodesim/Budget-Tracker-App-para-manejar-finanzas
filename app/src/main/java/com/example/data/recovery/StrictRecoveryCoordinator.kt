package com.example.data.recovery

import android.content.Context
import com.example.data.local.AppDatabase
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.Properties

/**
 * Coordinates only the currently certified v14 path:
 * read-only preflight -> raw snapshot -> isolated SQLite/Room validation ->
 * read-only financial audit -> explicit user authorization.
 *
 * Legacy, future, absent, malformed or inconsistent evidence outside the
 * explicitly-supported sidecar cases stays in protection mode. This version
 * deliberately does not rehearse migrations or create a new database.
 */
object StrictRecoveryCoordinator {

    private const val CURRENT_SCHEMA_VERSION = 14
    private const val VALIDATION_FORMAT = "INSERC_SQLITE_VALIDATION_REPORT_1"
    private const val FINANCIAL_REPORT_FORMAT = "INSERC_FINANCIAL_CONSISTENCY_REPORT_1"
    private const val VALIDATION_REPORT_NAME = "validation-report.properties"
    private const val DATABASE_FILE_NAME = "database.sqlite"

    private data class PendingAuthorization(
        val snapshotId: String,
        val workspace: File,
        val databaseFile: File,
        val validationReport: File,
        val financialReport: File,
        val databaseSha256: String
    )

    @Volatile
    private var pendingAuthorization: PendingAuthorization? = null

    sealed interface PreparationResult {
        data class ReadyForAuthorization(
            val snapshotId: String,
            val recordsExamined: Long
        ) : PreparationResult

        data class ManualReviewRequired(
            val snapshotId: String,
            val issueCount: Int,
            val sampleFindings: List<String>
        ) : PreparationResult

        data class Blocked(
            val reasonCode: String,
            val message: String
        ) : PreparationResult
    }

    sealed interface AuthorizationResult {
        data class Authorized(val databaseFile: File) : AuthorizationResult
        data class Rejected(val reasonCode: String) : AuthorizationResult
    }

    @Synchronized
    fun prepare(context: Context): PreparationResult {
        pendingAuthorization = null
        val appContext = context.applicationContext

        if (DatabaseAccessGate.isNormalAccessAllowed() || AppDatabase.hasInstance()) {
            return PreparationResult.Blocked(
                "ROOM_NOT_QUIESCED",
                "Cierra y libera cualquier instancia de Room antes de iniciar otra evaluación."
            )
        }

        return try {
            val sourceFile = when (val source = ActiveDatabaseCopyRegistry.sourceForNextValidation(appContext)) {
                is ActiveDatabaseCopyRegistry.Source.LegacyOriginal -> source.file
                is ActiveDatabaseCopyRegistry.Source.PreviouslyActivatedCopy -> source.file
                is ActiveDatabaseCopyRegistry.Source.Rejected -> return PreparationResult.Blocked(
                    source.reasonCode,
                    "No se pudo identificar de forma segura la última base activa. No se usará una copia anterior."
                )
            }
            val preflight = DatabasePreflightInspector.inspect(sourceFile)
            val hasSupportedV14Header = preflight.schemaVersion == CURRENT_SCHEMA_VERSION &&
                (
                    preflight.condition == DatabaseCondition.HEADER_RECOGNIZED ||
                        preflight.reasonCode == "WAL_SIDECAR_REQUIRES_CONSISTENT_SNAPSHOT" ||
                        preflight.reasonCode == "ROLLBACK_JOURNAL_PRESENT"
                    )
            if (!hasSupportedV14Header) {
                return PreparationResult.Blocked(
                    preflight.reasonCode,
                    preflight.message
                )
            }

            val rawSnapshot = when (val copied = DatabaseSnapshotCopier.copyDatabase(appContext, sourceFile)) {
                is SnapshotCopyResult.Created -> copied
                is SnapshotCopyResult.Rejected -> return PreparationResult.Blocked(
                    copied.reasonCode,
                    "No se pudo copiar la base original sin alterar su contenido."
                )
            }

            val validated = when (
                val validation = DatabaseSnapshotValidator.validate(appContext, rawSnapshot)
            ) {
                is SnapshotValidationResult.Validated -> validation
                is SnapshotValidationResult.Rejected -> return PreparationResult.Blocked(
                    validation.reasonCode,
                    "La copia aislada no superó la validación SQLite y Room."
                )
            }

            val audited = when (
                val audit = DatabaseFinancialConsistencyAuditor.audit(appContext, validated)
            ) {
                is DatabaseFinancialConsistencyAuditor.Result.Completed -> audit
                is DatabaseFinancialConsistencyAuditor.Result.Rejected -> return PreparationResult.Blocked(
                    audit.reasonCode,
                    "No se pudo completar la auditoría financiera de solo lectura."
                )
            }

            if (!audited.databaseUnchanged ||
                audited.databaseSha256Before != audited.databaseSha256After ||
                audited.roomAuthorized
            ) {
                return PreparationResult.Blocked(
                    "AUDIT_EVIDENCE_NOT_SAFE",
                    "La auditoría no produjo evidencia inmutable y sin autorización."
                )
            }

            if (audited.issues.isNotEmpty()) {
                return PreparationResult.ManualReviewRequired(
                    snapshotId = audited.snapshotId,
                    issueCount = audited.issues.size,
                    sampleFindings = audited.issues.take(8).map {
                        "${it.severity}: ${it.code} (${it.table} #${it.rowId})"
                    }
                )
            }

            val databaseFile = validated.validatedDatabaseFile.canonicalFile
            val expectedWorkspace = File(appContext.filesDir, "recovery/validation").canonicalFile
            val workspace = validated.workspaceDirectory.canonicalFile
            if (!workspace.path.startsWith(expectedWorkspace.path + File.separator) ||
                databaseFile.parentFile != workspace ||
                databaseFile.name != DATABASE_FILE_NAME ||
                !databaseFile.isFile
            ) {
                return PreparationResult.Blocked(
                    "VALIDATED_COPY_PATH_INVALID",
                    "La copia validada no está en la ubicación privada esperada."
                )
            }

            pendingAuthorization = PendingAuthorization(
                snapshotId = validated.snapshotId,
                workspace = workspace,
                databaseFile = databaseFile,
                validationReport = validated.reportFile.canonicalFile,
                financialReport = audited.reportFile.canonicalFile,
                databaseSha256 = audited.databaseSha256After
            )

            PreparationResult.ReadyForAuthorization(
                snapshotId = validated.snapshotId,
                recordsExamined = audited.recordsExamined
            )
        } catch (_: Exception) {
            pendingAuthorization = null
            PreparationResult.Blocked(
                "RECOVERY_PREPARATION_FAILED_CLOSED",
                "La evaluación falló. El acceso a la base de datos permanece bloqueado."
            )
        }
    }

    /**
     * Must only be called from the user's explicit confirmation action.
     * No evidence is retained across process restarts.
     */
    @Synchronized
    fun authorizeAfterExplicitUserConfirmation(context: Context): AuthorizationResult {
        val pending = pendingAuthorization
            ?: return AuthorizationResult.Rejected("NO_PENDING_AUTHORIZATION")

        if (DatabaseAccessGate.isNormalAccessAllowed() || AppDatabase.hasInstance()) {
            pendingAuthorization = null
            return AuthorizationResult.Rejected("ROOM_NOT_QUIESCED")
        }

        return try {
            val appContext = context.applicationContext
            val privateValidationRoot = File(appContext.filesDir, "recovery/validation").canonicalFile
            val workspace = pending.workspace.canonicalFile
            val databaseFile = pending.databaseFile.canonicalFile
            val validationReport = pending.validationReport.canonicalFile
            val financialReport = pending.financialReport.canonicalFile

            if (FilesSupport.isSymbolicLink(pending.workspace) ||
                FilesSupport.isSymbolicLink(pending.databaseFile) ||
                FilesSupport.isSymbolicLink(pending.validationReport) ||
                FilesSupport.isSymbolicLink(pending.financialReport) ||
                !workspace.path.startsWith(privateValidationRoot.path + File.separator) ||
                databaseFile.parentFile != workspace ||
                validationReport.parentFile != workspace ||
                financialReport.parentFile != workspace ||
                databaseFile.name != DATABASE_FILE_NAME ||
                !databaseFile.isFile ||
                !validationReport.isFile ||
                !financialReport.isFile
            ) {
                pendingAuthorization = null
                return AuthorizationResult.Rejected("PENDING_EVIDENCE_PATH_INVALID")
            }

            val validation = readProperties(validationReport)
            val audit = readProperties(financialReport)
            val currentHash = sha256(databaseFile)

            val validationStillEligible =
                validation.getProperty("format") == VALIDATION_FORMAT &&
                    validation.getProperty("snapshot_id") == pending.snapshotId &&
                    validation.getProperty("workspace_id") == workspace.name &&
                    validation.getProperty("schema_version") == CURRENT_SCHEMA_VERSION.toString() &&
                    validation.getProperty("room_identity_hash") == "86254750241babac4b8d52996a675549" &&
                    validation.getProperty("quick_check") == "ok" &&
                    validation.getProperty("integrity_check") == "ok" &&
                    validation.getProperty("schema_validated") == "true" &&
                    validation.getProperty("sqlite_integrity_checks_passed") == "true" &&
                    validation.getProperty("normal_access_authorized") == "false"

            val auditStillEligible =
                audit.getProperty("format") == FINANCIAL_REPORT_FORMAT &&
                    audit.getProperty("snapshot_id") == pending.snapshotId &&
                    audit.getProperty("workspace_id") == workspace.name &&
                    audit.getProperty("scan_completed") == "true" &&
                    audit.getProperty("financial_consistency_scan_completed") == "true" &&
                    audit.getProperty("financial_consistency_validated") == "true" &&
                    audit.getProperty("financial_consistency_passed") == "true" &&
                    audit.getProperty("issue_count") == "0" &&
                    audit.getProperty("database_unchanged") == "true" &&
                    audit.getProperty("normal_access_authorized") == "false" &&
                    audit.getProperty("room_authorized") == "false" &&
                    audit.getProperty("database_sha256_before") == pending.databaseSha256 &&
                    audit.getProperty("database_sha256_after") == pending.databaseSha256

            if (!validationStillEligible || !auditStillEligible ||
                currentHash != pending.databaseSha256
            ) {
                pendingAuthorization = null
                return AuthorizationResult.Rejected("RECOVERY_EVIDENCE_CHANGED_OR_INCOMPLETE")
            }

            // Persist the active pointer before granting this process access. If the
            // process dies between these operations, the next launch revalidates this
            // exact copy instead of reverting to the legacy original.
            if (!ActiveDatabaseCopyRegistry.registerActivatedCopy(
                    context = appContext,
                    snapshotId = pending.snapshotId,
                    databaseFile = databaseFile,
                    databaseSha256 = pending.databaseSha256
                )
            ) {
                pendingAuthorization = null
                return AuthorizationResult.Rejected("ACTIVE_DATABASE_REGISTRY_WRITE_FAILED")
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
                activeDatabaseCopy = databaseFile,
                context = appContext
            )

            if (permit != DatabasePermit.NORMAL_ACCESS) {
                pendingAuthorization = null
                AuthorizationResult.Rejected("DATABASE_ACCESS_GATE_DENIED")
            } else {
                pendingAuthorization = null
                AuthorizationResult.Authorized(databaseFile)
            }
        } catch (_: Exception) {
            pendingAuthorization = null
            AuthorizationResult.Rejected("AUTHORIZATION_FAILED_CLOSED")
        }
    }

    @Synchronized
    internal fun resetForTests() {
        pendingAuthorization = null
    }

    private fun readProperties(file: File): Properties =
        Properties().apply { FileInputStream(file).use(::load) }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private object FilesSupport {
        fun isSymbolicLink(file: File): Boolean = try {
            java.nio.file.Files.isSymbolicLink(file.toPath())
        } catch (_: Exception) {
            true
        }
    }
}
