package com.example.presentation.reminders

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.MainActivity
import com.example.domain.model.Reminder
import com.example.domain.model.Transaction
import com.example.domain.model.TransactionType
import com.example.domain.repository.ReminderRepository
import com.example.domain.usecase.category.GetCategoriesUseCase
import com.example.domain.usecase.goal.ContributeToGoalUseCase
import com.example.domain.usecase.goal.GetSavingsGoalsUseCase
import com.example.domain.usecase.transaction.AddTransactionUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * ViewModel para gestionar los recordatorios financieros de la aplicación,
 * coordinando la persistencia de estados, creación de transacciones asociadas y simulaciones de notificaciones.
 */
class RemindersViewModel(
    private val reminderRepository: ReminderRepository,
    private val getSavingsGoalsUseCase: GetSavingsGoalsUseCase,
    private val getCategoriesUseCase: GetCategoriesUseCase,
    private val addTransactionUseCase: AddTransactionUseCase,
    private val contributeToGoalUseCase: ContributeToGoalUseCase
) : ViewModel() {

    private val _messageState = MutableStateFlow<Triple<String?, String?, SimulatedNotification?>>(Triple(null, null, null))

    val uiState: StateFlow<RemindersUiState> = combine(
        reminderRepository.getAllReminders(),
        getSavingsGoalsUseCase(),
        getCategoriesUseCase(),
        _messageState
    ) { reminders, goals, categories, messages ->
        RemindersUiState.Success(
            reminders = reminders,
            savingsGoals = goals,
            categories = categories,
            successMessage = messages.first,
            errorMessage = messages.second,
            simulatedNotification = messages.third
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = RemindersUiState.Loading
    )

    fun toggleReminder(reminder: Reminder, context: Context? = null) {
        viewModelScope.launch {
            try {
                val updated = reminder.copy(isEnabled = !reminder.isEnabled)
                reminderRepository.updateReminder(updated)
                context?.let { ctx ->
                    if (updated.isEnabled) {
                        com.example.util.NotificationScheduler.scheduleReminder(ctx, updated)
                    } else {
                        com.example.util.NotificationScheduler.cancelReminder(ctx, updated.id)
                    }
                }
                val statusMsg = if (updated.isEnabled) "activado y notificaciones programadas" else "desactivado"
                _messageState.update { it.copy(first = "Recordatorio '${reminder.title}' $statusMsg") }
            } catch (e: Exception) {
                _messageState.update { it.copy(second = "Error al actualizar recordatorio") }
            }
        }
    }

    fun updateReminderDetails(
        reminder: Reminder,
        title: String,
        description: String,
        amount: Double?,
        dueDateDay: Int?,
        time: String?,
        notifyDaysBefore: Int = 1,
        notifyTime: String = "09:00",
        associatedGoalId: Long?,
        context: Context? = null
    ) {
        viewModelScope.launch {
            try {
                val updated = reminder.copy(
                    title = title,
                    description = description,
                    amount = amount,
                    dueDateDay = dueDateDay,
                    time = time,
                    notifyDaysBefore = notifyDaysBefore,
                    notifyTime = notifyTime,
                    associatedGoalId = associatedGoalId
                )
                reminderRepository.updateReminder(updated)
                context?.let { ctx ->
                    com.example.util.ReminderNotificationManager.clearReminderHistory(ctx, reminder.id)
                    com.example.util.NotificationScheduler.scheduleReminder(ctx, updated)
                }
                _messageState.update { it.copy(first = "Recordatorio actualizado y notificación programada") }
            } catch (e: Exception) {
                _messageState.update { it.copy(second = "Error al actualizar detalles") }
            }
        }
    }

    fun addNewReminder(
        title: String,
        description: String,
        type: String,
        amount: Double?,
        dueDateDay: Int?,
        time: String?,
        notifyDaysBefore: Int = 1,
        notifyTime: String = "09:00",
        associatedGoalId: Long?,
        context: Context? = null
    ) {
        viewModelScope.launch {
            try {
                val newReminder = Reminder(
                    title = title,
                    description = description,
                    isEnabled = true,
                    time = time,
                    type = type,
                    amount = amount,
                    dueDateDay = dueDateDay,
                    notifyDaysBefore = notifyDaysBefore,
                    notifyTime = notifyTime,
                    associatedGoalId = associatedGoalId,
                    isCompletedThisMonth = false
                )
                val id = reminderRepository.insertReminder(newReminder)
                val savedReminder = newReminder.copy(id = id)
                context?.let { ctx ->
                    com.example.util.NotificationScheduler.scheduleReminder(ctx, savedReminder)
                }
                _messageState.update { it.copy(first = "Nuevo recordatorio '$title' creado y notificación programada") }
            } catch (e: Exception) {
                _messageState.update { it.copy(second = "Error al crear recordatorio") }
            }
        }
    }

    private var lastDeletedReminder: Reminder? = null
    private val reminderCompletionMutex = Mutex()

    fun deleteReminder(reminder: Reminder, context: Context? = null) {
        lastDeletedReminder = reminder
        viewModelScope.launch {
            try {
                reminderRepository.deleteReminder(reminder)
                context?.let { ctx ->
                    com.example.util.NotificationScheduler.cancelReminder(ctx, reminder.id)
                }
            } catch (e: Exception) {
                _messageState.update { it.copy(second = "Error al eliminar recordatorio") }
            }
        }
    }

    fun undoDeleteReminder(context: Context? = null) {
        val reminder = lastDeletedReminder ?: return
        viewModelScope.launch {
            try {
                reminderRepository.insertReminder(reminder)
                context?.let { ctx ->
                    com.example.util.NotificationScheduler.scheduleReminder(ctx, reminder)
                }
                lastDeletedReminder = null
                _messageState.update { it.copy(first = "Recordatorio '${reminder.title}' restaurado con éxito") }
            } catch (e: Exception) {
                _messageState.update { it.copy(second = "Error al restaurar recordatorio") }
            }
        }
    }

    fun completeReminder(reminder: Reminder, selectedCategoryId: Long?, context: Context? = null) {
        viewModelScope.launch {
            reminderCompletionMutex.withLock {
                try {
                    // Consultar el estado persistido para no registrar dos veces el mismo recordatorio.
                    val currentReminder = reminderRepository.getReminderById(reminder.id) ?: return@withLock
                    if (currentReminder.isCompletedThisMonth) {
                        _messageState.update {
                            it.copy(first = "Este recordatorio ya está completado este mes; no se registró otro pago.")
                        }
                        return@withLock
                    }

                    val updated = currentReminder.copy(isCompletedThisMonth = true)
                    reminderRepository.updateReminder(updated)
                    context?.let { ctx ->
                        com.example.util.NotificationScheduler.scheduleReminder(ctx, updated)
                    }

                    var actionDetails = ""
                    if (currentReminder.amount != null && currentReminder.amount > 0) {
                        when (currentReminder.type) {
                            "PAGO_UNIVERSIDAD" -> {
                                val catId = selectedCategoryId ?: 6L
                                val tx = Transaction(
                                    amount = currentReminder.amount,
                                    type = TransactionType.EXPENSE,
                                    category = com.example.domain.model.Category(id = catId, name = "Educación", iconKey = "school", isDefault = true),
                                    description = "Pago automático: ${currentReminder.title}",
                                    date = java.util.Date()
                                )
                                addTransactionUseCase(tx)
                                actionDetails = " Se registró un gasto de ${com.example.presentation.common.util.CurrencyFormatter.format(currentReminder.amount)} en Educación."
                            }
                            "PAGO_SERVICIOS" -> {
                                val catId = selectedCategoryId ?: 7L
                                val tx = Transaction(
                                    amount = currentReminder.amount,
                                    type = TransactionType.EXPENSE,
                                    category = com.example.domain.model.Category(id = catId, name = "Servicios", iconKey = "receipt", isDefault = true),
                                    description = "Pago automático: ${currentReminder.title}",
                                    date = java.util.Date()
                                )
                                addTransactionUseCase(tx)
                                actionDetails = " Se registró un gasto de ${com.example.presentation.common.util.CurrencyFormatter.format(currentReminder.amount)} en Servicios."
                            }
                            "META_AHORRO" -> {
                                currentReminder.associatedGoalId?.let { goalId ->
                                    contributeToGoalUseCase(goalId, currentReminder.amount)
                                    val catId = selectedCategoryId ?: 8L
                                    val tx = Transaction(
                                        amount = currentReminder.amount,
                                        type = TransactionType.EXPENSE,
                                        category = com.example.domain.model.Category(id = catId, name = "Ahorro", iconKey = "savings", isDefault = true),
                                        description = "Aporte a meta: ${currentReminder.title}",
                                        date = java.util.Date()
                                    )
                                    addTransactionUseCase(tx)
                                    actionDetails = " Se aportaron ${com.example.presentation.common.util.CurrencyFormatter.format(currentReminder.amount)} a tu meta de ahorro."
                                }
                            }
                        }
                    }

                    _messageState.update {
                        it.copy(first = "¡Recordatorio '${currentReminder.title}' completado este mes!$actionDetails")
                    }
                } catch (e: Exception) {
                    _messageState.update { it.copy(second = "Error al completar recordatorio: ${e.localizedMessage}") }
                }
            }
        }
    }

    fun resetAllMonthlyReminders(context: Context? = null) {
        viewModelScope.launch {
            try {
                val state = uiState.value
                if (state is RemindersUiState.Success) {
                    state.reminders.forEach { reminder ->
                        if (reminder.isCompletedThisMonth) {
                            val reset = reminder.copy(isCompletedThisMonth = false)
                            reminderRepository.updateReminder(reset)
                            context?.let { ctx ->
                                com.example.util.ReminderNotificationManager.clearAllHistory(ctx)
                                com.example.util.NotificationScheduler.scheduleReminder(ctx, reset)
                            }
                        }
                    }
                    _messageState.update { it.copy(first = "Se han reiniciado todos los pagos del mes para demostración") }
                }
            } catch (e: Exception) {
                _messageState.update { it.copy(second = "Error al reiniciar recordatorios") }
            }
        }
    }

    fun triggerNotificationSimulation(reminder: Reminder, context: Context) {
        val title = reminder.title
        val message = com.example.util.NotificationScheduler.buildReminderContentText(
            type = reminder.type,
            title = reminder.title,
            amount = reminder.amount,
            notifyDaysBefore = reminder.notifyDaysBefore
        )

        // 1. Actualizar estado de UI para mostrar banner flotante en la app
        val simNotif = SimulatedNotification(
            title = title,
            message = message,
            type = reminder.type
        )
        _messageState.update { it.copy(third = simNotif) }

        // 2. Enviar notificación de sistema real a la barra de estado del dispositivo
        com.example.util.NotificationScheduler.showNotification(
            context = context,
            reminderId = reminder.id,
            title = title,
            contentText = message,
            type = reminder.type,
            amount = reminder.amount
        )
    }

    fun clearMessages() {
        _messageState.update { Triple(null, null, null) }
    }

    // Helper para actualizar el Triple
    private fun Triple<String?, String?, SimulatedNotification?>.copy(
        first: String? = this.first,
        second: String? = this.second,
        third: SimulatedNotification? = this.third
    ) = Triple(first, second, third)
}
