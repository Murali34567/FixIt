package uk.ac.tees.mad.fixit.presentation.feature.reportissue

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Composable
fun CharacterCounter(
    currentCount: Int,
    maxCount: Int,
    isError: Boolean = false
) {
    val color = when {
        isError -> Color.Red
        currentCount > maxCount * 0.8 -> Color(0xFFFFA000) // Amber
        else -> Color.Gray
    }

    Text(
        text = "$currentCount/$maxCount",
        color = color,
        fontSize = 12.sp,
        fontWeight = if (isError) FontWeight.Bold else FontWeight.Normal
    )
}