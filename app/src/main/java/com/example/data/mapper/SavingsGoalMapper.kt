package com.example.data.mapper

import com.example.data.local.entity.SavingsGoalEntity
import com.example.domain.model.SavingsGoal
import java.util.Date

/**
 * Funciones de extensión para mapear metas de ahorro entre la capa de datos y el dominio.
 */
fun SavingsGoalEntity.toDomain(): SavingsGoal {
    return SavingsGoal(
        id = id,
        name = name,
        targetAmount = targetAmount,
        currentAmount = currentAmount,
        deadline = deadline?.let { Date(it) },
        createdAt = Date(createdAt),
        description = description,
        priority = priority,
        category = category
    )
}

fun SavingsGoal.toEntity(): SavingsGoalEntity {
    return SavingsGoalEntity(
        id = id,
        name = name,
        targetAmount = targetAmount,
        currentAmount = currentAmount,
        deadline = deadline?.time,
        createdAt = createdAt.time,
        description = description,
        priority = priority,
        category = category
    )
}
