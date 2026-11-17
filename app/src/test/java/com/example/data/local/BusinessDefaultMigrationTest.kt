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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BusinessDefaultMigrationTest {

    @Test
    fun migrationPreservesVariableTimestampLegacyLookalikeBecauseOwnershipIsAmbiguous() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "business-variable-placeholder-migration-test.db"
        context.deleteDatabase(databaseName)

        val current = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .allowMainThreadQueries()
            .build()
        try {
            current.openHelper.writableDatabase.execSQL(
                """
                INSERT INTO businesses
                (id, name, type, description, colorHex, iconKey, inventoryValue, createdAt)
                VALUES (1, 'INSERC Store', 'Tecnología y Comercio', 'Negocio comercial principal',
                        '#2563EB', 'storefront', 0.0, 1700000000000)
                """.trimIndent()
            )
        } finally {
            current.close()
        }

        val file = context.getDatabasePath(databaseName)
        val legacy = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
        try {
            legacy.version = 13
        } finally {
            legacy.close()
        }

        val migrated = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .allowMainThreadQueries()
            .addMigrations(AppDatabase.MIGRATION_13_14)
            .build()
        try {
            val db = migrated.openHelper.writableDatabase
            db.query(
                "SELECT name, description, inventoryValue, createdAt FROM businesses WHERE id = 1"
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("INSERC Store", cursor.getString(0))
                assertEquals("Negocio comercial principal", cursor.getString(1))
                assertEquals(0.0, cursor.getDouble(2), 0.001)
                assertEquals(1700000000000L, cursor.getLong(3))
            }
        } finally {
            migrated.close()
            context.deleteDatabase(databaseName)
        }
    }


    @Test
    fun migrationRemovesOnlyUntouchedDefaultAndPreservesUserBusinesses() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "business-default-migration-test.db"
        context.deleteDatabase(databaseName)

        val current = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .allowMainThreadQueries()
            .build()

        try {
            val db = current.openHelper.writableDatabase
            db.execSQL(
                """
                INSERT INTO businesses
                (id, name, type, description, colorHex, iconKey, inventoryValue, createdAt)
                VALUES (1, 'INSERC Store', 'Tecnología y Comercio', 'Negocio principal de servicios y venta comercial',
                        '#2563EB', 'storefront', 0.0, 1704067200000)
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO businesses
                (id, name, type, description, colorHex, iconKey, inventoryValue, createdAt)
                VALUES (2, 'INSERC Multiservice', 'Servicios Profesionales', 'Negocio creado por el usuario',
                        '#10B981', 'storefront', 2750.0, 1700000000001)
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO businesses
                (id, name, type, description, colorHex, iconKey, inventoryValue, createdAt)
                VALUES (3, 'INSERC Store', 'Tecnología y Comercio', 'Mi negocio editado',
                        '#2563EB', 'storefront', 500.0, 1700000000002)
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO businesses
                (id, name, type, description, colorHex, iconKey, inventoryValue, createdAt)
                VALUES (4, 'INSERC Store', 'Tecnología y Comercio', 'Negocio comercial principal',
                        '#2563EB', 'storefront', 0.0, 1700000000003)
                """.trimIndent()
            )
        } finally {
            current.close()
        }

        val file = context.getDatabasePath(databaseName)
        val legacy = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
        try {
            legacy.version = 13
        } finally {
            legacy.close()
        }

        val migrated = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .allowMainThreadQueries()
            .addMigrations(AppDatabase.MIGRATION_13_14)
            .build()

        try {
            val db = migrated.openHelper.writableDatabase
            db.query("SELECT COUNT(*) FROM businesses").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(3, cursor.getInt(0))
            }
            db.query("SELECT name, inventoryValue FROM businesses WHERE id = 2").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("INSERC Multiservice", cursor.getString(0))
                assertEquals(2750.0, cursor.getDouble(1), 0.001)
            }
            db.query("SELECT description, inventoryValue FROM businesses WHERE id = 3").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Mi negocio editado", cursor.getString(0))
                assertEquals(500.0, cursor.getDouble(1), 0.001)
            }
            db.query("SELECT COUNT(*) FROM businesses WHERE id = 1").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
            db.query("SELECT description, inventoryValue FROM businesses WHERE id = 4").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Negocio comercial principal", cursor.getString(0))
                assertEquals(0.0, cursor.getDouble(1), 0.001)
            }
            assertEquals(14, db.version)
        } finally {
            migrated.close()
            context.deleteDatabase(databaseName)
        }
    }
}
