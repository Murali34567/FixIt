package uk.ac.tees.mad.fixit.presentation.feature.viewreports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uk.ac.tees.mad.fixit.data.model.IssueReport
import uk.ac.tees.mad.fixit.data.model.ReportStatus
import uk.ac.tees.mad.fixit.data.model.Result
import uk.ac.tees.mad.fixit.domain.repository.IssueRepository
import javax.inject.Inject

@HiltViewModel
class ViewReportsViewModel @Inject constructor(
    private val issueRepository: IssueRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ViewReportsUiState())
    val uiState: StateFlow<ViewReportsUiState> = _uiState.asStateFlow()

    private val currentUserId: String
        get() = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    init {
        loadReports()
        observeSyncStatus()
    }

    /**
     * Load reports with cache-first strategy
     */
    fun loadReports() {
        if (currentUserId.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            issueRepository.getAllReports(currentUserId).collect { result ->
                when (result) {
                    is Result.Loading -> {
                        _uiState.update { it.copy(isLoading = true) }
                    }
                    is Result.Success -> {
                        _uiState.update {
                            it.copy(
                                reports = result.data,
                                isLoading = false,
                                isRefreshing = false,
                                errorMessage = null
                            )
                        }
                        updatePendingSyncCount()
                    }
                    is Result.Error -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                isRefreshing = false,
                                errorMessage = result.message
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * Refresh reports with pull-to-refresh
     */
    fun refreshReports() {
        if (currentUserId.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, errorMessage = null) }

            // Force sync with remote
            when (val syncResult = issueRepository.syncReports(currentUserId)) {
                is Result.Success -> {
                    // Reload from local cache after sync
                    loadReports()
                }
                is Result.Error -> {
                    _uiState.update {
                        it.copy(
                            isRefreshing = false,
                            errorMessage = "Sync failed: ${syncResult.message}"
                        )
                    }
                }
                else -> {
                    _uiState.update {
                        it.copy(
                            isRefreshing = false,
                            errorMessage = "Sync failed: Unknown error"
                        )
                    }
                }
            }
        }
    }

    /**
     * Update search query
     */
    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    /**
     * Update status filter
     */
    fun updateStatusFilter(status: ReportStatus?) {
        _uiState.update { it.copy(selectedFilter = status) }
    }

    /**
     * Clear all filters
     */
    fun clearFilters() {
        _uiState.update {
            it.copy(
                selectedFilter = null,
                searchQuery = ""
            )
        }
    }

    /**
     * Delete a report
     */
    fun deleteReport(reportId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            when (val result = issueRepository.deleteReport(reportId, currentUserId)) {
                is Result.Success -> {
                    // Reload reports after deletion
                    loadReports()
                }
                is Result.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "Failed to delete report: ${result.message}"
                        )
                    }
                }
                else -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "Failed to delete report: Unknown error"
                        )
                    }
                }
            }
        }
    }

    /**
     * Clear error message
     */
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    /**
     * Update offline status
     */
    fun updateOfflineStatus(isOffline: Boolean) {
        _uiState.update { it.copy(isOffline = isOffline) }
    }

    /**
     * Observe sync status and update UI accordingly
     */
    private fun observeSyncStatus() {
        // This would observe the SyncManager from IssueRepository
        // For now, we'll update pending count
        viewModelScope.launch {
            updatePendingSyncCount()
        }
    }

    /**
     * Update pending sync count badge
     */
    private suspend fun updatePendingSyncCount() {
        val pendingCount = issueRepository.getPendingSyncCount(currentUserId)
        _uiState.update { it.copy(pendingSyncCount = pendingCount) }
    }

    /**
     * Get report by ID for navigation
     */
    fun getReportById(reportId: String): IssueReport? {
        return _uiState.value.reports.find { it.id == reportId }
    }
}