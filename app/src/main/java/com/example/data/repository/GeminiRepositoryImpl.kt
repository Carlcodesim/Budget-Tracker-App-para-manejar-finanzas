package com.example.data.repository

import android.graphics.Bitmap
import android.util.Base64
import com.example.BuildConfig
import com.example.data.api.Content
import com.example.data.api.GenerationConfig
import com.example.data.api.GeminiRequest
import com.example.data.api.InlineData
import com.example.data.api.Part
import com.example.data.api.RetrofitClient
import com.example.domain.model.ParsedReceipt
import com.example.domain.repository.GeminiRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GeminiRepositoryImpl(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : GeminiRepository {
    override suspend fun generateResponse(systemPrompt: String, userPrompt: String): String = withContext(ioDispatcher) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            throw IllegalStateException("API_KEY_NOT_CONFIGURED")
        }

        val request = GeminiRequest(
            contents = listOf(
                Content(parts = listOf(Part(text = userPrompt)))
            ),
            systemInstruction = Content(parts = listOf(Part(text = systemPrompt)))
        )

        val response = RetrofitClient.service.generateContent(apiKey, request)
        val textResponse = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
        if (textResponse.isNullOrBlank()) {
            throw Exception("Respuesta vacía de la API de Gemini.")
        }
        textResponse
    }

    override suspend fun parseReceipt(imageBitmap: Bitmap, availableCategories: List<String>): ParsedReceipt = withContext(ioDispatcher) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            throw IllegalStateException("API_KEY_NOT_CONFIGURED")
        }

        // Redimensionar imagen para optimizar el tamaño de transferencia y velocidad de OCR
        val resized = resizeBitmapPreservingAspect(imageBitmap, maxDimension = 1280)
        val outputStream = ByteArrayOutputStream()
        resized.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        val base64Data = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)

        val categoriesPrompt = if (availableCategories.isNotEmpty()) {
            "Las categorías disponibles en la app son: ${availableCategories.joinToString(", ")}. Por favor asigna el nombre exacto de la categoría más afín."
        } else {
            "Asigna una categoría general adecuada en español (ej. Alimentación, Transporte, Servicios, Ocio, Salud, etc.)."
        }

        val promptText = """
            Eres un asistente de OCR experto en contabilidad y extracción de datos de comprobantes, facturas, tickets de compra y recibos de pago físicos.
            Analiza con máxima precisión la imagen adjunta y extrae la información en un formato JSON estructurado.
            
            $categoriesPrompt
            
            Debes extraer con exactitud:
            1. "amount": El importe total final pagado (número decimal, ej: 25.50). Si hay varios subtotales, propina, impuestos o ítems individuales, extrae el TOTAL FINAL que pagó el cliente.
            2. "merchant": Nombre comercial del negocio, tienda, emisor o restaurante.
            3. "date": Fecha del ticket en formato YYYY-MM-DD (ej: 2026-03-24). Si no es legible o no aparece en el comprobante, usa la fecha actual.
            4. "category": El nombre de la categoría más adecuada según el rubro del comercio y los artículos comprados.
            5. "note": Breve descripción o resumen de los conceptos o artículos principales (ej: "Compra de víveres: leche, pan y café").
            6. "tags": 1 a 3 palabras clave en minúsculas separadas por comas que describan el gasto (ej: "supermercado, comida").
            
            IMPORTANTE: Responde con el objeto JSON puro.
            Ejemplo de estructura esperada:
            {
              "amount": 34.90,
              "merchant": "Supermercados Metro",
              "date": "2026-03-24",
              "category": "Alimentación",
              "note": "Víveres y artículos de limpieza",
              "tags": "supermercado, comida, hogar"
            }
        """.trimIndent()

        val request = GeminiRequest(
            contents = listOf(
                Content(
                    parts = listOf(
                        Part(text = promptText),
                        Part(inlineData = InlineData(mimeType = "image/jpeg", data = base64Data))
                    )
                )
            ),
            generationConfig = GenerationConfig(
                responseMimeType = "application/json",
                temperature = 0.1f
            )
        )

        val response = RetrofitClient.service.generateContent(apiKey, request)
        val rawResponse = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            ?: throw Exception("No se pudo extraer texto del comprobante.")

        parseReceiptJson(rawResponse)
    }

    private fun resizeBitmapPreservingAspect(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxDimension && height <= maxDimension) return bitmap

        val ratio = width.toFloat() / height.toFloat()
        val newWidth: Int
        val newHeight: Int
        if (width > height) {
            newWidth = maxDimension
            newHeight = (maxDimension / ratio).toInt()
        } else {
            newHeight = maxDimension
            newWidth = (maxDimension * ratio).toInt()
        }
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    private fun parseReceiptJson(rawResponse: String): ParsedReceipt {
        var cleanJson = rawResponse.trim()
        if (cleanJson.startsWith("```json")) {
            cleanJson = cleanJson.removePrefix("```json")
        } else if (cleanJson.startsWith("```")) {
            cleanJson = cleanJson.removePrefix("```")
        }
        if (cleanJson.endsWith("```")) {
            cleanJson = cleanJson.removeSuffix("```")
        }
        cleanJson = cleanJson.trim()

        val startIndex = cleanJson.indexOf('{')
        val endIndex = cleanJson.lastIndexOf('}')
        if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
            cleanJson = cleanJson.substring(startIndex, endIndex + 1)
        }

        val jsonObject = JSONObject(cleanJson)

        val amount = if (jsonObject.has("amount") && !jsonObject.isNull("amount")) {
            try {
                val raw = jsonObject.opt("amount")
                when (raw) {
                    is Number -> raw.toDouble()
                    is String -> {
                        val normalized = raw.replace(Regex("[^0-9.,]"), "")
                            .replace(",", ".")
                        normalized.toDoubleOrNull()
                    }
                    else -> null
                }
            } catch (e: Exception) {
                null
            }
        } else null

        val merchant = if (jsonObject.has("merchant") && !jsonObject.isNull("merchant")) {
            jsonObject.optString("merchant").takeIf { it.isNotBlank() }
        } else null

        val dateEpoch = if (jsonObject.has("date") && !jsonObject.isNull("date")) {
            val dateStr = jsonObject.optString("date").trim()
            val formats = listOf(
                "yyyy-MM-dd",
                "dd/MM/yyyy",
                "dd-MM-yyyy",
                "yyyy/MM/dd"
            )
            var parsedTime: Long? = null
            for (pattern in formats) {
                try {
                    val sdf = SimpleDateFormat(pattern, Locale.getDefault())
                    sdf.isLenient = true
                    val parsed = sdf.parse(dateStr)
                    if (parsed != null) {
                        parsedTime = parsed.time
                        break
                    }
                } catch (_: Exception) {
                    // try next pattern
                }
            }
            parsedTime ?: System.currentTimeMillis()
        } else {
            System.currentTimeMillis()
        }

        val category = if (jsonObject.has("category") && !jsonObject.isNull("category")) {
            jsonObject.optString("category").takeIf { it.isNotBlank() }
        } else null

        val note = if (jsonObject.has("note") && !jsonObject.isNull("note")) {
            jsonObject.optString("note").takeIf { it.isNotBlank() }
        } else null

        val tags = if (jsonObject.has("tags") && !jsonObject.isNull("tags")) {
            jsonObject.optString("tags").takeIf { it.isNotBlank() }
        } else null

        return ParsedReceipt(
            amount = amount,
            merchant = merchant,
            dateEpoch = dateEpoch,
            suggestedCategory = category,
            note = note,
            suggestedTags = tags
        )
    }
}
