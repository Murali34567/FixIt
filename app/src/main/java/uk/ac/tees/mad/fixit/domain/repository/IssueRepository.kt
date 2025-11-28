package uk.ac.tees.mad.fixit.domain.repository

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
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
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class IssueRepository @Inject constructor(
    private val localDao: IssueReportDao,
    private val remoteRepository: ReportRepository,
    private val networkHelper: NetworkHelper,
    private val syncManager: SyncManager
) {

    companion object {
        private const val TAG = "IssueRepository"
        private const val SYNC_THRESHOLD_MINUTES = 1L
    }

    /**
     * 🔥 FIXED: Properly sync with Firebase FIRST, then emit local data
     */
    fun getAllReports(userId: String): Flow<Result<List<IssueReport>>> = flow {
        try {
            Log.d(TAG, "🟡 getAllReports called for userId: $userId")

            if (userId.isBlank()) {
                emit(Result.Error("User not authenticated"))
                return@flow
            }

            emit(Result.Loading)

            // STEP 1: If network available and should sync, do Firebase sync FIRST
            if (networkHelper.isNetworkAvailable() && shouldSync()) {
                Log.d(TAG, "🟡 Syncing with Firebase...")
                syncManager.setSyncState(SyncState.SYNCING)

                try {
                    // Fetch from Firebase
                    remoteRepository.getReportsByUserId(userId).collect { firebaseResult ->
                        when (firebaseResult) {
                            is Result.Success -> {
                                Log.d(TAG, "✅ Firebase returned ${firebaseResult.data.size} reports")

                                // Clear old data and save new data
                                localDao.deleteAllUserReports(userId)
                                val entities = firebaseResult.data.map { it.toEntity(isSynced = true) }
                                localDao.insertAll(entities)

                                Log.d(TAG, "✅ Saved to local database")
                                syncManager.setSyncState(SyncState.COMPLETED)
                            }
                            is Result.Error -> {
                                Log.e(TAG, "🔴 Firebase sync failed: ${firebaseResult.message}")
                                syncManager.setSyncState(SyncState.ERROR(firebaseResult.message))
                            }
                            is Result.Loading -> {
                                Log.d(TAG, "🟡 Firebase loading...")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "🔴 Sync error: ${e.message}", e)
                    syncManager.setSyncState(SyncState.ERROR(e.message ?: "Sync failed"))
                }
            } else {
                Log.d(TAG, "🟡 Using cached data (no network or recently synced)")
            }

            // STEP 2: Now observe and emit from local database
            localDao.getReportsByUserId(userId)
                .catch { e ->
                    Log.e(TAG, "🔴 Local DB error: ${e.message}", e)
                    emit(Result.Error("Failed to load from database: ${e.message}", e as? Exception))
                }
                .collect { entities ->
                    val reports = entities.toDomainModels()
                    Log.d(TAG, "✅ Emitting ${reports.size} reports from local DB")
                    emit(Result.Success(reports))
                }

        } catch (e: Exception) {
            Log.e(TAG, "🔴 Error in getAllReports: ${e.message}", e)
            emit(Result.Error("Failed to load reports: ${e.message}", e))
        }
    }

    suspend fun getReportById(reportId: String, userId: String): Result<IssueReport> {
        return try {
            val localReport = localDao.getReportById(reportId, userId)
            if (localReport != null) {
                Result.Success(localReport.toDomainModel())
            } else {
                if (networkHelper.isNetworkAvailable()) {
                    forceSync(userId)
                    val reportAfterSync = localDao.getReportById(reportId, userId)
                    if (reportAfterSync != null) {
                        Result.Success(reportAfterSync.toDomainModel())
                    } else {
                        Result.Error("Report not found")
                    }
                } else {
                    Result.Error("Report not found and no network")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting report: ${e.message}", e)
            Result.Error("Failed to get report: ${e.message}", e)
        }
    }

    suspend fun createReport(report: IssueReport): Result<String> {
        return try {
            Log.d(TAG, "🟡 Creating report: ${report.id}")

            if (networkHelper.isNetworkAvailable()) {
                // Try Firebase first
                var firebaseSuccess = false
                var errorMessage = ""

                remoteRepository.createReport(report).collect { result ->
                    when (result) {
                        is Result.Success -> {
                            Log.d(TAG, "✅ Saved to Firebase")
                            firebaseSuccess = true
                            localDao.insertReport(report.toEntity(isSynced = true))
                        }
                        is Result.Error -> {
                            Log.e(TAG, "🔴 Firebase save failed: ${result.message}")
                            errorMessage = result.message
                            localDao.insertReport(report.toEntity(isSynced = false))
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
                localDao.insertReport(report.toEntity(isSynced = false))
                Log.d(TAG, "⚠️ Offline - saved locally only")
                Result.Success(report.id)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating report: ${e.message}", e)
            try {
                localDao.insertReport(report.toEntity(isSynced = false))
                Result.Success(report.id)
            } catch (localError: Exception) {
                Result.Error("Failed completely: ${e.message}", e)
            }
        }
    }

    suspend fun updateReport(report: IssueReport): Result<Boolean> {
        return try {
            localDao.updateReport(report.toEntity(isSynced = false))

            if (networkHelper.isNetworkAvailable()) {
                var success = false
                remoteRepository.updateReport(report.id, report).collect { result ->
                    if (result is Result.Success) {
                        localDao.updateReport(report.toEntity(isSynced = true))
                        success = true
                    }
                }
                Result.Success(success)
            } else {
                Result.Success(true)
            }
        } catch (e: Exception) {
            Result.Error("Failed to update: ${e.message}", e)
        }
    }

    suspend fun deleteReport(reportId: String, userId: String): Result<Boolean> {
        return try {
            val localReport = localDao.getReportById(reportId, userId)
            if (localReport != null) {
                localDao.deleteReport(reportId, userId)
                Log.d(TAG, "✅ Deleted from local DB")

                if (networkHelper.isNetworkAvailable()) {
                    remoteRepository.deleteReport(reportId).collect { result ->
                        if (result is Result.Success) {
                            Log.d(TAG, "✅ Deleted from Firebase")
                        }
                    }
                }
                Result.Success(true)
            } else {
                Result.Error("Report not found")
            }
        } catch (e: Exception) {
            Result.Error("Failed to delete: ${e.message}", e)
        }
    }

    suspend fun syncReports(userId: String): Result<Boolean> {
        return try {
            if (!networkHelper.isNetworkAvailable()) {
                return Result.Error("No network connection")
            }

            forceSync(userId)
            Result.Success(true)
        } catch (e: Exception) {
            Result.Error("Sync failed: ${e.message}", e)
        }
    }

    private suspend fun forceSync(userId: String) {
        syncManager.setSyncState(SyncState.SYNCING)

        try {
            remoteRepository.getReportsByUserId(userId).collect { result ->
                when (result) {
                    is Result.Success -> {
                        localDao.deleteAllUserReports(userId)
                        val entities = result.data.map { it.toEntity(isSynced = true) }
                        localDao.insertAll(entities)
                        syncManager.setSyncState(SyncState.COMPLETED)
                    }
                    is Result.Error -> {
                        syncManager.setSyncState(SyncState.ERROR(result.message))
                    }
                    else -> {}
                }
            }
        } catch (e: Exception) {
            syncManager.setSyncState(SyncState.ERROR(e.message ?: "Sync failed"))
        }
    }

    fun getReportsByStatus(userId: String, status: ReportStatus): Flow<Result<List<IssueReport>>> = flow {
        try {
            emit(Result.Loading)

            if (networkHelper.isNetworkAvailable() && shouldSync()) {
                forceSync(userId)
            }

            localDao.getReportsByStatus(userId, status)
                .map { entities -> entities.toDomainModels() }
                .collect { reports ->
                    emit(Result.Success(reports))
                }
        } catch (e: Exception) {
            emit(Result.Error("Failed to load filtered reports: ${e.message}", e))
        }
    }

    private suspend fun shouldSync(): Boolean {
        val lastSync = syncManager.lastSyncTime.value
        return if (lastSync == null) {
            true
        } else {
            val timeSinceLastSync = System.currentTimeMillis() - lastSync
            timeSinceLastSync > TimeUnit.MINUTES.toMillis(SYNC_THRESHOLD_MINUTES)
        }
    }

    suspend fun getPendingSyncCount(userId: String): Int {
        return try {
            localDao.getPendingSyncCount(userId)
        } catch (e: Exception) {
            0
        }
    }

    suspend fun forceRefresh(userId: String): Result<Boolean> {
        return try {
            if (!networkHelper.isNetworkAvailable()) {
                return Result.Error("No network connection")
            }
            forceSync(userId)
            Result.Success(true)
        } catch (e: Exception) {
            Result.Error("Refresh failed: ${e.message}", e)
        }
    }
}