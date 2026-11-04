package com.example.data.recovery

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ActiveDatabaseCopyRegistryTest {

    private lateinit var context: Context
    private lateinit var filesRoot: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        filesRoot = context.filesDir.canonicalFile
        File(filesRoot, "recovery").deleteRecursively()
    }

    @Test
    fun missingRegistrySelectsLegacyOriginalForFirstRecovery() {
        val source = ActiveDatabaseCopyRegistry.sourceForNextValidation(context)

        assertTrue(source is ActiveDatabaseCopyRegistry.Source.LegacyOriginal)
        assertEquals(
            context.getDatabasePath("budget_tracker_database").canonicalFile,
            (source as ActiveDatabaseCopyRegistry.Source.LegacyOriginal).file
        )
    }

    @Test
    fun registeredActivatedWorkspaceIsTheNextSource() {
        val snapshotId = UUID.randomUUID().toString()
        val workspaceId = UUID.randomUUID().toString()
        val workspace = File(filesRoot, "recovery/validation/$workspaceId")
        assertTrue(workspace.mkdirs())
        val database = File(workspace, "database.sqlite").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        val hash = sha256(database)

        assertTrue(
            ActiveDatabaseCopyRegistry.registerActivatedCopy(
                context = context,
                snapshotId = snapshotId,
                workspaceId = workspaceId,
                databaseFile = database,
                databaseSha256 = hash
            )
        )

        val selected = ActiveDatabaseCopyRegistry.sourceForNextValidation(context)
        assertTrue("Registry source was: $selected", selected is ActiveDatabaseCopyRegistry.Source.PreviouslyActivatedCopy)
        selected as ActiveDatabaseCopyRegistry.Source.PreviouslyActivatedCopy
        assertEquals(snapshotId, selected.snapshotId)
        assertEquals(database.canonicalFile, selected.file)
    }

    @Test
    fun deletedActiveRegistryDoesNotRevertToLegacyOriginalAfterActivation() {
        val snapshotId = UUID.randomUUID().toString()
        val workspaceId = UUID.randomUUID().toString()
        val workspace = File(filesRoot, "recovery/validation/$workspaceId")
        assertTrue(workspace.mkdirs())
        val database = File(workspace, "database.sqlite").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }

        assertTrue(
            ActiveDatabaseCopyRegistry.registerActivatedCopy(
                context = context,
                snapshotId = snapshotId,
                workspaceId = workspaceId,
                databaseFile = database,
                databaseSha256 = sha256(database)
            )
        )
        assertTrue(File(filesRoot, "recovery/active-database-required.marker").isFile)
        assertTrue(File(filesRoot, "recovery/active-database.properties").delete())

        val source = ActiveDatabaseCopyRegistry.sourceForNextValidation(context)

        assertEquals(
            ActiveDatabaseCopyRegistry.Source.Rejected("ACTIVE_DATABASE_REGISTRY_MISSING_AFTER_ACTIVATION"),
            source
        )
    }

    @Test
    fun corruptRegistryDoesNotFallBackToLegacyOriginal() {
        val recoveryRoot = File(filesRoot, "recovery").apply { mkdirs() }
        File(recoveryRoot, "active-database.properties").writeText(
            """
            format=INSERC_ACTIVE_DATABASE_REGISTRY_1
            snapshot_id=00000000-0000-0000-0000-000000000000
            workspace_id=00000000-0000-0000-0000-000000000000
            database_relative_path=recovery/validation/another/database.sqlite
            """.trimIndent()
        )

        val source = ActiveDatabaseCopyRegistry.sourceForNextValidation(context)

        assertTrue(source is ActiveDatabaseCopyRegistry.Source.Rejected)
        assertFalse(source is ActiveDatabaseCopyRegistry.Source.LegacyOriginal)
        assertEquals(
            "ACTIVE_DATABASE_REGISTRY_PATH_INVALID",
            (source as ActiveDatabaseCopyRegistry.Source.Rejected).reasonCode
        )
    }

    @Test
    fun registryRefusesHashMismatchAndDoesNotPublishPointer() {
        val snapshotId = UUID.randomUUID().toString()
        val workspaceId = UUID.randomUUID().toString()
        val workspace = File(filesRoot, "recovery/validation/$workspaceId")
        assertTrue(workspace.mkdirs())
        val database = File(workspace, "database.sqlite").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }

        val registered = ActiveDatabaseCopyRegistry.registerActivatedCopy(
            context = context,
            snapshotId = snapshotId,
            workspaceId = workspaceId,
            databaseFile = database,
            databaseSha256 = "0".repeat(64)
        )

        assertFalse(registered)
        assertTrue(ActiveDatabaseCopyRegistry.sourceForNextValidation(context) is ActiveDatabaseCopyRegistry.Source.LegacyOriginal)
    }

    private fun sha256(file: File): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
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
