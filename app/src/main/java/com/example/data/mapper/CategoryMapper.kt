package com.example.data.mapper

import com.example.data.local.entity.CategoryEntity
import com.example.domain.model.Category

/**
 * Funciones de extensión para mapear categorías entre la capa de datos y el dominio.
 */
fun CategoryEntity.toDomain(): Category {
    return Category(
        id = id,
        name = name,
        iconKey = iconKey,
        isDefault = isDefault,
        maxMonthlyBudget = maxMonthlyBudget,
        colorHex = colorHex,
        type = type
    )
}

fun Category.toEntity(): CategoryEntity {
    return CategoryEntity(
        id = id,
        name = name,
        iconKey = iconKey,
        isDefault = isDefault,
        maxMonthlyBudget = maxMonthlyBudget,
        colorHex = colorHex,
        type = type
    )
}
