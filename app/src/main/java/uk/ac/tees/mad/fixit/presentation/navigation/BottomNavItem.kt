package uk.ac.tees.mad.fixit.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Data class representing bottom navigation items
 */
data class BottomNavItem(
    val label: String,
    val icon: ImageVector,
    val route: String
)

/**
 * List of bottom navigation items
 */
val bottomNavItems = listOf(
    BottomNavItem(
        label = "Report Issue",
        icon = Icons.Default.Add,
        route = Screen.ReportIssue.route
    ),
    BottomNavItem(
        label = "View Reports",
        icon = Icons.AutoMirrored.Filled.List,
        route = Screen.ViewReports.route
    ),
    BottomNavItem(
        label = "Profile",
        icon = Icons.Default.Person,
        route = Screen.Profile.route
    )
)