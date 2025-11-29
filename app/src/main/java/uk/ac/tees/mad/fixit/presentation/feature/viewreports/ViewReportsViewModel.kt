package uk.ac.tees.mad.fixit.presentation.feature.viewreports

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uk.ac.tees.mad.fixit.data.model.ReportStatus
import uk.ac.tees.mad.fixit.data.model.Result
import uk.ac.tees.mad.fixit.domain.repository.IssueRepository
import javax.inject.Inject

@HiltViewModel
class ViewReportsViewModel @Inject constructor(
    private val issueRepository: IssueRepository
) : ViewModel() {

    companion object {
        private const val TAG = "ViewReportsViewModel"
    }

    private val _uiState = MutableStateFlow(ViewReportsUiState())
    val uiState: StateFlow<ViewReportsUiState> = _uiState.asStateFlow()

    private val auth = FirebaseAuth.getInstance()

    init {
        Log.d(TAG, "🟡 ViewReportsViewModel initialized")
        // Wait a bit for auth to be ready
        viewModelScope.launch {
            delay(500) // Give Firebase Auth time to initialize
            val userId = auth.currentUser?.uid
            Log.d(TAG, "🟡 Current user ID: $userId")

            if (userId.isNullOrBlank()) {
                Log.e(TAG, "🔴 No authenticated user found!")
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Not authenticated. Please log in."
                    )
                }
            } else {
                loadReports()
            }
        }
    }

    /**
     * ✅ FIXED: Load reports with proper error handling
     */
    fun loadReports() {
        val userId = auth.currentUser?.uid

        if (userId.isNullOrBlank()) {
            Log.e(TAG, "🔴 Cannot load reports: No user ID")
            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = "Not authenticated. Please log in."
                )
            }
            return
        }

        Log.d(TAG, "🟡 Loading reports for user: $userId")

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            try {
                issueRepository.getAllReports(userId).collect { result ->
                    when (result) {
                        is Result.Loading -> {
                            Log.d(TAG, "🟡 Reports loading...")
                            _uiState.update { it.copy(isLoading = true) }
                        }
                        is Result.Success -> {
                            Log.d(TAG, "✅ Reports loaded successfully: ${result.data.size} reports")
                            _uiState.update {
                                it.copy(
                                    reports = result.data,
                                    isLoading = false,
                                    isRefreshing = false,
                                    errorMessage = null
                                )
                            }
                            updatePendingSyncCount(userId)
                        }
                        is Result.Error -> {
                            Log.e(TAG, "🔴 Error loading reports: ${result.message}")
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    isRefreshing = false,
                                    errorMessage = "Failed to load reports: ${result.message}"
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "🔴 Exception in loadReports: ${e.message}", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        errorMessage = "Error: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * ✅ FIXED: Refresh reports with force sync
     */
    fun refreshReports() {
        val userId = auth.currentUser?.uid

        if (userId.isNullOrBlank()) {
            Log.e(TAG, "🔴 Cannot refresh: No user ID")
            return
        }

        Log.d(TAG, "🟡 Refreshing reports for user: $userId")

        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, errorMessage = null) }

            try {
                // Force sync with Firebase
                when (val syncResult = issueRepository.forceRefresh(userId)) {
                    is Result.Success -> {
                        Log.d(TAG, "✅ Sync successful")
                        // The getAllReports flow will automatically emit updated data
                    }
                    is Result.Error -> {
                        Log.e(TAG, "🔴 Sync failed: ${syncResult.message}")
                        _uiState.update {
                            it.copy(
                                isRefreshing = false,
                                errorMessage = "Sync failed: ${syncResult.message}"
                            )
                        }
                    }
                    else -> {
                        Log.e(TAG, "🔴 Unknown sync result")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "🔴 Exception in refreshReports: ${e.message}", e)
                _uiState.update {
                    it.copy(
                        isRefreshing = false,
                        errorMessage = "Refresh failed: ${e.message}"
                    )
                }
            } finally {
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    /**
     * Update search query
     */
    fun updateSearchQuery(query: String) {
        Log.d(TAG, "🔍 Search query: $query")
        _uiState.update { it.copy(searchQuery = query) }
    }

    /**
     * Update status filter
     */
    fun updateStatusFilter(status: ReportStatus?) {
        Log.d(TAG, "🔍 Filter: ${status?.displayName ?: "All"}")
        _uiState.update { it.copy(selectedFilter = status) }
    }

    /**
     * Clear all filters
     */
    fun clearFilters() {
        Log.d(TAG, "🔍 Clearing filters")
        _uiState.update {
            it.copy(
                selectedFilter = null,
                searchQuery = ""
            )
        }
    }

    /**
     * ✅ FIXED: Delete report with proper error handling
     */
    fun deleteReport(reportId: String) {
        val userId = auth.currentUser?.uid

        if (userId.isNullOrBlank()) {
            Log.e(TAG, "🔴 Cannot delete: No user ID")
            return
        }

        Log.d(TAG, "🟡 Deleting report: $reportId")

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            try {
                when (val result = issueRepository.deleteReport(reportId, userId)) {
                    is Result.Success -> {
                        Log.d(TAG, "✅ Report deleted successfully")
                        // Reload reports to reflect changes
                        loadReports()
                    }
                    is Result.Error -> {
                        Log.e(TAG, "🔴 Delete failed: ${result.message}")
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = "Failed to delete: ${result.message}"
                            )
                        }
                    }
                    else -> {
                        Log.e(TAG, "🔴 Unknown delete result")
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = "Failed to delete report"
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "🔴 Exception in deleteReport: ${e.message}", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Delete failed: ${e.message}"
                    )
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
     * Update pending sync count
     */
    private suspend fun updatePendingSyncCount(userId: String) {
        try {
            val pendingCount = issueRepository.getPendingSyncCount(userId)
            Log.d(TAG, "🟡 Pending sync count: $pendingCount")
            _uiState.update { it.copy(pendingSyncCount = pendingCount) }
        } catch (e: Exception) {
            Log.e(TAG, "🔴 Error getting sync count: ${e.message}")
        }
    }
}