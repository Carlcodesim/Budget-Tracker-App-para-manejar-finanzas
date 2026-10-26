package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.domain.model.Business

/**
 * Entidad de base de datos Room para almacenar negocios y emprendimientos.
 */
@Entity(tableName = "businesses")
data class BusinessEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String = "Comercio / Ventas",
    val description: String = "",
    val colorHex: String = "#2563EB",
    val iconKey: String = "storefront",
    val inventoryValue: Double = 0.0,
    val createdAt: Long = System.currentTimeMillis()
)

fun BusinessEntity.toDomain() = Business(
    id = id,
    name = name,
    type = type,
    description = description,
    colorHex = colorHex,
    iconKey = iconKey,
    inventoryValue = inventoryValue,
    createdAt = createdAt
)

fun Business.toEntity() = BusinessEntity(
    id = id,
    name = name,
    type = type,
    description = description,
    colorHex = colorHex,
    iconKey = iconKey,
    inventoryValue = inventoryValue,
    createdAt = createdAt
)
