// Modified by Xushengming222, 2026-10-15: electricity payment enhancement; see CHANGES.md.
package com.ntu.electricity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ntu.electricity.data.local.UserPreferences
import com.ntu.electricity.payment.PaymentRequest
import com.ntu.electricity.ui.screens.HistoryScreen
import com.ntu.electricity.ui.screens.HomeScreen
import com.ntu.electricity.ui.screens.PaymentScreen
import com.ntu.electricity.ui.screens.QueryScreen
import com.ntu.electricity.ui.theme.NTUElectricityTheme
import com.ntu.electricity.viewmodel.HistoryViewModel
import com.ntu.electricity.viewmodel.HomeViewModel
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val prefs = remember { UserPreferences(this) }
            var colorIndex by remember { mutableIntStateOf(prefs.colorIndex) }
            NTUElectricityTheme(colorIndex = colorIndex) {
                EcoCheckNavHost(
                    colorIndex = colorIndex,
                    onColorChange = { idx ->
                        colorIndex = idx
                        prefs.colorIndex = idx
                    }
                )
            }
        }
    }
}

@Composable
fun EcoCheckNavHost(
    colorIndex: Int = 0,
    onColorChange: (Int) -> Unit = {}
) {
    val navController = rememberNavController()
    var queryRequest by remember { mutableStateOf<PaymentRequest?>(null) }
    var paymentRequest by remember { mutableStateOf<PaymentRequest?>(null) }
    var showQueryDialog by remember { mutableStateOf(false) }

    LaunchedEffect(showQueryDialog) {
        if (!showQueryDialog) {
            delay(100)
            queryRequest = null
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(navController = navController, startDestination = "home") {
            composable("home") {
                val homeViewModel: HomeViewModel = viewModel()
                HomeScreen(
                    viewModel = homeViewModel,
                    onNavigateToHistory = { navController.navigate("history") },
                    onQuery = { paymentRequest = null; queryRequest = it; showQueryDialog = true },
                    onPayment = { showQueryDialog = false; queryRequest = null; paymentRequest = it },
                    colorIndex = colorIndex,
                    onColorChange = onColorChange
                )
            }
            composable("history",
                enterTransition = { slideInHorizontally(tween(350)) { it } },
                exitTransition = { slideOutHorizontally(tween(350)) { -it } },
                popEnterTransition = { slideInHorizontally(tween(350)) { -it } },
                popExitTransition = { slideOutHorizontally(tween(350)) { it } }
            ) {
                val historyViewModel: HistoryViewModel = viewModel()
                HistoryScreen(
                    viewModel = historyViewModel,
                    onBack = { navController.popBackStack() }
                )
            }
        }

        AnimatedVisibility(
            visible = showQueryDialog,
            enter = fadeIn(tween(150, easing = LinearEasing)) +
                scaleIn(tween(150, easing = LinearEasing), initialScale = 0.96f),
            exit = fadeOut(tween(75, easing = LinearEasing)) +
                scaleOut(tween(75, easing = LinearEasing), targetScale = 0.96f)
        ) {
            queryRequest?.let { request ->
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f))
                        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {},
                    contentAlignment = Alignment.Center
                ) {
                    QueryScreen(request = request, onDismiss = { showQueryDialog = false })
                }
            }
        }

        paymentRequest?.let { request ->
            PaymentScreen(request = request, onDismiss = { paymentRequest = null })
        }
    }
}
