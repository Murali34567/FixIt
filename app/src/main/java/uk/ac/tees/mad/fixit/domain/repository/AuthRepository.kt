package uk.ac.tees.mad.fixit.domain.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import uk.ac.tees.mad.fixit.data.model.AuthResult

/**
 * Repository for handling all Firebase Authentication operations
 * Implements single source of truth pattern
 */
class AuthRepository {

    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()

    /**
     * Get currently authenticated user
     */
    val currentUser: FirebaseUser?
        get() = firebaseAuth.currentUser

    /**
     * Check if user is currently authenticated
     */
    fun isUserAuthenticated(): Boolean {
        return currentUser != null
    }

    /**
     * Register new user with email and password
     * @param email User's email address
     * @param password User's password (min 6 characters)
     * @return Flow emitting AuthResult states
     */
    suspend fun signUp(email: String, password: String): Flow<AuthResult> = flow {
        try {
            emit(AuthResult.Loading)
            val result = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
            val userId = result.user?.uid ?: throw Exception("User ID is null")
            emit(AuthResult.Success(userId))
        } catch (e: Exception) {
            emit(AuthResult.Error(e.message ?: "Sign up failed"))
        }
    }

    /**
     * Login existing user with email and password
     * @param email User's email address
     * @param password User's password
     * @return Flow emitting AuthResult states
     */
    suspend fun login(email: String, password: String): Flow<AuthResult> = flow {
        try {
            emit(AuthResult.Loading)
            val result = firebaseAuth.signInWithEmailAndPassword(email, password).await()
            val userId = result.user?.uid ?: throw Exception("User ID is null")
            emit(AuthResult.Success(userId))
        } catch (e: Exception) {
            emit(AuthResult.Error(e.message ?: "Login failed"))
        }
    }

    /**
     *
     * Send password reset email
     * @param email User's registered email address
     * @return Flow emitting AuthResult states
     */
    suspend fun resetPassword(email: String): Flow<AuthResult> = flow {
        try {
            emit(AuthResult.Loading)
            firebaseAuth.sendPasswordResetEmail(email).await()
            emit(AuthResult.Success("Password reset email sent"))
        } catch (e: Exception) {
            emit(AuthResult.Error(e.message ?: "Password reset failed"))
        }
    }

    /**
     * Sign out current user
     */
    fun signOut() {
        firebaseAuth.signOut()
    }
}