package uk.ac.tees.mad.fixit.presentation.feature.reportissue

import android.content.Context
import android.net.Uri
import android.net.ConnectivityManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
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
import uk.ac.tees.mad.fixit.domain.util.ValidationHelper

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
                imageErrors = emptyList(),
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
                imageErrors = emptyList(),
                errorMessage = null
            )
        }
    }

    /**
     * Update the description text with character count
     */
    fun updateDescription(description: String) {
        _uiState.update {
            it.copy(
                description = description,
                descriptionCharCount = description.length,
                descriptionErrors = emptyList(),
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
                    locationErrors = listOf("Please try again")
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                isLoading = true,
                locationErrors = emptyList(),
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
                            locationErrors = emptyList()
                        )
                    }
                },
                onFailure = { exception ->
                    _uiState.update {
                        it.copy(
                            locationErrors = listOf("Failed to get location: ${exception.message}"),
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
     * Enhanced form validation using ValidationHelper
     */
    private fun validateForm(): Boolean {
        val state = _uiState.value
        val validationResult = ValidationHelper.validateReport(
            imageUri = state.imageUri,
            description = state.description,
            location = state.location
        )

        // Clear previous errors
        _uiState.update {
            it.copy(
                descriptionErrors = emptyList(),
                imageErrors = emptyList(),
                locationErrors = emptyList(),
                errorMessage = null
            )
        }

        if (!validationResult.isValid) {
            // Group errors by field
            val descriptionErrors = ValidationHelper.getFieldErrors(validationResult.errors, "description")
            val imageErrors = ValidationHelper.getFieldErrors(validationResult.errors, "image")
            val locationErrors = ValidationHelper.getFieldErrors(validationResult.errors, "location")

            _uiState.update {
                it.copy(
                    descriptionErrors = descriptionErrors,
                    imageErrors = imageErrors,
                    locationErrors = locationErrors,
                    errorMessage = "Please fix the errors before submitting"
                )
            }
            return false
        }

        return true
    }

    /**
     * Clear all validation errors
     */
    fun clearAllErrors() {
        _uiState.update {
            it.copy(
                descriptionErrors = emptyList(),
                imageErrors = emptyList(),
                locationErrors = emptyList(),
                errorMessage = null
            )
        }
    }

    /**
     * Check if network is available
     */
    private fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkInfo = connectivityManager.activeNetworkInfo
        return networkInfo != null && networkInfo.isConnected
    }

    /**
     * Enhanced submit with better error handling
     */
    fun submitReport(context: Context) {
        // Check network first
        if (!isNetworkAvailable(context)) {
            _uiState.update {
                it.copy(
                    errorMessage = "No internet connection. Please check your network and try again."
                )
            }
            return
        }

        if (!validateForm()) {
            return
        }

        if (!this::reportRepository.isInitialized || !this::imageUploadRepository.isInitialized) {
            _uiState.update {
                it.copy(
                    errorMessage = "Services not initialized. Please restart the app.",
                    isLoading = false
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                isLoading = true,
                errorMessage = null,
                isSubmitted = false,
                uploadSuccess = false
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
                    imageUrl = imageUrl,
                    description = _uiState.value.description.trim(),
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
                                    uploadSuccess = true,
                                    errorMessage = null
                                )
                            }
                            _uploadProgress.value = 1.0f
                            // Perform haptic feedback on success
                            performHapticFeedback(context)
                        }
                        is Result.Error -> {
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    errorMessage = "Failed to submit report: ${result.message}",
                                    isSubmitted = false,
                                    uploadSuccess = false
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
                        isSubmitted = false,
                        uploadSuccess = false
                    )
                }
                _uploadProgress.value = 0f
            }
        }
    }

    /**
     * Perform haptic feedback
     */
    private fun performHapticFeedback(context: Context) {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(50)
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
                locationErrors = emptyList()
            )
        }
    }
}