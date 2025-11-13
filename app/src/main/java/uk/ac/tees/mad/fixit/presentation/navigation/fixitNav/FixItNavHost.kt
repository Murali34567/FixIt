package uk.ac.tees.mad.fixit.presentation.navigation.fixitNav

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import uk.ac.tees.mad.fixit.presentation.feature.HomeScreen
import uk.ac.tees.mad.fixit.presentation.feature.auth.AuthScreen
import uk.ac.tees.mad.fixit.presentation.navigation.Screen

/**
 * Main navigation graph for the FixIt app
 * Handles all navigation between screens
 *
 * @param navController Navigation controller to manage app navigation
 * @param startDestination Initial screen to show based on auth status
 */
@Composable
fun NavGraph(
    navController: NavHostController,
    startDestination: String
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // Authentication Screen (Login/Sign up)
        composable(Screen.Auth.route) {
            AuthScreen(navController = navController)
        }

        // Home Screen (Main app content)
        composable(Screen.Home.route) {
            HomeScreen(navController = navController)
        }
    }
}