package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.local.dao.CategoryDao
import com.example.data.local.dao.SavingsGoalDao
import com.example.data.local.dao.TransactionDao
import com.example.data.local.dao.ReminderDao
import com.example.data.local.dao.AccountDao
import com.example.data.local.dao.BusinessDao
import com.example.data.local.dao.RecurringExpenseDao
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.SavingsGoalEntity
import com.example.data.local.entity.TransactionEntity
import com.example.data.local.entity.ReminderEntity
import com.example.data.local.entity.AccountEntity
import com.example.data.local.entity.BusinessEntity
import com.example.data.local.entity.RecurringExpenseEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Base de datos Room principal de la aplicación.
 */
@Database(
    entities = [
        TransactionEntity::class,
        CategoryEntity::class,
        SavingsGoalEntity::class,
        ReminderEntity::class,
        AccountEntity::class,
        RecurringExpenseEntity::class,
        BusinessEntity::class
    ],
    version = 12,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun savingsGoalDao(): SavingsGoalDao
    abstract fun reminderDao(): ReminderDao
    abstract fun accountDao(): AccountDao
    abstract fun recurringExpenseDao(): RecurringExpenseDao
    abstract fun businessDao(): BusinessDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE reminders ADD COLUMN notifyDaysBefore INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE reminders ADD COLUMN notifyTime TEXT NOT NULL DEFAULT '09:00'")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE categories ADD COLUMN colorHex TEXT NOT NULL DEFAULT '#3B82F6'")
                db.execSQL("ALTER TABLE categories ADD COLUMN type TEXT NOT NULL DEFAULT 'EXPENSE'")
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_type_date ON transactions(type, date)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_accountId_date ON transactions(accountId, date)")
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS businesses (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        type TEXT NOT NULL DEFAULT 'Comercio / Ventas',
                        description TEXT NOT NULL DEFAULT '',
                        colorHex TEXT NOT NULL DEFAULT '#2563EB',
                        iconKey TEXT NOT NULL DEFAULT 'storefront',
                        inventoryValue REAL NOT NULL DEFAULT 0.0,
                        createdAt INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT OR IGNORE INTO businesses (id, name, type, description, colorHex, iconKey, inventoryValue, createdAt)
                    VALUES (1, 'INSERC Store', 'Tecnología y Comercio', 'Negocio principal de servicios y venta comercial', '#2563EB', 'storefront', 0.0, 1704067200000)
                """.trimIndent())
            }
        }

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "budget_tracker_database"
                )
                    .addMigrations(MIGRATION_7_8, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12)
                    // Deliberately do not use fallbackToDestructiveMigration: if a migration path is
                    // missing, Room must fail rather than silently erase financial records.
                    // Legacy-version recovery UX and migration coverage must be validated separately.
                    .addCallback(AppDatabaseCallback(scope))
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }

    private class AppDatabaseCallback(
        private val scope: CoroutineScope
    ) : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            INSTANCE?.let { database ->
                scope.launch(Dispatchers.IO) {
                    populateDefaultCategories(database.categoryDao())
                    populateDefaultAccounts(database.accountDao())
                    populateDefaultBusinesses(database.businessDao())
                }
            }
        }

        override fun onOpen(db: SupportSQLiteDatabase) {
            super.onOpen(db)
            INSTANCE?.let { database ->
                scope.launch(Dispatchers.IO) {
                    if (database.businessDao().getBusinessCount() == 0) {
                        populateDefaultBusinesses(database.businessDao())
                    }
                }
            }
        }

        private suspend fun populateDefaultBusinesses(businessDao: BusinessDao) {
            if (businessDao.getBusinessCount() == 0) {
                businessDao.insertBusiness(
                    BusinessEntity(
                        id = 1,
                        name = "INSERC Store",
                        type = "Tecnología y Comercio",
                        description = "Negocio comercial principal",
                        colorHex = "#2563EB",
                        iconKey = "storefront",
                        inventoryValue = 0.0,
                        createdAt = System.currentTimeMillis()
                    )
                )
            }
        }

        private suspend fun populateDefaultCategories(categoryDao: CategoryDao) {
            val defaultCategories = listOf(
                CategoryEntity(id = 1, name = "Salario", iconKey = "attach_money", isDefault = true, colorHex = "#10B981", type = "INCOME"),
                CategoryEntity(id = 2, name = "Comida", iconKey = "restaurant", isDefault = true, maxMonthlyBudget = 8000.0, colorHex = "#EF4444", type = "EXPENSE"),
                CategoryEntity(id = 3, name = "Transporte", iconKey = "directions_car", isDefault = true, maxMonthlyBudget = 2000.0, colorHex = "#F59E0B", type = "EXPENSE"),
                CategoryEntity(id = 4, name = "Ocio", iconKey = "sports_esports", isDefault = true, maxMonthlyBudget = 2000.0, colorHex = "#8B5CF6", type = "EXPENSE"),
                CategoryEntity(id = 5, name = "Salud", iconKey = "medical_services", isDefault = true, colorHex = "#EC4899", type = "EXPENSE"),
                CategoryEntity(id = 6, name = "Educación", iconKey = "school", isDefault = true, colorHex = "#3B82F6", type = "EXPENSE"),
                CategoryEntity(id = 7, name = "Servicios", iconKey = "receipt", isDefault = true, maxMonthlyBudget = 5000.0, colorHex = "#06B6D4", type = "EXPENSE"),
                CategoryEntity(id = 8, name = "Otros", iconKey = "category", isDefault = true, colorHex = "#6B7280", type = "EXPENSE"),
                CategoryEntity(id = 9, name = "Negocio", iconKey = "storefront", isDefault = true, colorHex = "#10B981", type = "INCOME"),
                CategoryEntity(id = 10, name = "Transferencia", iconKey = "swap_horiz", isDefault = true, colorHex = "#6366F1", type = "EXPENSE")
            )
            categoryDao.insertCategories(defaultCategories)
        }

        private suspend fun populateDefaultAccounts(accountDao: AccountDao) {
            val defaultAccounts = listOf(
                AccountEntity(
                    id = 1,
                    name = "Efectivo",
                    type = "CASH",
                    initialBalance = 0.0,
                    colorHex = "#4CAF50" // Verde
                ),
                AccountEntity(
                    id = 2,
                    name = "Cuenta bancaria",
                    type = "BANK",
                    initialBalance = 0.0,
                    colorHex = "#2196F3" // Azul
                ),
                AccountEntity(
                    id = 3,
                    name = "Tarjeta de débito",
                    type = "DEBIT",
                    initialBalance = 0.0,
                    colorHex = "#9C27B0" // Morado
                ),
                AccountEntity(
                    id = 4,
                    name = "Tarjeta de crédito",
                    type = "CREDIT",
                    initialBalance = 0.0,
                    colorHex = "#FF9800" // Naranja
                )
            )
            accountDao.insertAccounts(defaultAccounts)
        }
    }
}
