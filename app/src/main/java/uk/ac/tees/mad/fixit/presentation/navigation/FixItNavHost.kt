package uk.ac.tees.mad.fixit.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import uk.ac.tees.mad.fixit.presentation.feature.auth.AuthScreen

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

        // Main Screen with Bottom Navigation
        composable(Screen.Main.route) {
            MainScreen(mainNavController = navController)
        }
    }
}