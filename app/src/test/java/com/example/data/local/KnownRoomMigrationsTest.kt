package com.example.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * End-to-end migration test for the known 9 -> 10 -> 11 -> 12 path.
 *
 * The fixture starts from Room's current schema and reverses only the changes
 * explicitly represented by MIGRATION_9_10, MIGRATION_10_11, and MIGRATION_11_12.
 * It verifies the known migration deltas and data preservation, but is not a
 * substitute for an archived v9 APK/database because historical schema exports
 * are not present in the repository.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class KnownRoomMigrationsTest {

    @Test
    fun version9To12_preservesExistingRecordsAndAppliesKnownChanges() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "migration-v9-to-v12-test.db"
        context.deleteDatabase(databaseName)

        // Create a valid current Room database, then seed representative data.
        val current = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .allowMainThreadQueries()
            .build()

        current.openHelper.writableDatabase.execSQL(
            """
            INSERT INTO categories (id, name, iconKey, isDefault, maxMonthlyBudget, colorHex, type)
            VALUES (41, 'Legacy category', 'wallet', 0, 500.0, '#3B82F6', 'EXPENSE')
            """.trimIndent()
        )
        current.openHelper.writableDatabase.execSQL(
            """
            INSERT INTO transactions (id, amount, type, categoryId, description, date, accountId, tags)
            VALUES (91, 123.45, 'EXPENSE', 41, 'Legacy transaction', 1700000000000, 1, NULL)
            """.trimIndent()
        )
        current.close()

        val file = context.getDatabasePath(databaseName)
        val legacy = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
        try {
            // Reverse the documented 11 -> 12 delta.
            legacy.execSQL("DROP TABLE businesses")

            // Reverse the documented 10 -> 11 delta.
            legacy.execSQL("DROP INDEX IF EXISTS index_transactions_type_date")
            legacy.execSQL("DROP INDEX IF EXISTS index_transactions_accountId_date")

            // Reverse the documented 9 -> 10 delta. The remaining columns are
            // the category fields that existed before colorHex and type were added.
            legacy.execSQL(
                """
                CREATE TABLE categories_legacy (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    name TEXT NOT NULL,
                    iconKey TEXT NOT NULL,
                    isDefault INTEGER NOT NULL,
                    maxMonthlyBudget REAL
                )
                """.trimIndent()
            )
            legacy.execSQL(
                """
                INSERT INTO categories_legacy (id, name, iconKey, isDefault, maxMonthlyBudget)
                SELECT id, name, iconKey, isDefault, maxMonthlyBudget FROM categories
                """.trimIndent()
            )
            legacy.execSQL("DROP TABLE categories")
            legacy.execSQL("ALTER TABLE categories_legacy RENAME TO categories")
            legacy.version = 9
        } finally {
            legacy.close()
        }

        try {
            val migrated = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
                .allowMainThreadQueries()
                .addMigrations(
                    AppDatabase.MIGRATION_7_8,
                    AppDatabase.MIGRATION_9_10,
                    AppDatabase.MIGRATION_10_11,
                    AppDatabase.MIGRATION_11_12
                )
                .build()

            try {
                val db = migrated.openHelper.writableDatabase

                db.query("SELECT name, colorHex, type FROM categories WHERE id = 41").use { cursor ->
                    assertEquals(true, cursor.moveToFirst())
                    assertEquals("Legacy category", cursor.getString(0))
                    assertEquals("#3B82F6", cursor.getString(1))
                    assertEquals("EXPENSE", cursor.getString(2))
                }

                db.query("SELECT amount, description FROM transactions WHERE id = 91").use { cursor ->
                    assertEquals(true, cursor.moveToFirst())
                    assertEquals(123.45, cursor.getDouble(0), 0.001)
                    assertEquals("Legacy transaction", cursor.getString(1))
                }

                db.query("SELECT type, date FROM transactions WHERE id = 91").use { cursor ->
                    assertEquals(true, cursor.moveToFirst())
                    assertEquals("EXPENSE", cursor.getString(0))
                    assertEquals(1700000000000L, cursor.getLong(1))
                }

                db.query("SELECT name FROM businesses WHERE id = 1").use { cursor ->
                    assertEquals(true, cursor.moveToFirst())
                    assertEquals("INSERC Store", cursor.getString(0))
                }

                val transactionIndexes = mutableSetOf<String>()
                db.query("PRAGMA index_list('transactions')").use { cursor ->
                    val nameColumn = cursor.getColumnIndexOrThrow("name")
                    while (cursor.moveToNext()) {
                        transactionIndexes += cursor.getString(nameColumn)
                    }
                }
                assertTrue(transactionIndexes.contains("index_transactions_type_date"))
                assertTrue(transactionIndexes.contains("index_transactions_accountId_date"))
                assertEquals(12, db.version)
            } finally {
                migrated.close()
            }
        } finally {
            context.deleteDatabase(databaseName)
        }
    }
}
