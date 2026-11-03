package com.example.data.recovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.MessageDigest

class DatabasePreflightInspectorTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun absentDatabaseIsClassifiedWithoutCreatingAFile() {
        val file = File(temporaryFolder.root, "missing.db")

        val result = DatabasePreflightInspector.inspect(file)

        assertEquals(DatabaseCondition.ABSENT, result.condition)
        assertFalse(file.exists())
    }

    @Test
    fun validCurrentSchemaHeaderIsNotTreatedAsIntegrityCertification() {
        val file = sqliteFile("current.db", schemaVersion = 14)

        val result = DatabasePreflightInspector.inspect(file)

        assertEquals(DatabaseCondition.HEADER_RECOGNIZED, result.condition)
        assertEquals(14, result.schemaVersion)
        assertEquals("HEADER_RECOGNIZED_INTEGRITY_NOT_CERTIFIED", result.reasonCode)
    }

    @Test
    fun futureSchemaIsQuarantined() {
        val file = sqliteFile("future.db", schemaVersion = 15)

        val result = DatabasePreflightInspector.inspect(file)

        assertEquals(DatabaseCondition.FUTURE_VERSION, result.condition)
    }

    @Test
    fun schemaWithCompleteRegisteredMigrationPathIsOnlyACandidate() {
        val file = sqliteFile("legacy.db", schemaVersion = 12)

        val result = DatabasePreflightInspector.inspect(file)

        assertEquals(DatabaseCondition.LEGACY_MIGRATION_CANDIDATE, result.condition)
    }

    @Test
    fun schemaWithKnownMigrationGapIsUnsupported() {
        val file = sqliteFile("legacy-gap.db", schemaVersion = 8)

        val result = DatabasePreflightInspector.inspect(file)

        assertEquals(DatabaseCondition.LEGACY_UNSUPPORTED, result.condition)
    }

    @Test
    fun walSidecarPreventsMainFileOnlyClassification() {
        val file = sqliteFile("wal.db", schemaVersion = 14)
        File(file.path + "-wal").writeBytes(byteArrayOf(1, 2, 3))

        val result = DatabasePreflightInspector.inspect(file)

        assertEquals(DatabaseCondition.UNKNOWN, result.condition)
        assertTrue(result.hasWal)
        assertEquals("WAL_SIDECAR_REQUIRES_CONSISTENT_SNAPSHOT", result.reasonCode)
    }

    @Test
    fun rollbackJournalCausesInconsistentClassification() {
        val file = sqliteFile("journal.db", schemaVersion = 14)
        File(file.path + "-journal").writeBytes(byteArrayOf(1))

        val result = DatabasePreflightInspector.inspect(file)

        assertEquals(DatabaseCondition.INCONSISTENT, result.condition)
        assertTrue(result.hasJournal)
    }

    @Test
    fun invalidSqliteSignatureIsCorrupt() {
        val file = File(temporaryFolder.root, "not-sqlite.db")
        file.writeBytes(ByteArray(512) { 0x41 })

        val result = DatabasePreflightInspector.inspect(file)

        assertEquals(DatabaseCondition.CORRUPT, result.condition)
        assertEquals("INVALID_SQLITE_SIGNATURE", result.reasonCode)
    }

    @Test
    fun truncatedHeaderIsCorrupt() {
        val file = File(temporaryFolder.root, "truncated.db")
        file.writeBytes(byteArrayOf(1, 2, 3, 4))

        val result = DatabasePreflightInspector.inspect(file)

        assertEquals(DatabaseCondition.CORRUPT, result.condition)
        assertEquals("TRUNCATED_SQLITE_HEADER", result.reasonCode)
    }

    @Test
    fun preflightDoesNotModifyOriginalFileBytes() {
        val file = sqliteFile("unchanged.db", schemaVersion = 14)
        val before = sha256(file)
        val timestamp = file.lastModified()

        DatabasePreflightInspector.inspect(file)

        assertEquals(before, sha256(file))
        assertEquals(timestamp, file.lastModified())
    }

    private fun sqliteFile(name: String, schemaVersion: Int): File {
        val file = File(temporaryFolder.root, name)
        val bytes = ByteArray(512)

        val signature = byteArrayOf(
            0x53, 0x51, 0x4c, 0x69, 0x74, 0x65, 0x20, 0x66,
            0x6f, 0x72, 0x6d, 0x61, 0x74, 0x20, 0x33, 0x00
        )
        signature.copyInto(bytes, 0)
        putShort(bytes, 16, 512)
        bytes[18] = 1
        bytes[19] = 1
        bytes[20] = 0
        bytes[21] = 64
        bytes[22] = 32
        bytes[23] = 32
        putInt(bytes, 28, 1)
        putInt(bytes, 56, 1)
        putInt(bytes, 60, schemaVersion)
        file.writeBytes(bytes)
        return file
    }

    private fun putShort(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value ushr 8).toByte()
        bytes[offset + 1] = value.toByte()
    }

    private fun putInt(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value ushr 24).toByte()
        bytes[offset + 1] = (value ushr 16).toByte()
        bytes[offset + 2] = (value ushr 8).toByte()
        bytes[offset + 3] = value.toByte()
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
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
