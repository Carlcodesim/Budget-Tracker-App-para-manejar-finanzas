package com.example.presentation.common.util

import android.content.Context
import androidx.room.withTransaction
import com.example.data.local.AppDatabase
import com.example.data.local.entity.AccountEntity
import com.example.data.local.entity.BusinessEntity
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.ReminderEntity
import com.example.data.local.entity.SavingsGoalEntity
import com.example.data.local.entity.TransactionEntity
import com.example.util.BudgetCycleManager
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
        val businesses = db.businessDao().getAllBusinesses().first()

        val rootJson = JSONObject()
        rootJson.put("version", 1)
        rootJson.put("exportDateMillis", System.currentTimeMillis())
        rootJson.put("exportDateFormatted", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))

        // Persist financial display settings that would otherwise be lost on reinstall.
        rootJson.put("preferences", JSONObject().apply {
            put("cycleStartDay", BudgetCycleManager.getStartDay())
            put("predictedIncome", BudgetCycleManager.getPredictedIncome() ?: JSONObject.NULL)
            put("currencyCode", CurrencyFormatter.getCurrency())
        })

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
                put("frequency", rec.frequency)
                put("anchorDate", rec.anchorDate)
                put("nextDueDate", rec.nextDueDate)
            }
            recurringArray.put(obj)
        }
        rootJson.put("recurringExpenses", recurringArray)

        // Businesses
        val businessesArray = JSONArray()
        businesses.forEach { business ->
            val obj = JSONObject().apply {
                put("id", business.id)
                put("name", business.name)
                put("type", business.type)
                put("description", business.description)
                put("colorHex", business.colorHex)
                put("iconKey", business.iconKey)
                put("inventoryValue", business.inventoryValue)
                put("createdAt", business.createdAt)
            }
            businessesArray.put(obj)
        }
        rootJson.put("businesses", businessesArray)

        // Actualizar sello de fecha de último respaldo
        setLastBackupTimestamp(context, System.currentTimeMillis())

        return rootJson.toString(2)
    }

    /**
     * Validate the full JSON structure before starting any Room transaction.
     * Critical financial values are never silently replaced with opt* defaults.
     * Optional fields remain optional only where version-1 legacy support is documented.
     */
    private fun validateBackupForImport(root: JSONObject) {
        val version = requiredInteger(root, "version", "backup")
        if (version != 1L) {
            throw IllegalArgumentException("Versión de respaldo no compatible: $version.")
        }

        validateRecords(root, "accounts") { record, path ->
            requiredString(record, "name", path)
            requiredString(record, "type", path)
            requiredNumber(record, "initialBalance", path)
            requiredString(record, "colorHex", path)
        }
        validateRecords(root, "categories") { record, path ->
            requiredString(record, "name", path)
            requiredString(record, "iconKey", path)
            requiredBoolean(record, "isDefault", path)
            optionalNumber(record, "maxMonthlyBudget", path)
            requiredString(record, "colorHex", path)
            requiredString(record, "type", path)
        }
        validateRecords(root, "transactions") { record, path ->
            requiredNumber(record, "amount", path)
            requiredString(record, "type", path)
            requiredInteger(record, "categoryId", path)
            requiredString(record, "description", path)
            requiredInteger(record, "date", path)
            requiredInteger(record, "accountId", path)
            optionalString(record, "tags", path)
        }
        validateRecords(root, "reminders") { record, path ->
            requiredString(record, "type", path)
            requiredString(record, "title", path)
            requiredString(record, "description", path)
            requiredBoolean(record, "isEnabled", path)
            optionalNumber(record, "amount", path)
            optionalInteger(record, "dueDateDay", path)
            optionalString(record, "time", path)
            optionalInteger(record, "notifyDaysBefore", path)
            optionalString(record, "notifyTime", path)
            optionalInteger(record, "associatedGoalId", path)
            optionalBoolean(record, "isCompletedThisMonth", path)
        }
        validateRecords(root, "savingsGoals") { record, path ->
            requiredString(record, "name", path)
            requiredNumber(record, "targetAmount", path)
            requiredNumber(record, "currentAmount", path)
            optionalInteger(record, "deadline", path)
            requiredInteger(record, "createdAt", path)
            requiredString(record, "description", path)
            requiredString(record, "priority", path)
            requiredString(record, "category", path)
        }
        validateRecords(root, "recurringExpenses") { record, path ->
            requiredString(record, "title", path)
            requiredNumber(record, "amount", path)
            requiredInteger(record, "categoryId", path)
            requiredInteger(record, "accountId", path)
            requiredInteger(record, "dayOfMonth", path)
            optionalString(record, "description", path)
            optionalString(record, "tags", path)
            requiredBoolean(record, "isEnabled", path)
            optionalString(record, "lastExecutedMonth", path)
            optionalBoolean(record, "autoCreate", path)
            requiredInteger(record, "createdAt", path)
            // Old version-1 backups predate configurable frequencies and intentionally
            // retain the MONTHLY / zero-date compatibility defaults.
            if (record.has("frequency") && !record.isNull("frequency")) {
                val frequency = requiredString(record, "frequency", path)
                val supportedFrequencies = setOf("WEEKLY", "BIWEEKLY", "MONTHLY", "YEARLY")
                if (frequency !in supportedFrequencies) {
                    throw IllegalArgumentException("El campo $path.frequency contiene una frecuencia no compatible.")
                }
            }
            optionalInteger(record, "anchorDate", path)
            optionalInteger(record, "nextDueDate", path)
        }
        validateRecords(root, "businesses") { record, path ->
            requiredString(record, "name", path)
            requiredString(record, "type", path)
            requiredString(record, "description", path)
            requiredString(record, "colorHex", path)
            requiredString(record, "iconKey", path)
            requiredNumber(record, "inventoryValue", path)
            requiredInteger(record, "createdAt", path)
        }

        if (root.has("preferences") && !root.isNull("preferences")) {
            val preferences = root.opt("preferences") as? JSONObject
                ?: throw IllegalArgumentException("El campo backup.preferences debe ser un objeto.")
            if (preferences.has("cycleStartDay") && !preferences.isNull("cycleStartDay")) {
                val startDay = requiredInteger(preferences, "cycleStartDay", "preferences")
                if (startDay !in 1L..31L) {
                    throw IllegalArgumentException("backup.preferences.cycleStartDay debe estar entre 1 y 31.")
                }
            }
            if (preferences.has("predictedIncome") && !preferences.isNull("predictedIncome")) {
                val predictedIncome = requiredNumber(
                    preferences, "predictedIncome", "preferences"
                ).toDouble()
                val storedIncome = predictedIncome.toFloat()
                if (predictedIncome <= 0.0 || !storedIncome.isFinite() ||
                    storedIncome.toDouble() != predictedIncome
                ) {
                    throw IllegalArgumentException(
                        "backup.preferences.predictedIncome no puede conservarse exactamente como preferencia financiera."
                    )
                }
            }
            if (preferences.has("currencyCode") && !preferences.isNull("currencyCode")) {
                val currencyCode = requiredString(preferences, "currencyCode", "preferences")
                if (CurrencyFormatter.availableCurrencies.none { it.code == currencyCode }) {
                    throw IllegalArgumentException(
                        "backup.preferences.currencyCode no corresponde a una moneda compatible."
                    )
                }
            }
        }
    }

    private inline fun validateRecords(
        root: JSONObject,
        arrayName: String,
        validateRecord: (JSONObject, String) -> Unit
    ) {
        if (!root.has(arrayName)) return
        val records = root.opt(arrayName) as? JSONArray
            ?: throw IllegalArgumentException("El campo backup.$arrayName debe ser una lista.")
        val seenIds = mutableSetOf<Long>()
        for (index in 0 until records.length()) {
            val path = "$arrayName[$index]"
            val record = records.opt(index) as? JSONObject
                ?: throw IllegalArgumentException("El registro $path debe ser un objeto.")
            val id = requiredInteger(record, "id", path)
            if (id <= 0L) {
                throw IllegalArgumentException("El campo $path.id debe ser un identificador positivo.")
            }
            if (!seenIds.add(id)) {
                throw IllegalArgumentException("El respaldo contiene el identificador duplicado $id en $arrayName.")
            }
            validateRecord(record, path)
        }
    }

    private fun requiredValue(record: JSONObject, key: String, path: String): Any {
        if (!record.has(key) || record.isNull(key)) {
            throw IllegalArgumentException("Falta el campo obligatorio $path.$key.")
        }
        return record.opt(key)
            ?: throw IllegalArgumentException("El campo obligatorio $path.$key está vacío.")
    }

    private fun requiredString(record: JSONObject, key: String, path: String): String =
        requiredValue(record, key, path) as? String
            ?: throw IllegalArgumentException("El campo $path.$key debe ser texto.")

    private fun requiredBoolean(record: JSONObject, key: String, path: String): Boolean =
        requiredValue(record, key, path) as? Boolean
            ?: throw IllegalArgumentException("El campo $path.$key debe ser booleano.")

    private fun requiredNumber(record: JSONObject, key: String, path: String): Number {
        val value = requiredValue(record, key, path) as? Number
            ?: throw IllegalArgumentException("El campo $path.$key debe ser numérico.")
        if (!value.toDouble().isFinite()) {
            throw IllegalArgumentException("El campo $path.$key debe ser un número finito.")
        }
        return value
    }

    private fun requiredInteger(record: JSONObject, key: String, path: String): Long {
        val number = requiredNumber(record, key, path).toDouble()
        val integer = number.toLong()
        if (number != integer.toDouble()) {
            throw IllegalArgumentException("El campo $path.$key debe ser un entero.")
        }
        return integer
    }

    private fun optionalString(record: JSONObject, key: String, path: String) {
        if (record.has(key) && !record.isNull(key)) requiredString(record, key, path)
    }

    private fun optionalBoolean(record: JSONObject, key: String, path: String) {
        if (record.has(key) && !record.isNull(key)) requiredBoolean(record, key, path)
    }

    private fun optionalNumber(record: JSONObject, key: String, path: String) {
        if (record.has(key) && !record.isNull(key)) requiredNumber(record, key, path)
    }

    private fun optionalInteger(record: JSONObject, key: String, path: String) {
        if (record.has(key) && !record.isNull(key)) requiredInteger(record, key, path)
    }


    /**
     * Importa y restaura una copia de seguridad JSON guardada previamente.
     */
    suspend fun importBackupFromJson(context: Context, db: AppDatabase, jsonString: String): Int {
        val rootJson = JSONObject(jsonString)
        validateBackupForImport(rootJson)
        val restoredCount = db.withTransaction {
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
                        autoCreate = obj.optBoolean("autoCreate", false),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                        // Older JSON backups predate configurable frequencies and remain monthly.
                        frequency = obj.optString("frequency", "MONTHLY"),
                        anchorDate = obj.optLong("anchorDate", 0L),
                        nextDueDate = obj.optLong("nextDueDate", 0L)
                    )
                )
            }
            if (recurringList.isNotEmpty()) {
                db.recurringExpenseDao().insertRecurringExpenses(recurringList)
                restoredCount += recurringList.size
            }
        }

        // Restore Businesses (optional for backwards compatibility with older JSON backups).
        // Ignore only the exact untouched placeholder inserted by old releases; user-created
        // businesses, including edited businesses with the same name, must be restored.
        if (rootJson.has("businesses")) {
            val arr = rootJson.getJSONArray("businesses")
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val business = BusinessEntity(
                    id = obj.optLong("id", 0L),
                    name = obj.optString("name", "Negocio"),
                    type = obj.optString("type", "Comercio / Ventas"),
                    description = obj.optString("description", ""),
                    colorHex = obj.optString("colorHex", "#2563EB"),
                    iconKey = obj.optString("iconKey", "storefront"),
                    inventoryValue = obj.optDouble("inventoryValue", 0.0),
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                )
                val isUntouchedLegacyPlaceholder =
                    business.id == 1L &&
                    business.name == "INSERC Store" &&
                    business.type == "Tecnología y Comercio" &&
                    business.description == "Negocio principal de servicios y venta comercial" &&
                    business.colorHex == "#2563EB" &&
                    business.iconKey == "storefront" &&
                    business.inventoryValue == 0.0 &&
                    business.createdAt == 1704067200000L

                if (!isUntouchedLegacyPlaceholder) {
                    db.businessDao().insertBusiness(business)
                    restoredCount += 1
                }
            }
        }

        restoredCount
        }
        // Restore supported app preferences only after the database transaction succeeds.
        rootJson.optJSONObject("preferences")?.let { preferences ->
            if (preferences.has("cycleStartDay")) {
                BudgetCycleManager.setStartDay(context, preferences.optInt("cycleStartDay", 1))
            }
            if (preferences.has("predictedIncome")) {
                val predictedIncome = if (preferences.isNull("predictedIncome")) {
                    null
                } else {
                    preferences.optDouble("predictedIncome").takeIf { it > 0.0 }
                }
                BudgetCycleManager.setPredictedIncome(context, predictedIncome)
            }
            if (preferences.has("currencyCode")) {
                CurrencyFormatter.setCurrency(context, preferences.optString("currencyCode", CurrencyFormatter.DEFAULT_CURRENCY_CODE))
            }
        }

        // Update the timestamp only after the whole restore transaction succeeds.
        setLastBackupTimestamp(context, System.currentTimeMillis())
        return restoredCount
    }
}
