package uk.ac.tees.mad.fixit.presentation.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import uk.ac.tees.mad.fixit.data.model.AuthResult
import uk.ac.tees.mad.fixit.domain.repository.AuthRepository

/**
 * UI state for authentication screens
 */
data class AuthUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isSuccess: Boolean = false
)

class AuthViewModel(
    private val authRepository: AuthRepository = AuthRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState

    /**
     * Register new user with email and password
     */
    fun signUp(email: String, password: String) {
        viewModelScope.launch {
            authRepository.signUp(email, password).collect { result ->
                when (result) {
                    is AuthResult.Loading -> {
                        _uiState.value = AuthUiState(isLoading = true)
                    }
                    is AuthResult.Success -> {
                        _uiState.value = AuthUiState(isSuccess = true)
                    }
                    is AuthResult.Error -> {
                        _uiState.value = AuthUiState(
                            errorMessage = formatErrorMessage(result.message)
                        )
                    }
                }
            }
        }
    }

    /**
     * Login existing user with email and password
     */
    fun login(email: String, password: String) {
        viewModelScope.launch {
            authRepository.login(email, password).collect { result ->
                when (result) {
                    is AuthResult.Loading -> {
                        _uiState.value = AuthUiState(isLoading = true)
                    }
                    is AuthResult.Success -> {
                        _uiState.value = AuthUiState(isSuccess = true)
                    }
                    is AuthResult.Error -> {
                        _uiState.value = AuthUiState(
                            errorMessage = formatErrorMessage(result.message)
                        )
                    }
                }
            }
        }
    }

    /**
     * Send password reset email
     */
    fun resetPassword(email: String) {
        viewModelScope.launch {
            authRepository.resetPassword(email).collect { result ->
                when (result) {
                    is AuthResult.Loading -> {
                        _uiState.value = AuthUiState(isLoading = true)
                    }
                    is AuthResult.Success -> {
                        _uiState.value = AuthUiState(
                            isSuccess = false,
                            errorMessage = "✓ Password reset email sent! Check your inbox."
                        )
                    }
                    is AuthResult.Error -> {
                        _uiState.value = AuthUiState(
                            errorMessage = formatErrorMessage(result.message)
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
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    /**
     * Format Firebase error messages to be more user-friendly
     */
    private fun formatErrorMessage(message: String): String {
        return when {
            message.contains("network") -> "Network error. Please check your connection."
            message.contains("password") -> "Invalid email or password."
            message.contains("email") -> "Invalid email format."
            message.contains("user-not-found") -> "No account found with this email."
            message.contains("wrong-password") -> "Incorrect password."
            message.contains("email-already-in-use") -> "An account with this email already exists."
            message.contains("weak-password") -> "Password should be at least 6 characters."
            else -> message
        }
    }
}