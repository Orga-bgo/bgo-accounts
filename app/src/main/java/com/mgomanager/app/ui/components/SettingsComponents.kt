package com.mgomanager.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mgomanager.app.ui.theme.StatusGreen

/**
 * Reusable section card component for Settings screen
 * Provides consistent styling across all settings sections
 *
 * @param title Section title displayed at the top
 * @param content Composable content of the section
 */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            content()
        }
    }
}

/**
 * Save icon button that shows green checkmark when saved
 *
 * @param saved Whether the value has been saved
 * @param onSave Callback when save is clicked
 * @param enabled Whether the button is enabled
 */
@Composable
fun SaveIconButton(
    saved: Boolean,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    IconButton(
        onClick = onSave,
        modifier = modifier,
        enabled = enabled
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = "Speichern",
            tint = if (saved) StatusGreen else MaterialTheme.colorScheme.onSurface
        )
    }
}
