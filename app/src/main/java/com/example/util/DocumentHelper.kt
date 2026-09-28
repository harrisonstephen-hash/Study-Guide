package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.example.data.model.RawFileData
import com.example.data.model.UploadedFileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.text.DecimalFormat

object DocumentHelper {

    suspend fun processUri(context: Context, uri: Uri): UploadedFileItem = withContext(Dispatchers.IO) {
        val fileName = getFileName(context, uri)
        val mimeType = getMimeType(context, uri, fileName)
        val sizeBytes = getFileSize(context, uri)
        val isPdf = mimeType == "application/pdf" || fileName.endsWith(".pdf", ignoreCase = true)

        var thumbnail: Bitmap? = null
        var pageCount = 1

        if (isPdf) {
            val (pdfThumb, count) = renderPdfThumbnail(context, uri)
            thumbnail = pdfThumb
            pageCount = count
        } else if (mimeType.startsWith("image/")) {
            thumbnail = decodeImageThumbnail(context, uri)
        }

        UploadedFileItem(
            uri = uri,
            fileName = fileName,
            mimeType = mimeType,
            sizeText = formatFileSize(sizeBytes),
            isPdf = isPdf,
            thumbnailBitmap = thumbnail,
            pageCount = pageCount
        )
    }

    suspend fun prepareRawFileData(context: Context, items: List<UploadedFileItem>): List<RawFileData> =
        withContext(Dispatchers.IO) {
            items.mapNotNull { item ->
                try {
                    val bytes = if (item.isPdf) {
                        context.contentResolver.openInputStream(item.uri)?.use { it.readBytes() }
                    } else {
                        // For images, optimize size so request doesn't exceed Gemini limits
                        val inputStream = context.contentResolver.openInputStream(item.uri)
                        val original = BitmapFactory.decodeStream(inputStream)
                        inputStream?.close()
                        if (original != null) {
                            val maxDim = 1600
                            val scaled = if (original.width > maxDim || original.height > maxDim) {
                                val factor = maxDim.toFloat() / maxOf(original.width, original.height).toFloat()
                                Bitmap.createScaledBitmap(
                                    original,
                                    (original.width * factor).toInt(),
                                    (original.height * factor).toInt(),
                                    true
                                )
                            } else {
                                original
                            }
                            val bos = ByteArrayOutputStream()
                            scaled.compress(Bitmap.CompressFormat.JPEG, 85, bos)
                            bos.toByteArray()
                        } else null
                    }

                    if (bytes != null && bytes.isNotEmpty()) {
                        RawFileData(
                            fileName = item.fileName,
                            mimeType = if (item.isPdf) "application/pdf" else "image/jpeg",
                            bytes = bytes
                        )
                    } else null
                } catch (e: Exception) {
                    Log.w("DocumentHelper", "Failed to read bytes for ${item.fileName}: ${e.message}")
                    null
                }
            }
        }

    private fun renderPdfThumbnail(context: Context, uri: Uri): Pair<Bitmap?, Int> {
        return try {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return Pair(null, 1)
            val renderer = PdfRenderer(pfd)
            val count = renderer.pageCount
            var thumbBitmap: Bitmap? = null
            if (count > 0) {
                val page = renderer.openPage(0)
                val targetW = 280
                val targetH = (targetW.toFloat() * page.height / page.width).toInt().coerceIn(180, 420)
                val bitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                thumbBitmap = bitmap
            }
            renderer.close()
            pfd.close()
            Pair(thumbBitmap, count)
        } catch (e: Exception) {
            Log.w("DocumentHelper", "PdfRenderer thumbnail error: ${e.message}")
            Pair(null, 1)
        }
    }

    private fun decodeImageThumbnail(context: Context, uri: Uri): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
            options.inSampleSize = calculateInSampleSize(options, 240, 240)
            options.inJustDecodeBounds = false
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun getFileName(context: Context, uri: Uri): String {
        var name = "Document"
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            val colName = cursor.getString(nameIndex)
                            if (!colName.isNullOrBlank()) {
                                name = colName
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
        if (name == "Document") {
            uri.lastPathSegment?.let { seg ->
                name = seg.substringAfterLast('/')
            }
        }
        return name
    }

    private fun getMimeType(context: Context, uri: Uri, fileName: String): String {
        var type = context.contentResolver.getType(uri) ?: ""
        if (type.isBlank()) {
            val ext = fileName.substringAfterLast('.', "").lowercase()
            type = when (ext) {
                "pdf" -> "application/pdf"
                "png" -> "image/png"
                "jpg", "jpeg" -> "image/jpeg"
                "webp" -> "image/webp"
                else -> "application/octet-stream"
            }
        }
        return type
    }

    private fun getFileSize(context: Context, uri: Uri): Long {
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex != -1) {
                        return cursor.getLong(sizeIndex)
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore
        }
        return 0L
    }

    private fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "Unknown size"
        val df = DecimalFormat("#.##")
        return when {
            bytes >= 1024 * 1024 -> "${df.format(bytes.toFloat() / (1024 * 1024))} MB"
            bytes >= 1024 -> "${df.format(bytes.toFloat() / 1024)} KB"
            else -> "$bytes B"
        }
    }
}
