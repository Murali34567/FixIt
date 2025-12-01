package uk.ac.tees.mad.fixit.presentation.feature.profile

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import uk.ac.tees.mad.fixit.data.model.Result
import uk.ac.tees.mad.fixit.data.model.UserProfile
import uk.ac.tees.mad.fixit.domain.repository.ImageUploadRepository
import uk.ac.tees.mad.fixit.domain.repository.ProfileRepository
import javax.inject.Inject

data class ProfileUiState(
    val profile: UserProfile? = null,
    val isLoading: Boolean = false,
    val isOffline: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val isEditMode: Boolean = false,
    val editedName: String = "",
    val photoUploadProgress: Float = 0f,
    val showDeleteAccountDialog: Boolean = false,
    val showLogoutDialog: Boolean = false
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val imageUploadRepository: ImageUploadRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    companion object {
        private const val TAG = "ProfileViewModel"
    }

    init {
        loadProfile()
        checkNetworkStatus()
    }

    /**
     * Load user profile from repository
     */
    fun loadProfile() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            profileRepository.getUserProfile().collect { result ->
                when (result) {
                    is Result.Loading -> {
                        _uiState.value = _uiState.value.copy(isLoading = true)
                    }

                    is Result.Success -> {
                        _uiState.value = _uiState.value.copy(
                            profile = result.data,
                            isLoading = false,
                            editedName = result.data.name,
                            errorMessage = null
                        )
                        Log.d(TAG, "Profile loaded: ${result.data.email}")
                    }

                    is Result.Error -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = result.message
                        )
                        Log.e(TAG, "Failed to load profile: ${result.message}")
                    }
                }
            }
        }
    }

    /**
     * Update user profile name
     */
    fun updateProfileName(name: String) {
        viewModelScope.launch {
            val currentProfile = _uiState.value.profile ?: return@launch

            if (name.isBlank()) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Name cannot be empty"
                )
                return@launch
            }

            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            val updatedProfile = currentProfile.copy(name = name.trim())

            when (val result = profileRepository.updateProfile(updatedProfile)) {
                is Result.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isEditMode = false,
                        successMessage = "Profile updated successfully",
                        errorMessage = null
                    )
                    Log.d(TAG, "Profile name updated to: $name")
                }

                is Result.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = result.message
                    )
                    Log.e(TAG, "Failed to update name: ${result.message}")
                }

                is Result.Loading -> { /* Already handling */ }
            }
        }
    }

    /**
     * Upload profile photo
     */
    fun uploadProfilePhoto(imageUri: Uri, context: Context) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                errorMessage = null,
                photoUploadProgress = 0f
            )

            when (val result = profileRepository.uploadProfilePhoto(imageUri, imageUploadRepository)) {
                is Result.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        photoUploadProgress = 1f,
                        successMessage = "Profile photo updated",
                        errorMessage = null
                    )
                    Log.d(TAG, "Profile photo uploaded: ${result.data}")
                }

                is Result.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        photoUploadProgress = 0f,
                        errorMessage = result.message
                    )
                    Log.e(TAG, "Failed to upload photo: ${result.message}")
                }

                is Result.Loading -> {
                    _uiState.value = _uiState.value.copy(
                        photoUploadProgress = 0.5f
                    )
                }
            }
        }
    }

    /**
     * Update notification preferences
     */
    fun updateNotificationPreferences(
        notificationsEnabled: Boolean,
        emailNotifications: Boolean,
        pushNotifications: Boolean
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            when (val result = profileRepository.updateNotificationPreferences(
                notificationsEnabled, emailNotifications, pushNotifications
            )) {
                is Result.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        successMessage = "Preferences updated",
                        errorMessage = null
                    )
                    Log.d(TAG, "Notification preferences updated")
                }

                is Result.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = result.message
                    )
                    Log.e(TAG, "Failed to update preferences: ${result.message}")
                }

                is Result.Loading -> { /* Already handling */ }
            }
        }
    }

    /**
     * Toggle edit mode
     */
    fun toggleEditMode() {
        val currentProfile = _uiState.value.profile
        _uiState.value = _uiState.value.copy(
            isEditMode = !_uiState.value.isEditMode,
            editedName = currentProfile?.name ?: "",
            errorMessage = null
        )
    }

    /**
     * Update edited name (temporary)
     */
    fun updateEditedName(name: String) {
        _uiState.value = _uiState.value.copy(editedName = name)
    }

    /**
     * Cancel edit mode
     */
    fun cancelEdit() {
        val currentProfile = _uiState.value.profile
        _uiState.value = _uiState.value.copy(
            isEditMode = false,
            editedName = currentProfile?.name ?: "",
            errorMessage = null
        )
    }

    /**
     * Save edited name
     */
    fun saveEdit() {
        updateProfileName(_uiState.value.editedName)
    }

    /**
     * Show delete account dialog
     */
    fun showDeleteAccountDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showDeleteAccountDialog = show)
    }

    /**
     * Show logout dialog
     */
    fun showLogoutDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showLogoutDialog = show)
    }

    /**
     * Delete user account
     */
    fun deleteAccount(onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            when (val result = profileRepository.deleteAccount()) {
                is Result.Success -> {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    Log.d(TAG, "Account deleted successfully")
                    onSuccess()
                }

                is Result.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = result.message,
                        showDeleteAccountDialog = false
                    )
                    Log.e(TAG, "Failed to delete account: ${result.message}")
                }

                is Result.Loading -> { /* Already handling */ }
            }
        }
    }

    /**
     * Clear messages
     */
    fun clearMessages() {
        _uiState.value = _uiState.value.copy(
            errorMessage = null,
            successMessage = null
        )
    }

    /**
     * Check network status
     */
    private fun checkNetworkStatus() {
        _uiState.value = _uiState.value.copy(
            isOffline = !profileRepository.isNetworkAvailable()
        )
    }

    /**
     * Refresh network status
     */
    fun refreshNetworkStatus() {
        checkNetworkStatus()
    }
}