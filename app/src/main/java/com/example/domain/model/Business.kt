package com.example.domain.model

/**
 * Modelo de dominio que representa un negocio o emprendimiento comercial gestionado por el usuario.
 */
data class Business(
    val id: Long = 0,
    val name: String,
    val type: String = "Comercio / Ventas",
    val description: String = "",
    val colorHex: String = "#2563EB",
    val iconKey: String = "storefront",
    val inventoryValue: Double = 0.0,
    val createdAt: Long = System.currentTimeMillis()
)
