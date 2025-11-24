package uk.ac.tees.mad.fixit.presentation.feature.reportissue

import android.net.Uri
import uk.ac.tees.mad.fixit.data.model.IssueLocation
import uk.ac.tees.mad.fixit.data.model.IssueType

/**
 * UI state for Report Issue screen
 */
data class ReportIssueUiState(
    val imageUri: Uri? = null,
    val description: String = "",
    val selectedIssueType: IssueType = IssueType.OTHER,
    val location: IssueLocation? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isSubmitted: Boolean = false,
    val uploadSuccess: Boolean = false,

    // Enhanced validation errors (can have multiple errors per field)
    val descriptionErrors: List<String> = emptyList(),
    val imageErrors: List<String> = emptyList(),
    val locationErrors: List<String> = emptyList(),

    // Character count for description
    val descriptionCharCount: Int = 0
) {
    // Helper properties for easy access
    val hasDescriptionErrors: Boolean get() = descriptionErrors.isNotEmpty()
    val hasImageErrors: Boolean get() = imageErrors.isNotEmpty()
    val hasLocationErrors: Boolean get() = locationErrors.isNotEmpty()
    val hasAnyErrors: Boolean get() = hasDescriptionErrors || hasImageErrors || hasLocationErrors
}