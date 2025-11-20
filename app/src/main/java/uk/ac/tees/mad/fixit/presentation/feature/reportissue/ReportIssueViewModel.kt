package uk.ac.tees.mad.fixit.presentation.feature.reportissue

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import uk.ac.tees.mad.fixit.data.model.IssueLocation
import uk.ac.tees.mad.fixit.data.model.IssueReport
import uk.ac.tees.mad.fixit.data.model.IssueType
import uk.ac.tees.mad.fixit.data.model.Result
import uk.ac.tees.mad.fixit.domain.repository.LocationRepository
import uk.ac.tees.mad.fixit.domain.repository.ReportRepository

class ReportIssueViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(ReportIssueUiState())
    val uiState: StateFlow<ReportIssueUiState> = _uiState.asStateFlow()

    private lateinit var locationRepository: LocationRepository
    private lateinit var reportRepository: ReportRepository

    private companion object {
        const val TAG = "ReportIssueViewModel"
    }

    /**
     * Initialize repositories (should be called from the screen)
     */
//    fun initializeRepositories(context: Context) {
//        locationRepository = LocationRepository(context)
//        reportRepository = ReportRepository()
//    }

    fun initializeRepositories(context: Context) {
        locationRepository = LocationRepository(context)
        reportRepository = ReportRepository()

        viewModelScope.launch {
            val isConnected = reportRepository.testFirebaseConnection()
            if (!isConnected) {
                Log.e(TAG, "Cannot proceed - Firebase connection failed")
            }
        }
    }



    /**
     * Update the selected image URI
     */
    fun updateImageUri(uri: Uri?) {
        _uiState.update {
            it.copy(
                imageUri = uri,
                imageError = null,
                errorMessage = null
            )
        }
    }

    /**
     * Remove the selected image
     */
    fun removeImage() {
        _uiState.update {
            it.copy(
                imageUri = null,
                imageError = null,
                errorMessage = null
            )
        }
    }

    /**
     * Update the description text
     */
    fun updateDescription(description: String) {
        _uiState.update {
            it.copy(
                description = description,
                descriptionError = null,
                errorMessage = null
            )
        }
    }

    /**
     * Update the selected issue type
     */
    fun updateIssueType(issueType: IssueType) {
        _uiState.update {
            it.copy(
                selectedIssueType = issueType,
                errorMessage = null
            )
        }
    }

    /**
     * Fetch current location
     */
    fun fetchCurrentLocation() {
        if (!this::locationRepository.isInitialized) {
            _uiState.update {
                it.copy(
                    errorMessage = "Location services not initialized",
                    locationError = "Please try again"
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                isLoading = true,
                locationError = null,
                errorMessage = null
            )
        }

        viewModelScope.launch {
            val result = locationRepository.getCurrentLocation()

            _uiState.update { state ->
                state.copy(isLoading = false)
            }

            result.fold(
                onSuccess = { location ->
                    _uiState.update {
                        it.copy(
                            location = location,
                            locationError = null
                        )
                    }
                },
                onFailure = { exception ->
                    _uiState.update {
                        it.copy(
                            locationError = "Failed to get location: ${exception.message}",
                            errorMessage = "Location service unavailable"
                        )
                    }
                }
            )
        }
    }

    /**
     * Update location manually (for testing or future map integration)
     */
    fun updateLocation(location: IssueLocation) {
        _uiState.update {
            it.copy(
                location = location,
                locationError = null
            )
        }
    }

    /**
     * Clear location
     */
    fun clearLocation() {
        _uiState.update {
            it.copy(
                location = null,
                locationError = null
            )
        }
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

        // Validate location
        if (state.location == null) {
            _uiState.update { it.copy(locationError = "Please get your current location") }
            isValid = false
        }

        return isValid
    }

    /**
     * Submit the report to Firebase
     */
    fun submitReport() {
        Log.d(TAG, "submitReport called")

        if (!validateForm()) {
            Log.d(TAG, "Form validation failed")
            _uiState.update { it.copy(errorMessage = "Please fix the errors before submitting") }
            return
        }

        if (!this::reportRepository.isInitialized) {
            Log.e(TAG, "ReportRepository not initialized")
            _uiState.update {
                it.copy(
                    errorMessage = "Report service not initialized",
                    isLoading = false
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                isLoading = true,
                errorMessage = null,
                isSubmitted = false
            )
        }

        viewModelScope.launch {
            try {
                Log.d(TAG, "Creating report object...")

                // Create report object (with placeholder image URL for now)
                val report = IssueReport(
                    imageUrl = "placeholder", // Will be updated in Part 5 with actual image URL
                    description = _uiState.value.description,
                    issueType = _uiState.value.selectedIssueType,
                    location = _uiState.value.location ?: IssueLocation(),
                    timestamp = System.currentTimeMillis()
                )

                Log.d(TAG, "Report created: $report")

                // Submit to Firebase with timeout
                try {
                    withTimeout(30000) { // 30 second timeout
                        reportRepository.createReport(report).collect { result ->
                            Log.d(TAG, "Repository result: $result")
                            when (result) {
                                is Result.Loading -> {
                                    Log.d(TAG, "Still loading...")
                                    // Loading state already set
                                }
                                is Result.Success -> {
                                    Log.d(TAG, "✅ Report submission successful!")
                                    _uiState.update {
                                        it.copy(
                                            isLoading = false,
                                            isSubmitted = true,
                                            errorMessage = null
                                        )
                                    }
                                }
                                is Result.Error -> {
                                    Log.e(TAG, "❌ Report submission failed: ${result.message}")
                                    _uiState.update {
                                        it.copy(
                                            isLoading = false,
                                            errorMessage = "Failed to submit report: ${result.message}",
                                            isSubmitted = false
                                        )
                                    }
                                }
                            }
                        }
                    }
                } catch (timeoutException: TimeoutCancellationException) {
                    Log.e(TAG, "❌ Report submission timed out")
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "Submission timed out. Please check your internet connection.",
                            isSubmitted = false
                        )
                    }
                }

            } catch (exception: Exception) {
                Log.e(TAG, "❌ Exception in submitReport: ${exception.message}", exception)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Submission failed: ${exception.message}",
                        isSubmitted = false
                    )
                }
            }
        }
    }

    /**
     * Reset the form after successful submission
     */
    fun resetForm() {
        _uiState.value = ReportIssueUiState()
    }
}