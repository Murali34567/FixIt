package uk.ac.tees.mad.fixit.domain.repository

import android.util.Log
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import uk.ac.tees.mad.fixit.data.local.IssueReportDao
import uk.ac.tees.mad.fixit.data.local.toDomainModel
import uk.ac.tees.mad.fixit.data.local.toDomainModels
import uk.ac.tees.mad.fixit.data.local.toEntity
import uk.ac.tees.mad.fixit.data.model.IssueReport
import uk.ac.tees.mad.fixit.data.model.ReportStatus
import uk.ac.tees.mad.fixit.data.model.Result
import uk.ac.tees.mad.fixit.domain.util.NetworkHelper
import uk.ac.tees.mad.fixit.domain.util.SyncManager
import uk.ac.tees.mad.fixit.domain.util.SyncState
import javax.inject.Inject

class IssueRepository @Inject constructor(
    private val localDao: IssueReportDao,
    private val remoteRepository: ReportRepository,
    private val networkHelper: NetworkHelper,
    private val syncManager: SyncManager
) {

    companion object {
        private const val TAG = "IssueRepository"
    }

    /**
     * ✅ FIXED: Offline-first pattern.
     * 1. Emit Loading.
     * 2. Launch background sync (non-blocking).
     * 3. Immediately start emitting data from the local DB.
     * 4. When sync finishes, it updates the DB, which causes the local DB Flow to re-emit.
     */
    fun getAllReports(userId: String): Flow<Result<List<IssueReport>>> {
        Log.d(TAG, "🟡 getAllReports called for userId: $userId")

        if (userId.isBlank()) {
            Log.e(TAG, "🔴 User ID is blank!")
            return flow { emit(Result.Error("User not authenticated")) }
        }

        // STEP 1: Get the flow from the local database
        val localDataFlow = localDao.getReportsByUserId(userId)
            .catch { e ->
                Log.e(TAG, "🔴 Local DB error: ${e.message}", e)
                // This will emit an error and terminate the flow
                //emit(Result.Error("Failed to load from database: ${e.message}", e as? Exception))
            }
            .map { entities ->
                val reports = entities.toDomainModels()
                Log.d(TAG, "✅ Emitting ${reports.size} reports from local DB")
                // Map to Result.Success. This is now a Flow<Result<List<IssueReport>>>
                Result.Success(reports) as Result<List<IssueReport>>
            }

        // STEP 2: Use onStart to trigger loading and background sync
        return localDataFlow.onStart {
            emit(Result.Loading) // Emit Loading first

            // Launch a non-blocking sync in the flow's coroutine scope
            coroutineScope {
                launch {
                    try {
                        if (networkHelper.isNetworkAvailable()) {
                            Log.d(TAG, "🚀 [Background] Starting sync...")
                            syncReports(userId) // This is now a one-time sync
                            Log.d(TAG, "✅ [Background] Sync finished.")
                        } else {
                            Log.d(TAG, "🟡 [Background] No network, skipping sync.")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "🔴 [Background] Sync error: ${e.message}", e)
                        // Don't emit an error here, as it would cancel the main data flow.
                        // The local data flow will continue.
                        // We could update a separate "syncError" state in the UI if needed.
                    }
                }
            }

        }
    }


    suspend fun getReportById(reportId: String, userId: String): Result<IssueReport> {
        return try {
            Log.d(TAG, "🟡 Getting report by ID: $reportId")

            val localReport = localDao.getReportById(reportId, userId)
            if (localReport != null) {
                Log.d(TAG, "✅ Found report in local DB")
                Result.Success(localReport.toDomainModel())
            } else {
                Log.d(TAG, "⚠️ Report not found in local DB")

                if (networkHelper.isNetworkAvailable()) {
                    Log.d(TAG, "🟡 Trying to sync from Firebase...")
                    // We sync all reports, which might be inefficient but will get the one we need
                    syncReports(userId)

                    val reportAfterSync = localDao.getReportById(reportId, userId)
                    if (reportAfterSync != null) {
                        Log.d(TAG, "✅ Found report after sync")
                        Result.Success(reportAfterSync.toDomainModel())
                    } else {
                        Log.e(TAG, "🔴 Report not found even after sync")
                        Result.Error("Report not found")
                    }
                } else {
                    Log.e(TAG, "🔴 Report not found and no network")
                    Result.Error("Report not found and no network")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "🔴 Error getting report: ${e.message}", e)
            Result.Error("Failed to get report: ${e.message}", e)
        }
    }

    suspend fun createReport(report: IssueReport): Result<String> {
        return try {
            Log.d(TAG, "🟡 Creating report: ${report.id}")

            if (networkHelper.isNetworkAvailable()) {
                Log.d(TAG, "🟡 Network available - saving to Firebase first")

                var firebaseSuccess = false
                var errorMessage = ""

                remoteRepository.createReport(report).collect { result ->
                    when (result) {
                        is Result.Success -> {
                            Log.d(TAG, "✅ Saved to Firebase")
                            firebaseSuccess = true
                            // Save to local with synced flag
                            localDao.insertReport(report.toEntity(isSynced = true))
                            Log.d(TAG, "✅ Saved to local DB")
                        }

                        is Result.Error -> {
                            Log.e(TAG, "🔴 Firebase save failed: ${result.message}")
                            errorMessage = result.message
                            // Save to local with unsynced flag
                            localDao.insertReport(report.toEntity(isSynced = false))
                            Log.d(TAG, "⚠️ Saved to local DB (unsynced)")
                        }

                        is Result.Loading -> {
                            Log.d(TAG, "🟡 Saving to Firebase...")
                        }
                    }
                }

                if (firebaseSuccess) {
                    Result.Success(report.id)
                } else {
                    Result.Error("Saved locally but sync failed: $errorMessage")
                }
            } else {
                Log.d(TAG, "⚠️ No network - saving locally only")
                localDao.insertReport(report.toEntity(isSynced = false))
                Result.Success(report.id)
            }
        } catch (e: Exception) {
            Log.e(TAG, "🔴 Error creating report: ${e.message}", e)
            try {
                // Fallback: save locally
                localDao.insertReport(report.toEntity(isSynced = false))
                Result.Success(report.id)
            } catch (localError: Exception) {
                Result.Error("Failed completely: ${e.message}", e)
            }
        }
    }

    suspend fun updateReport(report: IssueReport): Result<Boolean> {
        return try {
            Log.d(TAG, "🟡 Updating report: ${report.id}")

            localDao.updateReport(report.toEntity(isSynced = false))

            if (networkHelper.isNetworkAvailable()) {
                var success = false
                remoteRepository.updateReport(report.id, report).collect { result ->
                    if (result is Result.Success) {
                        localDao.updateReport(report.toEntity(isSynced = true))
                        success = true
                        Log.d(TAG, "✅ Updated in Firebase and local DB")
                    }
                }
                Result.Success(success)
            } else {
                Log.d(TAG, "⚠️ Updated locally only (no network)")
                Result.Success(true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "🔴 Error updating: ${e.message}", e)
            Result.Error("Failed to update: ${e.message}", e)
        }
    }

    suspend fun deleteReport(reportId: String, userId: String): Result<Boolean> {
        return try {
            Log.d(TAG, "🟡 Deleting report: $reportId")

            val localReport = localDao.getReportById(reportId, userId)
            if (localReport != null) {
                localDao.deleteReport(reportId, userId)
                Log.d(TAG, "✅ Deleted from local DB")

                if (networkHelper.isNetworkAvailable()) {
                    remoteRepository.deleteReport(reportId).collect { result ->
                        if (result is Result.Success) {
                            Log.d(TAG, "✅ Deleted from Firebase")
                        } else if (result is Result.Error) {
                            Log.e(TAG, "⚠️ Failed to delete from Firebase: ${result.message}")
                        }
                    }
                }
                Result.Success(true)
            } else {
                Log.e(TAG, "🔴 Report not found")
                Result.Error("Report not found")
            }
        } catch (e: Exception) {
            Log.e(TAG, "🔴 Error deleting: ${e.message}", e)
            Result.Error("Failed to delete: ${e.message}", e)
        }
    }

    /**
     * ✅ FIXED: Use the new one-time fetch function `getReportsByUserIdOnce`
     */
    suspend fun syncReports(userId: String): Result<Boolean> {
        return try {
            Log.d(TAG, "🟡 Manual sync requested for userId: $userId")

            if (!networkHelper.isNetworkAvailable()) {
                Log.e(TAG, "🔴 No network connection")
                return Result.Error("No network connection")
            }

            syncManager.setSyncState(SyncState.SYNCING)

            // Use the NEW one-time fetch function
            when (val result = remoteRepository.getReportsByUserIdOnce(userId)) {
                is Result.Success -> {
                    Log.d(TAG, "✅ Sync successful - ${result.data.size} reports")
                    // This logic is fine: clear old and insert new
                    localDao.deleteAllUserReports(userId)
                    val entities = result.data.map { it.toEntity(isSynced = true) }
                    localDao.insertAll(entities)
                    Log.d(TAG, "✅ Local DB updated with ${entities.size} reports")
                    syncManager.setSyncState(SyncState.COMPLETED)
                    Result.Success(true)
                }

                is Result.Error -> {
                    Log.e(TAG, "🔴 Sync failed: ${result.message}")
                    syncManager.setSyncState(SyncState.ERROR(result.message))
                    Result.Error(result.message) // Propagate error
                }

                is Result.Loading -> {
                    // This case shouldn't happen with the new suspend function
                    Log.e(TAG, "🔴 Sync in unexpected Loading state")
                    Result.Error("Sync in unexpected state")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "🔴 Sync error: ${e.message}", e)
            syncManager.setSyncState(SyncState.ERROR(e.message ?: "Sync failed"))
            Result.Error("Sync failed: ${e.message}", e)
        }
    }

    fun getReportsByStatus(userId: String, status: ReportStatus): Flow<Result<List<IssueReport>>> =
        flow {
            try {
                emit(Result.Loading)

                if (networkHelper.isNetworkAvailable()) {
                    syncReports(userId)
                }

                localDao.getReportsByStatus(userId, status)
                    .catch { e ->
                        Log.e(TAG, "🔴 Error: ${e.message}", e)
                        emit(Result.Error("Failed to load: ${e.message}", e as? Exception))
                    }
                    .collect { entities ->
                        emit(Result.Success(entities.toDomainModels()))
                    }
            } catch (e: Exception) {
                emit(Result.Error("Failed to load: ${e.message}", e))
            }
        }

    suspend fun getPendingSyncCount(userId: String): Int {
        return try {
            localDao.getPendingSyncCount(userId)
        } catch (e: Exception) {
            Log.e(TAG, "🔴 Error getting sync count: ${e.message}")
            0
        }
    }

    suspend fun forceRefresh(userId: String): Result<Boolean> {
        return syncReports(userId)
    }
}
