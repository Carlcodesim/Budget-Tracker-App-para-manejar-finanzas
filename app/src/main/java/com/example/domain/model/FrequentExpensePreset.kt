package com.example.domain.model

/**
 * Representa una plantilla o preset de gasto habitual preconfigurado para registro en 1 solo toque.
 */
data class FrequentExpensePreset(
    val id: String,
    val title: String,
    val subtitle: String,
    val defaultAmount: Double,
    val categoryName: String,
    val iconKey: String,
    val note: String = "",
    val emoji: String = "⚡"
)

object FrequentExpenseDefaults {
    val defaultPresets: List<FrequentExpensePreset> = listOf(
        FrequentExpensePreset(
            id = "preset_coffee",
            title = "Café y Desayuno",
            subtitle = "Snack matutino / café",
            defaultAmount = 150.0,
            categoryName = "Comida",
            iconKey = "local_cafe",
            note = "Café / Snack",
            emoji = "☕"
        ),
        FrequentExpensePreset(
            id = "preset_transport",
            title = "Transporte / Pasaje",
            subtitle = "Metro, autobús o taxi",
            defaultAmount = 50.0,
            categoryName = "Transporte",
            iconKey = "directions_bus",
            note = "Pasaje diario",
            emoji = "🚌"
        ),
        FrequentExpensePreset(
            id = "preset_lunch",
            title = "Almuerzo del Día",
            subtitle = "Menú o comida ejecutiva",
            defaultAmount = 350.0,
            categoryName = "Comida",
            iconKey = "restaurant",
            note = "Almuerzo diario",
            emoji = "🍔"
        ),
        FrequentExpensePreset(
            id = "preset_groceries",
            title = "Supermercado Express",
            subtitle = "Compras rápidas del día",
            defaultAmount = 600.0,
            categoryName = "Servicios",
            iconKey = "shopping_cart",
            note = "Supermercado express",
            emoji = "🛒"
        ),
        FrequentExpensePreset(
            id = "preset_pharmacy",
            title = "Farmacia / Salud",
            subtitle = "Medicamentos y cuidado",
            defaultAmount = 250.0,
            categoryName = "Salud",
            iconKey = "local_pharmacy",
            note = "Farmacia",
            emoji = "💊"
        ),
        FrequentExpensePreset(
            id = "preset_fuel",
            title = "Combustible / Gasolina",
            subtitle = "Recarga de combustible",
            defaultAmount = 1000.0,
            categoryName = "Transporte",
            iconKey = "local_gas_station",
            note = "Combustible",
            emoji = "⛽"
        )
    )
}
