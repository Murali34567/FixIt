package uk.ac.tees.mad.fixit.data.local

import uk.ac.tees.mad.fixit.data.model.IssueLocation
import uk.ac.tees.mad.fixit.data.model.IssueReport

// Convert from domain model to entity
fun IssueReport.toEntity(isSynced: Boolean = true): IssueReportEntity {
    return IssueReportEntity(
        id = this.id,
        userId = this.userId,
        imageUrl = this.imageUrl,
        description = this.description,
        issueType = this.issueType,
        latitude = this.location.latitude,
        longitude = this.location.longitude,
        address = this.location.address,
        status = this.status,
        timestamp = this.timestamp,
        lastUpdated = System.currentTimeMillis(),
        isSynced = isSynced
    )
}

// Convert from entity to domain model
fun IssueReportEntity.toDomainModel(): IssueReport {
    return IssueReport(
        id = this.id,
        userId = this.userId,
        imageUrl = this.imageUrl,
        description = this.description,
        issueType = this.issueType,
        location = IssueLocation(
            latitude = this.latitude,
            longitude = this.longitude,
            address = this.address
        ),
        status = this.status,
        timestamp = this.timestamp
    )
}

// Convert list of entities to domain models
fun List<IssueReportEntity>.toDomainModels(): List<IssueReport> {
    return this.map { it.toDomainModel() }
}