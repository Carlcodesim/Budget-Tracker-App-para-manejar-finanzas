package com.example.data.mapper

import com.example.data.local.entity.RecurringExpenseEntity
import com.example.domain.model.Account
import com.example.domain.model.Category
import com.example.domain.model.RecurringExpense

/**
 * Funciones de extensión para mapear gastos recurrentes entre la capa de datos y el dominio.
 */
fun RecurringExpenseEntity.toDomain(category: Category, account: Account? = null): RecurringExpense {
    return RecurringExpense(
        id = id,
        title = title,
        amount = amount,
        category = category,
        account = account,
        accountId = accountId,
        dayOfMonth = dayOfMonth,
        description = description,
        tags = tags,
        isEnabled = isEnabled,
        lastExecutedMonth = lastExecutedMonth,
        autoCreate = autoCreate,
        createdAt = createdAt
    )
}

fun RecurringExpense.toEntity(): RecurringExpenseEntity {
    return RecurringExpenseEntity(
        id = id,
        title = title,
        amount = amount,
        categoryId = category.id,
        accountId = accountId,
        dayOfMonth = dayOfMonth,
        description = description,
        tags = tags,
        isEnabled = isEnabled,
        lastExecutedMonth = lastExecutedMonth,
        autoCreate = autoCreate,
        createdAt = createdAt
    )
}
