package uk.ac.tees.mad.fixit.data.local

import androidx.room.TypeConverter
import uk.ac.tees.mad.fixit.data.model.IssueType
import uk.ac.tees.mad.fixit.data.model.ReportStatus

class IssueTypeConverters {
    @TypeConverter
    fun fromIssueType(issueType: IssueType): String = issueType.name

    @TypeConverter
    fun toIssueType(name: String): IssueType = IssueType.fromName(name)
}

class ReportStatusConverters {
    @TypeConverter
    fun fromReportStatus(status: ReportStatus): String = status.name

    @TypeConverter
    fun toReportStatus(name: String): ReportStatus = ReportStatus.fromName(name)
}