package com.example.domain.repository

import com.example.domain.model.Reminder
import kotlinx.coroutines.flow.Flow

/**
 * Interfaz de repositorio para operaciones con recordatorios financieros.
 */
interface ReminderRepository {
    fun getAllReminders(): Flow<List<Reminder>>
    suspend fun getReminderById(id: Long): Reminder?
    suspend fun insertReminder(reminder: Reminder): Long
    suspend fun updateReminder(reminder: Reminder)
    suspend fun deleteReminder(reminder: Reminder)
}
