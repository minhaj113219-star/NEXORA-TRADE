package com.nexora.trade.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.nexora.trade.NexoraApplication
import com.nexora.trade.R
import com.nexora.trade.ui.screens.dashboard.DashboardScreen
import com.nexora.trade.ui.screens.dashboard.DashboardViewModel
import com.nexora.trade.ui.screens.splash.SplashScreen
import com.nexora.trade.ui.screens.trade.AddEditTradeScreen
import com.nexora.trade.ui.screens.trade.AddEditTradeViewModel
import com.nexora.trade.ui.screens.trade.DailyTradeDetailsScreen
import com.nexora.trade.ui.screens.trade.DailyTradeDetailsViewModel
import com.nexora.trade.ui.theme.NexoraTheme
import java.time.LocalDate

private data class BottomNavTab(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector
)

private val TopLevelTabs = listOf(
    BottomNavTab(
        route = NexoraRoutes.DASHBOARD,
        labelRes = R.string.nav_dashboard,
        icon = Icons.Rounded.Home
    ),
    BottomNavTab(
        route = NexoraRoutes.THIRTY_DAY,
        labelRes = R.string.nav_thirty_day,
        icon = Icons.Rounded.CalendarMonth
    ),
    BottomNavTab(
        route = NexoraRoutes.HISTORY,
        labelRes = R.string.nav_history,
        icon = Icons.Rounded.History
    ),
    BottomNavTab(
        route = NexoraRoutes.SETTINGS,
        labelRes = R.string.nav_settings,
        icon = Icons.Rounded.Settings
    )
)

@Composable
fun NexoraNavGraph() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentRoute = currentDestination?.route

    val application = LocalContext.current.applicationContext as NexoraApplication
    val repository = application.appContainer.tradingRepository

    val showBottomBar = currentRoute in setOf(
        NexoraRoutes.DASHBOARD,
        NexoraRoutes.THIRTY_DAY,
        NexoraRoutes.HISTORY,
        NexoraRoutes.SETTINGS
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBottomBar) {
                Column {
                    HorizontalDivider(color = NexoraTheme.colors.cardBorder, thickness = 1.dp)
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.dp
                    ) {
                        TopLevelTabs.forEach { tab ->
                            val selected = currentDestination?.hierarchy?.any {
                                it.route == tab.route
                            } == true

                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(tab.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = stringResource(id = tab.labelRes)
                                    )
                                },
                                label = {
                                    Text(
                                        text = stringResource(id = tab.labelRes),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = NexoraRoutes.SPLASH,
            modifier = Modifier.padding(innerPadding),
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None }
        ) {
            composable(NexoraRoutes.SPLASH) {
                SplashScreen(
                    onSplashFinished = {
                        navController.navigate(NexoraRoutes.DASHBOARD) {
                            popUpTo(NexoraRoutes.SPLASH) { inclusive = true }
                        }
                    }
                )
            }

            composable(NexoraRoutes.DASHBOARD) {
                val dashboardViewModel: DashboardViewModel = viewModel(
                    factory = DashboardViewModel.provideFactory(repository)
                )
                DashboardScreen(
                    viewModel = dashboardViewModel,
                    onAddTradeClick = { date ->
                        navController.navigate(NexoraRoutes.addTrade(date.toString()))
                    },
                    onOpenReflectionClick = { date ->
                        navController.navigate(NexoraRoutes.reflection(date.toString()))
                    },
                    onOpenDailyDetailsClick = { date ->
                        navController.navigate(NexoraRoutes.dailyDetails(date.toString()))
                    }
                )
            }

            composable(NexoraRoutes.THIRTY_DAY) {
                StepShellScreen(
                    title = "30-Day Management",
                    subtitle = "30-day calendar summary will be implemented in Step 7."
                )
            }

            composable(NexoraRoutes.HISTORY) {
                StepShellScreen(
                    title = "Trade History",
                    subtitle = "Persistent trade history will be implemented in Step 7."
                )
            }

            composable(NexoraRoutes.SETTINGS) {
                StepShellScreen(
                    title = "Settings & Rules",
                    subtitle = "Calculation rules and alert settings will be implemented in Step 10."
                )
            }

            composable(
                route = NexoraRoutes.DAILY_DETAILS_PATTERN,
                arguments = listOf(
                    navArgument(NexoraRoutes.ARG_DATE) {
                        type = NavType.StringType
                        defaultValue = LocalDate.now().toString()
                    }
                )
            ) { backStackEntry ->
                val rawDate = backStackEntry.arguments?.getString(NexoraRoutes.ARG_DATE)
                val date = parseLocalDateOrDefault(rawDate)
                val detailsViewModel: DailyTradeDetailsViewModel = viewModel(
                    key = "details_$date",
                    factory = DailyTradeDetailsViewModel.provideFactory(repository, date)
                )
                DailyTradeDetailsScreen(
                    viewModel = detailsViewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onAddTradeClick = { targetDate ->
                        navController.navigate(NexoraRoutes.addTrade(targetDate.toString()))
                    },
                    onEditTradeClick = { targetDate, tradeId ->
                        navController.navigate(NexoraRoutes.editTrade(targetDate.toString(), tradeId))
                    },
                    onOpenReflectionClick = { targetDate ->
                        navController.navigate(NexoraRoutes.reflection(targetDate.toString()))
                    }
                )
            }

            composable(
                