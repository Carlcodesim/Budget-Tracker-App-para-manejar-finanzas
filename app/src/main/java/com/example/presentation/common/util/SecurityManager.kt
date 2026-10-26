package com.example.presentation.common.util

import android.content.Context

/**
 * Gestor de seguridad para el bloqueo de la aplicación con PIN.
 */
object SecurityManager {
    fun isPinEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences("security_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("pin_enabled", false)
    }

    fun setPinEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences("security_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("pin_enabled", enabled).apply()
    }

    fun getPin(context: Context): String? {
        val prefs = context.getSharedPreferences("security_prefs", Context.MODE_PRIVATE)
        return prefs.getString("app_pin", null)
    }

    fun setPin(context: Context, pin: String?) {
        val prefs = context.getSharedPreferences("security_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("app_pin", pin).apply()
    }
}
