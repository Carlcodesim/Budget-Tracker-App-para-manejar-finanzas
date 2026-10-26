package com.example.presentation.common.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Utilidad optimizada para formatear y analizar fechas con almacenamiento en caché
 * y formatters thread-local para máximo rendimiento y evitar contención en hilos.
 */
object DateFormatter {
    private val standardFormat = ThreadLocal.withInitial {
        SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    }

    private val monthYearFormat = ThreadLocal.withInitial {
        SimpleDateFormat("MMMM yyyy", Locale.forLanguageTag("es-ES"))
    }

    private val headerSameYearFormat = ThreadLocal.withInitial {
        SimpleDateFormat("EEEE, d 'de' MMMM", Locale.forLanguageTag("es-ES"))
    }

    private val headerDiffYearFormat = ThreadLocal.withInitial {
        SimpleDateFormat("d 'de' MMMM, yyyy", Locale.forLanguageTag("es-ES"))
    }

    // Caché de fechas formateadas por timestamp de día (elimina miles de llamadas a SimpleDateFormat durante scrolls)
    private val dayTimestampCache = ConcurrentHashMap<Long, String>(128)
    private val headerCache = ConcurrentHashMap<Long, String>(128)

    fun format(date: Date): String {
        // Redondear a nivel de día (milisegundos) para la clave de caché
        val epochDay = date.time / (24 * 60 * 60 * 1000L)
        return dayTimestampCache.computeIfAbsent(epochDay) {
            standardFormat.get()?.format(date) ?: SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(date)
        }
    }

    fun formatGroupHeader(date: Date): String {
        val epochDay = date.time / (24 * 60 * 60 * 1000L)
        return headerCache.computeIfAbsent(epochDay) {
            val cal = java.util.Calendar.getInstance()
            val todayCal = java.util.Calendar.getInstance()
            val yesterdayCal = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, -1) }
            cal.time = date
            val isSameYear = cal.get(java.util.Calendar.YEAR) == todayCal.get(java.util.Calendar.YEAR)
            val isToday = isSameYear && cal.get(java.util.Calendar.DAY_OF_YEAR) == todayCal.get(java.util.Calendar.DAY_OF_YEAR)
            val isYesterday = isSameYear && cal.get(java.util.Calendar.DAY_OF_YEAR) == yesterdayCal.get(java.util.Calendar.DAY_OF_YEAR)
            val esLocale = Locale.forLanguageTag("es-ES")
            when {
                isToday -> "Hoy"
                isYesterday -> "Ayer"
                isSameYear -> (headerSameYearFormat.get()?.format(date) ?: SimpleDateFormat("EEEE, d 'de' MMMM", esLocale).format(date))
                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase(esLocale) else it.toString() }
                else -> (headerDiffYearFormat.get()?.format(date) ?: SimpleDateFormat("d 'de' MMMM, yyyy", esLocale).format(date))
                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase(esLocale) else it.toString() }
            }
        }
    }

    fun formatMonthYear(date: Date): String {
        val spanishLocale = Locale.forLanguageTag("es-ES")
        return monthYearFormat.get()?.format(date)?.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(spanishLocale) else it.toString()
        } ?: SimpleDateFormat("MMMM yyyy", spanishLocale).format(date)
    }

    fun parse(dateString: String): Date? {
        return try {
            standardFormat.get()?.parse(dateString)
        } catch (e: Exception) {
            null
        }
    }

    fun clearCache() {
        dayTimestampCache.clear()
        headerCache.clear()
    }
}
