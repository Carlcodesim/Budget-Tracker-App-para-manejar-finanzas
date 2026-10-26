package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.domain.repository.StoreRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Implementación del repositorio de tienda utilizando SharedPreferences para persistencia local.
 */
class StoreRepositoryImpl(
    context: Context
) : StoreRepository {

    private val prefs: SharedPreferences = context.getSharedPreferences("store_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_INVENTORY_VALUE = "store_inventory_val"
    }

    override fun getInventoryValue(): Flow<Double> = callbackFlow {
        trySend(prefs.getFloat(KEY_INVENTORY_VALUE, 0f).toDouble())

        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_INVENTORY_VALUE) {
                trySend(prefs.getFloat(KEY_INVENTORY_VALUE, 0f).toDouble())
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    override suspend fun saveInventoryValue(value: Double) {
        prefs.edit().putFloat(KEY_INVENTORY_VALUE, value.toFloat()).apply()
    }
}
