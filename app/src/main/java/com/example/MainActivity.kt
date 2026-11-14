package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.navigation.QRScannerAppShell
import com.example.ui.theme.QRScannerTheme
import com.example.ui.viewmodel.AppViewModelFactory
import com.example.ui.viewmodel.HistoryViewModel
import com.example.ui.viewmodel.QrGeneratorViewModel
import com.example.ui.viewmodel.ScanWorkflowViewModel
import com.example.ui.viewmodel.SettingsViewModel

class MainActivity : ComponentActivity() {

    private val appContainer by lazy { AppContainer(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val factory = AppViewModelFactory(
            historyRepository = appContainer.historyRepository,
            preferencesRepository = appContainer.preferencesRepository
        )

        setContent {
            val settingsViewModel: SettingsViewModel = viewModel(factory = factory)
            val historyViewModel: HistoryViewModel = viewModel(factory = factory)
            val scanWorkflowViewModel: ScanWorkflowViewModel = viewModel(factory = factory)
            val generatorViewModel: QrGeneratorViewModel = viewModel(factory = factory)

            val settings by settingsViewModel.settings.collectAsStateWithLifecycle()

            QRScannerTheme(themeMode = settings.themeMode) {
                QRScannerAppShell(
                    settingsViewModel = settingsViewModel,
                    historyViewModel = historyViewModel,
                    scanWorkflowViewModel = scanWorkflowViewModel,
                    generatorViewModel = generatorViewModel
                )
            }
        }
    }
}
