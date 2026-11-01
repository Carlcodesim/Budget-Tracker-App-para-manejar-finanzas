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
import com.example.data.local.entity.RecurringExpenseEntity
import com.example.util.BudgetCycleManager

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

            db.recurringExpenseDao().insertRecurringExpense(
                RecurringExpenseEntity(
                    id = 88,
                    title = "Biweekly test expense",
                    amount = 150.0,
                    categoryId = 2,
                    accountId = 1,
                    dayOfMonth = 15,
                    frequency = "BIWEEKLY",
                    anchorDate = 1700000000000,
                    nextDueDate = 1701296000000
                )
            )
            BudgetCycleManager.setStartDay(context, 30)
            BudgetCycleManager.setPredictedIncome(context, 42500.0)

            val json = JSONObject(BackupManager.exportFullBackupJson(context, db))
            val businesses = json.getJSONArray("businesses")

            assertEquals(1, businesses.length())
            assertEquals("Test business", businesses.getJSONObject(0).getString("name"))
            assertEquals(123.45, businesses.getJSONObject(0).getDouble("inventoryValue"), 0.001)

            val restoredDb = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
            try {
                val restoredCount = BackupManager.importBackupFromJson(context, restoredDb, json.toString())
                assertEquals(2, restoredCount) // One business and one recurring expense.
                val restoredBusiness = restoredDb.businessDao().getBusinessById(77)
                assertEquals("Test business", restoredBusiness?.name)
                assertEquals(123.45, restoredBusiness?.inventoryValue ?: 0.0, 0.001)
                val restoredRecurring = restoredDb.recurringExpenseDao().getAllRecurringExpensesSync()
                assertEquals(1, restoredRecurring.size)
                assertEquals("BIWEEKLY", restoredRecurring.first().frequency)
                assertEquals(1701296000000L, restoredRecurring.first().nextDueDate)
            } finally {
                restoredDb.close()
            }

            val recurring = json.getJSONArray("recurringExpenses").getJSONObject(0)
            assertEquals("BIWEEKLY", recurring.getString("frequency"))
            assertEquals(1700000000000L, recurring.getLong("anchorDate"))
            assertEquals(1701296000000L, recurring.getLong("nextDueDate"))

            val preferences = json.getJSONObject("preferences")
            assertEquals(30, preferences.getInt("cycleStartDay"))
            assertEquals(42500.0, preferences.getDouble("predictedIncome"), 0.001)
        } finally {
            db.close()
        }
    }

    @Test
    fun importRestoresCyclePreferencesWhenPresent() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val backup = JSONObject().apply {
            put("version", 1)
            put("preferences", JSONObject().apply {
                put("cycleStartDay", 30)
                put("predictedIncome", 39000.0)
                put("currencyCode", "USD")
            })
        }.toString()

        try {
            BudgetCycleManager.setStartDay(context, 1)
            BudgetCycleManager.setPredictedIncome(context, null)
            CurrencyFormatter.setCurrency(context, CurrencyFormatter.DEFAULT_CURRENCY_CODE)

            BackupManager.importBackupFromJson(context, db, backup)

            assertEquals(30, BudgetCycleManager.getStartDay())
            assertEquals(39000.0, BudgetCycleManager.getPredictedIncome()!!, 0.001)
            assertEquals("USD", CurrencyFormatter.getCurrency())
        } finally {
            db.close()
            BudgetCycleManager.setStartDay(context, 1)
            BudgetCycleManager.setPredictedIncome(context, null)
            CurrencyFormatter.setCurrency(context, CurrencyFormatter.DEFAULT_CURRENCY_CODE)
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
