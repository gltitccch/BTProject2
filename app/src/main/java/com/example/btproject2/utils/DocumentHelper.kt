package com.example.btproject2.utils

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.btproject2.models.AttachedDocument
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.min

object DocumentHelper {

    fun saveImageToInternal(context: Context, uri: Uri, personId: String): Pair<String, String>? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            if (originalBitmap == null) return null

            // Scale to max dimension of 256 for fast node rendering & lightweight Firestore sync
            val maxDim = 256
            val width = originalBitmap.width
            val height = originalBitmap.height
            val scale = min(maxDim.toFloat() / width, maxDim.toFloat() / height).coerceAtMost(1f)
            val scaledBitmap = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    originalBitmap,
                    (width * scale).toInt().coerceAtLeast(1),
                    (height * scale).toInt().coerceAtLeast(1),
                    true
                )
            } else {
                originalBitmap
            }

            val dir = File(context.filesDir, "avatars").apply { if (!exists()) mkdirs() }
            val destFile = File(dir, "${personId}_avatar.jpg")
            val outputStream = FileOutputStream(destFile)
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
            outputStream.flush()
            outputStream.close()

            // Convert to Base64 thumbnail
            val byteStream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, byteStream)
            val base64 = Base64.encodeToString(byteStream.toByteArray(), Base64.NO_WRAP)

            Pair(destFile.absolutePath, base64)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun saveDocumentToInternal(
        context: Context,
        uri: Uri,
        personId: String,
        customCategory: String? = null
    ): AttachedDocument? {
        return try {
            var fileName = "Document_${System.currentTimeMillis()}"
            var fileSizeStr = ""

            // Query file name and size
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        fileName = cursor.getString(nameIndex) ?: fileName
                    }
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex != -1) {
                        val bytes = cursor.getLong(sizeIndex)
                        fileSizeStr = formatFileSize(bytes)
                    }
                }
            }

            val lowerName = fileName.lowercase()
            val fileType = when {
                lowerName.endsWith(".pdf") -> "pdf"
                lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") || lowerName.endsWith(".png") -> "image"
                else -> "doc"
            }

            val category = customCategory ?: when {
                lowerName.contains("birth") -> "Birth Certificate"
                lowerName.contains("id") || lowerName.contains("gov") || lowerName.contains("national") || lowerName.contains("passport") -> "Government ID"
                lowerName.contains("marriage") -> "Marriage Certificate"
                else -> "Birth Certificate"
            }

            val dir = File(context.filesDir, "documents").apply { if (!exists()) mkdirs() }
            val cleanName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val destFile = File(dir, "${personId}_${System.currentTimeMillis()}_$cleanName")

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }

            if (fileSizeStr.isEmpty() && destFile.exists()) {
                fileSizeStr = formatFileSize(destFile.length())
            }

            AttachedDocument(
                id = UUID.randomUUID().toString(),
                name = fileName,
                fileUri = destFile.absolutePath,
                fileType = fileType,
                category = category,
                uploadedAt = System.currentTimeMillis(),
                fileSize = fileSizeStr
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun openDocument(context: Context, document: AttachedDocument) {
        try {
            val file = File(document.fileUri)
            if (!file.exists()) {
                Toast.makeText(context, "Document file not found on device.", Toast.LENGTH_SHORT).show()
                return
            }

            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val mimeType = when (document.fileType) {
                "pdf" -> "application/pdf"
                "image" -> "image/*"
                else -> "*/*"
            }

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "No app available to open ${document.fileType.uppercase()} files.", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open document: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun calculateAge(birthDate: String, deathDate: String? = null, isLiving: Boolean = true): String {
        if (birthDate.isBlank()) return if (!isLiving) "Deceased" else ""

        val birthCal = parseDateToCalendar(birthDate) ?: return if (!isLiving) "Deceased" else ""
        val targetCal = if (!isLiving && !deathDate.isNullOrBlank()) {
            parseDateToCalendar(deathDate) ?: Calendar.getInstance()
        } else {
            Calendar.getInstance()
        }

        var age = targetCal.get(Calendar.YEAR) - birthCal.get(Calendar.YEAR)
        if (targetCal.get(Calendar.DAY_OF_YEAR) < birthCal.get(Calendar.DAY_OF_YEAR)) {
            age--
        }
        val safeAge = age.coerceAtLeast(0)

        return if (!isLiving) {
            if (safeAge > 0) "Died at age $safeAge" else "Deceased"
        } else {
            "$safeAge yrs old"
        }
    }

    private fun parseDateToCalendar(dateStr: String): Calendar? {
        val formats = listOf(
            "yyyy-MM-dd",
            "yyyy/MM/dd",
            "yyyy.MM.dd",
            "yyyy",
            "MMM dd, yyyy",
            "MMMM dd, yyyy",
            "dd-MM-yyyy",
            "dd/MM/yyyy"
        )
        for (pattern in formats) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US).apply { isLenient = true }
                val parsed = sdf.parse(dateStr)
                if (parsed != null) {
                    val cal = Calendar.getInstance()
                    cal.time = parsed
                    return cal
                }
            } catch (_: Exception) {}
        }
        // Try extracting leading 4-digit year
        val yearMatch = Regex("""\b(18|19|20)\d{2}\b""").find(dateStr)
        if (yearMatch != null) {
            val year = yearMatch.value.toIntOrNull()
            if (year != null) {
                val cal = Calendar.getInstance()
                cal.set(year, Calendar.JANUARY, 1)
                return cal
            }
        }
        return null
    }

    fun decodeBase64Bitmap(base64: String, targetSize: Int = 256): Bitmap? {
        if (base64.isBlank()) return null
        return try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            var sampleSize = 1
            val maxDim = maxOf(options.outWidth, options.outHeight)
            while (maxDim / (sampleSize * 2) >= targetSize) {
                sampleSize *= 2
            }
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
        } catch (_: Exception) {
            null
        }
    }

    fun getCircularBitmap(src: Bitmap, size: Int): Bitmap {
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val srcSize = min(src.width, src.height)
        val left = (src.width - srcSize) / 2
        val top = (src.height - srcSize) / 2
        val cropped = if (src.width != srcSize || src.height != srcSize) {
            Bitmap.createBitmap(src, left, top, srcSize, srcSize)
        } else {
            src
        }

        val scaled = if (cropped.width != size || cropped.height != size) {
            Bitmap.createScaledBitmap(cropped, size, size, true)
        } else {
            cropped
        }

        val shader = BitmapShader(scaled, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        paint.shader = shader

        val radius = size / 2f
        canvas.drawCircle(radius, radius, radius, paint)

        if (cropped != src && cropped != scaled) cropped.recycle()
        if (scaled != src) scaled.recycle()

        return output
    }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
        }
    }
}

