package com.ntu.electricity.payment

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import java.io.OutputStream

internal object PaymentQr {
    fun decode(bitmap: Bitmap): List<PaymentCode> {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return PaymentQrDecoder.decode(bitmap.width, bitmap.height, pixels)
    }

    fun write(value: String, output: OutputStream) {
        val matrix = MultiFormatWriter().encode(value, BarcodeFormat.QR_CODE, 768, 768,
            mapOf(EncodeHintType.MARGIN to 4, EncodeHintType.CHARACTER_SET to "UTF-8"))
        val bitmap = Bitmap.createBitmap(768, 768, Bitmap.Config.ARGB_8888)
        try {
            val pixels = IntArray(768 * 768) { index -> if (matrix[index % 768, index / 768]) Color.BLACK else Color.WHITE }
            bitmap.setPixels(pixels, 0, 768, 0, 0, 768, 768)
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "二维码保存失败" }
        } finally { bitmap.recycle() }
    }

    /** Android 10+: save only an image created by this app, without photo-library permission. */
    fun saveToGallery(context: Context, value: String) {
        check(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "NTU-payment-${System.currentTimeMillis()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/NTU_EcoCheck")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = checkNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values))
        try {
            checkNotNull(resolver.openOutputStream(uri)).use { write(value, it) }
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        } catch (error: Exception) {
            resolver.delete(uri, null, null)
            throw error
        }
    }
}
