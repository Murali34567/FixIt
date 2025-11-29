package uk.ac.tees.mad.fixit.presentation.feature.reportissue

import android.content.Context
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uk.ac.tees.mad.fixit.data.model.IssueLocation
import uk.ac.tees.mad.fixit.data.model.IssueReport
import uk.ac.tees.mad.fixit.data.model.IssueType
import uk.ac.tees.mad.fixit.data.model.ReportStatus
import uk.ac.tees.mad.fixit.data.model.Result
import uk.ac.tees.mad.fixit.domain.repository.ImageUploadRepository
import uk.ac.tees.mad.fixit.domain.repository.IssueRepository
import uk.ac.tees.mad.fixit.domain.repository.LocationRepository
import uk.ac.tees.mad.fixit.domain.util.ValidationHelper
import javax.inject.Inject

@HiltViewModel
class ReportIssueViewModel @Inject constructor(
    private val issueRepository: IssueRepository,
    private val imageUploadRepository: ImageUploadRepository,
    private val locationRepository: LocationRepository
) : ViewModel() {

    companion object {
        private const val TAG = "ReportIssueViewModel"
    }

    private val _uiState = MutableStateFlow(ReportIssueUiState())
    val uiState: StateFlow<ReportIssueUiState> = _uiState.asStateFlow()

    private val _uploadProgress = MutableStateFlow(0f)
    val uploadProgress: StateFlow<Float> = _uploadProgress.asStateFlow()

    private val auth = FirebaseAuth.getInstance()

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
                    Log.d(TAG, "✅ Location fetched: ${location.latitude}, ${location.longitude}")
                    _uiState.update {
                        it.copy(
                            location = location,
                            locationErrors = emptyList()
                        )
                    }
                },
                onFailure = { exception ->
                    Log.e(TAG, "🔴 Location error: ${exception.message}")
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
                        _uploadProgress.value = 0.5f
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

        _uiState.update {
            it.copy(
                descriptionErrors = emptyList(),
                imageErrors = emptyList(),
                locationErrors = emptyList(),
                errorMessage = null
            )
        }

        if (!validationResult.isValid) {
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
     * Check if network is available
     */
    private fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkInfo = connectivityManager.activeNetworkInfo
        return networkInfo != null && networkInfo.isConnected
    }

    /**
     * ✅ FIXED: Submit report with proper userId
     */
    fun submitReport(context: Context) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Log.e(TAG, "🔴 No authenticated user")
            _uiState.update {
                it.copy(
                    errorMessage = "Not authenticated. Please log in.",
                    isLoading = false
                )
            }
            return
        }

        val userId = currentUser.uid
        Log.d(TAG, "🟡 Submitting report for user: $userId")

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
                Log.d(TAG, "🟡 Step 1: Uploading image...")
                _uploadProgress.value = 0.3f
                val imageUploadResult = uploadImageToSupabase(imageUri)

                if (imageUploadResult is Result.Error) {
                    Log.e(TAG, "🔴 Image upload failed: ${imageUploadResult.message}")
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

                // Step 2: Create report with image URL
                Log.d(TAG, "✅ Image uploaded successfully")
                _uploadProgress.value = 0.6f
                val imageUrl = (imageUploadResult as Result.Success).data

                val report = IssueReport(
                    id = java.util.UUID.randomUUID().toString(),
                    userId = userId, // ✅ Set userId from authenticated user
                    imageUrl = imageUrl,
                    description = _uiState.value.description.trim(),
                    issueType = _uiState.value.selectedIssueType,
                    location = _uiState.value.location ?: IssueLocation(),
                    status = ReportStatus.PENDING,
                    timestamp = System.currentTimeMillis()
                )

                Log.d(TAG, "🟡 Step 2: Creating report...")
                Log.d(TAG, "📄 Report ID: ${report.id}")
                Log.d(TAG, "📄 User ID: ${report.userId}")

                // Step 3: Save report using IssueRepository
                _uploadProgress.value = 0.8f
                when (val result = issueRepository.createReport(report)) {
                    is Result.Success -> {
                        Log.d(TAG, "✅ Report created successfully!")
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                isSubmitted = true,
                                uploadSuccess = true,
                                errorMessage = null
                            )
                        }
                        _uploadProgress.value = 1.0f
                        performHapticFeedback(context)
                    }
                    is Result.Error -> {
                        Log.e(TAG, "🔴 Report creation failed: ${result.message}")
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
                    else -> {
                        Log.e(TAG, "🔴 Unknown result")
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = "Failed to submit report",
                                isSubmitted = false,
                                uploadSuccess = false
                            )
                        }
                        _uploadProgress.value = 0f
                    }
                }

            } catch (exception: Exception) {
                Log.e(TAG, "🔴 Exception: ${exception.message}", exception)
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