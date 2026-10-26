package com.example.presentation.common.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Mapeador de claves de icono almacenadas en la base de datos a vectores de iconos de Material Design 3.
 */
object IconHelper {
    fun getIcon(key: String): ImageVector {
        return when (key) {
            "attach_money" -> Icons.Default.AttachMoney
            "restaurant" -> Icons.Default.Restaurant
            "directions_car" -> Icons.Default.DirectionsCar
            "sports_esports" -> Icons.Default.SportsEsports
            "medical_services" -> Icons.Default.MedicalServices
            "school" -> Icons.Default.School
            "receipt" -> Icons.Default.Receipt
            "shopping_cart" -> Icons.Default.ShoppingCart
            "home" -> Icons.Default.Home
            "flight" -> Icons.Default.Flight
            "movie" -> Icons.Default.Movie
            "work" -> Icons.Default.Work
            "pets" -> Icons.Default.Pets
            "fitness_center" -> Icons.Default.FitnessCenter
            "payment" -> Icons.Default.Payment
            "card_giftcard" -> Icons.Default.CardGiftcard
            "swap_horiz" -> Icons.Default.SwapHoriz
            "storefront" -> Icons.Default.Storefront
            "account_balance" -> Icons.Default.AccountBalance
            "local_cafe" -> Icons.Default.LocalCafe
            "directions_bus" -> Icons.Default.DirectionsBus
            "local_pharmacy" -> Icons.Default.LocalPharmacy
            "local_gas_station" -> Icons.Default.LocalGasStation
            "fastfood" -> Icons.Default.Fastfood
            "flash_on" -> Icons.Default.FlashOn
            "shopping_bag" -> Icons.Default.ShoppingBag
            else -> Icons.Default.Category
        }
    }
}
