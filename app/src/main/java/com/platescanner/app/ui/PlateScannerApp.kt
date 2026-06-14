package com.platescanner.app.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.platescanner.app.ui.screen.AboutScreen
import com.platescanner.app.ui.screen.HomeScreen
import com.platescanner.app.ui.screen.RecordListScreen
import com.platescanner.app.ui.screen.ScannerScreen
import com.platescanner.app.ui.screen.settings.SettingsScreen

/**
 * Top-level navigation graph for the app.
 */
@Composable
fun PlateScannerApp() {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = Routes.HOME
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                onStartScan = { navController.navigate(Routes.SCANNER) },
                onViewRecords = { navController.navigate(Routes.RECORDS) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.SCANNER) {
            ScannerScreen(
                onBack = { navController.popBackStack() },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.RECORDS) {
            RecordListScreen(
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenAbout = { navController.navigate(Routes.ABOUT) },
            )
        }
        composable(Routes.ABOUT) {
            AboutScreen(
                onBack = { navController.popBackStack() },
            )
        }
    }
}

object Routes {
    const val HOME = "home"
    const val SCANNER = "scanner"
    const val RECORDS = "records"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
}