package uk.ac.tees.mad.fixit.presentation.feature.issuedetail

import uk.ac.tees.mad.fixit.data.model.IssueReport

/**
 * UI state for Issue Detail screen
 */
data class IssueDetailUiState(
    val report: IssueReport? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isRefreshing: Boolean = false
) {
    val hasError: Boolean get() = errorMessage != null
    val showLoading: Boolean get() = isLoading && report == null
    val showContent: Boolean get() = report != null && !isLoading
}