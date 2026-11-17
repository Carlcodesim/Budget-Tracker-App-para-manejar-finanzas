package com.example.presentation.common.util

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.example.data.local.AppDatabase
import com.example.data.local.entity.BusinessEntity
import com.example.data.local.entity.AccountEntity
import com.example.data.local.entity.CategoryEntity
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
            db.accountDao().insertAccount(AccountEntity(id = 1, name = "Cash", type = "CASH", initialBalance = 0.0, colorHex = "#4CAF50"))
            db.categoryDao().insertCategory(CategoryEntity(id = 2, name = "Comida", iconKey = "restaurant", isDefault = true, colorHex = "#EF4444", type = "EXPENSE"))

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
                assertEquals(4, restoredCount) // One account, one category, one business and one recurring expense.
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
    fun importSkipsOnlyDeterministicMigrationPlaceholderAndPreservesAmbiguousLegacyBusiness() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val backup = JSONObject().apply {
            put("version", 1)
            put("accounts", JSONArray())
            put("categories", JSONArray())
            put("transactions", JSONArray())
            put("reminders", JSONArray())
            put("savingsGoals", JSONArray())
            put("recurringExpenses", JSONArray())
            put("businesses", JSONArray())
            put("preferences", JSONObject())
            put("businesses", JSONArray().apply {
                put(JSONObject().apply {
                    put("id", 1)
                    put("name", "INSERC Store")
                    put("type", "Tecnología y Comercio")
                    put("description", "Negocio principal de servicios y venta comercial")
                    put("colorHex", "#2563EB")
                    put("iconKey", "storefront")
                    put("inventoryValue", 0.0)
                    put("createdAt", 1704067200000L)
                })
                put(JSONObject().apply {
                    put("id", 2)
                    put("name", "INSERC Multiservice")
                    put("type", "Servicios Profesionales")
                    put("description", "Negocio creado por el usuario")
                    put("colorHex", "#10B981")
                    put("iconKey", "storefront")
                    put("inventoryValue", 2750.0)
                    put("createdAt", 1700000000001L)
                })
                put(JSONObject().apply {
                    put("id", 3)
                    put("name", "INSERC Store")
                    put("type", "Tecnología y Comercio")
                    put("description", "Negocio editado por el usuario")
                    put("colorHex", "#2563EB")
                    put("iconKey", "storefront")
                    put("inventoryValue", 500.0)
                    put("createdAt", 1700000000002L)
                })
                put(JSONObject().apply {
                    put("id", 4)
                    put("name", "INSERC Store")
                    put("type", "Tecnología y Comercio")
                    put("description", "Negocio comercial principal")
                    put("colorHex", "#2563EB")
                    put("iconKey", "storefront")
                    put("inventoryValue", 0.0)
                    put("createdAt", 1700000000003L)
                })
            })
        }.toString()

        try {
            val restoredCount = BackupManager.importBackupFromJson(context, db, backup)
            assertEquals(3, restoredCount)
            assertEquals(null, db.businessDao().getBusinessById(1))
            assertEquals("INSERC Multiservice", db.businessDao().getBusinessById(2)?.name)
            assertEquals("Negocio editado por el usuario", db.businessDao().getBusinessById(3)?.description)
            assertEquals("Negocio comercial principal", db.businessDao().getBusinessById(4)?.description)
        } finally {
            db.close()
        }
    }

    @Test
    fun importPreservesVariableTimestampLegacyLookalikeBusiness() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val backup = JSONObject().apply {
            put("version", 1)
            put("accounts", JSONArray())
            put("categories", JSONArray())
            put("transactions", JSONArray())
            put("reminders", JSONArray())
            put("savingsGoals", JSONArray())
            put("recurringExpenses", JSONArray())
            put("businesses", JSONArray())
            put("preferences", JSONObject())
            put("businesses", JSONArray().put(JSONObject().apply {
                put("id", 1)
                put("name", "INSERC Store")
                put("type", "Tecnología y Comercio")
                put("description", "Negocio comercial principal")
                put("colorHex", "#2563EB")
                put("iconKey", "storefront")
                put("inventoryValue", 0.0)
                put("createdAt", 1700000000000L)
            }))
        }.toString()

        try {
            val restored = BackupManager.importBackupFromJson(context, db, backup)
            assertEquals(1, restored)
            assertEquals(
                "Negocio comercial principal",
                db.businessDao().getBusinessById(1)?.description
            )
            assertEquals(1700000000000L, db.businessDao().getBusinessById(1)?.createdAt)
        } finally {
            db.close()
        }
    }


    @Test
    fun malformedFinancialBackupIsRejectedBeforeAnyDatabaseWrite() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val backup = JSONObject().apply {
            put("version", 1)
            put("accounts", JSONArray())
            put("categories", JSONArray())
            put("transactions", JSONArray())
            put("reminders", JSONArray())
            put("savingsGoals", JSONArray())
            put("recurringExpenses", JSONArray())
            put("businesses", JSONArray())
            put("preferences", JSONObject())
            put("accounts", JSONArray().put(JSONObject().apply {
                put("id", 1)
                put("name", "Cash")
                put("type", "CASH")
                put("initialBalance", 100.0)
                put("colorHex", "#00AA00")
            }))
            put("transactions", JSONArray().apply {
                put(JSONObject().apply {
                    put("id", 1)
                    put("amount", 20.0)
                    put("type", "EXPENSE")
                    put("categoryId", 1)
                    put("description", "Valid row before malformed row")
                    put("date", 1700000000000L)
                    put("accountId", 1)
                })
                put(JSONObject().apply {
                    put("id", 2)
                    // amount is deliberately absent; the old importer silently defaulted it to 0.
                    put("type", "EXPENSE")
                    put("categoryId", 1)
                    put("description", "Must not be imported")
                    put("date", 1700000000001L)
                    put("accountId", 1)
                })
            })
        }.toString()

        try {
            val error = try {
                BackupManager.importBackupFromJson(context, db, backup)
                null
            } catch (exception: IllegalArgumentException) {
                exception.message
            }
            assertTrue(error?.contains("transactions[1].amount") == true)

            val readable = db.openHelper.readableDatabase
            readable.query("SELECT COUNT(*) FROM accounts").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
            readable.query("SELECT COUNT(*) FROM transactions").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
        } finally {
            db.close()
        }
    }


    @Test
    fun importRejectsValuesThatWouldOtherwiseBeSilentlyCoerced() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val unsupportedFrequency = JSONObject().apply {
            put("version", 1)
            put("accounts", JSONArray())
            put("categories", JSONArray())
            put("transactions", JSONArray())
            put("reminders", JSONArray())
            put("savingsGoals", JSONArray())
            put("recurringExpenses", JSONArray())
            put("businesses", JSONArray())
            put("preferences", JSONObject())
            put("recurringExpenses", JSONArray().put(JSONObject().apply {
                put("id", 77)
                put("title", "Unknown frequency")
                put("amount", 25.0)
                put("categoryId", 1)
                put("accountId", 1)
                put("dayOfMonth", 15)
                put("isEnabled", true)
                put("autoCreate", false)
                put("createdAt", 1700000000000L)
                put("frequency", "EVERY_TEN_DAYS")
            }))
        }.toString()
        val invalidPredictedIncome = JSONObject().apply {
            put("version", 1)
            put("accounts", JSONArray())
            put("categories", JSONArray())
            put("transactions", JSONArray())
            put("reminders", JSONArray())
            put("savingsGoals", JSONArray())
            put("recurringExpenses", JSONArray())
            put("businesses", JSONArray())
            put("preferences", JSONObject())
            put("preferences", JSONObject().put("predictedIncome", -100.0))
        }.toString()
        val unknownCurrency = JSONObject().apply {
            put("version", 1)
            put("accounts", JSONArray())
            put("categories", JSONArray())
            put("transactions", JSONArray())
            put("reminders", JSONArray())
            put("savingsGoals", JSONArray())
            put("recurringExpenses", JSONArray())
            put("businesses", JSONArray())
            put("preferences", JSONObject())
            put("preferences", JSONObject().put("currencyCode", "XYZ"))
        }.toString()

        try {
            listOf(unsupportedFrequency, invalidPredictedIncome, unknownCurrency).forEach { payload ->
                assertThrows(IllegalArgumentException::class.java) {
                    runBlocking { BackupManager.importBackupFromJson(context, db, payload) }
                }
            }
            val readable = db.openHelper.readableDatabase
            readable.query("SELECT COUNT(*) FROM recurring_expenses").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
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
            put("accounts", JSONArray())
            put("categories", JSONArray())
            put("transactions", JSONArray())
            put("reminders", JSONArray())
            put("savingsGoals", JSONArray())
            put("recurringExpenses", JSONArray())
            put("businesses", JSONArray())
            put("preferences", JSONObject())
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

            BackupManager.importBackupFromJson(context, db, backup, applyAppPreferences = true)

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
    fun malformedJsonIsRejectedBeforeAnyDatabaseWrite() {
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
            assertThrows(IllegalArgumentException::class.java) {
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

    @Test
    fun importRejectsPopulatedDestinationWithoutChangingExistingRecords() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val backup = JSONObject().apply {
            put("version", 1)
            put("accounts", JSONArray())
            put("categories", JSONArray())
            put("transactions", JSONArray())
            put("reminders", JSONArray())
            put("savingsGoals", JSONArray())
            put("recurringExpenses", JSONArray())
            put("businesses", JSONArray())
            put("preferences", JSONObject())
        }.toString()

        try {
            db.accountDao().insertAccount(
                AccountEntity(
                    id = 91,
                    name = "Existing account",
                    type = "CASH",
                    initialBalance = 250.0,
                    colorHex = "#123456"
                )
            )

            assertThrows(IllegalStateException::class.java) {
                runBlocking { BackupManager.importBackupFromJson(context, db, backup) }
            }

            val accounts = db.accountDao().getAllAccounts().first()
            assertEquals(1, accounts.size)
            assertEquals("Existing account", accounts.single().name)
            assertEquals(250.0, accounts.single().initialBalance, 0.001)
        } finally {
            db.close()
        }
    }

    @Test
    fun stagedImportDoesNotChangeGlobalPreferences() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val backup = JSONObject().apply {
            put("version", 1)
            put("accounts", JSONArray())
            put("categories", JSONArray())
            put("transactions", JSONArray())
            put("reminders", JSONArray())
            put("savingsGoals", JSONArray())
            put("recurringExpenses", JSONArray())
            put("businesses", JSONArray())
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

            BackupManager.importBackupFromJson(context, db, backup, applyAppPreferences = false)

            assertEquals(1, BudgetCycleManager.getStartDay())
            assertEquals(null, BudgetCycleManager.getPredictedIncome())
            assertEquals(CurrencyFormatter.DEFAULT_CURRENCY_CODE, CurrencyFormatter.getCurrency())
        } finally {
            db.close()
            BudgetCycleManager.setStartDay(context, 1)
            BudgetCycleManager.setPredictedIncome(context, null)
            CurrencyFormatter.setCurrency(context, CurrencyFormatter.DEFAULT_CURRENCY_CODE)
        }
    }

}
