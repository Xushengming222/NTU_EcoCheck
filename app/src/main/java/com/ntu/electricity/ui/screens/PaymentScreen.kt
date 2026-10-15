package com.ntu.electricity.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.ntu.electricity.payment.ExternalPayment
import com.ntu.electricity.payment.PaymentBrowser
import com.ntu.electricity.payment.PaymentRequest
import com.ntu.electricity.payment.PaymentCode
import com.ntu.electricity.payment.PaymentCodeType
import com.ntu.electricity.payment.PaymentQr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentScreen(request: PaymentRequest, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var browser by remember { mutableStateOf<PaymentBrowser?>(null) }
    var status by remember { mutableStateOf("正在打开官方缴费平台") }
    var page by remember { mutableStateOf(PaymentBrowser.ENTRY_URL) }
    var progress by remember { mutableIntStateOf(0) }
    var automated by remember { mutableStateOf(true) }
    var external by remember { mutableStateOf<ExternalPayment?>(null) }
    var scanHelp by remember { mutableStateOf<PaymentCode?>(null) }
    var lastCode by remember { mutableStateOf<PaymentCode?>(null) }
    var inspecting by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var pendingSave by remember { mutableStateOf<PaymentCode?>(null) }
    var openAfterSave by remember { mutableStateOf(false) }
    var mobileLayout by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    fun openScannerApp(code: PaymentCode) {
        val wechat = code.type in setOf(PaymentCodeType.WECHAT_QR, PaymentCodeType.WECHAT_APP, PaymentCodeType.WECHAT_H5)
        val name = if (wechat) "微信" else "支付宝"
        val packageName = if (wechat) "com.tencent.mm" else "com.eg.android.AlipayGphone"
        try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent == null) {
                status = "支付码已保存，未找到$name，请安装后使用扫一扫识别"
            } else {
                status = "支付码已保存，请在$name 的扫一扫中从相册选择支付码"
                context.startActivity(intent)
            }
        } catch (_: RuntimeException) {
            status = "支付码已保存，请手动打开$name，使用扫一扫从相册识别"
        }
    }

    val createPaymentImage = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        val code = pendingSave
        val shouldOpen = openAfterSave
        pendingSave = null
        if (uri != null && code != null) {
            scope.launch {
                saving = true
                try {
                    withContext(Dispatchers.IO) {
                        checkNotNull(context.contentResolver.openOutputStream(uri)).use { PaymentQr.write(code.value, it) }
                    }
                    status = "支付码已保存，请使用扫一扫从相册识别"
                    if (shouldOpen) openScannerApp(code)
                } catch (_: Exception) {
                    status = "支付码保存失败，请重新选择保存位置"
                } finally { saving = false }
            }
        }
    }

    fun saveCode(code: PaymentCode, openApp: Boolean) {
        scanHelp = null
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            pendingSave = code
            openAfterSave = openApp
            createPaymentImage.launch("NTU-payment.png")
            return
        }
        scope.launch {
            saving = true
            try {
                withContext(Dispatchers.IO) { PaymentQr.saveToGallery(context, code.value) }
                status = "支付码已保存到相册，请使用扫一扫识别"
                if (openApp) openScannerApp(code)
            } catch (_: Exception) {
                status = "支付码保存失败，请检查可用存储空间后重试"
            } finally { saving = false }
        }
    }

    DisposableEffect(Unit) { onDispose { browser?.destroy() } }
    BackHandler { if (browser?.goBack() != true) onDismiss() }

    scanHelp?.let { code ->
        val provider = if (code.type == PaymentCodeType.ALIPAY_QR) "支付宝" else "微信"
        AlertDialog(
            onDismissRequest = { scanHelp = null },
            title = { Text("${provider}扫码支付") },
            text = { Text("已识别到学校当前订单的支付码。保存后，在$provider“扫一扫”的相册中选择该支付码。二维码有效期以官方页面为准。") },
            confirmButton = {
                TextButton(onClick = { saveCode(code, openApp = true) }, enabled = !saving) { Text("保存并打开$provider") }
            },
            dismissButton = { TextButton(onClick = { scanHelp = null }) { Text("取消") } }
        )
    }

    external?.let { target ->
        AlertDialog(
            onDismissRequest = { external = null },
            title = { Text("打开${target.name}") },
            text = { Text("是否打开${target.name}继续官方支付流程？付款后请返回缴费页查看订单状态。") },
            confirmButton = {
                TextButton(onClick = {
                    external = null
                    try {
                        // Build a fresh explicit intent; never pass page-supplied components or extras.
                        context.startActivity(Intent(Intent.ACTION_VIEW, target.uri).apply {
                            addCategory(Intent.CATEGORY_BROWSABLE)
                            setPackage(target.packageName)
                        })
                    } catch (_: ActivityNotFoundException) {
                        status = "未安装${target.name}或无法打开，请使用官方页面提供的其他方式"
                        lastCode?.takeIf { it.type == PaymentCodeType.ALIPAY_QR }?.let { scanHelp = it }
                    } catch (_: SecurityException) {
                        status = "无法打开${target.name}，请使用官方页面提供的其他方式"
                    }
                }) { Text("打开${target.name}") }
            },
            dismissButton = { TextButton(onClick = { external = null }) { Text("取消") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("电费充值") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "关闭缴费页") }
                },
                actions = {
                    TextButton(onClick = {
                        mobileLayout = !mobileLayout
                        browser?.setMobileLayout(mobileLayout)
                    }) { Text(if (mobileLayout) "原网页" else "手机排版") }
                    IconButton(onClick = { automated = true; browser?.restart() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "重新打开缴费页")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(request.roomLabel, style = MaterialTheme.typography.titleSmall)
                Text(
                    // Show the actual page origin, without leaking tickets or query parameters.
                    runCatching { Uri.parse(page).let { "${it.scheme}://${it.host.orEmpty()}" } }.getOrDefault(""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(Modifier.fillMaxWidth()) {
                    Text(status, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                        maxLines = 3, overflow = TextOverflow.Ellipsis)
                    if (automated) {
                        TextButton(onClick = { browser?.stopAutomation() }) { Text("手动操作") }
                    }
                }
            }
            if (progress < 100) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            AndroidView(
                modifier = Modifier.fillMaxWidth().weight(1f),
                factory = {
                    PaymentBrowser(
                        context = it,
                        request = request,
                        onStatus = { value -> status = value },
                        onPage = { value ->
                            if (page != value) { lastCode = null; scanHelp = null }
                            page = value
                        },
                        onProgress = { value -> progress = value },
                        onAutomationStopped = { automated = false },
                        onExternalPayment = { value -> external = value },
                        onPaymentCode = { code ->
                            lastCode = code
                            if (code.type == PaymentCodeType.WECHAT_QR) scanHelp = code
                            else browser?.openPaymentCode(code)
                        }
                    ).also { view -> browser = view }
                }
            )
            if (!automated) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Button(
                        onClick = { inspecting = true; browser?.inspectPayment { inspecting = false } },
                        enabled = !inspecting && !saving,
                        modifier = Modifier.weight(1f)
                    ) { Text(if (inspecting) "正在识别…" else "识别并打开支付") }
                    lastCode?.let { code ->
                        TextButton(onClick = { saveCode(code, openApp = false) }, enabled = !saving) {
                            Text(if (saving) "正在保存…" else "保存支付码")
                        }
                    }
                }
            }
        }
    }
}
