package com.ntu.electricity.payment

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.multi.qrcode.QRCodeMultiReader

internal object PaymentQrDecoder {
    fun decode(width: Int, height: Int, pixels: IntArray): List<PaymentCode> {
        val source = RGBLuminanceSource(width, height, pixels)
        val hints = mapOf(DecodeHintType.TRY_HARDER to true, DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE))
        return listOf(source, source.invert()).flatMap { luminance ->
            runCatching {
                QRCodeMultiReader().decodeMultiple(BinaryBitmap(HybridBinarizer(luminance)), hints)
                    .mapNotNull { PaymentLinks.parse(it.text) }
            }.getOrDefault(emptyList())
        }.distinctBy { it.value }
    }
}
