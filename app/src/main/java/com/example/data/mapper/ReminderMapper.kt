package com.example.data.mapper

import com.example.data.local.entity.ReminderEntity
import com.example.domain.model.Reminder

/**
 * Funciones de extensión para mapear recordatorios entre la capa de datos y el dominio.
 */
fun ReminderEntity.toDomain(): Reminder {
    return Reminder(
        id = id,
        type = type,
        title = title,
        description = description,
        isEnabled = isEnabled,
        amount = amount,
        dueDateDay = dueDateDay,
        time = time,
        notifyDaysBefore = notifyDaysBefore,
        notifyTime = notifyTime,
        associatedGoalId = associatedGoalId,
        isCompletedThisMonth = isCompletedThisMonth
    )
}

fun Reminder.toEntity(): ReminderEntity {
    return ReminderEntity(
        id = id,
        type = type,
        title = title,
        description = description,
        isEnabled = isEnabled,
        amount = amount,
        dueDateDay = dueDateDay,
        time = time,
        notifyDaysBefore = notifyDaysBefore,
        notifyTime = notifyTime,
        associatedGoalId = associatedGoalId,
        isCompletedThisMonth = isCompletedThisMonth
    )
}
