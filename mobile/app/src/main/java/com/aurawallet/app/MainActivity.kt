package com.aurawallet.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aurawallet.app.ui.CategorizeScreen
import com.aurawallet.app.ui.CategorizeViewModel
import com.aurawallet.app.ui.HistoryDetailsScreen
import com.aurawallet.app.ui.HistoryScreen
import com.aurawallet.app.ui.HistoryViewModel
import com.aurawallet.app.ui.theme.AuraTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AuraTheme {
                // Проста навігація без окремої бібліотеки: main → history → details (рішення D17).
                var screen by rememberSaveable { mutableStateOf("main") }
                var detailId by rememberSaveable { mutableLongStateOf(0L) }
                val categorizeVm: CategorizeViewModel = viewModel(factory = CategorizeViewModel.Factory)
                val historyVm: HistoryViewModel = viewModel(factory = HistoryViewModel.Factory)

                BackHandler(enabled = screen != "main") { screen = if (screen == "details") "history" else "main" }

                when (screen) {
                    "history" -> HistoryScreen(historyVm, onBack = { screen = "main" }, onOpen = { detailId = it; screen = "details" })
                    "details" -> HistoryDetailsScreen(historyVm, detailId, onBack = { screen = "history" })
                    else -> CategorizeScreen(categorizeVm, onOpenHistory = { screen = "history" })
                }
            }
        }
    }
}
