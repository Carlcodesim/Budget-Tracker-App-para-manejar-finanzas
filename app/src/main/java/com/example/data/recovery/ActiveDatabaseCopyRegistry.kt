package com.example.data.recovery

import android.content.Context
import android.system.Os
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Properties

/**
 * Durable pointer to the last explicitly activated private database copy.
 *
 * This registry is intentionally separate from the database itself. On a new
 * process the pointer is used only as the source for a fresh raw snapshot and
 * full revalidation; the gate always starts closed. An invalid registry never
 * silently falls back to the legacy original, because doing so could discard
 * transactions written to a previously activated copy.
 */
object ActiveDatabaseCopyRegistry {

    private const val REGISTRY_NAME = "active-database.properties"
    private const val TEMP_NAME = "active-database.properties.tmp"
    private const val REQUIRED_MARKER_NAME = "active-database-required.marker"
    private const val REQUIRED_MARKER_VALUE = "INSERC_ACTIVE_DATABASE_REQUIRED_1"
    private const val FORMAT = "INSERC_ACTIVE_DATABASE_REGISTRY_1"
    private const val VALIDATION_DIRECTORY = "recovery/validation"
    private const val DATABASE_NAME = "database.sqlite"

    sealed interface Source {
        data class LegacyOriginal(val file: File) : Source
        data class PreviouslyActivatedCopy(
            val file: File,
            val snapshotId: String
        ) : Source
        data class Rejected(val reasonCode: String) : Source
    }

    fun sourceForNextValidation(context: Context): Source {
        val appContext = context.applicationContext
        return try {
            val filesRoot = appContext.filesDir.canonicalFile
            val recoveryRootRaw = File(filesRoot, "recovery")
            if (FilesSupport.isSymbolicLink(recoveryRootRaw)) {
                return Source.Rejected("ACTIVE_DATABASE_REGISTRY_SYMLINK_REJECTED")
            }
            val recoveryRoot = recoveryRootRaw.canonicalFile
            val registry = File(recoveryRoot, REGISTRY_NAME)
            val temporary = File(recoveryRoot, TEMP_NAME)
            val requiredMarker = File(recoveryRoot, REQUIRED_MARKER_NAME)

            if (FilesSupport.isSymbolicLink(registry) ||
                FilesSupport.isSymbolicLink(temporary) ||
                FilesSupport.isSymbolicLink(requiredMarker)
            ) {
                return Source.Rejected("ACTIVE_DATABASE_REGISTRY_SYMLINK_REJECTED")
            }

            if (!registry.exists()) {
                if (temporary.exists()) {
                    return Source.Rejected("ACTIVE_DATABASE_REGISTRY_RECOVERY_REQUIRED")
                }
                if (requiredMarker.exists()) {
                    return Source.Rejected("ACTIVE_DATABASE_REGISTRY_MISSING_AFTER_ACTIVATION")
                }
                return Source.LegacyOriginal(
                    appContext.getDatabasePath("budget_tracker_database").canonicalFile
                )
            }
            if (!registry.isFile || !registry.canRead()) {
                return Source.Rejected("ACTIVE_DATABASE_REGISTRY_UNREADABLE")
            }
            if (!requiredMarker.isFile ||
                requiredMarker.readText(Charsets.UTF_8) != REQUIRED_MARKER_VALUE
            ) {
                return Source.Rejected("ACTIVE_DATABASE_REGISTRY_MARKER_INVALID")
            }

            val properties = Properties().apply {
                FileInputStream(registry).use(::load)
            }
            if (properties.getProperty("format") != FORMAT) {
                return Source.Rejected("ACTIVE_DATABASE_REGISTRY_FORMAT_UNKNOWN")
            }
            val snapshotId = properties.getProperty("snapshot_id")
                ?.takeIf { it.matches(Regex("^[0-9a-fA-F-]{36}$")) }
                ?: return Source.Rejected("ACTIVE_DATABASE_REGISTRY_SNAPSHOT_ID_INVALID")
            val workspaceId = properties.getProperty("workspace_id")
                ?.takeIf { it.matches(Regex("^[0-9a-fA-F-]{36}$")) }
                ?: return Source.Rejected("ACTIVE_DATABASE_REGISTRY_WORKSPACE_ID_INVALID")
            val relativePath = properties.getProperty("database_relative_path")
                ?: return Source.Rejected("ACTIVE_DATABASE_REGISTRY_PATH_MISSING")
            val expectedRelativePath = "$VALIDATION_DIRECTORY/$workspaceId/$DATABASE_NAME"
            if (relativePath != expectedRelativePath) {
                return Source.Rejected("ACTIVE_DATABASE_REGISTRY_PATH_INVALID")
            }

            val validationRootRaw = File(filesRoot, VALIDATION_DIRECTORY)
            val workspaceRaw = File(validationRootRaw, workspaceId)
            val databaseRaw = File(workspaceRaw, DATABASE_NAME)
            if (FilesSupport.isSymbolicLink(validationRootRaw) ||
                FilesSupport.isSymbolicLink(workspaceRaw) ||
                FilesSupport.isSymbolicLink(databaseRaw)
            ) {
                return Source.Rejected("REGISTERED_ACTIVE_DATABASE_SYMLINK_REJECTED")
            }
            val validationRoot = validationRootRaw.canonicalFile
            val databaseFile = databaseRaw.canonicalFile
            if (!databaseFile.path.startsWith(validationRoot.path + File.separator) ||
                databaseFile.name != DATABASE_NAME ||
                databaseFile.parentFile?.name != workspaceId ||
                !databaseFile.isFile
            ) {
                return Source.Rejected("REGISTERED_ACTIVE_DATABASE_MISSING_OR_INVALID")
            }

            Source.PreviouslyActivatedCopy(databaseFile, snapshotId)
        } catch (_: Exception) {
            Source.Rejected("ACTIVE_DATABASE_REGISTRY_READ_FAILED_CLOSED")
        }
    }

    /**
     * Publish a new active pointer only for an audited v14 validation workspace.
     * Atomic rename ensures startup sees either the old pointer or the new one.
     */
    @Synchronized
    fun registerActivatedCopy(
        context: Context,
        snapshotId: String,
        workspaceId: String,
        databaseFile: File,
        databaseSha256: String
    ): Boolean {
        return try {
            val appContext = context.applicationContext
            val filesRoot = appContext.filesDir.canonicalFile
            val validationRootRaw = File(filesRoot, VALIDATION_DIRECTORY)
            val workspaceRaw = File(validationRootRaw, workspaceId)
            val databaseRaw = File(workspaceRaw, DATABASE_NAME)
            if (FilesSupport.isSymbolicLink(validationRootRaw) ||
                FilesSupport.isSymbolicLink(workspaceRaw) ||
                FilesSupport.isSymbolicLink(databaseRaw)
            ) return false
            val validationRoot = validationRootRaw.canonicalFile
            val canonicalDatabase = databaseFile.canonicalFile
            if (!snapshotId.matches(Regex("^[0-9a-fA-F-]{36}$")) ||
                !workspaceId.matches(Regex("^[0-9a-fA-F-]{36}$")) ||
                !databaseSha256.matches(Regex("^[a-fA-F0-9]{64}$")) ||
                FilesSupport.isSymbolicLink(databaseFile) ||
                !canonicalDatabase.path.startsWith(validationRoot.path + File.separator) ||
                canonicalDatabase.parentFile?.name != workspaceId ||
                canonicalDatabase.name != DATABASE_NAME ||
                !canonicalDatabase.isFile
            ) {
                return false
            }

            val relativePath = filesRoot.toPath().relativize(canonicalDatabase.toPath()).toString()
            if (relativePath != "$VALIDATION_DIRECTORY/$workspaceId/$DATABASE_NAME" ||
                sha256(canonicalDatabase) != databaseSha256.lowercase()
            ) {
                return false
            }

            val recoveryRootRaw = File(filesRoot, "recovery")
            if (FilesSupport.isSymbolicLink(recoveryRootRaw)) return false
            val recoveryRoot = recoveryRootRaw.canonicalFile
            if (!recoveryRoot.exists() && !recoveryRoot.mkdirs()) return false
            if (!recoveryRoot.isDirectory) return false

            val registry = File(recoveryRoot, REGISTRY_NAME)
            val temporary = File(recoveryRoot, TEMP_NAME)
            if (FilesSupport.isSymbolicLink(registry) || FilesSupport.isSymbolicLink(temporary)) {
                return false
            }

            val requiredMarker = File(recoveryRoot, REQUIRED_MARKER_NAME)
            if (!requiredMarker.exists()) {
                FileOutputStream(requiredMarker).use { output ->
                    output.write(REQUIRED_MARKER_VALUE.toByteArray(Charsets.UTF_8))
                    output.fd.sync()
                }
            } else if (!requiredMarker.isFile ||
                requiredMarker.readText(Charsets.UTF_8) != REQUIRED_MARKER_VALUE
            ) {
                return false
            }

            val properties = Properties().apply {
                setProperty("format", FORMAT)
                setProperty("snapshot_id", snapshotId)
                setProperty("workspace_id", workspaceId)
                setProperty("database_relative_path", relativePath)
                setProperty("database_sha256_at_activation", databaseSha256.lowercase())
                setProperty("schema_version", "14")
            }
            FileOutputStream(temporary).use { output ->
                properties.store(output, "Active private DB pointer; revalidate before every process activation")
                output.fd.sync()
            }
            Os.rename(temporary.absolutePath, registry.absolutePath)
            true
        } catch (_: Exception) {
            false
        }
    }

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
