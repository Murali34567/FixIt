package uk.ac.tees.mad.fixit.domain.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import uk.ac.tees.mad.fixit.data.model.AuthResult
import javax.inject.Inject

class AuthRepository @Inject constructor() {

    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()

    val currentUser: FirebaseUser?
        get() = firebaseAuth.currentUser

    fun isUserAuthenticated(): Boolean {
        return currentUser != null
    }

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

    suspend fun resetPassword(email: String): Flow<AuthResult> = flow {
        try {
            emit(AuthResult.Loading)
            firebaseAuth.sendPasswordResetEmail(email).await()
            emit(AuthResult.Success("Password reset email sent"))
        } catch (e: Exception) {
            emit(AuthResult.Error(e.message ?: "Password reset failed"))
        }
    }

    fun signOut() {
        firebaseAuth.signOut()
    }
}