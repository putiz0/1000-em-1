package com.playbox.offline.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.playbox.offline.R
import com.playbox.offline.core.designsystem.PlayBoxIcon
import com.playbox.offline.core.designsystem.PlayBoxIconPainter
import com.playbox.offline.LocalAppContainer
import com.playbox.offline.ui.screens.CategoryScreen
import com.playbox.offline.ui.screens.DownloadsScreen
import com.playbox.offline.ui.screens.GameDetailScreen
import com.playbox.offline.ui.screens.GamePlayScreen
import com.playbox.offline.ui.screens.HomeScreen
import com.playbox.offline.ui.screens.LibraryScreen
import com.playbox.offline.ui.screens.SettingsScreen

object Routes {
    const val HOME = "home"
    const val LIBRARY = "library"
    const val DOWNLOADS = "downloads"
    const val SETTINGS = "settings"
    const val CATEGORY = "category/{categoryId}"
    const val GAME = "game/{gameId}"
    const val PLAY = "play/{gameId}"

    fun category(categoryId: String) = "category/$categoryId"
    fun game(gameId: String) = "game/$gameId"
    fun play(gameId: String) = "play/$gameId"
}

data class BottomDestination(
    val route: String,
    val labelRes: Int,
    val icon: PlayBoxIcon
)

private val bottomDestinations = listOf(
    BottomDestination(Routes.HOME, R.string.nav_home, PlayBoxIcon.HOME),
    BottomDestination(Routes.LIBRARY, R.string.nav_library, PlayBoxIcon.LIBRARY),
    BottomDestination(Routes.DOWNLOADS, R.string.nav_downloads, PlayBoxIcon.DOWNLOADS),
    BottomDestination(Routes.SETTINGS, R.string.nav_settings, PlayBoxIcon.SETTINGS)
)

@Composable
fun AppRoot() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val showBottomBar = currentRoute in bottomDestinations.map { it.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomDestinations.forEach { dest ->
                        val selected = currentRoute == dest.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(dest.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    painter = PlayBoxIconPainter(dest.icon),
                                    contentDescription = stringResource(dest.labelRes)
                                )
                            },
                            label = { Text(stringResource(dest.labelRes)) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(padding)
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    onOpenCategory = { categoryId ->
                        navController.navigate(Routes.category(categoryId))
                    },
                    onOpenGame = { gameId ->
                        navController.navigate(Routes.game(gameId))
                    },
                    onOpenLibrary = {
                        navController.navigate(Routes.LIBRARY)
                    }
                )
            }
            composable(Routes.LIBRARY) {
                LibraryScreen(
                    onOpenCatalog = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                        }
                    },
                    onOpenGame = { gameId -> navController.navigate(Routes.game(gameId)) }
                )
            }
            composable(Routes.DOWNLOADS) {
                DownloadsScreen(
                    onOpenGame = { gameId -> navController.navigate(Routes.game(gameId)) }
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen()
            }
            composable(
                route = Routes.CATEGORY,
                arguments = listOf(navArgument("categoryId") { type = NavType.StringType })
            ) { entry ->
                val categoryId = entry.arguments?.getString("categoryId")
                CategoryScreen(
                    categoryId = categoryId,
                    onBack = { navController.popBackStack() },
                    onOpenGame = { gameId -> navController.navigate(Routes.game(gameId)) }
                )
            }
            composable(
                route = Routes.GAME,
                arguments = listOf(navArgument("gameId") { type = NavType.StringType })
            ) { entry ->
                val gameId = entry.arguments?.getString("gameId")
                GameDetailScreen(
                    gameId = gameId,
                    onBack = { navController.popBackStack() },
                    onPlay = { id -> navController.navigate(Routes.play(id)) }
                )
            }
            composable(
                route = Routes.PLAY,
                arguments = listOf(navArgument("gameId") { type = NavType.StringType })
            ) { entry ->
                val container = LocalAppContainer.current
                GamePlayScreen(
                    gameId = entry.arguments?.getString("gameId"),
                    packManager = container.packManager,
                    onSair = { navController.popBackStack() }
                )
            }
        }
    }
}
