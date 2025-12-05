package uk.ac.tees.mad.fixit.domain.repository

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.database
import jakarta.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import uk.ac.tees.mad.fixit.data.model.IssueLocation
import uk.ac.tees.mad.fixit.data.model.IssueReport
import uk.ac.tees.mad.fixit.data.model.IssueType
import uk.ac.tees.mad.fixit.data.model.ReportStatus
import uk.ac.tees.mad.fixit.data.model.Result
import java.util.UUID

class ReportRepository @Inject constructor() {

    private val database: FirebaseDatabase = Firebase.database("https://fixit-83fcd-default-rtdb.firebaseio.com/")
    private val auth: FirebaseAuth = Firebase.auth

    private companion object {
        const val TAG = "ReportRepository"
    }

    private fun getReportsRef() = database.getReference("reports")
    private fun getUserReportsRef(userId: String) = getReportsRef().child(userId)

    /**
     * ✅ FIXED: Create report with proper userId handling
     */
    suspend fun createReport(report: IssueReport): Flow<Result<String>> = callbackFlow {
        Log.d(TAG, "📝 Creating report...")

        try {
            // Get current user ID
            val currentUser = auth.currentUser
            if (currentUser == null) {
                Log.e(TAG, "❌ No authenticated user")
                trySend(Result.Error("User not authenticated"))
                close()
                return@callbackFlow
            }

            val userId = currentUser.uid
            val reportId = report.id.ifEmpty { UUID.randomUUID().toString() }

            // Create report with userId
            val reportWithId = report.copy(
                id = reportId,
                userId = userId
            )

            Log.d(TAG, "📤 Saving to: reports/$userId/$reportId")
            Log.d(TAG, "📄 Data: ${reportWithId.toMap()}")

            trySend(Result.Loading)

            getUserReportsRef(userId).child(reportId).setValue(reportWithId.toMap())
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Log.d(TAG, "✅ Report saved to Firebase: $reportId")
                        trySend(Result.Success(reportId))
                    } else {
                        val error = task.exception?.message ?: "Unknown error"
                        Log.e(TAG, "🔴 Failed to save: $error")
                        trySend(Result.Error("Failed to create report: $error", task.exception))
                    }
                    close()
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "🔴 Firebase error: ${e.message}")
                    trySend(Result.Error("Firebase error: ${e.message}", e))
                    close()
                }

        } catch (e: Exception) {
            Log.e(TAG, "🔴 Exception: ${e.message}", e)
            trySend(Result.Error("Failed to create report: ${e.message}", e))
            close()
        }

        awaitClose {
            Log.d(TAG, "🔚 CreateReport flow closed")
        }
    }

    /**
     * ✅ FIXED: Get reports with proper error handling and logging (REAL-TIME LISTENER)
     */
    fun getReportsByUserId(userId: String): Flow<Result<List<IssueReport>>> = callbackFlow {
        Log.d(TAG, "🔥 Fetching reports for user: $userId (REAL-TIME)")
        Log.d(TAG, "📍 Firebase path: reports/$userId")

        if (userId.isBlank()) {
            Log.e(TAG, "🔴 User ID is blank!")
            trySend(Result.Error("Invalid user ID"))
            close()
            return@callbackFlow
        }

        try {
            trySend(Result.Loading)

            val reportsListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    Log.d(TAG, "📊 Firebase onDataChange called")
                    Log.d(TAG, "📊 Snapshot exists: ${snapshot.exists()}")
                    Log.d(TAG, "📊 Children count: ${snapshot.childrenCount}")

                    val reports = mutableListOf<IssueReport>()

                    snapshot.children.forEachIndexed { index, child ->
                        try {
                            Log.d(TAG, "  Processing child $index: ${child.key}")
                            val reportMap = child.value as? Map<*, *>

                            if (reportMap != null) {
                                val report = mapToIssueReport(reportMap as Map<String, Any>)
                                reports.add(report)
                                Log.d(TAG, "  ✅ Parsed: ${report.issueType.displayName} - ${report.id}")
                            } else {
                                Log.w(TAG, "  ⚠️ Child $index has null data")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "  🔴 Error parsing child $index: ${e.message}", e)
                        }
                    }

                    Log.d(TAG, "✅ Successfully parsed ${reports.size} reports")
                    trySend(Result.Success(reports))
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "🔴 Firebase error: ${error.message}")
                    Log.e(TAG, "🔴 Error code: ${error.code}")
                    Log.e(TAG, "🔴 Error details: ${error.details}")
                    trySend(Result.Error("Failed to get reports: ${error.message}"))
                }
            }

            val ref = getUserReportsRef(userId)
            ref.addValueEventListener(reportsListener)

            awaitClose {
                Log.d(TAG, "🔚 Removing Firebase listener")
                ref.removeEventListener(reportsListener)
            }

        } catch (e: Exception) {
            Log.e(TAG, "🔴 Exception in getReportsByUserId: ${e.message}", e)
            trySend(Result.Error("Failed to get reports: ${e.message}", e))
            close()
        }
    }

    /**
     * ✅ NEW: Get reports with a single fetch (not real-time) for sync operations
     */
    suspend fun getReportsByUserIdOnce(userId: String): Result<List<IssueReport>> {
        Log.d(TAG, "🔥 Fetching reports ONCE for user: $userId")
        Log.d(TAG, "📍 Firebase path: reports/$userId")

        if (userId.isBlank()) {
            Log.e(TAG, "🔴 User ID is blank!")
            return Result.Error("Invalid user ID")
        }

        return try {
            val snapshot = getUserReportsRef(userId).get().await() // Use .get() for one-time read

            Log.d(TAG, "📊 Firebase get() snapshot exists: ${snapshot.exists()}")
            Log.d(TAG, "📊 Children count: ${snapshot.childrenCount}")

            val reports = mutableListOf<IssueReport>()
            snapshot.children.forEachIndexed { index, child ->
                try {
                    Log.d(TAG, "  Processing child $index: ${child.key}")
                    val reportMap = child.value as? Map<*, *>

                    if (reportMap != null) {
                        val report = mapToIssueReport(reportMap as Map<String, Any>)
                        reports.add(report)
                        Log.d(TAG, "  ✅ Parsed: ${report.issueType.displayName} - ${report.id}")
                    } else {
                        Log.w(TAG, "  ⚠️ Child $index has null data")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "  🔴 Error parsing child $index: ${e.message}", e)
                }
            }
            Log.d(TAG, "✅ Successfully parsed ${reports.size} reports (once)")
            Result.Success(reports)

        } catch (e: Exception) {
            Log.e(TAG, "🔴 Firebase get() error: ${e.message}", e)
            Result.Error("Failed to get reports: ${e.message}", e)
        }
    }

    suspend fun updateReport(reportId: String, report: IssueReport): Flow<Result<Boolean>> = callbackFlow {
        try {
            val currentUser = auth.currentUser
            if (currentUser == null) {
                trySend(Result.Error("User not authenticated"))
                close()
                return@callbackFlow
            }

            val userId = currentUser.uid
            trySend(Result.Loading)

            getUserReportsRef(userId).child(reportId).setValue(report.toMap())
                .addOnSuccessListener {
                    Log.d(TAG, "✅ Report updated: $reportId")
                    trySend(Result.Success(true))
                    close()
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "🔴 Update failed: ${e.message}")
                    trySend(Result.Error("Failed to update: ${e.message}", e))
                    close()
                }
                .await()

        } catch (e: Exception) {
            trySend(Result.Error("Failed to update: ${e.message}", e))
            close()
        }

        awaitClose { }
    }

    suspend fun getReportById(reportId: String): Flow<Result<IssueReport>> = callbackFlow {
        try {
            val currentUser = auth.currentUser
            if (currentUser == null) {
                trySend(Result.Error("User not authenticated"))
                close()
                return@callbackFlow
            }

            val userId = currentUser.uid
            trySend(Result.Loading)

            getUserReportsRef(userId).child(reportId).get()
                .addOnSuccessListener { snapshot ->
                    if (snapshot.exists()) {
                        val reportMap = snapshot.value as? Map<String, Any>
                        reportMap?.let { map ->
                            val report = mapToIssueReport(map)
                            trySend(Result.Success(report))
                        } ?: trySend(Result.Error("Report data is invalid"))
                    } else {
                        trySend(Result.Error("Report not found"))
                    }
                    close()
                }
                .addOnFailureListener { e ->
                    trySend(Result.Error("Failed to get report: ${e.message}", e))
                    close()
                }
                .await()

        } catch (e: Exception) {
            trySend(Result.Error("Failed to get report: ${e.message}", e))
            close()
        }

        awaitClose { }
    }

    suspend fun deleteReport(reportId: String): Flow<Result<Boolean>> = callbackFlow {
        try {
            val currentUser = auth.currentUser
            if (currentUser == null) {
                trySend(Result.Error("User not authenticated"))
                close()
                return@callbackFlow
            }

            val userId = currentUser.uid
            trySend(Result.Loading)

            getUserReportsRef(userId).child(reportId).removeValue()
                .addOnSuccessListener {
                    Log.d(TAG, "✅ Report deleted: $reportId")
                    trySend(Result.Success(true))
                    close()
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "🔴 Delete failed: ${e.message}")
                    trySend(Result.Error("Failed to delete: ${e.message}", e))
                    close()
                }
                .await()

        } catch (e: Exception) {
            trySend(Result.Error("Failed to delete: ${e.message}", e))
            close()
        }

        awaitClose { }
    }

    private fun mapToIssueReport(map: Map<String, Any>): IssueReport {
        val locationMap = map["location"] as? Map<String, Any> ?: emptyMap()

        return IssueReport(
            id = (map["id"] as? String) ?: "",
            userId = (map["userId"] as? String) ?: "",
            imageUrl = (map["imageUrl"] as? String) ?: "",
            description = (map["description"] as? String) ?: "",
            issueType = IssueType.fromName((map["issueType"] as? String) ?: "OTHER"),
            location = IssueLocation.fromMap(locationMap),
            status = ReportStatus.fromName((map["status"] as? String) ?: "PENDING"),
            timestamp = (map["timestamp"] as? Long) ?: System.currentTimeMillis()
        )
    }

    suspend fun testFirebaseConnection(): Boolean {
        return try {
            Log.d(TAG, "🧪 Testing Firebase connection...")
            val testRef = database.getReference("connection_test")
            testRef.setValue("test_${System.currentTimeMillis()}").await()
            Log.d(TAG, "✅ Firebase connection test PASSED")
            true
        } catch (e: Exception) {
            Log.e(TAG, "🔴 Firebase connection test FAILED: ${e.message}")
            false
        }
    }
}