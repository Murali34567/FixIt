package uk.ac.tees.mad.fixit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import uk.ac.tees.mad.fixit.domain.repository.AuthRepository
import uk.ac.tees.mad.fixit.presentation.navigation.Screen
import uk.ac.tees.mad.fixit.presentation.navigation.NavGraph
import uk.ac.tees.mad.fixit.ui.theme.FixItTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        installSplashScreen()


        val authRepository = AuthRepository()

        val startDestination = if (authRepository.isUserAuthenticated()) {
            Screen.Main.route
        } else {
            Screen.Auth.route
        }
        setContent {
            FixItTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    NavGraph(
                        navController = navController,
                        startDestination = startDestination
                    )
                }
            }
        }
    }
}