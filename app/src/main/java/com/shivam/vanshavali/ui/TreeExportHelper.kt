package com.shivam.vanshavali.ui

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

object TreeExportHelper {

    suspend fun saveCacheBitmap(context: Context, bitmap: Bitmap, title: String): File = withContext(Dispatchers.IO) {
        val cleanTitle = title.replace("\\s+".toRegex(), "_")
        val file = File(context.cacheDir, "${cleanTitle}_print.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        file
    }

    suspend fun saveBitmapToGallery(context: Context, title: String, bitmap: Bitmap) = withContext(Dispatchers.IO) {
        val filename = "${title.replace("\\s+".toRegex(), "_")}_${System.currentTimeMillis()}.png"
        var outputStream: OutputStream? = null

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Vanshavali")
                }
                val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    outputStream = context.contentResolver.openOutputStream(uri)
                }
            } else {
                val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString() + "/Vanshavali"
                val dir = File(imagesDir).apply { mkdirs() }
                val file = File(dir, filename)
                outputStream = FileOutputStream(file)
            }

            outputStream?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Image saved to Pictures/Vanshavali!", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Failed to save image: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    suspend fun downloadJsonFile(context: Context, title: String, jsonContent: String) = withContext(Dispatchers.IO) {
        val filename = "${title.replace("\\s+".toRegex(), "_")}_tree.json"
        var outputStream: OutputStream? = null

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    outputStream = context.contentResolver.openOutputStream(uri)
                }
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val file = File(downloadsDir, filename)
                outputStream = FileOutputStream(file)
            }

            outputStream?.use { out ->
                out.write(jsonContent.toByteArray())
            }

            withContext(Dispatchers.Main) {
                Toast.makeText(context, "JSON saved to Downloads/$filename", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Failed to save JSON: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun shareToWhatsApp(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            setPackage("com.whatsapp")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            context.startActivity(shareIntent)
        } catch (_: Exception) {
            shareIntent.setPackage("com.whatsapp.w4b")
            try {
                context.startActivity(shareIntent)
            } catch (_: Exception) {
                Toast.makeText(context, "WhatsApp not installed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun printWithNokoPrint(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val printIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            setPackage("com.nokoprint")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            context.startActivity(printIntent)
        } catch (_: Exception) {
            printIntent.setPackage("com.noco.print")
            try {
                context.startActivity(printIntent)
            } catch (_: Exception) {
                Toast.makeText(context, "NokoPrint app not installed", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
