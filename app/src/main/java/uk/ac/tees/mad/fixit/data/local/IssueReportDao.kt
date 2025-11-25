package uk.ac.tees.mad.fixit.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import uk.ac.tees.mad.fixit.data.model.ReportStatus

@Dao
interface IssueReportDao {

    @Query("SELECT * FROM issue_reports WHERE userId = :userId ORDER BY timestamp DESC")
    fun getReportsByUserId(userId: String): Flow<List<IssueReportEntity>>

    @Query("SELECT * FROM issue_reports WHERE id = :reportId AND userId = :userId")
    suspend fun getReportById(reportId: String, userId: String): IssueReportEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReport(report: IssueReportEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(reports: List<IssueReportEntity>)

    @Update
    suspend fun updateReport(report: IssueReportEntity): Int

    @Query("DELETE FROM issue_reports WHERE id = :reportId AND userId = :userId")
    suspend fun deleteReport(reportId: String, userId: String)

    @Query("DELETE FROM issue_reports WHERE userId = :userId")
    suspend fun deleteAllUserReports(userId: String)

    @Query("SELECT * FROM issue_reports WHERE userId = :userId AND status = :status ORDER BY timestamp DESC")
    fun getReportsByStatus(userId: String, status: ReportStatus): Flow<List<IssueReportEntity>>

    @Query("SELECT * FROM issue_reports WHERE userId = :userId AND lastUpdated > :sinceTimestamp ORDER BY timestamp DESC")
    suspend fun getReportsUpdatedSince(userId: String, sinceTimestamp: Long): List<IssueReportEntity>

    @Query("SELECT COUNT(*) FROM issue_reports WHERE userId = :userId AND isSynced = 0")
    suspend fun getPendingSyncCount(userId: String): Int

    @Query("SELECT * FROM issue_reports WHERE userId = :userId AND isSynced = 0")
    suspend fun getUnsyncedReports(userId: String): List<IssueReportEntity>
}