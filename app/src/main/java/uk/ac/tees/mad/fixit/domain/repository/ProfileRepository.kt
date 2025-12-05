package uk.ac.tees.mad.fixit.domain.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.auth.auth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.database
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import uk.ac.tees.mad.fixit.data.model.Result
import uk.ac.tees.mad.fixit.data.model.UserProfile
import javax.inject.Inject

class ProfileRepository @Inject constructor(
    private val context: Context
) {
    private val auth: FirebaseAuth = Firebase.auth
    private val database: FirebaseDatabase =
        Firebase.database("https://fixit-83fcd-default-rtdb.firebaseio.com/")

    companion object {
        private const val TAG = "ProfileRepository"
        private const val USERS_REF = "users"
    }

    private fun getUserRef(userId: String) =
        database.getReference(USERS_REF).child(userId)

    /**
     * Get current user profile from Firebase Realtime Database
     */
    fun getUserProfile(): Flow<Result<UserProfile>> = callbackFlow {
        val currentUser = auth.currentUser

        if (currentUser == null) {
            trySend(Result.Error("User not authenticated"))
            close()
            return@callbackFlow
        }

        val userId = currentUser.uid
        trySend(Result.Loading)

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    if (snapshot.exists()) {
                        val profileMap = snapshot.value as? Map<String, Any>
                        if (profileMap != null) {
                            val profile = UserProfile.fromMap(profileMap)
                            trySend(Result.Success(profile))
                        } else {
                            // Create default profile from Firebase Auth data
                            createDefaultProfileNonSuspend(currentUser.email ?: "")
                        }
                    } else {
                        // Profile doesn't exist, create one
                        createDefaultProfileNonSuspend(currentUser.email ?: "")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing profile: ${e.message}", e)
                    trySend(Result.Error("Failed to load profile: ${e.message}"))
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Database error: ${error.message}")
                trySend(Result.Error("Failed to load profile: ${error.message}"))
            }
        }

        getUserRef(userId).addValueEventListener(listener)

        awaitClose {
            getUserRef(userId).removeEventListener(listener)
        }
    }

    /**
     * Create a default profile for new users (non-suspend version)
     */
    private fun createDefaultProfileNonSuspend(email: String) {
        val currentUser = auth.currentUser ?: return

        val profile = UserProfile(
            uid = currentUser.uid,
            email = email,
            name = email.substringBefore("@"),
            photoUrl = "",
            notificationsEnabled = true,
            emailNotifications = true,
            pushNotifications = true,
            createdAt = System.currentTimeMillis(),
            lastUpdated = System.currentTimeMillis()
        )

        getUserRef(currentUser.uid).setValue(profile.toMap())
            .addOnSuccessListener {
                Log.d(TAG, "Default profile created for user: ${currentUser.uid}")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to create default profile: ${e.message}", e)
            }
    }

    /**
     * Update user profile
     */
    suspend fun updateProfile(profile: UserProfile): Result<Boolean> {
        return try {
            val currentUser = auth.currentUser
                ?: return Result.Error("User not authenticated")

            val updatedProfile = profile.copy(
                lastUpdated = System.currentTimeMillis()
            )

            // Update in Firebase Realtime Database
            getUserRef(currentUser.uid).setValue(updatedProfile.toMap()).await()

            // Update display name in Firebase Auth if changed
            if (profile.name.isNotBlank() && profile.name != currentUser.displayName) {
                val profileUpdates = UserProfileChangeRequest.Builder()
                    .setDisplayName(profile.name)
                    .build()
                currentUser.updateProfile(profileUpdates).await()
            }

            Log.d(TAG, "Profile updated successfully")
            Result.Success(true)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to update profile: ${e.message}", e)
            Result.Error("Failed to update profile: ${e.message}")
        }
    }

    /**
     * Update notification preferences
     */
    suspend fun updateNotificationPreferences(
        notificationsEnabled: Boolean,
        emailNotifications: Boolean,
        pushNotifications: Boolean
    ): Result<Boolean> {
        return try {
            val currentUser = auth.currentUser
                ?: return Result.Error("User not authenticated")

            val updates = mapOf(
                "notificationsEnabled" to notificationsEnabled,
                "emailNotifications" to emailNotifications,
                "pushNotifications" to pushNotifications,
                "lastUpdated" to System.currentTimeMillis()
            )

            getUserRef(currentUser.uid).updateChildren(updates).await()
            Log.d(TAG, "Notification preferences updated")
            Result.Success(true)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to update preferences: ${e.message}", e)
            Result.Error("Failed to update preferences: ${e.message}")
        }
    }

    /**
     * Upload profile photo
     */
    suspend fun uploadProfilePhoto(
        imageUri: Uri,
        imageUploadRepository: ImageUploadRepository
    ): Result<String> {
        return try {
            val currentUser = auth.currentUser
                ?: return Result.Error("User not authenticated")

            var uploadedUrl = ""
            var errorMessage = ""

            // Upload image using existing ImageUploadRepository
            imageUploadRepository.uploadImage(
                imageUri,
                "profile_${currentUser.uid}_${System.currentTimeMillis()}.jpg"
            ).collect { result ->
                when (result) {
                    is Result.Success -> uploadedUrl = result.data
                    is Result.Error -> errorMessage = result.message
                    is Result.Loading -> { /* Continue */ }
                }
            }

            if (errorMessage.isNotBlank()) {
                return Result.Error(errorMessage)
            }

            if (uploadedUrl.isBlank()) {
                return Result.Error("Failed to upload image")
            }

            // Update photo URL in database
            val updates = mapOf(
                "photoUrl" to uploadedUrl,
                "lastUpdated" to System.currentTimeMillis()
            )
            getUserRef(currentUser.uid).updateChildren(updates).await()

            Log.d(TAG, "Profile photo uploaded: $uploadedUrl")
            Result.Success(uploadedUrl)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload photo: ${e.message}", e)
            Result.Error("Failed to upload photo: ${e.message}")
        }
    }

    /**
     * Delete user account and all associated data
     */
    suspend fun deleteAccount(): Result<Boolean> {
        return try {
            val currentUser = auth.currentUser
                ?: return Result.Error("User not authenticated")

            val userId = currentUser.uid

            // Delete user profile from database
            getUserRef(userId).removeValue().await()

            // Delete all user reports (from reports/{userId})
            database.getReference("reports").child(userId).removeValue().await()

            // Delete Firebase Auth account
            currentUser.delete().await()

            Log.d(TAG, "Account deleted successfully")
            Result.Success(true)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete account: ${e.message}", e)
            Result.Error("Failed to delete account: ${e.message}")
        }
    }

    /**
     * Check network connectivity
     */
    fun isNetworkAvailable(): Boolean {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE)
                    as android.net.ConnectivityManager
            val network = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            capabilities != null && (
                    capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) ||
                            capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) ||
                            capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET)
                    )
        } catch (e: Exception) {
            false
        }
    }
}