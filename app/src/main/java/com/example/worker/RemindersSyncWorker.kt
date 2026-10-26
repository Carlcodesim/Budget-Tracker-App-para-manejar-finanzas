package com.example.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.BudgetTrackerApp
import com.example.util.NotificationScheduler
import com.example.util.ReminderNotificationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Worker en segundo plano ejecutado periódicamente por WorkManager para garantizar
 * que los recordatorios de pago se evalúen y emitan notificaciones confiables en el sistema,
 * incluso si la aplicación está cerrada o el dispositivo estuvo en modo reposo (Doze).
 */
class RemindersSyncWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val app = context.applicationContext as? BudgetTrackerApp ?: return@withContext Result.success()
            val reminderRepository = app.appContainer.reminderRepository
            val allReminders = reminderRepository.getAllReminders().first()

            val nowCal = Calendar.getInstance()
            val currentYear = nowCal.get(Calendar.YEAR)
            val currentMonth = nowCal.get(Calendar.MONTH)
            val currentDayOfMonth = nowCal.get(Calendar.DAY_OF_MONTH)
            val currentHour = nowCal.get(Calendar.HOUR_OF_DAY)
            val currentMinute = nowCal.get(Calendar.MINUTE)
            val currentTimeMinutes = currentHour * 60 + currentMinute

            for (reminder in allReminders) {
                if (!reminder.isEnabled) continue
                if (reminder.isCompletedThisMonth) continue

                // Si ya fue notificado hoy, evitar duplicados
                if (ReminderNotificationManager.hasAlreadyNotifiedToday(context, reminder.id)) {
                    continue
                }

                val notifyTime = reminder.notifyTime ?: reminder.time ?: "09:00"
                val (targetHour, targetMinute) = NotificationScheduler.parseHourAndMinute(notifyTime)
                val targetTimeMinutes = targetHour * 60 + targetMinute

                var shouldNotifyToday = false

                if (reminder.dueDateDay == null) {
                    // Recordatorio diario (ej. registrar gastos a las 20:00)
                    if (currentTimeMinutes >= targetTimeMinutes) {
                        shouldNotifyToday = true
                    }
                } else {
                    val safeDueDay = reminder.dueDateDay.coerceIn(1, 31)
                    val daysBefore = reminder.notifyDaysBefore.coerceAtLeast(0)
                    val advanceNoticeDay = (safeDueDay - daysBefore).coerceAtLeast(1)

                    // ¿Hoy corresponde a la fecha de aviso anticipado o a la fecha de vencimiento?
                    val isAdvanceNoticeDay = (currentDayOfMonth == advanceNoticeDay && daysBefore > 0)
                    val isDueDay = (currentDayOfMonth == safeDueDay)
                    val isOverdueDay = (currentDayOfMonth > safeDueDay)

                    if ((isAdvanceNoticeDay || isDueDay || isOverdueDay) && currentTimeMinutes >= targetTimeMinutes) {
                        shouldNotifyToday = true
                    }
                }

                if (shouldNotifyToday) {
                    val contentText = NotificationScheduler.buildReminderContentText(
                        type = reminder.type,
                        title = reminder.title,
                        amount = reminder.amount,
                        notifyDaysBefore = reminder.notifyDaysBefore
                    )

                    NotificationScheduler.showNotification(
                        context = context,
                        reminderId = reminder.id,
                        title = reminder.title,
                        contentText = contentText,
                        type = reminder.type,
                        amount = reminder.amount
                    )

                    ReminderNotificationManager.recordNotifiedToday(context, reminder.id)
                }

                // Asegurar que la alarma exacta en AlarmManager esté correctamente sincronizada
                NotificationScheduler.scheduleReminder(context, reminder)
            }

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }

    companion object {
        private const val WORK_TAG = "reminders_background_sync_work"

        /**
         * Registra la comprobación periódica en segundo plano mediante WorkManager.
         */
        fun enqueue(context: Context) {
            try {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                    .build()

                val workRequest = PeriodicWorkRequestBuilder<RemindersSyncWorker>(
                    repeatInterval = 6,
                    repeatIntervalTimeUnit = TimeUnit.HOURS,
                    flexTimeInterval = 30,
                    flexTimeIntervalUnit = TimeUnit.MINUTES
                )
                    .setConstraints(constraints)
                    .addTag(WORK_TAG)
                    .build()

                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    WORK_TAG,
                    ExistingPeriodicWorkPolicy.KEEP,
                    workRequest
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
