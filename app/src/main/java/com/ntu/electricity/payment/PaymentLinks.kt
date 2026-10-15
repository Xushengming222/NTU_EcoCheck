package com.ntu.electricity.payment

import java.net.URI

internal enum class PaymentCodeType { WECHAT_QR, WECHAT_APP, WECHAT_H5, ALIPAY_APP, ALIPAY_QR }

internal data class PaymentCode(val value: String, val type: PaymentCodeType)

/** Classifies supplied provider links. Never invents an H5 order from a Native QR order. */
internal object PaymentLinks {
    fun parse(value: String): PaymentCode? {
        if (value.length !in 1..8192 || value.any { it.isWhitespace() || it.isISOControl() }) return null
        val uri = runCatching { URI(value) }.getOrNull() ?: return null
        if (uri.rawUserInfo != null || uri.port != -1) return null
        val type = when {
            uri.scheme == "weixin" && uri.host in setOf("wxpay", "pay.weixin.qq.com", "pay.wechatpay.cn") &&
                uri.path.orEmpty().startsWith("/bizpayurl") -> PaymentCodeType.WECHAT_QR
            uri.scheme == "weixin" && uri.host == "wap" && uri.path == "/pay" -> PaymentCodeType.WECHAT_APP
            uri.scheme == "https" && uri.host == "wx.tenpay.com" &&
                uri.path == "/cgi-bin/mmpayweb-bin/checkmweb" -> PaymentCodeType.WECHAT_H5
            uri.scheme in setOf("alipays", "alipay") && uri.host == "platformapi" &&
                uri.path == "/startapp" -> PaymentCodeType.ALIPAY_APP
            uri.scheme == "https" && uri.host == "qr.alipay.com" && !uri.path.isNullOrBlank() -> PaymentCodeType.ALIPAY_QR
            else -> return null
        }
        if (type != PaymentCodeType.ALIPAY_QR && uri.rawQuery.isNullOrBlank()) return null
        return PaymentCode(value, type)
    }
}
