package com.example.misal.ui.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream

object ProfilePictureHelper {

    fun uriToBase64(context: Context, uri: Uri): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val bytes = inputStream?.readBytes()
            inputStream?.close()

            if (bytes != null) {
                var bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
                
                // Resmi 256x256 civarına küçült, Firestore 1MB limitini korumak ve hızlı yüklemek için.
                val maxDim = 256f
                if (bitmap.width > maxDim || bitmap.height > maxDim) {
                    val ratio = maxDim / maxOf(bitmap.width, bitmap.height)
                    val newWidth = (bitmap.width * ratio).toInt()
                    val newHeight = (bitmap.height * ratio).toInt()
                    bitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
                }

                val outputStream = ByteArrayOutputStream()
                // JPEG, %70 kalite genelde yeterlidir.
                bitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
                val compressedBytes = outputStream.toByteArray()

                Base64.encodeToString(compressedBytes, Base64.NO_WRAP)
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
