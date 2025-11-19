package uk.ac.tees.mad.fixit.data.model

import com.google.firebase.database.Exclude

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
) {
    @Exclude
    fun toMap(): Map<String, Any?> {
        return mapOf(
            "id" to id,
            "userId" to userId,
            "imageUrl" to imageUrl,
            "description" to description,
            "issueType" to issueType.name,
            "location" to location.toMap(),
            "status" to status.name,
            "timestamp" to timestamp
        )
    }
}

/**
 * Represents the location of an issue
 */
data class IssueLocation(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val address: String = ""
) {
    @Exclude
    fun toMap(): Map<String, Any?> {
        return mapOf(
            "latitude" to latitude,
            "longitude" to longitude,
            "address" to address
        )
    }

    companion object {
        fun fromMap(map: Map<String, Any>): IssueLocation {
            return IssueLocation(
                latitude = (map["latitude"] as? Double) ?: 0.0,
                longitude = (map["longitude"] as? Double) ?: 0.0,
                address = (map["address"] as? String) ?: ""
            )
        }
    }
}

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
        fun fromName(name: String): IssueType {
            return values().find { it.name == name } ?: OTHER
        }

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
    RESOLVED("Resolved");

    companion object {
        fun fromName(name: String): ReportStatus {
            return values().find { it.name == name } ?: PENDING
        }
    }
}