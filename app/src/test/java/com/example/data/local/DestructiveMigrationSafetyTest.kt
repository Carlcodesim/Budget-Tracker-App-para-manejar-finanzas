package com.example.data.local

import android.database.sqlite.SQLiteDatabase
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Regression test: a database with an unsupported historical version must not be
 * silently replaced with an empty current database.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DestructiveMigrationSafetyTest {

    @Test
    fun missingMigrationPath_preservesExistingDatabaseAndRecords() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "legacy-room-v8-safety-test.db"
        context.deleteDatabase(databaseName)
        val databaseFile = context.getDatabasePath(databaseName)
        databaseFile.parentFile?.mkdirs()

        // Simulate a legacy v8 database with a sentinel record. Its full historical
        // schema is intentionally not fabricated; the sentinel only proves preservation.
        val legacy = SQLiteDatabase.openOrCreateDatabase(databaseFile, null)
        legacy.execSQL("CREATE TABLE safety_sentinel (id INTEGER PRIMARY KEY, value TEXT NOT NULL)")
        legacy.execSQL("INSERT INTO safety_sentinel (id, value) VALUES (1, 'keep-me')")
        legacy.version = 8
        legacy.close()

        val roomDatabase = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            databaseName
        )
            .addMigrations(
                AppDatabase.MIGRATION_7_8,
                AppDatabase.MIGRATION_9_10,
                AppDatabase.MIGRATION_10_11,
                AppDatabase.MIGRATION_11_12
            )
            // Intentionally no fallbackToDestructiveMigration().
            .build()

        try {
            assertThrows(IllegalStateException::class.java) {
                roomDatabase.openHelper.writableDatabase
            }
        } finally {
            roomDatabase.close()
        }

        // Reopen the same file independently and prove the original record/version survived.
        val persisted = SQLiteDatabase.openDatabase(
            databaseFile.absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY
        )
        try {
            persisted.rawQuery(
                "SELECT value FROM safety_sentinel WHERE id = 1",
                null
            ).use { cursor ->
                assertEquals(true, cursor.moveToFirst())
                assertEquals("keep-me", cursor.getString(0))
            }
            assertEquals(8, persisted.version)
        } finally {
            persisted.close()
            context.deleteDatabase(databaseName)
        }
    }
}
