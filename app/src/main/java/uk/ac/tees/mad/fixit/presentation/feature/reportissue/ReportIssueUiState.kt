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

    // Validation errors
    val descriptionError: String? = null,
    val imageError: String? = null,
    val locationError: String? = null
)