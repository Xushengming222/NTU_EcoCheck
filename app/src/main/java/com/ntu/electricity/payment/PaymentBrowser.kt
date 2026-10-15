package com.ntu.electricity.payment

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.net.http.SslError
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.SystemClock
import android.util.Base64
import android.view.View
import android.webkit.CookieManager
import android.webkit.JsResult
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import com.ntu.electricity.EcoCheckApplication
import com.ntu.electricity.data.local.entity.QueryHistoryEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
import org.json.JSONTokener
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

internal data class ExternalPayment(val uri: Uri, val packageName: String, val name: String)

/** Visible official browser, sharing the existing query WebView's CookieManager. No JS bridge. */
@SuppressLint("SetJavaScriptEnabled", "ViewConstructor")
internal class PaymentBrowser(
    context: Context,
    private val request: PaymentRequest,
    private val onStatus: (String) -> Unit,
    private val onPage: (String) -> Unit,
    private val onProgress: (Int) -> Unit,
    private val onAutomationStopped: () -> Unit,
    private val onExternalPayment: (ExternalPayment) -> Unit,
    private val onPaymentCode: (PaymentCode) -> Unit,
    private val queryOnly: Boolean = false,
    private val onQueryResult: (QueryHistoryEntity) -> Unit = {},
    private val onLoginRequired: () -> Unit = {},
    private val onQueryStep: (Int) -> Unit = {}
) : FrameLayout(context) {
    private val handler = Handler(Looper.getMainLooper())
    private val autofill = PaymentAutofill(context, request, queryOnly)
    private val loginGate = (context.applicationContext as EcoCheckApplication).loginAttemptGate
    private val mobileLayout = context.assets.open("payment-mobile.js").bufferedReader().use { it.readText() }
    private val qrImages = context.assets.open("payment-qr-images.js").bufferedReader().use { it.readText() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val windows = mutableListOf<WebView>()
    private var stopped = false
    private var destroyed = false
    private var loginAttempted = !loginGate.canAttempt(request.studentId)
    private var mobileLayoutEnabled = true
    private var generation = 0
    private var pageGeneration = 0
    private var startedAt = SystemClock.elapsedRealtime()
    private lateinit var main: WebView

    private val poll = object : Runnable {
        override fun run() {
            if (destroyed || stopped) return
            if (Uri.parse(main.url.orEmpty()).host == "authserver.ntu.edu.cn") {
                // Human verification is not an automation timeout; keep the official page interactive.
                startedAt = SystemClock.elapsedRealtime()
            }
            if (SystemClock.elapsedRealtime() - startedAt > 60_000) {
                stopAutomation(if (queryOnly) "查询超时，请检查网络后重试" else "自动填写已超时，请在官方页面继续操作")
                return
            }
            val script = autofill.scriptFor(main.url.orEmpty(), !loginAttempted)
            if (script == null || main.progress < 100 || windows.lastOrNull() !== main) {
                handler.postDelayed(this, 750)
                return
            }
            val currentGeneration = generation
            main.evaluateJavascript(script) { value ->
                if (destroyed || stopped || currentGeneration != generation) return@evaluateJavascript
                val result = runCatching {
                    JSONObject(JSONTokener(value).nextValue() as String)
                }.getOrNull()
                if (result != null) {
                    val stage = result.optString("stage")
                    val message = result.optString("message")
                    if (queryOnly) onQueryStep(result.optInt("queryStep", 0))
                    if (stage == "login_ready" && !loginAttempted) {
                        loginAttempted = true
                        if (loginGate.reserve(request.studentId)) {
                            val authorized = autofill.scriptFor(main.url.orEmpty(), true, result.optString("nonce"))
                            if (authorized != null) main.evaluateJavascript(authorized, null)
                        }
                    }
                    if (stage == "manual_login" && !loginAttempted) {
                        loginAttempted = true
                        loginGate.requireManual(request.studentId)
                    }
                    if (stage == "manual_login") onLoginRequired()
                    if (queryOnly && stage == "query_result") {
                        val data = result.getJSONObject("data")
                        stopAutomation("查询完成")
                        onQueryResult(QueryHistoryEntity(date = data.getString("date"), time = data.getString("time"),
                            electricity = data.getString("electricity"), campus = data.getString("campus"),
                            building = data.getString("building"), room = data.getString("room")))
                        return@evaluateJavascript
                    }
                    if (stage in listOf("ready", "manual", "mismatch")) {
                        stopAutomation(message)
                        return@evaluateJavascript
                    }
                    onStatus(message)
                }
                handler.postDelayed(this, 750)
            }
        }
    }

    init {
        main = createWebView()
        addWindow(main)
        main.loadUrl(if (queryOnly) QUERY_ENTRY_URL else ENTRY_URL)
        handler.postDelayed(poll, 750)
    }

    fun stopAutomation(message: String = "已停止自动填写，请手动操作") {
        stopped = true
        generation++
        handler.removeCallbacks(poll)
        onStatus(message)
        onAutomationStopped()
    }

    fun restart() {
        windows.drop(1).toList().forEach { closeWindow(it) }
        handler.removeCallbacks(poll)
        generation++
        stopped = false
        // Refresh/close/reopen must never grant another automatic login attempt.
        loginAttempted = loginAttempted || !loginGate.canAttempt(request.studentId)
        startedAt = SystemClock.elapsedRealtime()
        main.loadUrl(if (queryOnly) QUERY_ENTRY_URL else ENTRY_URL)
        handler.postDelayed(poll, 750)
    }

    fun goBack(): Boolean {
        val active = windows.lastOrNull() ?: return false
        // Back navigation is a manual takeover: never refill a room after the user changed it.
        stopAutomation()
        return when {
            active.canGoBack() -> { active.goBack(); true }
            active !== main -> { closeWindow(active); true }
            else -> false
        }
    }

    fun setMobileLayout(enabled: Boolean) {
        mobileLayoutEnabled = enabled
        windows.forEach { applyMobileLayout(it) }
    }

    private fun applyMobileLayout(view: WebView) {
        // Keep the original query document and its handlers unchanged.
        if (queryOnly && Uri.parse(view.url.orEmpty()).host != "authserver.ntu.edu.cn") return
        view.evaluateJavascript("$mobileLayout\necoCheckMobileLayout($mobileLayoutEnabled);", null)
    }

    fun destroy() {
        destroyed = true
        scope.cancel()
        handler.removeCallbacksAndMessages(null)
        windows.toList().forEach {
            removeView(it)
            it.stopLoading()
            it.webChromeClient = null
            it.destroy()
        }
        windows.clear()
        CookieManager.getInstance().flush()
    }

    /** Reads QR images and the composited window locally; software WebView.draw can be blank. */
    fun inspectPayment(onFinished: () -> Unit) {
        if (queryOnly) { onFinished(); return }
        val active = windows.lastOrNull()
        if (active == null || active.width == 0 || active.height == 0 || active.progress < 100) {
            onStatus("请等待支付二维码显示后再识别")
            onFinished()
            return
        }
        val capturedPage = pageGeneration
        onStatus("正在本机识别支付码…")
        scope.launch {
            try {
                val imageData = withTimeoutOrNull(2_000) {
                    suspendCancellableCoroutine<String?> { pending ->
                        active.evaluateJavascript("$qrImages\nJSON.stringify(ecoCheckQrImages());") { value ->
                            if (pending.isActive) pending.resume(value)
                        }
                    }
                }
                val codes = withContext(Dispatchers.Default) { decodeQrImages(imageData) }.toMutableList()
                var captured = false
                val bitmap = withTimeoutOrNull(3_000) { PaymentCapture.capture(active) }
                if (bitmap != null) {
                    try {
                        captured = true
                        codes += withContext(Dispatchers.Default) { PaymentQr.decode(bitmap) }
                    } finally { bitmap.recycle() }
                }
                val distinctCodes = codes.distinctBy { it.value }
                if (capturedPage != pageGeneration || active !== windows.lastOrNull()) {
                    onStatus("页面已变化，请在当前支付页面重新识别")
                } else when (distinctCodes.size) {
                    0 -> onStatus(if (captured) "未识别到支付码，请等待二维码加载完整后重试" else "无法读取支付画面，请等待页面显示后重试")
                    1 -> {
                        onStatus("支付码已识别，请选择后续操作；支付结果以官方订单为准")
                        onPaymentCode(distinctCodes.single())
                    }
                    else -> onStatus("页面有多个支付码，请放大需要使用的二维码后再识别")
                }
            } catch (_: Exception) {
                if (!destroyed) onStatus("支付码识别失败，请放大二维码后重试")
            } finally {
                onFinished()
            }
        }
    }

    private fun decodeQrImages(value: String?): List<PaymentCode> {
        val images = runCatching { JSONArray(JSONTokener(value.orEmpty()).nextValue() as String) }.getOrNull()
            ?: return emptyList()
        return (0 until minOf(images.length(), 6)).flatMap { index ->
            val data = images.optString(index)
            if (!data.startsWith("data:image/png;base64,") || data.length > 2_000_000) return@flatMap emptyList()
            val bitmap = runCatching {
                val bytes = Base64.decode(data.substringAfter(','), Base64.DEFAULT)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                if (bounds.outWidth !in 1..1600 || bounds.outHeight !in 1..1600) null
                else BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }.getOrNull() ?: return@flatMap emptyList()
            try { PaymentQr.decode(bitmap) } finally { bitmap.recycle() }
        }
    }

    fun openPaymentCode(code: PaymentCode) {
        if (queryOnly) return
        when (code.type) {
            PaymentCodeType.WECHAT_QR -> onPaymentCode(code)
            PaymentCodeType.WECHAT_H5 -> {
                val view = windows.lastOrNull() ?: return
                val referer = view.url.orEmpty()
                // Use the merchant-supplied H5 URL unchanged, preserving its real referring page.
                view.loadUrl(code.value, mapOf("Referer" to referer))
            }
            PaymentCodeType.WECHAT_APP -> onExternalPayment(ExternalPayment(Uri.parse(code.value), "com.tencent.mm", "微信"))
            PaymentCodeType.ALIPAY_APP, PaymentCodeType.ALIPAY_QR ->
                onExternalPayment(ExternalPayment(Uri.parse(code.value), "com.eg.android.AlipayGphone", "支付宝"))
        }
    }

    private fun addWindow(view: WebView) {
        windows.lastOrNull()?.visibility = View.GONE
        windows.add(view)
        addView(view, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        view.requestFocus()
    }

    private fun closeWindow(view: WebView) {
        if (view === main) return
        windows.remove(view)
        removeView(view)
        view.stopLoading()
        view.webChromeClient = null
        view.destroy()
        windows.lastOrNull()?.let {
            it.visibility = View.VISIBLE
            onPage(it.url.orEmpty())
        }
    }

    private fun createWebView(): WebView = WebView(context).apply {
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            // The desktop project has the same schoolid/building/room fields as the existing query.
            userAgentString = DESKTOP_UA
            useWideViewPort = true
            // A desktop overview was shrinking the whole checkout to a tiny strip on phones.
            loadWithOverviewMode = false
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            allowFileAccess = false
            allowContentAccess = false
            setSupportMultipleWindows(true)
        }
        val cookies = CookieManager.getInstance()
        cookies.setAcceptCookie(true)
        cookies.setAcceptThirdPartyCookies(this, true)
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                // Some payment pages launch their app through a hidden iframe.
                if (!request.isForMainFrame && uri.scheme in listOf("https", "http", "about")) return false
                if (uri.scheme == "http" && uri.host in SCHOOL_HOSTS) {
                    view.loadUrl(uri.buildUpon().scheme("https").build().toString())
                    return true
                }
                if (uri.scheme == "https" || uri.toString() == "about:blank") return false
                if (queryOnly) {
                    stopAutomation("查询无需打开支付应用，请返回首页使用一键缴费")
                    return true
                }
                val external = paymentIntent(uri)
                val code = PaymentLinks.parse(external?.uri?.toString() ?: uri.toString())
                if (code?.type == PaymentCodeType.WECHAT_QR) {
                    stopAutomation("这是微信扫码支付订单，请保存支付码后在微信中识别")
                    onPaymentCode(code)
                } else if (external != null) {
                    stopAutomation("请确认是否打开${external.name}，付款后返回查看官方订单状态")
                    onExternalPayment(external)
                } else {
                    onStatus("此链接无法在内置页面打开，请使用官方页面提供的其他支付方式")
                }
                return true
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                if (view === windows.lastOrNull()) {
                    pageGeneration++
                    onPage(url.orEmpty())
                    if (stopped && !queryOnly) onStatus("请在官方页面继续支付，结果以订单状态为准")
                }
            }

            override fun onPageFinished(view: WebView, url: String?) {
                CookieManager.getInstance().flush()
                applyMobileLayout(view)
                if (view === windows.lastOrNull()) onPage(url.orEmpty())
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) stopAutomation("页面加载失败，请检查网络后重新打开")
            }

            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                if (request.isForMainFrame && response.statusCode >= 400) {
                    stopAutomation("官方页面暂时不可用（${response.statusCode}），请稍后重试")
                }
            }

            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                handler.cancel()
                stopAutomation("网站证书校验失败，请稍后重试")
            }
        }
        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                if (view === windows.lastOrNull()) onProgress(newProgress)
            }

            override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
                if (queryOnly && Uri.parse(view.url.orEmpty()).host != "authserver.ntu.edu.cn") return false
                if (!isUserGesture || windows.size >= 3) {
                    onStatus("页面弹窗未打开，请再次点击官方页面中的按钮")
                    return false
                }
                // Keep POST-based bank/QR payment windows inside the same cookie session.
                val popup = createWebView()
                addWindow(popup)
                (resultMsg.obj as WebView.WebViewTransport).webView = popup
                resultMsg.sendToTarget()
                return true
            }

            override fun onCloseWindow(window: WebView) = closeWindow(window)

            override fun onJsAlert(view: WebView, url: String?, message: String?, result: JsResult): Boolean {
                AlertDialog.Builder(context).setTitle("官方页面提示").setMessage(message)
                    .setPositiveButton("确定") { _, _ -> result.confirm() }
                    .setOnCancelListener { result.cancel() }.show()
                return true
            }

            override fun onJsConfirm(view: WebView, url: String?, message: String?, result: JsResult): Boolean {
                AlertDialog.Builder(context).setTitle("官方页面确认").setMessage(message)
                    .setPositiveButton("确定") { _, _ -> result.confirm() }
                    .setNegativeButton("取消") { _, _ -> result.cancel() }
                    .setOnCancelListener { result.cancel() }.show()
                return true
            }
        }
    }

    private fun paymentIntent(uri: Uri): ExternalPayment? {
        val target = if (uri.scheme == "intent") {
            runCatching { Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME).data }.getOrNull()
        } else uri
        return when (target?.scheme) {
            "alipays", "alipay" -> ExternalPayment(target, "com.eg.android.AlipayGphone", "支付宝")
            "weixin" -> ExternalPayment(target, "com.tencent.mm", "微信")
            else -> null
        }
    }

    companion object {
        const val ENTRY_URL = "https://pay.ntu.edu.cn/showPublic"
        const val QUERY_ENTRY_URL = "https://pay.ntu.edu.cn/innerUserLogin"
        private val SCHOOL_HOSTS = setOf("pay.ntu.edu.cn", "authserver.ntu.edu.cn", "cas.ntu.edu.cn")
        private const val DESKTOP_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36"
    }
}
