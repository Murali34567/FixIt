package uk.ac.tees.mad.fixit.presentation.feature.reportissue

import android.net.Uri
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import uk.ac.tees.mad.fixit.data.model.IssueType

class ReportIssueViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(ReportIssueUiState())
    val uiState: StateFlow<ReportIssueUiState> = _uiState.asStateFlow()

    /**
     * Update the selected image URI
     */
    fun updateImageUri(uri: Uri?) {
        _uiState.update { it.copy(imageUri = uri, imageError = null) }
    }

    /**
     * Update the description text
     */
    fun updateDescription(description: String) {
        _uiState.update { it.copy(description = description, descriptionError = null) }
    }

    /**
     * Update the selected issue type
     */
    fun updateIssueType(issueType: IssueType) {
        _uiState.update { it.copy(selectedIssueType = issueType) }
    }

    /**
     * Clear error message
     */
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    /**
     * Validate form before submission
     */
    private fun validateForm(): Boolean {
        val state = _uiState.value
        var isValid = true

        // Validate image
        if (state.imageUri == null) {
            _uiState.update { it.copy(imageError = "Please capture or select an image") }
            isValid = false
        }

        // Validate description
        if (state.description.isBlank()) {
            _uiState.update { it.copy(descriptionError = "Description is required") }
            isValid = false
        } else if (state.description.length < 10) {
            _uiState.update { it.copy(descriptionError = "Description must be at least 10 characters") }
            isValid = false
        }

        return isValid
    }

    /**
     * Submit the report (placeholder for now)
     */
    fun submitReport() {
        if (!validateForm()) {
            _uiState.update { it.copy(errorMessage = "Please fix the errors before submitting") }
            return
        }

        // TODO: Implement actual submission in later parts
        _uiState.update {
            it.copy(
                isLoading = true,
                errorMessage = null
            )
        }

        // Simulate submission
        _uiState.update {
            it.copy(
                isLoading = false,
                errorMessage = "Submission functionality will be implemented in later parts"
            )
        }
    }

    /**
     * Reset the form
     */
    fun resetForm() {
        _uiState.value = ReportIssueUiState()
    }
}