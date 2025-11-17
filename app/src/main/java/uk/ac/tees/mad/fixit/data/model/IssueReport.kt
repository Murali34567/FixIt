package uk.ac.tees.mad.fixit.data.model

/**
 * Represents a civic issue report
 */
data class IssueReport(
    val id: String = "",
    val userId: String = "",
    val imageUrl: String = "",
    val description: String = "",
    val issueType: IssueType = IssueType.OTHER,
    val location: IssueLocation = IssueLocation(),
    val status: ReportStatus = ReportStatus.PENDING,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Represents the location of an issue
 */
data class IssueLocation(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val address: String = ""
)

/**
 * Types of civic issues that can be reported
 */
enum class IssueType(val displayName: String) {
    POTHOLE("Pothole"),
    STREETLIGHT("Streetlight"),
    GARBAGE("Garbage"),
    DRAINAGE("Drainage"),
    ROAD_DAMAGE("Road Damage"),
    OTHER("Other");

    companion object {
        fun fromDisplayName(name: String): IssueType {
            return values().find { it.displayName == name } ?: OTHER
        }
    }
}

/**
 * Status of a report
 */
enum class ReportStatus(val displayName: String) {
    PENDING("Pending"),
    SUBMITTED("Submitted"),
    IN_PROGRESS("In Progress"),
    RESOLVED("Resolved")
}