package com.example.ui.navigation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.data.model.ScannerMode
import com.example.data.model.ScanSource
import com.example.data.preferences.UserSettings
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.QrGeneratorScreen
import com.example.ui.screens.ScanResultScreen
import com.example.ui.screens.ScannerScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.viewmodel.HistoryViewModel
import com.example.ui.viewmodel.QrGeneratorViewModel
import com.example.ui.viewmodel.ScanWorkflowViewModel
import com.example.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable object HomeRoute
@Serializable data class ScanRoute(val modeName: String = ScannerMode.ALL.name)
@Serializable object HistoryRoute
@Serializable object SettingsRoute
@Serializable object GeneratorRoute
@Serializable object ResultRoute

private data class TopLevelDestination(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String,
    val isSelected: (androidx.navigation.NavDestination?) -> Boolean,
    val navigate: () -> Unit
)

@Composable
fun QRScannerAppShell(
    settingsViewModel: SettingsViewModel,
    historyViewModel: HistoryViewModel,
    scanWorkflowViewModel: ScanWorkflowViewModel,
    generatorViewModel: QrGeneratorViewModel
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
    val historyState by historyViewModel.uiState.collectAsStateWithLifecycle()
    val recentScans by historyViewModel.recentScans.collectAsStateWithLifecycle()
    val totalScanCount by historyViewModel.totalCount.collectAsStateWithLifecycle()
    val currentResult by scanWorkflowViewModel.currentResult.collectAsStateWithLifecycle()
    val isSavedToHistory by scanWorkflowViewModel.isSavedToHistory.collectAsStateWithLifecycle()
    val galleryScanning by scanWorkflowViewModel.galleryScanning.collectAsStateWithLifecycle()
    val galleryError by scanWorkflowViewModel.galleryError.collectAsStateWithLifecycle()
    val statusMessage by scanWorkflowViewModel.statusMessage.collectAsStateWithLifecycle()
    val generatorState by generatorViewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(statusMessage) {
        statusMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            scanWorkflowViewModel.clearStatusMessage()
        }
    }

    val showMessage: (String) -> Unit = { msg ->
        coroutineScope.launch {
            snackbarHostState.showSnackbar(msg)
        }
    }

    // Android Photo Picker for zero-permission Gallery QR/Barcode scanning
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scanWorkflowViewModel.scanFromGalleryUri(
                context = context,
                uri = uri,
                autoSave = settings.saveHistoryEnabled
            ) {
                navController.navigate(ResultRoute)
            }
        }
    }

    val launchGalleryScan = {
        photoPickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val isTopLevel = currentDestination?.let { dest ->
        dest.hasRoute<HomeRoute>() ||
            dest.hasRoute<ScanRoute>() ||
            dest.hasRoute<HistoryRoute>() ||
            dest.hasRoute<SettingsRoute>()
    } ?: true

    val topLevelDestinations = listOf(
        TopLevelDestination(
            label = "Home",
            selectedIcon = Icons.Rounded.Home,
            unselectedIcon = Icons.Outlined.Home,
            testTag = "nav_home",
            isSelected = { it?.hasRoute<HomeRoute>() == true },
            navigate = {
                navController.navigate(HomeRoute) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        ),
        TopLevelDestination(
            label = "Scan",
            selectedIcon = Icons.Rounded.QrCodeScanner,
            unselectedIcon = Icons.Outlined.QrCodeScanner,
            testTag = "nav_scan",
            isSelected = { it?.hasRoute<ScanRoute>() == true },
            navigate = {
                navController.navigate(ScanRoute(ScannerMode.ALL.name)) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                }
            }
        ),
        TopLevelDestination(
            label = "History",
            selectedIcon = Icons.Rounded.History,
            unselectedIcon = Icons.Outlined.History,
            testTag = "nav_history",
            isSelected = { it?.hasRoute<HistoryRoute>() == true },
            navigate = {
                navController.navigate(HistoryRoute) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        ),
        TopLevelDestination(
            label = "Settings",
            selectedIcon = Icons.Rounded.Settings,
            unselectedIcon = Icons.Outlined.Settings,
            testTag = "nav_settings",
            isSelected = { it?.hasRoute<SettingsRoute>() == true },
            navigate = {
                navController.navigate(SettingsRoute) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        )
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 600.dp

        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (isTopLevel && !useNavigationRail) {
                    NavigationBar {
                        topLevelDestinations.forEach { dest ->
                            val selected = dest.isSelected(currentDestination)
                            NavigationBarItem(
                                selected = selected,
                                onClick = dest.navigate,
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label
                                    )
                                },
                                label = { Text(dest.label) },
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isTopLevel && useNavigationRail) {
                    NavigationRail(modifier = Modifier.fillMaxHeight()) {
                        topLevelDestinations.forEach { dest ->
                            val selected = dest.isSelected(currentDestination)
                            NavigationRailItem(
                                selected = selected,
                                onClick = dest.navigate,
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label
                                    )
                                },
                                label = { Text(dest.label) },
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    NavHost(
                        navController = navController,
                        startDestination = HomeRoute
                    ) {
                        composable<HomeRoute> {
                            HomeScreen(
                                recentScans = recentScans,
                                totalScanCount = totalScanCount,
                                onScanQrClick = {
                                    navController.navigate(ScanRoute(ScannerMode.QR_ONLY.name)) {
                                        launchSingleTop = true
                                    }
                                },
                                onScanBarcodeClick = {
                                    navController.navigate(ScanRoute(ScannerMode.BARCODE_ONLY.name)) {
                                        launchSingleTop = true
                                    }
                                },
                                onGenerateQrClick = {
                                    navController.navigate(GeneratorRoute) {
                                        launchSingleTop = true
                                    }
                                },
                                onGalleryScanClick = launchGalleryScan,
                                onHistoryClick = {
                                    navController.navigate(HistoryRoute) {
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                onSettingsClick = {
                                    navController.navigate(SettingsRoute) {
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                onRecentScanClick = { entity ->
                                    scanWorkflowViewModel.openFromHistory(entity) {
                                        navController.navigate(ResultRoute)
                                    }
                                },
                                onShowMessage = showMessage
                            )
                        }

                        composable<ScanRoute> { backStackEntry ->
                            val route = backStackEntry.toRoute<ScanRoute>()
                            val mode = runCatching { ScannerMode.valueOf(route.modeName) }
                                .getOrDefault(ScannerMode.ALL)
                            ScannerScreen(
                                initialMode = mode,
                                settings = settings,
                                onCodeDetected = { rawValue, formatCode ->
                                    scanWorkflowViewModel.onCodeDetected(
                                        rawValue = rawValue,
                                        formatCode = formatCode,
                                        source = ScanSource.CAMERA,
                                        autoSave = settings.saveHistoryEnabled
                                    ) {
                                        navController.navigate(ResultRoute)
                                    }
                                },
                                onGalleryScanClick = launchGalleryScan
                            )
                        }

                        composable<HistoryRoute> {
                            HistoryScreen(
                                uiState = historyState,
                                onSearchQueryChange = historyViewModel::updateSearchQuery,
                                onFilterSelected = historyViewModel::selectFilter,
                                onItemClick = { entity ->
                                    scanWorkflowViewModel.openFromHistory(entity) {
                                        navController.navigate(ResultRoute)
                                    }
                                },
                                onDeleteItem = historyViewModel::deleteItem,
                                onClearAll = historyViewModel::clearAll,
                                onScanNowClick = {
                                    navController.navigate(ScanRoute(ScannerMode.ALL.name))
                                },
                                onClearError = historyViewModel::clearError,
                                onShowMessage = showMessage
                            )
                        }

                        composable<SettingsRoute> {
                            SettingsScreen(
                                settings = settings,
                                onThemeModeChange = settingsViewModel::setThemeMode,
                                onSoundChange = settingsViewModel::setSoundEnabled,
                                onVibrationChange = settingsViewModel::setVibrationEnabled,
                                onAutoScanChange = settingsViewModel::setAutoScanEnabled,
                                onSaveHistoryChange = settingsViewModel::setSaveHistoryEnabled
                            )
                        }

                        composable<GeneratorRoute> {
                            QrGeneratorScreen(
                                uiState = generatorState,
                                viewModel = generatorViewModel,
                                onBack = { navController.popBackStack() },
                                onShowMessage = showMessage
                            )
                        }

                        composable<ResultRoute> {
                            ScanResultScreen(
                                result = currentResult,
                                isSavedToHistory = isSavedToHistory,
                                onSaveToHistory = scanWorkflowViewModel::saveCurrentResultToHistory,
                                onBack = { navController.popBackStack() },
                                onScanAnother = {
                                    navController.popBackStack()
                                    if (currentDestination?.hasRoute<ScanRoute>() != true) {
                                        navController.navigate(ScanRoute(ScannerMode.ALL.name)) {
                                            launchSingleTop = true
                                        }
                                    }
                                },
                                onShowMessage = showMessage
                            )
                        }
                    }

                    // Overlay indicator while decoding gallery image
                    if (galleryScanning) {
                        Surface(
                            modifier = Modifier.align(Alignment.Center),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                            tonalElevation = 6.dp
                        ) {
                            Box(
                                modifier = Modifier.padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                }
            }
        }
    }

    galleryError?.let { errorText ->
        AlertDialog(
            onDismissRequest = scanWorkflowViewModel::dismissGalleryError,
            title = { Text("No Code Found") },
            text = { Text(errorText) },
            confirmButton = {
                TextButton(
                    onClick = {
                        scanWorkflowViewModel.dismissGalleryError()
                        launchGalleryScan()
                    },
                    modifier = Modifier.testTag("gallery_error_retry_button")
                ) {
                    Text("Try Another Image")
                }
            },
            dismissButton = {
                TextButton(onClick = scanWorkflowViewModel::dismissGalleryError) {
                    Text("Dismiss")
                }
            }
        )
    }
}
