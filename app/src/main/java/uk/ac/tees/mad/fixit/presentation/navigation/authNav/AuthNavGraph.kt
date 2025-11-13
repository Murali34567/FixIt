//package uk.ac.tees.mad.fixit.presentation.navigation.authNav
//
//import androidx.navigation.NavController
//import androidx.navigation.NavGraphBuilder
//import androidx.navigation.compose.composable
//import androidx.navigation.navigation
//import uk.ac.tees.mad.fixit.presentation.feature.auth.LoginScreen
//import uk.ac.tees.mad.fixit.presentation.feature.splashscreen.Splash
//import uk.ac.tees.mad.fixit.presentation.feature.splashscreen.SplashScreen
//
//const val authRoute = "auth"
//
//sealed class AuthScreen(val route: String) {
//    object Splash : AuthScreen("splash")
//    object Login : AuthScreen("login")
//    object Signup : AuthScreen("signup")
//}
//
//
//fun NavGraphBuilder.authNavGraph(
//    onAuthSuccess: ()-> Unit,
//    navController: NavController
//) {
//
//    navigation(startDestination = AuthScreen.Splash.route, route = authRoute){
//        composable(AuthScreen.Splash.route){
//            SplashScreen()
//            navController.navigate(AuthScreen.Login.route)
//        }
//
//        composable(AuthScreen.Login.route) {
//            LoginScreen()
//        }
//    }
//
//}