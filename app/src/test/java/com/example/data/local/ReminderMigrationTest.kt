package com.example.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.dao.ReminderDao
import com.example.data.local.entity.ReminderEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Test-only Room target for exercising the known 7 -> 8 reminder migration.
 * The v7 fixture is reconstructed by removing only the two columns named by
 * MIGRATION_7_8; it is not an archived historical database.
 */
@Database(entities = [ReminderEntity::class], version = 8, exportSchema = false)
abstract class ReminderMigrationTestDatabase : RoomDatabase() {
    abstract fun reminderDao(): ReminderDao
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ReminderMigrationTest {

    @Test
    fun version7To8_addsNotificationColumnsAndPreservesReminder() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "migration-v7-to-v8-test.db"
        context.deleteDatabase(databaseName)
        val file = context.getDatabasePath(databaseName)
        file.parentFile?.mkdirs()

        val legacy = SQLiteDatabase.openOrCreateDatabase(file, null)
        try {
            legacy.execSQL(
                """
                CREATE TABLE reminders (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    type TEXT NOT NULL,
                    title TEXT NOT NULL,
                    description TEXT NOT NULL,
                    isEnabled INTEGER NOT NULL,
                    amount REAL,
                    dueDateDay INTEGER,
                    time TEXT,
                    associatedGoalId INTEGER,
                    isCompletedThisMonth INTEGER NOT NULL
                )
                """.trimIndent()
            )
            legacy.execSQL(
                """
                INSERT INTO reminders
                    (id, type, title, description, isEnabled, amount, dueDateDay, time,
                     associatedGoalId, isCompletedThisMonth)
                VALUES (5, 'OTRO', 'Legacy reminder', 'Keep this reminder', 1, 25.0, 15,
                        '20:00', NULL, 0)
                """.trimIndent()
            )
            legacy.version = 7
        } finally {
            legacy.close()
        }

        try {
            val migrated = Room.databaseBuilder(
                context,
                ReminderMigrationTestDatabase::class.java,
                databaseName
            )
                .allowMainThreadQueries()
                .addMigrations(AppDatabase.MIGRATION_7_8)
                .build()

            try {
                val db = migrated.openHelper.writableDatabase
                db.query(
                    "SELECT title, notifyDaysBefore, notifyTime FROM reminders WHERE id = 5"
                ).use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("Legacy reminder", cursor.getString(0))
                    assertEquals(1, cursor.getInt(1))
                    assertEquals("09:00", cursor.getString(2))
                }
                assertEquals(8, db.version)
            } finally {
                migrated.close()
            }
        } finally {
            context.deleteDatabase(databaseName)
        }
    }
}
