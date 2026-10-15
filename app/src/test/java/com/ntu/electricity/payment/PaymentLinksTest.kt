package com.ntu.electricity.payment

import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PaymentLinksTest {
    @Test fun nativeWechatQrIsNotClassifiedAsAppOrH5() {
        listOf("weixin://wxpay/bizpayurl?pr=TEST", "weixin://pay.weixin.qq.com/bizpayurl/up?pr=TEST").forEach {
            assertEquals(PaymentCodeType.WECHAT_QR, PaymentLinks.parse(it)?.type)
        }
    }

    @Test fun suppliedH5AndAppLinksArePreserved() {
        mapOf(
            "https://wx.tenpay.com/cgi-bin/mmpayweb-bin/checkmweb?prepay_id=TEST&package=TEST" to PaymentCodeType.WECHAT_H5,
            "weixin://wap/pay?prepayid=TEST&sign=TEST" to PaymentCodeType.WECHAT_APP,
            "alipays://platformapi/startapp?appId=TEST&url=TEST" to PaymentCodeType.ALIPAY_APP,
            "https://qr.alipay.com/TEST" to PaymentCodeType.ALIPAY_QR
        ).forEach { (url, type) ->
            val parsed = PaymentLinks.parse(url)
            assertEquals(type, parsed?.type)
            assertEquals(url, parsed?.value)
        }
    }

    @Test fun rejectsLookalikesAndOtherQrContent() {
        listOf(
            "https://qr.alipay.com.evil.example/TEST", "https://qr.alipay.com@evil.example/TEST",
            "http://qr.alipay.com/TEST", "https://qr.alipay.com:8443/TEST", "https://example.com/payment",
            "weixin://contacts/add?user=TEST", "javascript:alert(1)", "intent://anything#Intent;end",
            "weixin://wxpay/bizpayurl", "weixin://wap/pay?x=TEST\n", "arbitrary QR text"
        ).forEach { assertNull(it, PaymentLinks.parse(it)) }
    }

    @Test fun decoderReadsActualQrPixelsOnALargerPageWithoutChangingTheOrder() {
        val url = "weixin://wxpay/bizpayurl?pr=TEST_ORDER_DO_NOT_PAY"
        val qr = MultiFormatWriter().encode(url, BarcodeFormat.QR_CODE, 240, 240)
        val width = 480
        val height = 800
        val pixels = IntArray(width * height) { -1 }
        for (y in 0 until qr.height) for (x in 0 until qr.width) {
            pixels[(y + 140) * width + x + 120] = if (qr[x, y]) 0xff000000.toInt() else -1
        }
        val result = PaymentQrDecoder.decode(width, height, pixels).single()
        assertEquals(url, result.value)
        assertEquals(PaymentCodeType.WECHAT_QR, result.type)
    }

    @Test fun decoderRejectsNonPaymentQrAndBlankImage() {
        val qr = MultiFormatWriter().encode("https://example.com/not-a-payment", BarcodeFormat.QR_CODE, 240, 240)
        val pixels = IntArray(240 * 240) { i -> if (qr[i % 240, i / 240]) 0xff000000.toInt() else -1 }
        assertEquals(emptyList<PaymentCode>(), PaymentQrDecoder.decode(240, 240, pixels))
        assertEquals(emptyList<PaymentCode>(), PaymentQrDecoder.decode(240, 240, IntArray(240 * 240) { -1 }))
    }

    @Test fun multipleDistinctPaymentQrCodesStayDistinct() {
        val width = 640
        val pixels = IntArray(width * 360) { -1 }
        listOf("https://qr.alipay.com/TEST_A", "https://qr.alipay.com/TEST_B").forEachIndexed { i, url ->
            val qr = MultiFormatWriter().encode(url, BarcodeFormat.QR_CODE, 240, 240)
            for (y in 0 until 240) for (x in 0 until 240) {
                pixels[(y + 60) * width + x + 20 + 340 * i] = if (qr[x, y]) 0xff000000.toInt() else -1
            }
        }
        assertEquals(2, PaymentQrDecoder.decode(width, 360, pixels).size)
    }
}
