package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entidad de base de datos Room para las categorías.
 */
@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val iconKey: String,
    val isDefault: Boolean = false,
    val maxMonthlyBudget: Double? = null,
    val colorHex: String = "#3B82F6",
    val type: String = "EXPENSE" // "EXPENSE", "INCOME" o "ALL"
)
