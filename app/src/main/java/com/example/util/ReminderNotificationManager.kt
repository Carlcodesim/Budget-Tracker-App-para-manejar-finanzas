package com.example.util

import android.content.Context
import android.content.SharedPreferences
import com.example.domain.model.Reminder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Gestor centralizado de control de frecuencia y estado de notificaciones para los recordatorios.
 * Previene el spam y notificaciones repetitivas asegurando que cada recordatorio solo emita
 * una alerta por día o por evento, tanto dentro de la app como en segundo plano.
 */
object ReminderNotificationManager {

    private const val PREFS_NAME = "budget_tracker_reminder_prefs"
    private const val KEY_LAST_NOTIFIED_DATE_PREFIX = "last_notified_date_"
    private const val KEY_LAST_NOTIFIED_TS_PREFIX = "last_notified_ts_"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun getTodayDateString(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return sdf.format(Date())
    }

    /**
     * Comprueba si un recordatorio ya notificó al usuario en el día de hoy,
     * garantizando que nunca se envíen alertas duplicadas o continuas.
     */
    fun hasAlreadyNotifiedToday(context: Context, reminderId: Long): Boolean {
        val prefs = getPrefs(context)
        val lastDate = prefs.getString("$KEY_LAST_NOTIFIED_DATE_PREFIX$reminderId", null)
        return lastDate == getTodayDateString()
    }

    /**
     * Registra que un recordatorio fue notificado exitosamente en la fecha actual.
     */
    fun recordNotifiedToday(context: Context, reminderId: Long) {
        val prefs = getPrefs(context)
        prefs.edit()
            .putString("$KEY_LAST_NOTIFIED_DATE_PREFIX$reminderId", getTodayDateString())
            .putLong("$KEY_LAST_NOTIFIED_TS_PREFIX$reminderId", System.currentTimeMillis())
            .apply()
    }

    /**
     * Obtiene la marca de tiempo de la última notificación emitida para un recordatorio.
     */
    fun getLastNotifiedTimestamp(context: Context, reminderId: Long): Long? {
        val ts = getPrefs(context).getLong("$KEY_LAST_NOTIFIED_TS_PREFIX$reminderId", 0L)
        return if (ts > 0L) ts else null
    }

    /**
     * Limpia el registro de notificación de un recordatorio específico (ej. al editar fecha u hora).
     */
    fun clearReminderHistory(context: Context, reminderId: Long) {
        val prefs = getPrefs(context)
        prefs.edit()
            .remove("$KEY_LAST_NOTIFIED_DATE_PREFIX$reminderId")
            .remove("$KEY_LAST_NOTIFIED_TS_PREFIX$reminderId")
            .apply()
    }

    /**
     * Limpia todo el historial de notificaciones (ej. al reiniciar el mes de demostración).
     */
    fun clearAllHistory(context: Context) {
        getPrefs(context).edit().clear().apply()
    }

    /**
     * Genera una descripción legible para el usuario sobre cuándo será la próxima alerta del recordatorio.
     */
    fun getScheduleStatusText(context: Context, reminder: Reminder): String {
        if (!reminder.isEnabled) {
            return "Desactivado (sin notificaciones)"
        }

        if (reminder.isCompletedThisMonth) {
            return "Completado este mes • Próxima alerta el mes próximo"
        }

        if (hasAlreadyNotifiedToday(context, reminder.id)) {
            val notifyTimeStr = reminder.notifyTime ?: reminder.time ?: "09:00"
            return "Alerta enviada hoy a las $notifyTimeStr"
        }

        val nextTriggerMillis = NotificationScheduler.calculateNextTriggerTimeMillis(
            dueDateDay = reminder.dueDateDay,
            notifyDaysBefore = reminder.notifyDaysBefore,
            notifyTime = reminder.notifyTime ?: reminder.time ?: "09:00",
            isCompletedThisMonth = reminder.isCompletedThisMonth
        ) ?: return "Sin horario programado"

        val nowCal = Calendar.getInstance()
        val triggerCal = Calendar.getInstance().apply { timeInMillis = nextTriggerMillis }

        val sameYear = nowCal.get(Calendar.YEAR) == triggerCal.get(Calendar.YEAR)
        val sameDay = sameYear && nowCal.get(Calendar.DAY_OF_YEAR) == triggerCal.get(Calendar.DAY_OF_YEAR)

        val tomorrowCal = (nowCal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
        val isTomorrow = sameYear && tomorrowCal.get(Calendar.DAY_OF_YEAR) == triggerCal.get(Calendar.DAY_OF_YEAR)

        val timeFormatter = SimpleDateFormat("hh:mm a", Locale.forLanguageTag("es-ES"))
        val formattedTime = timeFormatter.format(triggerCal.time)

        return when {
            sameDay -> "Próxima alerta: Hoy a las $formattedTime"
            isTomorrow -> "Próxima alerta: Mañana a las $formattedTime"
            else -> {
                val dateFormatter = SimpleDateFormat("d 'de' MMM, hh:mm a", Locale.forLanguageTag("es-ES"))
                "Próxima alerta: ${dateFormatter.format(triggerCal.time)}"
            }
        }
    }
}
