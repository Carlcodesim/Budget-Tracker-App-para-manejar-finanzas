package com.example.domain.model

/**
 * Representa una categoría de transacción en la capa de dominio.
 */
data class Category(
    val id: Long = 0,
    val name: String,
    val iconKey: String,
    val isDefault: Boolean = false,
    val maxMonthlyBudget: Double? = null,
    val colorHex: String = "#3B82F6",
    val type: String = "EXPENSE"
)
