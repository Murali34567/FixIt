package uk.ac.tees.mad.fixit.presentation.feature.reportissue

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uk.ac.tees.mad.fixit.data.model.IssueLocation
import uk.ac.tees.mad.fixit.data.model.IssueReport
import uk.ac.tees.mad.fixit.data.model.IssueType
import uk.ac.tees.mad.fixit.data.model.Result
import uk.ac.tees.mad.fixit.domain.repository.ImageUploadRepository
import uk.ac.tees.mad.fixit.domain.repository.LocationRepository
import uk.ac.tees.mad.fixit.domain.repository.ReportRepository

class ReportIssueViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(ReportIssueUiState())
    val uiState: StateFlow<ReportIssueUiState> = _uiState.asStateFlow()

    private lateinit var locationRepository: LocationRepository
    private lateinit var reportRepository: ReportRepository
    private lateinit var imageUploadRepository: ImageUploadRepository

    // Add upload progress state
    private val _uploadProgress = MutableStateFlow(0f)
    val uploadProgress: StateFlow<Float> = _uploadProgress.asStateFlow()

    private companion object {
        const val TAG = "ReportIssueViewModel"
    }

    /**
     * Initialize repositories (should be called from the screen)
     */
    fun initializeRepositories(context: Context) {
        locationRepository = LocationRepository(context)
        reportRepository = ReportRepository()
        imageUploadRepository = ImageUploadRepository(context)
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
     * Upload image to Supabase and return URL
     */
    private suspend fun uploadImageToSupabase(imageUri: Uri): Result<String> {
        return try {
            var imageUrlResult: Result<String> = Result.Error("Upload failed")

            imageUploadRepository.uploadImage(imageUri).collect { result ->
                when (result) {
                    is Result.Loading -> {
                        // Update progress if needed
                        _uploadProgress.value = 0.5f // Simulate progress
                    }
                    is Result.Success -> {
                        imageUrlResult = Result.Success(result.data)
                        _uploadProgress.value = 1.0f
                    }
                    is Result.Error -> {
                        imageUrlResult = result
                        _uploadProgress.value = 0f
                    }
                }
            }

            imageUrlResult
        } catch (exception: Exception) {
            Result.Error("Image upload failed: ${exception.message}", exception)
        }
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
     * Submit the report with image upload
     */
    fun submitReport() {
        if (!validateForm()) {
            _uiState.update { it.copy(errorMessage = "Please fix the errors before submitting") }
            return
        }

        if (!this::reportRepository.isInitialized || !this::imageUploadRepository.isInitialized) {
            _uiState.update {
                it.copy(
                    errorMessage = "Services not initialized",
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

        _uploadProgress.value = 0f

        viewModelScope.launch {
            try {
                val imageUri = _uiState.value.imageUri
                if (imageUri == null) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "No image selected",
                            isSubmitted = false
                        )
                    }
                    return@launch
                }

                // Step 1: Upload image to Supabase
                _uploadProgress.value = 0.3f
                val imageUploadResult = uploadImageToSupabase(imageUri)

                if (imageUploadResult is Result.Error) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "Failed to upload image: ${imageUploadResult.message}",
                            isSubmitted = false
                        )
                    }
                    _uploadProgress.value = 0f
                    return@launch
                }

                // Step 2: Create report with actual image URL
                _uploadProgress.value = 0.6f
                val imageUrl = (imageUploadResult as Result.Success).data

                val report = IssueReport(
                    imageUrl = imageUrl, // Use actual Supabase URL instead of placeholder
                    description = _uiState.value.description,
                    issueType = _uiState.value.selectedIssueType,
                    location = _uiState.value.location ?: IssueLocation(),
                    timestamp = System.currentTimeMillis()
                )

                // Step 3: Submit report to Firebase
                _uploadProgress.value = 0.8f
                reportRepository.createReport(report).collect { result ->
                    when (result) {
                        is Result.Loading -> {
                            // Loading state already set
                        }
                        is Result.Success -> {
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    isSubmitted = true,
                                    errorMessage = null
                                )
                            }
                            _uploadProgress.value = 1.0f
                        }
                        is Result.Error -> {
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    errorMessage = "Failed to submit report: ${result.message}",
                                    isSubmitted = false
                                )
                            }
                            _uploadProgress.value = 0f
                        }
                    }
                }

            } catch (exception: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Submission failed: ${exception.message}",
                        isSubmitted = false
                    )
                }
                _uploadProgress.value = 0f
            }
        }
    }

    /**
     * Reset the form after successful submission
     */
    fun resetForm() {
        _uiState.value = ReportIssueUiState()
        _uploadProgress.value = 0f
    }

    /**
     * Clear error message
     */
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
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
}