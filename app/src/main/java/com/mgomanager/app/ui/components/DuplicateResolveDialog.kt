package com.mgomanager.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Choice for resolving a duplicate conflict
 */
enum class ResolveChoice {
    KEEP_LOCAL,
    KEEP_IMPORT
}

/**
 * Data class representing a duplicate pair during import
 */
data class DuplicatePair(
    val userId: String,
    val localName: String,
    val localCreatedAt: Long,
    val importName: String,
    val importCreatedAt: Long
)

/**
 * Dialog for resolving UserID conflicts during import
 * Shows each duplicate pair and allows user to choose which to keep
 *
 * DEVIATION FROM P6 SPEC: This dialog is ALWAYS shown for UserID collisions
 * instead of automatically skipping. This provides better UX by giving users
 * control over their data and prevents accidental data loss.
 *
 * @param duplicates List of duplicate pairs to resolve
 * @param onConfirm Callback with decisions map when user confirms
 * @param onDismiss Callback when user cancels (import will be aborted)
 */
@Composable
fun DuplicateResolveDialog(
    duplicates: List<DuplicatePair>,
    onConfirm: (Map<String, ResolveChoice>) -> Unit,
    onDismiss: () -> Unit
) {
    // Track individual choices for each duplicate
    val decisions = remember {
        mutableStateMapOf<String, ResolveChoice>().apply {
            // Default: KEEP_LOCAL for all
            duplicates.forEach { pair ->
                this[pair.userId] = ResolveChoice.KEEP_LOCAL
            }
        }
    }

    // Bulk apply state
    var bulkApplyEnabled by remember { mutableStateOf(false) }
    var bulkChoice by remember { mutableStateOf(ResolveChoice.KEEP_LOCAL) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "User ID bereits vorhanden",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Es wurden ${duplicates.size} Account(s) mit bereits vorhandenen User IDs gefunden. " +
                           "Wähle für jede Kollision, welcher Account behalten werden soll.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Bulk apply option
                if (duplicates.size > 1) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = bulkApplyEnabled,
                                    onCheckedChange = { enabled ->
                                        bulkApplyEnabled = enabled
                                        if (enabled) {
                                            // Apply bulk choice to all
                                            duplicates.forEach { pair ->
                                                decisions[pair.userId] = bulkChoice
                                            }
                                        }
                                    }
                                )
                                Text(
                                    text = "Für alle Duplikate übernehmen:",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }

                            if (bulkApplyEnabled) {
                                Row(
                                    modifier = Modifier.padding(start = 32.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .selectable(
                                                selected = bulkChoice == ResolveChoice.KEEP_LOCAL,
                                                onClick = {
                                                    bulkChoice = ResolveChoice.KEEP_LOCAL
                                                    duplicates.forEach { pair ->
                                                        decisions[pair.userId] = ResolveChoice.KEEP_LOCAL
                                                    }
                                                },
                                                role = Role.RadioButton
                                            ),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = bulkChoice == ResolveChoice.KEEP_LOCAL,
                                            onClick = null
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Lokal",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }

                                    Row(
                                        modifier = Modifier
                                            .selectable(
                                                selected = bulkChoice == ResolveChoice.KEEP_IMPORT,
                                                onClick = {
                                                    bulkChoice = ResolveChoice.KEEP_IMPORT
                                                    duplicates.forEach { pair ->
                                                        decisions[pair.userId] = ResolveChoice.KEEP_IMPORT
                                                    }
                                                },
                                                role = Role.RadioButton
                                            ),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = bulkChoice == ResolveChoice.KEEP_IMPORT,
                                            onClick = null
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Import",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider()

                // List of duplicates
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(duplicates) { index, pair ->
                        DuplicateItem(
                            index = index + 1,
                            pair = pair,
                            selectedChoice = decisions[pair.userId] ?: ResolveChoice.KEEP_LOCAL,
                            onChoiceSelected = { choice ->
                                decisions[pair.userId] = choice
                                // Disable bulk apply when manually changing
                                if (bulkApplyEnabled) {
                                    bulkApplyEnabled = false
                                }
                            },
                            enabled = !bulkApplyEnabled
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(decisions.toMap()) }
            ) {
                Text("Bestätigen")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen")
            }
        }
    )
}

@Composable
private fun DuplicateItem(
    index: Int,
    pair: DuplicatePair,
    selectedChoice: ResolveChoice,
    onChoiceSelected: (ResolveChoice) -> Unit,
    enabled: Boolean
) {
    val dateFormat = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Kollision $index",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = "User ID: ${pair.userId.takeLast(8)}...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Local account option
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .selectable(
                            selected = selectedChoice == ResolveChoice.KEEP_LOCAL,
                            onClick = { if (enabled) onChoiceSelected(ResolveChoice.KEEP_LOCAL) },
                            role = Role.RadioButton,
                            enabled = enabled
                        )
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = selectedChoice == ResolveChoice.KEEP_LOCAL,
                            onClick = null,
                            enabled = enabled
                        )
                        Text(
                            text = "Lokal behalten",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Text(
                        text = pair.localName,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 36.dp)
                    )
                    Text(
                        text = "Erstellt: ${dateFormat.format(Date(pair.localCreatedAt))}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 36.dp)
                    )
                }

                // Import account option
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .selectable(
                            selected = selectedChoice == ResolveChoice.KEEP_IMPORT,
                            onClick = { if (enabled) onChoiceSelected(ResolveChoice.KEEP_IMPORT) },
                            role = Role.RadioButton,
                            enabled = enabled
                        )
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = selectedChoice == ResolveChoice.KEEP_IMPORT,
                            onClick = null,
                            enabled = enabled
                        )
                        Text(
                            text = "Import behalten",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Text(
                        text = pair.importName,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 36.dp)
                    )
                    Text(
                        text = "Erstellt: ${dateFormat.format(Date(pair.importCreatedAt))}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 36.dp)
                    )
                }
            }
        }
    }
}
