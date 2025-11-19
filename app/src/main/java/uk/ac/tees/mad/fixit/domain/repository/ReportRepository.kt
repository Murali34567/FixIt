package uk.ac.tees.mad.fixit.domain.repository

import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
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
import uk.ac.tees.mad.fixit.data.model.IssueLocation
import uk.ac.tees.mad.fixit.data.model.IssueReport
import uk.ac.tees.mad.fixit.data.model.IssueType
import uk.ac.tees.mad.fixit.data.model.ReportStatus
import uk.ac.tees.mad.fixit.data.model.Result
import java.util.UUID

class ReportRepository {

    private val database: FirebaseDatabase = Firebase.database
    private val auth: FirebaseAuth = Firebase.auth

    private fun getCurrentUserId(): String {
        return auth.currentUser?.uid ?: throw Exception("User not authenticated")
    }

    private fun getReportsRef() = database.getReference("reports")
    private fun getUserReportsRef(userId: String) = getReportsRef().child(userId)

    /**
     * Create a new issue report
     */
    suspend fun createReport(report: IssueReport): Flow<Result<String>> = callbackFlow {
        try {
            val userId = getCurrentUserId()

            // Generate a unique ID for the report
            val reportId = report.id.ifEmpty { UUID.randomUUID().toString() }
            val reportWithId = report.copy(
                id = reportId,
                userId = userId
            )

            // Send loading state
            trySend(Result.Loading)

            // Save to Firebase
            getUserReportsRef(userId).child(reportId).setValue(reportWithId.toMap())
                .addOnSuccessListener {
                    trySend(Result.Success(reportId))
                    close()
                }
                .addOnFailureListener { exception ->
                    trySend(Result.Error("Failed to create report: ${exception.message}", exception))
                    close()
                }
                .await()

        } catch (exception: Exception) {
            trySend(Result.Error("Failed to create report: ${exception.message}", exception))
            close()
        }

        awaitClose { }
    }

    /**
     * Update an existing report
     */
    suspend fun updateReport(reportId: String, report: IssueReport): Flow<Result<Boolean>> = callbackFlow {
        try {
            val userId = getCurrentUserId()

            trySend(Result.Loading)

            getUserReportsRef(userId).child(reportId).setValue(report.toMap())
                .addOnSuccessListener {
                    trySend(Result.Success(true))
                    close()
                }
                .addOnFailureListener { exception ->
                    trySend(Result.Error("Failed to update report: ${exception.message}", exception))
                    close()
                }
                .await()

        } catch (exception: Exception) {
            trySend(Result.Error("Failed to update report: ${exception.message}", exception))
            close()
        }

        awaitClose { }
    }

    /**
     * Get all reports for the current user
     */
    fun getReportsByUserId(userId: String): Flow<Result<List<IssueReport>>> = callbackFlow {
        try {
            trySend(Result.Loading)

            val reportsListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val reports = mutableListOf<IssueReport>()

                    snapshot.children.forEach { child ->
                        try {
                            val reportMap = child.value as? Map<*, *>
                            reportMap?.let { map ->
                                val report = mapToIssueReport(map as Map<String, Any>)
                                reports.add(report)
                            }
                        } catch (e: Exception) {
                            // Skip invalid reports
                        }
                    }

                    trySend(Result.Success(reports))
                }

                override fun onCancelled(error: DatabaseError) {
                    trySend(Result.Error("Failed to get reports: ${error.message}"))
                }
            }

            getUserReportsRef(userId).addValueEventListener(reportsListener)

            awaitClose {
                getUserReportsRef(userId).removeEventListener(reportsListener)
            }

        } catch (exception: Exception) {
            trySend(Result.Error("Failed to get reports: ${exception.message}", exception))
            close()
        }
    }

    /**
     * Get a specific report by ID
     */
    suspend fun getReportById(reportId: String): Flow<Result<IssueReport>> = callbackFlow {
        try {
            val userId = getCurrentUserId()

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
                .addOnFailureListener { exception ->
                    trySend(Result.Error("Failed to get report: ${exception.message}", exception))
                    close()
                }
                .await()

        } catch (exception: Exception) {
            trySend(Result.Error("Failed to get report: ${exception.message}", exception))
            close()
        }

        awaitClose { }
    }

    /**
     * Delete a report
     */
    suspend fun deleteReport(reportId: String): Flow<Result<Boolean>> = callbackFlow {
        try {
            val userId = getCurrentUserId()

            trySend(Result.Loading)

            getUserReportsRef(userId).child(reportId).removeValue()
                .addOnSuccessListener {
                    trySend(Result.Success(true))
                    close()
                }
                .addOnFailureListener { exception ->
                    trySend(Result.Error("Failed to delete report: ${exception.message}", exception))
                    close()
                }
                .await()

        } catch (exception: Exception) {
            trySend(Result.Error("Failed to delete report: ${exception.message}", exception))
            close()
        }

        awaitClose { }
    }

    /**
     * Convert Firebase map to IssueReport object
     */
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
}