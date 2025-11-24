package uk.ac.tees.mad.fixit.presentation.feature.reportissue

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun ValidationErrorText(
    errors: List<String>,
    modifier: Modifier = Modifier
) {
    if (errors.isNotEmpty()) {
        Column(modifier = modifier) {
            errors.forEach { error ->
                Text(
                    text = "• $error",
                    color = Color.Red,
                    modifier = Modifier.padding(vertical = 2.dp),
                    textAlign = TextAlign.Start
                )
            }
        }
    }
}