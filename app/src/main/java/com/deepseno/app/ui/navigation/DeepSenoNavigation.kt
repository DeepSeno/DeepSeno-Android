package com.enmooy.deepseno.ui.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Source
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.enmooy.deepseno.i18n.LocalStrings
import com.enmooy.deepseno.ui.screen.briefing.BriefingScreen
import com.enmooy.deepseno.ui.screen.capture.CaptureScreen
import com.enmooy.deepseno.ui.screen.chat.ChatScreen
import com.enmooy.deepseno.ui.screen.settings.SettingsScreen
import com.enmooy.deepseno.ui.screen.sources.SourceDetailScreen
import com.enmooy.deepseno.ui.screen.sources.SourcesScreen
import com.enmooy.deepseno.ui.theme.AccentGreen
import com.enmooy.deepseno.ui.theme.BgSecondary
import com.enmooy.deepseno.ui.theme.TextSecondary

sealed class Tab(val route: String, val icon: ImageVector, val labelKey: (com.enmooy.deepseno.i18n.Strings) -> String) {
    data object Capture : Tab("capture", Icons.Default.Mic, { it.tabCapture })
    data object Sources : Tab("sources", Icons.Default.Source, { it.tabSources })
    data object Chat : Tab("chat", Icons.Default.Psychology, { it.tabAI })
    data object Briefing : Tab("briefing", Icons.Default.Description, { it.tabBriefing })
    data object Settings : Tab("settings", Icons.Default.Settings, { it.tabSettings })
}

private val tabs = listOf(Tab.Capture, Tab.Sources, Tab.Chat, Tab.Briefing, Tab.Settings)

@Composable
fun DeepSenoNavigation(
    appState: com.enmooy.deepseno.ui.viewmodel.AppState = androidx.hilt.navigation.compose.hiltViewModel(),
) {
    val navController = rememberNavController()
    val strings = LocalStrings.current

    Scaffold(
        // enableEdgeToEdge() removes the system bar insets — make Scaffold reserve
        // padding for the status bar at top and gesture/3-button nav at bottom so
        // content isn't drawn behind them.
        contentWindowInsets = WindowInsets.systemBars,
        bottomBar = {
            NavigationBar(containerColor = BgSecondary) {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                tabs.forEach { tab ->
                    NavigationBarItem(
                        icon = { Icon(tab.icon, contentDescription = tab.labelKey(strings)) },
                        label = { Text(tab.labelKey(strings), style = MaterialTheme.typography.labelSmall) },
                        selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = AccentGreen,
                            selectedTextColor = AccentGreen,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary,
                            indicatorColor = AccentGreen.copy(alpha = 0.15f),
                        ),
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(navController, startDestination = Tab.Capture.route, Modifier.padding(innerPadding)) {
            composable(Tab.Capture.route) { CaptureScreen() }
            composable(Tab.Sources.route) {
                SourcesScreen(onRecordingClick = { id ->
                    navController.navigate("source_detail/$id")
                })
            }
            composable(Tab.Chat.route) { ChatScreen() }
            composable(Tab.Briefing.route) {
                BriefingScreen(
                    onSourceClick = { recordingId, segmentId ->
                        val route = if (segmentId != null) {
                            "source_detail/$recordingId?segmentId=$segmentId"
                        } else {
                            "source_detail/$recordingId"
                        }
                        navController.navigate(route)
                    },
                    onAskAI = { prompt ->
                        appState.setPendingChatPrompt(prompt)
                        navController.navigate(Tab.Chat.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
            composable(Tab.Settings.route) { SettingsScreen() }
            composable(
                route = "source_detail/{recordingId}?segmentId={segmentId}",
                arguments = listOf(
                    navArgument("recordingId") { type = NavType.IntType },
                    navArgument("segmentId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) { backStackEntry ->
                val recordingId = backStackEntry.arguments?.getInt("recordingId") ?: return@composable
                val segmentId = backStackEntry.arguments?.getString("segmentId")?.toIntOrNull()
                SourceDetailScreen(
                    recordingId = recordingId,
                    focusSegmentId = segmentId,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}
