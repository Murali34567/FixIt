package uk.ac.tees.mad.fixit.presentation.feature.viewreports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import uk.ac.tees.mad.fixit.data.model.ReportStatus

@Composable
fun FilterSection(
    selectedFilter: ReportStatus?,
    onFilterSelected: (ReportStatus?) -> Unit,
    modifier: Modifier = Modifier
) {
    val filters = listOf(
        null to "All",
        ReportStatus.PENDING to "Pending",
        ReportStatus.SUBMITTED to "Submitted",
        ReportStatus.IN_PROGRESS to "In Progress",
        ReportStatus.RESOLVED to "Resolved"
    )

    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(filters) { (status, label) ->
            FilterChip(
                selected = selectedFilter == status,
                onClick = { onFilterSelected(status) },
                label = { Text(label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}