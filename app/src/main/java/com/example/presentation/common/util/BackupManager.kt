package com.example.presentation.common.util

import android.content.Context
import com.example.data.local.AppDatabase
import com.example.data.local.entity.AccountEntity
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.ReminderEntity
import com.example.data.local.entity.SavingsGoalEntity
import com.example.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Gestor centralizado para la copia de seguridad (Backup) y restauración (Restore)
 * de la base de datos de la aplicación en formato JSON, así como para la gestión
 * del recordatorio mensual de copia de seguridad.
 */
object BackupManager {

    private const val PREFS_NAME = "backup_prefs"
    private const val KEY_PROMPT_ENABLED = "monthly_backup_prompt_enabled"
    private const val KEY_LAST_BACKUP_TIME = "last_backup_timestamp"
    private const val KEY_DISMISSED_YEAR_MONTH = "dismissed_backup_year_month"

    fun isMonthlyPromptEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_PROMPT_ENABLED, true)
    }

    fun setMonthlyPromptEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_PROMPT_ENABLED, enabled).apply()
    }

    fun getLastBackupTimestamp(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong(KEY_LAST_BACKUP_TIME, 0L)
    }

    fun setLastBackupTimestamp(context: Context, timestamp: Long) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_LAST_BACKUP_TIME, timestamp).apply()
    }

    fun getCurrentYearMonth(): String {
        val cal = Calendar.getInstance()
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1
        return "$year-$month"
    }

    fun dismissPromptForCurrentMonth(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_DISMISSED_YEAR_MONTH, getCurrentYearMonth()).apply()
    }

    fun shouldShowMonthlyBackupPrompt(context: Context): Boolean {
        if (!isMonthlyPromptEnabled(context)) return false

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val dismissedMonth = prefs.getString(KEY_DISMISSED_YEAR_MONTH, "")
        if (dismissedMonth == getCurrentYearMonth()) return false

        val lastBackup = getLastBackupTimestamp(context)
        if (lastBackup == 0L) return true

        // Verificar si la última copia de seguridad fue hace más de 30 días o en un mes anterior
        val now = System.currentTimeMillis()
        val thirtyDaysInMillis = 30L * 24 * 60 * 60 * 1000
        val isOlderThan30Days = (now - lastBackup) > thirtyDaysInMillis

        val lastCal = Calendar.getInstance().apply { timeInMillis = lastBackup }
        val nowCal = Calendar.getInstance().apply { timeInMillis = now }

        val isDifferentMonth = (lastCal.get(Calendar.YEAR) != nowCal.get(Calendar.YEAR)) ||
                (lastCal.get(Calendar.MONTH) != nowCal.get(Calendar.MONTH))

        return isOlderThan30Days || isDifferentMonth
    }

    /**
     * Exporta todos los datos relevantes de la base de datos a un String JSON estructurado.
     */
    suspend fun exportFullBackupJson(context: Context, db: AppDatabase): String {
        val accounts = db.accountDao().getAllAccounts().first()
        val categories = db.categoryDao().getAllCategories().first()
        val transactions = db.transactionDao().getAllTransactions().first()
        val reminders = db.reminderDao().getAllReminders().first()
        val savingsGoals = db.savingsGoalDao().getAllSavingsGoals().first()
        val recurringExpenses = db.recurringExpenseDao().getAllRecurringExpensesSync()

        val rootJson = JSONObject()
        rootJson.put("version", 1)
        rootJson.put("exportDateMillis", System.currentTimeMillis())
        rootJson.put("exportDateFormatted", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))

        // Accounts
        val accountsArray = JSONArray()
        accounts.forEach { account ->
            val obj = JSONObject().apply {
                put("id", account.id)
                put("name", account.name)
                put("type", account.type)
                put("initialBalance", account.initialBalance)
                put("colorHex", account.colorHex)
            }
            accountsArray.put(obj)
        }
        rootJson.put("accounts", accountsArray)

        // Categories
        val categoriesArray = JSONArray()
        categories.forEach { cat ->
            val obj = JSONObject().apply {
                put("id", cat.id)
                put("name", cat.name)
                put("iconKey", cat.iconKey)
                put("isDefault", cat.isDefault)
                cat.maxMonthlyBudget?.let { put("maxMonthlyBudget", it) }
                put("colorHex", cat.colorHex)
                put("type", cat.type)
            }
            categoriesArray.put(obj)
        }
        rootJson.put("categories", categoriesArray)

        // Transactions
        val transactionsArray = JSONArray()
        transactions.forEach { tx ->
            val obj = JSONObject().apply {
                put("id", tx.id)
                put("amount", tx.amount)
                put("type", tx.type)
                put("categoryId", tx.categoryId)
                put("description", tx.description ?: "")
                put("date", tx.date)
                put("accountId", tx.accountId)
                tx.tags?.let { put("tags", it) }
            }
            transactionsArray.put(obj)
        }
        rootJson.put("transactions", transactionsArray)

        // Reminders
        val remindersArray = JSONArray()
        reminders.forEach { rem ->
            val obj = JSONObject().apply {
                put("id", rem.id)
                put("type", rem.type)
                put("title", rem.title)
                put("description", rem.description)
                put("isEnabled", rem.isEnabled)
                rem.amount?.let { put("amount", it) }
                rem.dueDateDay?.let { put("dueDateDay", it) }
                rem.time?.let { put("time", it) }
                put("notifyDaysBefore", rem.notifyDaysBefore)
                put("notifyTime", rem.notifyTime)
                rem.associatedGoalId?.let { put("associatedGoalId", it) }
                put("isCompletedThisMonth", rem.isCompletedThisMonth)
            }
            remindersArray.put(obj)
        }
        rootJson.put("reminders", remindersArray)

        // Savings Goals
        val goalsArray = JSONArray()
        savingsGoals.forEach { goal ->
            val obj = JSONObject().apply {
                put("id", goal.id)
                put("name", goal.name)
                put("targetAmount", goal.targetAmount)
                put("currentAmount", goal.currentAmount)
                goal.deadline?.let { put("deadline", it) }
                put("createdAt", goal.createdAt)
                put("description", goal.description)
                put("priority", goal.priority)
                put("category", goal.category)
            }
            goalsArray.put(obj)
        }
        rootJson.put("savingsGoals", goalsArray)

        // Recurring Expenses
        val recurringArray = JSONArray()
        recurringExpenses.forEach { rec ->
            val obj = JSONObject().apply {
                put("id", rec.id)
                put("title", rec.title)
                put("amount", rec.amount)
                put("categoryId", rec.categoryId)
                put("accountId", rec.accountId)
                put("dayOfMonth", rec.dayOfMonth)
                rec.description?.let { put("description", it) }
                rec.tags?.let { put("tags", it) }
                put("isEnabled", rec.isEnabled)
                rec.lastExecutedMonth?.let { put("lastExecutedMonth", it) }
                put("autoCreate", rec.autoCreate)
                put("createdAt", rec.createdAt)
            }
            recurringArray.put(obj)
        }
        rootJson.put("recurringExpenses", recurringArray)

        // Actualizar sello de fecha de último respaldo
        setLastBackupTimestamp(context, System.currentTimeMillis())

        return rootJson.toString(2)
    }

    /**
     * Importa y restaura una copia de seguridad JSON guardada previamente.
     */
    suspend fun importBackupFromJson(context: Context, db: AppDatabase, jsonString: String): Int {
        val rootJson = JSONObject(jsonString)
        var restoredCount = 0

        // Restore Accounts
        if (rootJson.has("accounts")) {
            val arr = rootJson.getJSONArray("accounts")
            val accounts = mutableListOf<AccountEntity>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                accounts.add(
                    AccountEntity(
                        id = obj.optLong("id", 0L),
                        name = obj.optString("name", "Cuenta"),
                        type = obj.optString("type", "CASH"),
                        initialBalance = obj.optDouble("initialBalance", 0.0),
                        colorHex = obj.optString("colorHex", "#4CAF50")
                    )
                )
            }
            if (accounts.isNotEmpty()) {
                db.accountDao().insertAccounts(accounts)
                restoredCount += accounts.size
            }
        }

        // Restore Categories
        if (rootJson.has("categories")) {
            val arr = rootJson.getJSONArray("categories")
            val categories = mutableListOf<CategoryEntity>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                categories.add(
                    CategoryEntity(
                        id = obj.optLong("id", 0L),
                        name = obj.optString("name", "Categoría"),
                        iconKey = obj.optString("iconKey", "attach_money"),
                        isDefault = obj.optBoolean("isDefault", false),
                        maxMonthlyBudget = if (obj.has("maxMonthlyBudget") && !obj.isNull("maxMonthlyBudget")) obj.getDouble("maxMonthlyBudget") else null,
                        colorHex = obj.optString("colorHex", "#3B82F6"),
                        type = obj.optString("type", "EXPENSE")
                    )
                )
            }
            if (categories.isNotEmpty()) {
                db.categoryDao().insertCategories(categories)
                restoredCount += categories.size
            }
        }

        // Restore Transactions
        if (rootJson.has("transactions")) {
            val arr = rootJson.getJSONArray("transactions")
            val transactions = mutableListOf<TransactionEntity>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                transactions.add(
                    TransactionEntity(
                        id = obj.optLong("id", 0L),
                        amount = obj.optDouble("amount", 0.0),
                        type = obj.optString("type", "EXPENSE"),
                        categoryId = obj.optLong("categoryId", 1L),
                        description = obj.optString("description", ""),
                        date = obj.optLong("date", System.currentTimeMillis()),
                        accountId = obj.optLong("accountId", 1L),
                        tags = if (obj.has("tags") && !obj.isNull("tags")) obj.getString("tags") else null
                    )
                )
            }
            if (transactions.isNotEmpty()) {
                db.transactionDao().insertAllTransactions(transactions)
                restoredCount += transactions.size
            }
        }

        // Restore Reminders
        if (rootJson.has("reminders")) {
            val arr = rootJson.getJSONArray("reminders")
            val reminders = mutableListOf<ReminderEntity>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                reminders.add(
                    ReminderEntity(
                        id = obj.optLong("id", 0L),
                        type = obj.optString("type", "OTRO"),
                        title = obj.optString("title", "Recordatorio"),
                        description = obj.optString("description", ""),
                        isEnabled = obj.optBoolean("isEnabled", true),
                        amount = if (obj.has("amount") && !obj.isNull("amount")) obj.getDouble("amount") else null,
                        dueDateDay = if (obj.has("dueDateDay") && !obj.isNull("dueDateDay")) obj.getInt("dueDateDay") else null,
                        time = if (obj.has("time") && !obj.isNull("time")) obj.getString("time") else null,
                        notifyDaysBefore = obj.optInt("notifyDaysBefore", 1),
                        notifyTime = obj.optString("notifyTime", "09:00"),
                        associatedGoalId = if (obj.has("associatedGoalId") && !obj.isNull("associatedGoalId")) obj.getLong("associatedGoalId") else null,
                        isCompletedThisMonth = obj.optBoolean("isCompletedThisMonth", false)
                    )
                )
            }
            if (reminders.isNotEmpty()) {
                db.reminderDao().insertReminders(reminders)
                restoredCount += reminders.size
            }
        }

        // Restore Savings Goals
        if (rootJson.has("savingsGoals")) {
            val arr = rootJson.getJSONArray("savingsGoals")
            val goals = mutableListOf<SavingsGoalEntity>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                goals.add(
                    SavingsGoalEntity(
                        id = obj.optLong("id", 0L),
                        name = obj.optString("name", "Meta"),
                        targetAmount = obj.optDouble("targetAmount", 100.0),
                        currentAmount = obj.optDouble("currentAmount", 0.0),
                        deadline = if (obj.has("deadline") && !obj.isNull("deadline")) obj.getLong("deadline") else null,
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                        description = obj.optString("description", ""),
                        priority = obj.optString("priority", "Media"),
                        category = obj.optString("category", "Otros")
                    )
                )
            }
            if (goals.isNotEmpty()) {
                goals.forEach { goal ->
                    db.savingsGoalDao().insertSavingsGoal(goal)
                }
                restoredCount += goals.size
            }
        }

        // Restore Recurring Expenses
        if (rootJson.has("recurringExpenses")) {
            val arr = rootJson.getJSONArray("recurringExpenses")
            val recurringList = mutableListOf<com.example.data.local.entity.RecurringExpenseEntity>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                recurringList.add(
                    com.example.data.local.entity.RecurringExpenseEntity(
                        id = obj.optLong("id", 0L),
                        title = obj.optString("title", "Gasto Recurrente"),
                        amount = obj.optDouble("amount", 0.0),
                        categoryId = obj.optLong("categoryId", 1L),
                        accountId = obj.optLong("accountId", 1L),
                        dayOfMonth = obj.optInt("dayOfMonth", 1),
                        description = if (obj.has("description") && !obj.isNull("description")) obj.getString("description") else null,
                        tags = if (obj.has("tags") && !obj.isNull("tags")) obj.getString("tags") else null,
                        isEnabled = obj.optBoolean("isEnabled", true),
                        lastExecutedMonth = if (obj.has("lastExecutedMonth") && !obj.isNull("lastExecutedMonth")) obj.getString("lastExecutedMonth") else null,
                        autoCreate = obj.optBoolean("autoCreate", true),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
            if (recurringList.isNotEmpty()) {
                db.recurringExpenseDao().insertRecurringExpenses(recurringList)
                restoredCount += recurringList.size
            }
        }

        setLastBackupTimestamp(context, System.currentTimeMillis())
        return restoredCount
    }
}
