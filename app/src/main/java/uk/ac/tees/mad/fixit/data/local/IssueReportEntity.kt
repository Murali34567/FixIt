package uk.ac.tees.mad.fixit.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import uk.ac.tees.mad.fixit.data.model.IssueType
import uk.ac.tees.mad.fixit.data.model.ReportStatus

@Entity(tableName = "issue_reports")
data class IssueReportEntity(
    @PrimaryKey
    val id: String,
    val userId: String,
    val imageUrl: String,
    val description: String,
    val issueType: IssueType,
    val latitude: Double,
    val longitude: Double,
    val address: String,
    val status: ReportStatus,
    val timestamp: Long,
    val lastUpdated: Long = System.currentTimeMillis(),
    val isSynced: Boolean = true // Initially true since we're syncing from Firebase
)