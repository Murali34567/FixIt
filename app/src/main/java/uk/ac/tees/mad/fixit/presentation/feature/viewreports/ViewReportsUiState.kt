package uk.ac.tees.mad.fixit.presentation.feature.viewreports

import uk.ac.tees.mad.fixit.data.model.IssueReport
import uk.ac.tees.mad.fixit.data.model.ReportStatus

/**
 * UI state for View Reports screen
 */
data class ViewReportsUiState(
    val reports: List<IssueReport> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    val selectedFilter: ReportStatus? = null,
    val searchQuery: String = "",
    val lastSyncTime: Long? = null,
    val isOffline: Boolean = false,
    val pendingSyncCount: Int = 0
) {
    // Computed properties for easy access
    val filteredReports: List<IssueReport> get() {
        var filtered = reports

        // Apply status filter
        selectedFilter?.let { status ->
            filtered = filtered.filter { it.status == status }
        }

        // Apply search filter
        if (searchQuery.isNotBlank()) {
            filtered = filtered.filter { report ->
                report.description.contains(searchQuery, ignoreCase = true) ||
                        report.issueType.displayName.contains(searchQuery, ignoreCase = true) ||
                        report.location.address.contains(searchQuery, ignoreCase = true)
            }
        }

        return filtered
    }

    val hasReports: Boolean get() = reports.isNotEmpty()
    val hasFilteredReports: Boolean get() = filteredReports.isNotEmpty()
    val showEmptyState: Boolean get() = !isLoading && !hasFilteredReports
    val canRefresh: Boolean get() = !isLoading && !isRefreshing
}

/**
 * Sealed class for different screen states
 */
sealed class ViewReportsState {
    object Loading : ViewReportsState()
    data class Success(val reports: List<IssueReport>) : ViewReportsState()
    data class Error(val message: String) : ViewReportsState()
    object Empty : ViewReportsState()
}