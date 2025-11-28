package uk.ac.tees.mad.fixit.presentation.navigation

sealed class Screen(val route: String) {
    object Auth : Screen("auth")
    object Main : Screen("main") // Main screen with bottom nav

    // Bottom nav destinations
    object ReportIssue : Screen("report_issue")
    object ViewReports : Screen("view_reports")
    object Profile : Screen("profile")

    // Detail screen (to be implemented in Part 5)
    object IssueDetail : Screen("issue_detail/{reportId}") {
        fun createRoute(reportId: String) = "issue_detail/$reportId"
    }
}