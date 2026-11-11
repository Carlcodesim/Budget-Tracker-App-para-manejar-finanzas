package com.example

import android.app.Application
import com.example.di.AppContainer
import com.example.data.recovery.DatabasePreflightInspector
import com.example.data.recovery.DatabaseStartupState
import com.example.data.recovery.DatabaseCondition
import com.example.data.recovery.DatabasePreflightResult
import com.example.data.recovery.ActiveDatabaseCopyRegistry
import com.example.data.recovery.DatabaseAccessGate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * App bootstrap remains database-free. Database-dependent observers and jobs
 * are started only after StrictRecoveryCoordinator has granted normal access.
 */
class BudgetTrackerApp : Application() {
    lateinit var appContainer: AppContainer

    @Volatile
    private var databaseServicesStarted = false

    override fun onCreate() {
        super.onCreate()

        val startupInspection = when (val source = ActiveDatabaseCopyRegistry.sourceForNextValidation(this)) {
            is ActiveDatabaseCopyRegistry.Source.LegacyOriginal ->
                DatabasePreflightInspector.inspect(source.file)
            is ActiveDatabaseCopyRegistry.Source.PreviouslyActivatedCopy ->
                DatabasePreflightInspector.inspect(source.file)
            is ActiveDatabaseCopyRegistry.Source.Rejected ->
                DatabasePreflightResult(
                    condition = DatabaseCondition.IO_ERROR,
                    reasonCode = source.reasonCode,
                    message = "No se pudo identificar con seguridad la última base activa. El acceso permanece bloqueado."
                )
        }
        DatabaseStartupState.publish(startupInspection)

        // AppContainer is safe to construct: its database is lazy and no repository is read here.
        appContainer = AppContainer(this)
        com.example.presentation.common.util.CurrencyFormatter.init(this)
        com.example.util.BudgetCycleManager.init(this)
    }

    /**
     * Call only after a direct user confirmation has enabled DatabaseAccessGate.
     * This restores the previous observers, reminders, widgets and recurring jobs
     * without allowing WorkManager/receivers to open Room during quarantine.
     */
    @Synchronized
    fun startDatabaseDependentServicesAfterAuthorization() {
        if (!DatabaseAccessGate.isNormalAccessAllowed() || databaseServicesStarted) return
        databaseServicesStarted = true

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val container = appContainer

        try {
            com.example.util.NotificationScheduler.createNotificationChannels(this)
        } catch (_: Exception) {
            // Notifications can be initialized again in a later process.
        }

        scope.launch {
            try {
                container.getTransactionsUseCase().collect {
                    com.example.presentation.widget.BalanceWidgetProvider.updateWidget(this@BudgetTrackerApp)
                }
            } catch (_: Exception) {
                // Widget refresh is non-critical; financial records remain untouched.
            }
        }

        scope.launch {
            try {
                container.reminderRepository.getAllReminders().collect { reminders ->
                    com.example.util.NotificationScheduler.scheduleAllActiveReminders(this@BudgetTrackerApp, reminders)
                }
            } catch (_: Exception) {
                // Reminder observation is isolated from database recovery/authorization.
            }
        }

        try {
            com.example.util.RecurringExpensesScheduler.scheduleAll(this)
            com.example.util.NotificationScheduler.scheduleDailyExpenseReminder(this)
            com.example.worker.RemindersSyncWorker.enqueue(this)
        } catch (_: Exception) {
            // Background entry points remain individually protected by DatabaseAccessGate.
        }
    }
}
