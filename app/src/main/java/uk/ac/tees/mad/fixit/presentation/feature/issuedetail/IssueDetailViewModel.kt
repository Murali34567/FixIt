package uk.ac.tees.mad.fixit.presentation.feature.issuedetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uk.ac.tees.mad.fixit.data.model.Result
import uk.ac.tees.mad.fixit.domain.repository.IssueRepository
import javax.inject.Inject

@HiltViewModel
class IssueDetailViewModel @Inject constructor(
    private val issueRepository: IssueRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(IssueDetailUiState())
    val uiState: StateFlow<IssueDetailUiState> = _uiState.asStateFlow()

    private val reportId: String = savedStateHandle["reportId"] ?: ""
    private val currentUserId: String
        get() = Firebase.auth.currentUser?.uid ?: ""

    init {
        loadReport()
    }

    /**
     * Load report details
     */
    fun loadReport() {
        if (reportId.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Invalid report ID") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            when (val result = issueRepository.getReportById(reportId, currentUserId)) {
                is Result.Success -> {
                    _uiState.update {
                        it.copy(
                            report = result.data,
                            isLoading = false,
                            errorMessage = null
                        )
                    }
                }
                is Result.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "Failed to load report: ${result.message}"
                        )
                    }
                }
                else -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "Failed to load report"
                        )
                    }
                }
            }
        }
    }

    /**
     * Refresh report data
     */
    fun refreshReport() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, errorMessage = null) }

            // Force sync and reload
            issueRepository.syncReports(currentUserId)
            loadReport()

            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    /**
     * Delete this report
     */
    fun deleteReport(onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            when (val result = issueRepository.deleteReport(reportId, currentUserId)) {
                is Result.Success -> {
                    _uiState.update { it.copy(isLoading = false) }
                    onSuccess()
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
                            errorMessage = "Failed to delete report"
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
}