package uk.ac.tees.mad.fixit.presentation.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.firebase.auth.FirebaseAuth
import uk.ac.tees.mad.fixit.presentation.feature.issuedetail.IssueDetailScreen
import uk.ac.tees.mad.fixit.presentation.feature.profile.ProfileScreen
import uk.ac.tees.mad.fixit.presentation.feature.reportissue.ReportIssueScreen
import uk.ac.tees.mad.fixit.presentation.feature.viewreports.ViewReportsScreen

@Composable
fun MainScreen(mainNavController: NavHostController) {
    val bottomNavController = rememberNavController()
    val navBackStackEntry by bottomNavController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            // Only show bottom bar for main tabs, not for detail screens
            if (currentRoute in listOf(
                    Screen.ReportIssue.route,
                    Screen.ViewReports.route,
                    Screen.Profile.route
                )) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    bottomNavItems.forEach { item ->
                        NavigationBarItem(
                            selected = currentRoute == item.route,
                            onClick = {
                                if (currentRoute != item.route) {
                                    bottomNavController.navigate(item.route) {
                                        // Pop up to the start destination to avoid building up backstack
                                        popUpTo(bottomNavController.graph.startDestinationId) {
                                            saveState = true
                                        }
                                        // Avoid multiple copies of same destination
                                        launchSingleTop = true
                                        // Restore state when reselecting a previously selected item
                                        restoreState = true
                                    }
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.label
                                )
                            },
                            label = { Text(text = item.label) },
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
    ) { paddingValues ->
        NavHost(
            navController = bottomNavController,
            startDestination = Screen.ReportIssue.route,
            modifier = Modifier.padding(paddingValues)
        ) {
            composable(Screen.ReportIssue.route) {
                ReportIssueScreen()
            }

            composable(Screen.ViewReports.route) {
                ViewReportsScreen(navController = bottomNavController)
            }

            composable(Screen.Profile.route) {
                ProfileScreen(
                    onLogout = {
                        FirebaseAuth.getInstance().signOut()
                        mainNavController.navigate(Screen.Auth.route) {
                            popUpTo(Screen.Main.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(
                route = Screen.IssueDetail.route,
                arguments = listOf(
                    navArgument("reportId") {
                        type = androidx.navigation.NavType.StringType
                    }
                )
            ) { backStackEntry ->
                IssueDetailScreen(
                    navController = bottomNavController,
                    viewModel = hiltViewModel()
                )
            }
        }
    }
}