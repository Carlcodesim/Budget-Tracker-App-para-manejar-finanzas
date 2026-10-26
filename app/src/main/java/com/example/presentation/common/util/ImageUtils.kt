package com.example.presentation.common.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.media.ExifInterface
import java.io.File
import java.io.InputStream

object ImageUtils {

    /**
     * Crea un archivo temporal en el directorio de caché para almacenar la foto capturada por la cámara.
     */
    fun createTempImageFile(context: Context): File {
        val storageDir = File(context.cacheDir, "images").apply {
            if (!exists()) mkdirs()
        }
        return File.createTempFile(
            "receipt_${System.currentTimeMillis()}_",
            ".jpg",
            storageDir
        )
    }

    /**
     * Decodifica un Uri de imagen corrigiendo la orientación EXIF y redimensionando si supera el tamaño máximo.
     */
    fun decodeUriToBitmap(context: Context, uri: Uri, maxDimension: Int = 1600): Bitmap? {
        return try {
            // 1. Obtener dimensiones sin cargar a memoria
            var inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream?.close()

            // 2. Calcular sample size
            var sampleSize = 1
            val maxSide = maxOf(options.outWidth, options.outHeight)
            while (maxSide / (sampleSize * 2) >= maxDimension) {
                sampleSize *= 2
            }

            // 3. Decodificar bitmap real con sample size
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            inputStream = context.contentResolver.openInputStream(uri)
            val decodedBitmap = BitmapFactory.decodeStream(inputStream, null, decodeOptions)
            inputStream?.close()

            if (decodedBitmap == null) return null

            // 4. Corregir orientación según EXIF
            val rotatedBitmap = correctExifOrientation(context, uri, decodedBitmap)
            rotatedBitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun correctExifOrientation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return bitmap
            val exif = ExifInterface(inputStream)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            inputStream.close()

            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
                else -> return bitmap
            }

            val rotated = Bitmap.createBitmap(
                bitmap,
                0,
                0,
                bitmap.width,
                bitmap.height,
                matrix,
                true
            )
            if (rotated != bitmap) {
                bitmap.recycle()
            }
            rotated
        } catch (e: Exception) {
            bitmap
        }
    }
}
