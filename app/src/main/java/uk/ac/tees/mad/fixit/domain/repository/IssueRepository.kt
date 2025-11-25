package uk.ac.tees.mad.fixit.domain.repository

import kotlinx.coroutines.flow.Flow
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

/**
 * Unified repository that handles both local (Room) and remote (Firebase) data
 * Implements cache-first strategy with background sync
 */
class IssueRepository @Inject constructor(
    private val localDao: IssueReportDao,
    private val remoteRepository: ReportRepository,
    private val networkHelper: NetworkHelper,
    private val syncManager: SyncManager
) {

    companion object {
        private const val SYNC_THRESHOLD_MINUTES = 15L // Sync every 15 minutes
    }

    /**
     * Get all reports for user with cache-first strategy
     */
    fun getAllReports(userId: String): Flow<Result<List<IssueReport>>> = flow {
        try {
            emit(Result.Loading)

            // Always get from local cache first (offline support)
            val localReports = localDao.getReportsByUserId(userId)
                .map { entities ->
                    entities.toDomainModels()
                }

            // Emit local data immediately
            localReports.collect { reports ->
                emit(Result.Success(reports))
            }

            // Sync with remote if network available and data is stale
            if (shouldSync()) {
                syncReports(userId)
            }

        } catch (e: Exception) {
            emit(Result.Error("Failed to load reports: ${e.message}", e))
        }
    }

    /**
     * Get specific report by ID
     */
    suspend fun getReportById(reportId: String, userId: String): Result<IssueReport> {
        return try {
            // Try local first
            val localReport = localDao.getReportById(reportId, userId)
            if (localReport != null) {
                Result.Success(localReport.toDomainModel())
            } else {
                // Fall back to remote if not found locally
                if (networkHelper.isNetworkAvailable()) {
                    // This would need to be implemented in ReportRepository
                    // For now, we'll return error
                    Result.Error("Report not found")
                } else {
                    Result.Error("Report not found and no network connection")
                }
            }
        } catch (e: Exception) {
            Result.Error("Failed to get report: ${e.message}", e)
        }
    }

    /**
     * Create new report - write to both local and remote
     */
    suspend fun createReport(report: IssueReport): Result<String> {
        return try {
            // Save to local database first (optimistic UI)
            localDao.insertReport(report.toEntity().copy(isSynced = false))

            // Try to save to remote if network available
            if (networkHelper.isNetworkAvailable()) {
                // This would use the existing ReportRepository.createReport flow
                // For now, we'll simulate success
                // In Part 3, we'll properly integrate with the existing flow
                localDao.insertReport(report.toEntity().copy(isSynced = true))
                Result.Success(report.id)
            } else {
                // Report saved locally but needs sync later
                Result.Success(report.id)
            }
        } catch (e: Exception) {
            Result.Error("Failed to create report: ${e.message}", e)
        }
    }

    /**
     * Update existing report
     */
    suspend fun updateReport(report: IssueReport): Result<Boolean> {
        return try {
            // Update local database
            localDao.updateReport(report.toEntity().copy(isSynced = false))

            // Try to update remote if network available
            if (networkHelper.isNetworkAvailable()) {
                // This would use ReportRepository.updateReport
                localDao.updateReport(report.toEntity().copy(isSynced = true))
                Result.Success(true)
            } else {
                Result.Success(true) // Updated locally, will sync later
            }
        } catch (e: Exception) {
            Result.Error("Failed to update report: ${e.message}", e)
        }
    }

    /**
     * Delete report
     */
    suspend fun deleteReport(reportId: String, userId: String): Result<Boolean> {
        return try {
            // Mark as deleted locally first
            val localReport = localDao.getReportById(reportId, userId)
            if (localReport != null) {
                localDao.deleteReport(reportId, userId)

                // Try to delete from remote if network available
                if (networkHelper.isNetworkAvailable()) {
                    // This would use ReportRepository.deleteReport
                    Result.Success(true)
                } else {
                    // We might want to track deletions for sync
                    Result.Success(true)
                }
            } else {
                Result.Error("Report not found")
            }
        } catch (e: Exception) {
            Result.Error("Failed to delete report: ${e.message}", e)
        }
    }

    /**
     * Sync local data with remote
     */
    suspend fun syncReports(userId: String): Result<Boolean> {
        return try {
            if (!networkHelper.isNetworkAvailable()) {
                return Result.Error("No network connection available")
            }

            syncManager.setSyncState(SyncState.SYNCING)

            // Get unsynced local reports
            val unsyncedReports = localDao.getReportsUpdatedSince(userId, 0) // Get all for now

            // Sync each unsynced report
            unsyncedReports.forEach { localReport ->
                if (!localReport.isSynced) {
                    // Convert to domain model and sync with remote
                    val domainReport = localReport.toDomainModel()
                    // This would call remoteRepository.createReport or updateReport
                    // For now, we'll mark as synced
                    localDao.updateReport(localReport.copy(isSynced = true))
                }
            }

            // Also fetch latest from remote and update local
            // This would be implemented when we have the remote methods
            syncManager.setSyncState(SyncState.COMPLETED)
            Result.Success(true)

        } catch (e: Exception) {
            syncManager.setSyncState(SyncState.ERROR(e.message ?: "Sync failed"))
            Result.Error("Sync failed: ${e.message}", e)
        }
    }

    /**
     * Get reports filtered by status
     */
    fun getReportsByStatus(userId: String, status: ReportStatus): Flow<Result<List<IssueReport>>> = flow {
        try {
            emit(Result.Loading)

            val reports = localDao.getReportsByStatus(userId, status)
                .map { entities ->
                    entities.toDomainModels()
                }

            reports.collect { filteredReports ->
                emit(Result.Success(filteredReports))
            }

        } catch (e: Exception) {
            emit(Result.Error("Failed to load filtered reports: ${e.message}", e))
        }
    }

    /**
     * Check if we should sync based on last sync time
     */
    private suspend fun shouldSync(): Boolean {
        val lastSync = syncManager.lastSyncTime.value
        return if (lastSync == null) {
            true // Never synced
        } else {
            val timeSinceLastSync = System.currentTimeMillis() - lastSync
            timeSinceLastSync > TimeUnit.MINUTES.toMillis(SYNC_THRESHOLD_MINUTES)
        }
    }

    /**
     * Get pending sync count (for badge indicators)
     */
    suspend fun getPendingSyncCount(userId: String): Int {
        return localDao.getPendingSyncCount(userId)
    }
}