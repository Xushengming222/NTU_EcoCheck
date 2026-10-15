package com.ntu.electricity.payment

import android.content.Context
import android.net.Uri
import org.json.JSONObject

internal class PaymentAutofill(context: Context, private val request: PaymentRequest, private val queryOnly: Boolean = false) {
    private val source = context.assets.open("payment-autofill.js").bufferedReader().use { it.readText() }

    fun scriptFor(url: String, allowLogin: Boolean, authorizeLoginNonce: String? = null): String? {
        val uri = Uri.parse(url)
        if (uri.scheme != "https") return null
        val host = uri.host
        if (host != "pay.ntu.edu.cn" && host != "authserver.ntu.edu.cn") return null
        val config = JSONObject().apply {
            put("campusId", request.campusId)
            put("buildingId", request.buildingId)
            put("roomId", request.roomId)
            put("campusName", request.campusName)
            put("buildingName", request.buildingName)
            put("roomName", request.roomName)
            put("queryOnly", queryOnly)
            if (authorizeLoginNonce != null) put("authorizeLoginNonce", authorizeLoginNonce)
            // Do not even inject credentials into the payment platform or a payment gateway.
            if (host == "authserver.ntu.edu.cn" && allowLogin) {
                put("credentials", JSONObject().apply {
                    put("studentId", request.studentId)
                    put("password", request.password)
                })
            }
        }
        return "$source\nJSON.stringify(ecoCheckPaymentStep($config));"
    }
}
