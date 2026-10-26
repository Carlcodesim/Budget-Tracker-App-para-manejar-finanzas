package com.example.util

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R
import com.example.domain.model.Reminder
import java.util.Calendar

/**
 * Utilidad centralizada para la creación de canales de notificación y la
 * programación/cancelación y emisión de alertas de recordatorios con AlarmManager.
 */
object NotificationScheduler {

    // Canales de notificación específicos según propósito y prioridad
    const val CHANNEL_REMINDERS_HIGH_PRIORITY = "budget_reminders_high_priority_v3"
    const val CHANNEL_REMINDERS_NAME = "Recordatorios de Presupuesto y Pagos"

    const val CHANNEL_DAILY_EXPENSE_ID = "daily_expense_reminders_channel"
    const val CHANNEL_DAILY_EXPENSE_NAME = "Registro Diario de Gastos"

    const val CHANNEL_RECURRING_ID = "recurring_expenses_channel"
    const val CHANNEL_RECURRING_NAME = "Gastos Recurrentes Automáticos"

    // Alias retrocompatible
    const val CHANNEL_ID = CHANNEL_REMINDERS_HIGH_PRIORITY
    const val CHANNEL_NAME = CHANNEL_REMINDERS_NAME

    const val ACTION_SHOW_REMINDER = "com.example.ACTION_SHOW_REMINDER"
    const val ACTION_DAILY_EXPENSE_CHECK = "com.example.ACTION_DAILY_EXPENSE_CHECK"
    const val DAILY_EXPENSE_NOTIFICATION_ID = 77001
    const val DAILY_EXPENSE_ALARM_REQUEST_CODE = 77002

    const val EXTRA_REMINDER_ID = "extra_reminder_id"
    const val EXTRA_REMINDER_TITLE = "extra_reminder_title"
    const val EXTRA_REMINDER_DESC = "extra_reminder_desc"
    const val EXTRA_REMINDER_TYPE = "extra_reminder_type"
    const val EXTRA_REMINDER_AMOUNT = "extra_reminder_amount"
    const val EXTRA_DUE_DAY = "extra_due_day"
    const val EXTRA_NOTIFY_DAYS_BEFORE = "extra_notify_days_before"
    const val EXTRA_NOTIFY_TIME = "extra_notify_time"

    /**
     * Crea y registra los canales de notificación en Android 8.0 (API 26) o superior.
     * Configura el canal de recordatorios de presupuesto con alta importancia (IMPORTANCE_HIGH),
     * atributos de audio sonoros para recordatorios, vibración distintiva, luces LED y visibilidad en pantalla de bloqueo
     * siguiendo las mejores prácticas recomendadas por Google y Android.
     */
    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val reminderAudioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                .build()

            // 1. Canal de Alta Importancia para Recordatorios de Presupuesto y Pagos
            val remindersChannel = NotificationChannel(
                CHANNEL_REMINDERS_HIGH_PRIORITY,
                CHANNEL_REMINDERS_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alertas prioritarias de vencimiento de facturas, servicios pendientes y metas de ahorro del presupuesto"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 200, 250)
                enableLights(true)
                lightColor = android.graphics.Color.BLUE
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setShowBadge(true)
                setSound(defaultSoundUri, reminderAudioAttributes)
            }

            // 2. Canal de Alta Importancia para Registro Diario de Gastos (Alerta nocturna)
            val dailyExpenseChannel = NotificationChannel(
                CHANNEL_DAILY_EXPENSE_ID,
                CHANNEL_DAILY_EXPENSE_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Recordatorio nocturno para registrar los consumos y gastos diarios del presupuesto"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 200, 150, 200)
                enableLights(true)
                lightColor = android.graphics.Color.CYAN
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setShowBadge(true)
                setSound(defaultSoundUri, reminderAudioAttributes)
            }

            // 3. Canal de Gastos Recurrentes Automáticos
            val recurringChannel = NotificationChannel(
                CHANNEL_RECURRING_ID,
                CHANNEL_RECURRING_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notificaciones informativas cuando se insertan pagos recurrentes automáticos en Room"
                enableVibration(true)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                setShowBadge(true)
                setSound(defaultSoundUri, reminderAudioAttributes)
            }

            notificationManager.createNotificationChannels(
                listOf(remindersChannel, dailyExpenseChannel, recurringChannel)
            )
        }
    }

    /**
     * Alias de compatibilidad para inicializar canales.
     */
    fun createNotificationChannel(context: Context) {
        createNotificationChannels(context)
    }

    /**
     * Comprueba si las notificaciones para recordatorios de presupuesto están activas en el sistema.
     */
    fun areRemindersNotificationsEnabled(context: Context): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return true
            val channel = manager.getNotificationChannel(CHANNEL_REMINDERS_HIGH_PRIORITY)
            if (channel != null && channel.importance == NotificationManager.IMPORTANCE_NONE) {
                return false
            }
        }
        return true
    }

    /**
     * Abre la pantalla de configuración del canal de notificación en los ajustes de Android.
     */
    fun openReminderChannelSettings(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    putExtra(Settings.EXTRA_CHANNEL_ID, CHANNEL_REMINDERS_HIGH_PRIORITY)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } else {
                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Emite una notificación real en la barra de estado del dispositivo de forma inmediata.
     */
    fun showNotification(
        context: Context,
        reminderId: Long,
        title: String,
        contentText: String,
        type: String = "",
        amount: Double? = null
    ) {
        createNotificationChannel(context)

        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("NAV_DESTINATION", "REMINDERS")
            putExtra("REMINDER_ID", reminderId)
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            reminderId.toInt(),
            mainIntent,
            flags
        )

        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_reminder)
            .setContentTitle("🔔 $title")
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSound(defaultSoundUri)
            .setVibrate(longArrayOf(0, 250, 250, 250))
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        try {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val targetNotificationId = if (reminderId > 0) reminderId.toInt() else (System.currentTimeMillis() % 100000).toInt()
            notificationManager.notify(targetNotificationId, builder.build())
        } catch (e: SecurityException) {
            // Falta permiso POST_NOTIFICATIONS en Android 13+
            e.printStackTrace()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Emite una notificación del sistema cuando se procesan e insertan gastos recurrentes
     * automáticamente en la base de datos Room.
     */
    fun showRecurringExpensesProcessedNotification(
        context: Context,
        count: Int,
        totalAmount: Double,
        firstExpenseTitle: String? = null
    ) {
        createNotificationChannel(context)

        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("NAV_DESTINATION", "RECURRING_EXPENSES")
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            99988,
            mainIntent,
            flags
        )

        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val formattedAmount = com.example.presentation.common.util.CurrencyFormatter.format(totalAmount)

        val title = if (count == 1) "Gasto Recurrente Registrado" else "$count Gastos Recurrentes Registrados"
        val message = if (count == 1 && !firstExpenseTitle.isNullOrBlank()) {
            "Se ha insertado automáticamente $firstExpenseTitle ($formattedAmount) en tus transacciones del mes."
        } else {
            "Se han generado $count transacciones automáticas este mes por un total de $formattedAmount."
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_RECURRING_ID)
            .setSmallIcon(R.drawable.ic_notification_reminder)
            .setContentTitle("🔄 $title")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSound(defaultSoundUri)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        try {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(88991, builder.build())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Programa una alarma para notificar al usuario antes de la fecha de pago.
     */
    fun scheduleReminder(context: Context, reminder: Reminder) {
        if (!reminder.isEnabled) {
            cancelReminder(context, reminder.id)
            return
        }

        createNotificationChannel(context)

        val triggerAtMillis = calculateNextTriggerTimeMillis(
            dueDateDay = reminder.dueDateDay,
            notifyDaysBefore = reminder.notifyDaysBefore,
            notifyTime = reminder.notifyTime ?: reminder.time ?: "09:00",
            isCompletedThisMonth = reminder.isCompletedThisMonth
        ) ?: return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, ReminderNotificationReceiver::class.java).apply {
            action = ACTION_SHOW_REMINDER
            data = android.net.Uri.parse("reminder://${reminder.id}")
            putExtra(EXTRA_REMINDER_ID, reminder.id)
            putExtra(EXTRA_REMINDER_TITLE, reminder.title)
            putExtra(EXTRA_REMINDER_DESC, reminder.description)
            putExtra(EXTRA_REMINDER_TYPE, reminder.type)
            reminder.amount?.let { putExtra(EXTRA_REMINDER_AMOUNT, it) }
            reminder.dueDateDay?.let { putExtra(EXTRA_DUE_DAY, it) }
            putExtra(EXTRA_NOTIFY_DAYS_BEFORE, reminder.notifyDaysBefore)
            putExtra(EXTRA_NOTIFY_TIME, reminder.notifyTime ?: reminder.time ?: "09:00")
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminder.id.toInt(),
            intent,
            flags
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } else {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
        } catch (e: SecurityException) {
            try {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } catch (ex: Exception) {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
        } catch (e: Exception) {
            try {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }
    }

    /**
     * Programa todos los recordatorios activos en el sistema.
     */
    fun scheduleAllActiveReminders(context: Context, reminders: List<Reminder>) {
        reminders.filter { it.isEnabled }.forEach { reminder ->
            scheduleReminder(context, reminder)
        }
    }

    /**
     * Cancela una alarma previamente registrada para un recordatorio.
     */
    fun cancelReminder(context: Context, reminderId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderNotificationReceiver::class.java).apply {
            action = ACTION_SHOW_REMINDER
            data = android.net.Uri.parse("reminder://${reminderId}")
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_NO_CREATE
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminderId.toInt(),
            intent,
            flags
        )

        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    /**
     * Calcula la fecha y hora exacta en milisegundos para disparar la próxima notificación.
     * Siempre calcula un instante estrictamente futuro, evitando bucles repetitivos y falsas alertas.
     */
    fun calculateNextTriggerTimeMillis(
        dueDateDay: Int?,
        notifyDaysBefore: Int,
        notifyTime: String,
        isCompletedThisMonth: Boolean = false,
        nowMillis: Long = System.currentTimeMillis()
    ): Long? {
        val (hour, minute) = parseHourAndMinute(notifyTime)
        val nowCal = Calendar.getInstance().apply { timeInMillis = nowMillis }

        if (dueDateDay == null) {
            // Recordatorio recurrente diario (ej. Registrar gastos de la jornada)
            val dailyCal = (nowCal.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (dailyCal.timeInMillis <= nowMillis) {
                dailyCal.add(Calendar.DAY_OF_MONTH, 1)
            }
            return dailyCal.timeInMillis
        }

        // Recordatorio mensual con fecha de vencimiento
        val safeDueDay = dueDateDay.coerceIn(1, 31)
        val daysBefore = notifyDaysBefore.coerceAtLeast(0)

        fun createCalendarFor(year: Int, month: Int, dayOffsetFromDue: Int): Calendar {
            return Calendar.getInstance().apply {
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month)
                set(Calendar.DAY_OF_MONTH, 1)
                val maxDays = getActualMaximum(Calendar.DAY_OF_MONTH)
                val dueDayInMonth = safeDueDay.coerceAtMost(maxDays)
                val targetDay = (dueDayInMonth - dayOffsetFromDue).coerceAtLeast(1)
                set(Calendar.DAY_OF_MONTH, targetDay)
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
        }

        val currentYear = nowCal.get(Calendar.YEAR)
        val currentMonth = nowCal.get(Calendar.MONTH)

        // Si ya se completó el pago este mes, programar directamente para el próximo ciclo mensual
        if (isCompletedThisMonth) {
            val nextMonthCal = (nowCal.clone() as Calendar).apply { add(Calendar.MONTH, 1) }
            val nextYear = nextMonthCal.get(Calendar.YEAR)
            val nextMonth = nextMonthCal.get(Calendar.MONTH)
            val targetOffset = if (daysBefore > 0) daysBefore else 0
            val nextCycleCal = createCalendarFor(nextYear, nextMonth, targetOffset)
            return nextCycleCal.timeInMillis
        }

        // Evaluación dentro del ciclo del mes actual:
        // Oportunidad 1: Fecha de aviso previo en el mes actual (si daysBefore > 0)
        if (daysBefore > 0) {
            val advanceNoticeCal = createCalendarFor(currentYear, currentMonth, daysBefore)
            if (advanceNoticeCal.timeInMillis > nowMillis) {
                return advanceNoticeCal.timeInMillis
            }
        }

        // Oportunidad 2: Día de vencimiento exacto en el mes actual
        val dueDayCal = createCalendarFor(currentYear, currentMonth, 0)
        if (dueDayCal.timeInMillis > nowMillis) {
            return dueDayCal.timeInMillis
        }

        // Oportunidad 3: El aviso o vencimiento de este mes ya pasó su hora hoy o en días pasados.
        // Si el mes aún no termina y sigue pendiente: avisar mañana a la hora indicada
        val tomorrowCal = (nowCal.clone() as Calendar).apply {
            add(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (tomorrowCal.get(Calendar.MONTH) == currentMonth) {
            return tomorrowCal.timeInMillis
        }

        // Si el mes ya concluyó, pasar al ciclo del próximo mes
        val nextMonthCal = (nowCal.clone() as Calendar).apply { add(Calendar.MONTH, 1) }
        val nextYear = nextMonthCal.get(Calendar.YEAR)
        val nextMonth = nextMonthCal.get(Calendar.MONTH)
        val targetOffset = if (daysBefore > 0) daysBefore else 0
        val nextCycleCal = createCalendarFor(nextYear, nextMonth, targetOffset)
        return nextCycleCal.timeInMillis
    }

    fun buildReminderContentText(
        type: String,
        title: String,
        amount: Double?,
        notifyDaysBefore: Int
    ): String {
        val daysText = when (notifyDaysBefore) {
            0 -> "hoy es la fecha de vencimiento"
            1 -> "vence mañana (en 1 día)"
            else -> "vence en $notifyDaysBefore días"
        }

        val amountText = if (amount != null && amount > 0) " por un monto de ${com.example.presentation.common.util.CurrencyFormatter.format(amount)}" else ""
        return when (type) {
            "REGISTRO_GASTOS" -> "¡Hora de registrar tus consumos de hoy! Mantén tu presupuesto al día."
            "META_AHORRO" -> "Recordatorio de aporte a meta de ahorro: $title$amountText."
            else -> "Recordatorio de pago: $title$amountText ($daysText). ¡Evita recargos!"
        }
    }

    /**
     * Emite la notificación recordatorio de fin de jornada invitando al usuario
     * a registrar sus gastos del día si no ha ingresado ninguna transacción.
     */
     fun showDailyExpenseReminderNotification(context: Context) {
        createNotificationChannel(context)

        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("NAV_DESTINATION", "ADD_TRANSACTION")
            putExtra("INITIAL_TYPE", "EXPENSE")
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            DAILY_EXPENSE_NOTIFICATION_ID,
            mainIntent,
            flags
        )

        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val title = "📝 ¿Olvidaste anotar tus gastos de hoy?"
        val contentText = "Aún no has registrado transacciones hoy. Mantén tus finanzas al día anotando tus gastos de la jornada."

        val builder = NotificationCompat.Builder(context, CHANNEL_DAILY_EXPENSE_ID)
            .setSmallIcon(R.drawable.ic_notification_reminder)
            .setContentTitle(title)
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSound(defaultSoundUri)
            .setVibrate(longArrayOf(0, 250, 250, 250))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        try {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(DAILY_EXPENSE_NOTIFICATION_ID, builder.build())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Programa la alarma diaria con AlarmManager a las 20:30 hrs para comprobar
     * si se registraron transacciones durante la jornada.
     */
    fun scheduleDailyExpenseReminder(context: Context, hour: Int = 20, minute: Int = 30) {
        try {
            createNotificationChannel(context)
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            val intent = Intent(context, DailyExpenseReminderReceiver::class.java).apply {
                action = ACTION_DAILY_EXPENSE_CHECK
            }

            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                DAILY_EXPENSE_ALARM_REQUEST_CODE,
                intent,
                flags
            )

            val calendar = Calendar.getInstance().apply {
                timeInMillis = System.currentTimeMillis()
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)

                // Si la hora de hoy ya pasó, programar para mañana a la misma hora
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
     * Cancela la alarma del recordatorio diario de gastos si es deshabilitado.
     */
    fun cancelDailyExpenseReminder(context: Context) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, DailyExpenseReminderReceiver::class.java).apply {
                action = ACTION_DAILY_EXPENSE_CHECK
            }

            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_NO_CREATE
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                DAILY_EXPENSE_ALARM_REQUEST_CODE,
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

    fun parseHourAndMinute(timeStr: String): Pair<Int, Int> {
        return try {
            val cleaned = timeStr.trim().uppercase()
            val isPm = cleaned.contains("PM")
            val isAm = cleaned.contains("AM")
            val numbers = cleaned.replace("AM", "").replace("PM", "").trim().split(":")

            var hour = numbers[0].trim().toInt()
            val minute = if (numbers.size > 1) numbers[1].trim().toInt() else 0

            if (isPm && hour < 12) hour += 12
            if (isAm && hour == 12) hour = 0

            Pair(hour, minute)
        } catch (e: Exception) {
            Pair(9, 0) // Default 9:00 AM
        }
    }
}

