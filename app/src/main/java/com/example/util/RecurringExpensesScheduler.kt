package com.example.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.worker.RecurringExpensesWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Gestor centralizado de programación y ejecución en segundo plano para gastos recurrentes.
 * Combina WorkManager (ejecución periódica garantizada por el SO) con AlarmManager
 * (disparo diario a hora fija) para asegurar que los gastos programados se inserten
 * automáticamente en la base de datos Room sin intervención del usuario.
 */
object RecurringExpensesScheduler {

    private const val PERIODIC_WORK_TAG = "recurring_expenses_periodic_work"
    private const val IMMEDIATE_WORK_TAG = "recurring_expenses_immediate_check"
    const val ACTION_PROCESS_RECURRING_ALARM = "com.example.ACTION_PROCESS_RECURRING_ALARM"
    private const val ALARM_REQUEST_CODE = 90210

    /**
     * Programa todos los mecanismos de background worker y alarma periódica.
     */
    fun scheduleAll(context: Context) {
        schedulePeriodicWork(context)
        scheduleDailyAlarm(context)
        runImmediateCheck(context)
    }

    /**
     * Encola un trabajo periódico con WorkManager que se ejecuta cada 24 horas.
     */
    fun schedulePeriodicWork(context: Context) {
        try {
            val constraints = Constraints.Builder()
                .build()

            val periodicWorkRequest = PeriodicWorkRequestBuilder<RecurringExpensesWorker>(
                repeatInterval = 1,
                repeatIntervalTimeUnit = TimeUnit.DAYS,
                flexTimeInterval = 3,
                flexTimeIntervalUnit = TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .addTag(PERIODIC_WORK_TAG)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_TAG,
                ExistingPeriodicWorkPolicy.KEEP,
                periodicWorkRequest
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Ejecuta una comprobación inmediata de gastos recurrentes pendientes de forma asíncrona
     * en segundo plano, evitando sobrecargar el JobScheduler del sistema al arrancar.
     */
    fun runImmediateCheck(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as? com.example.BudgetTrackerApp
                val appContainer = app?.appContainer ?: com.example.di.AppContainer(context)
                val processResult = appContainer.processRecurringExpensesUseCase(
                    targetYearMonth = null,
                    forceAllDue = false
                )

                if (processResult.executedCount > 0) {
                    NotificationScheduler.showRecurringExpensesProcessedNotification(
                        context = context,
                        count = processResult.executedCount,
                        totalAmount = processResult.totalAmount,
                        firstExpenseTitle = processResult.createdTransactions.firstOrNull()?.description
                    )
                    com.example.presentation.widget.BalanceWidgetProvider.updateWidget(context)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Programa una alarma diaria con AlarmManager a las 00:05 AM (medianoche) o a la mañana
     * para asegurar el procesamiento justo al comenzar el día de cobro.
     */
    fun scheduleDailyAlarm(context: Context) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            val intent = Intent(context, RecurringExpenseAlarmReceiver::class.java).apply {
                action = ACTION_PROCESS_RECURRING_ALARM
            }

            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                ALARM_REQUEST_CODE,
                intent,
                flags
            )

            // Configurar alarma para las 00:05 AM de la siguiente fecha
            val calendar = Calendar.getInstance().apply {
                timeInMillis = System.currentTimeMillis()
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 5)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)

                // Si las 00:05 de hoy ya pasaron, programar para mañana
                if (timeInMillis <= System.currentTimeMillis()) {
                    add(Calendar.DAY_OF_MONTH, 1)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            } else {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Cancela la alarma diaria si es necesario.
     */
    fun cancelDailyAlarm(context: Context) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, RecurringExpenseAlarmReceiver::class.java).apply {
                action = ACTION_PROCESS_RECURRING_ALARM
            }

            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_NO_CREATE
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                ALARM_REQUEST_CODE,
                intent,
                flags
            )

            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
