package com.example.data.recovery

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.Properties

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DatabaseSnapshotCopierTest {

    private lateinit var context: Context
    private lateinit var original: File
    private lateinit var snapshotRoot: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        original = context.getDatabasePath("budget_tracker_database")
        original.parentFile?.mkdirs()
        snapshotRoot = File(context.filesDir, "recovery/snapshots")
        snapshotRoot.deleteRecursively()
        original.delete()
        File(original.path + "-wal").delete()
        File(original.path + "-shm").delete()
        File(original.path + "-journal").delete()
    }

    @Test
    fun missingOriginalIsNotCreatedOrReplaced() {
        val result = DatabaseSnapshotCopier.copyOriginalDatabase(context)

        assertTrue(result is SnapshotCopyResult.Rejected)
        assertFalse(original.exists())
    }

    @Test
    fun copiesDatabaseAndWalButDoesNotCopyTransientShm() {
        val databaseBytes = ByteArray(4096) { (it % 251).toByte() }
        val walBytes = ByteArray(512) { (it % 127).toByte() }
        val shmBytes = ByteArray(128) { (it % 61).toByte() }
        original.writeBytes(databaseBytes)
        File(original.path + "-wal").writeBytes(walBytes)
        File(original.path + "-shm").writeBytes(shmBytes)

        val result = DatabaseSnapshotCopier.copyOriginalDatabase(context)

        assertTrue(result is SnapshotCopyResult.Created)
        val created = result as SnapshotCopyResult.Created
        assertArrayEquals(databaseBytes, created.databaseFile.readBytes())
        assertArrayEquals(walBytes, created.walFile!!.readBytes())
        assertFalse(File(created.directory, "database.sqlite-shm").exists())
        assertTrue(created.sourceShmObserved)
        assertTrue(created.sourceWalObserved)
        assertFalse(created.integrityCertified)
        assertFalse(created.authorizedForRoom)
        assertArrayEquals(databaseBytes, original.readBytes())
        assertArrayEquals(walBytes, File(original.path + "-wal").readBytes())
        assertArrayEquals(shmBytes, File(original.path + "-shm").readBytes())

        val manifest = Properties().apply {
            created.manifestFile.inputStream().use(::load)
        }
        assertEquals("false", manifest.getProperty("integrity_certified"))
        assertEquals("false", manifest.getProperty("normal_access_authorized"))
        assertEquals("false", manifest.getProperty("shm_copied"))
    }

    @Test
    fun copiesRollbackJournalAsSeparateEvidence() {
        val databaseBytes = ByteArray(2048) { (it % 239).toByte() }
        val journalBytes = ByteArray(256) { (it % 83).toByte() }
        original.writeBytes(databaseBytes)
        File(original.path + "-journal").writeBytes(journalBytes)

        val result = DatabaseSnapshotCopier.copyOriginalDatabase(context)

        assertTrue(result is SnapshotCopyResult.Created)
        val created = result as SnapshotCopyResult.Created
        assertArrayEquals(databaseBytes, created.databaseFile.readBytes())
        assertArrayEquals(journalBytes, created.journalFile!!.readBytes())
        assertTrue(created.sourceJournalObserved)
        assertArrayEquals(databaseBytes, original.readBytes())
        assertArrayEquals(journalBytes, File(original.path + "-journal").readBytes())
    }

    @Test
    fun eachSnapshotUsesAnIndependentDirectoryAndIsNotAnAuthorization() {
        original.writeBytes(ByteArray(1024) { 7 })

        val first = DatabaseSnapshotCopier.copyOriginalDatabase(context) as SnapshotCopyResult.Created
        val second = DatabaseSnapshotCopier.copyOriginalDatabase(context) as SnapshotCopyResult.Created

        assertNotEquals(first.snapshotId, second.snapshotId)
        assertNotEquals(first.directory.canonicalFile, second.directory.canonicalFile)
        assertFalse(first.authorizedForRoom)
        assertFalse(second.authorizedForRoom)
    }
}
