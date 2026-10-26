package com.example.util

import android.content.Context
import android.content.SharedPreferences

/**
 * Gestor de preferencias para la notificación de recordatorio diario de fin de jornada.
 */
object DailyReminderManager {
    private const val PREFS_NAME = "budget_tracker_daily_reminders"
    private const val KEY_DAILY_REMINDER_ENABLED = "key_daily_reminder_enabled"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isDailyReminderEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_DAILY_REMINDER_ENABLED, true)
    }

    fun setDailyReminderEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_DAILY_REMINDER_ENABLED, enabled).apply()
        if (enabled) {
            NotificationScheduler.scheduleDailyExpenseReminder(context)
        } else {
            NotificationScheduler.cancelDailyExpenseReminder(context)
        }
    }
}
