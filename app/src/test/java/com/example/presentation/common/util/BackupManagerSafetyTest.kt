package com.example.presentation.common.util

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.example.data.local.AppDatabase
import com.example.data.local.entity.BusinessEntity

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BackupManagerSafetyTest {

    @Test
    fun exportedJsonIncludesBusinesses() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        try {
            db.businessDao().insertBusiness(
                BusinessEntity(
                    id = 77,
                    name = "Test business",
                    type = "Retail",
                    description = "Must survive backup",
                    colorHex = "#123456",
                    iconKey = "storefront",
                    inventoryValue = 123.45,
                    createdAt = 1700000000000
                )
            )

            val json = JSONObject(BackupManager.exportFullBackupJson(context, db))
            val businesses = json.getJSONArray("businesses")

            assertEquals(1, businesses.length())
            assertEquals("Test business", businesses.getJSONObject(0).getString("name"))
            assertEquals(123.45, businesses.getJSONObject(0).getDouble("inventoryValue"), 0.001)
        } finally {
            db.close()
        }
    }

    @Test
    fun failedJsonRestoreRollsBackEarlierInserts() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val invalidBackup = """
            {
              "version": 1,
              "accounts": [
                {
                  "id": 1,
                  "name": "Should roll back",
                  "type": "CASH",
                  "initialBalance": 99.0,
                  "colorHex": "#123456"
                }
              ],
              "categories": ["not-an-object"]
            }
        """.trimIndent()

        try {
            assertThrows(JSONException::class.java) {
                runBlocking {
                    BackupManager.importBackupFromJson(context, db, invalidBackup)
                }
            }
            runBlocking {
                assertEquals(0, db.accountDao().getAllAccountsSync().size)
            }
        } finally {
            db.close()
        }
    }
}
