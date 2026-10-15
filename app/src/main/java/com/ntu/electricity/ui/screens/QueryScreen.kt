package com.ntu.electricity.ui.screens

import android.net.Uri
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.ntu.electricity.EcoCheckApplication
import com.ntu.electricity.data.local.entity.QueryHistoryEntity
import com.ntu.electricity.payment.PaymentBrowser
import com.ntu.electricity.payment.PaymentRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val STEP_INIT = 0
private const val STEP_DONE = 8

/** The original progress/result card, with protected login and manual verification prompts. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueryScreen(request: PaymentRequest, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var browser by remember { mutableStateOf<PaymentBrowser?>(null) }
    var stepLog by remember { mutableStateOf("正在连接...") }
    var currentStep by remember { mutableIntStateOf(STEP_INIT) }
    var running by remember { mutableStateOf(true) }
    var onLoginPage by remember { mutableStateOf(false) }
    var loginRequired by remember { mutableStateOf(false) }
    var queryResultData by remember { mutableStateOf<QueryHistoryEntity?>(null) }
    var historyError by remember { mutableStateOf(false) }
    val showLogin = running && onLoginPage && loginRequired && queryResultData == null
    val progressSteps = listOf("正在登录", "进入电费查询", "选择校区", "选择楼栋", "查询电量")
    fun progressState(index: Int): StepProgress {
        val doneAt = listOf(4, 5, 6, 7, STEP_DONE)
        return when {
            currentStep >= STEP_DONE -> StepProgress.DONE
            currentStep >= doneAt[index] -> StepProgress.DONE
            index == 0 || currentStep >= doneAt.getOrElse(index - 1) { STEP_INIT } -> StepProgress.LOADING
            else -> StepProgress.PENDING
        }
    }

    DisposableEffect(Unit) { onDispose { browser?.destroy() } }
    BackHandler(onBack = onDismiss)
    LaunchedEffect(queryResultData) {
        queryResultData?.let { data ->
            try {
                withContext(Dispatchers.IO) {
                    (context.applicationContext as EcoCheckApplication).database.queryHistoryDao().insert(data)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { historyError = true }
        }
    }
    if (!running && queryResultData == null || historyError) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(if (historyError) "历史保存失败" else "查询未完成") },
            text = { Text(if (historyError) "已查到电量，但本次历史记录未能保存。" else stepLog) },
            confirmButton = { TextButton(onClick = onDismiss) { Text("确定") } }
        )
    }
    Column(Modifier.fillMaxSize().imePadding()) {
        if (showLogin) {
            Surface {
                Column {
                    TopAppBar(title = { Text("学校登录验证") }, navigationIcon = {
                        IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "取消查询") }
                    })
                    Text(stepLog, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    Text("请手动完成验证并登录，成功后继续查询。应用不会重复提交登录。",
                        Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = {
                    PaymentBrowser(
                        context = it, request = request, queryOnly = true,
                        onStatus = { stepLog = it },
                        onPage = { url ->
                            onLoginPage = Uri.parse(url).host == "authserver.ntu.edu.cn"
                            if (!onLoginPage) loginRequired = false
                        },
                        onProgress = {}, onAutomationStopped = { running = false },
                        onExternalPayment = {}, onPaymentCode = {},
                        onLoginRequired = { loginRequired = true },
                        onQueryStep = { currentStep = maxOf(currentStep, it.coerceIn(0, STEP_DONE)) },
                        onQueryResult = { queryResultData = it; currentStep = STEP_DONE }
                    ).also { browser = it }
                },
                update = { it.visibility = if (showLogin) View.VISIBLE else View.INVISIBLE }
            )
            if (!showLogin) {
                // Dialog card
                Card(
                    modifier = Modifier
                        .padding(horizontal = 32.dp)
                        .fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = 12.dp,
                        pressedElevation = 12.dp
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        if (currentStep == STEP_DONE && queryResultData != null) {
                            // ── Result view ──
                            val data = queryResultData!!
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "查询完成",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${data.electricity} 度",
                                style = MaterialTheme.typography.displayMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "${data.campus} ${data.building} ${data.room}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${data.date}  ${data.time}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                            Button(
                                onClick = onDismiss,
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.large
                            ) {
                                Text("确定")
                            }
                        } else {
                            // ── Progress view ──
                            Text(
                                text = "NTU 电费查询",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            progressSteps.forEachIndexed { index, label ->
                                StepProgressRow(
                                    label = label,
                                    state = progressState(index)
                                )
                            }
                            // Current detail log
                            if (stepLog.isNotEmpty() && currentStep > STEP_INIT) {
                                Text(
                                    text = stepLog,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            TextButton(onClick = onDismiss) {
                                Text("取消")
                            }
                        }
                    }
                }
            }
        }
    }
}

private enum class StepProgress { PENDING, LOADING, DONE }

@Composable
private fun StepProgressRow(label: String, state: StepProgress) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        when (state) {
            StepProgress.LOADING -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            StepProgress.DONE -> {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            StepProgress.PENDING -> {
                Spacer(modifier = Modifier.size(20.dp))
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = when (state) {
                StepProgress.LOADING -> MaterialTheme.colorScheme.onSurface
                StepProgress.DONE -> MaterialTheme.colorScheme.onSurface
                StepProgress.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            }
        )
    }
}
