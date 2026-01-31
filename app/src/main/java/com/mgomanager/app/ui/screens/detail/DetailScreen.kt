package com.mgomanager.app.ui.screens.detail

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.mgomanager.app.data.model.SusLevel
import com.mgomanager.app.ui.theme.StatusRed

/**
 * Copyable field component - click to copy value to clipboard
 * Per P5 spec: "Jeder Eintrag (Key-Value-Zeile) ist klickbar. Klick kopiert den Value in die Zwischenablage."
 */
@Composable
private fun CopyableField(
    label: String,
    value: String,
    onCopy: (String, String) -> Unit,
    isMonospace: Boolean = false
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCopy(label, value) }
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = if (isMonospace) FontFamily.Monospace else FontFamily.Default
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    navController: NavController,
    accountId: Long,
    viewModel: DetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(accountId) {
        viewModel.loadAccount(accountId)
    }

    uiState.account?.let { account ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // Blue header section
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(16.dp)
            ) {
                Column {
                    // Account name (large, italic style)
                    Text(
                        text = account.fullName,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // User ID (clickable to copy)
                    Text(
                        text = "MoGo User ID: ${account.userId}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                        modifier = Modifier.clickable { viewModel.copyToClipboard("User ID", account.userId) }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Last played
                    Text(
                        text = "Zuletzt gespielt:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                    )
                    Text(
                        text = account.getFormattedLastPlayedAt(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }

            // Content area
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Action buttons row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Restore button (blue)
                    Button(
                        onClick = { viewModel.showRestoreDialog() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(
                            text = "WIEDERHERS\nTELLEN",
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            maxLines = 2
                        )
                    }

                    // Edit button (gray)
                    Button(
                        onClick = { viewModel.showEditDialog() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Gray
                        )
                    ) {
                        Text("EDIT")
                    }

                    // Delete button (red)
                    Button(
                        onClick = { viewModel.showDeleteDialog() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = StatusRed
                        )
                    ) {
                        Text("LÖSCHEN")
                    }
                }

                // Friendship link card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        CopyableField(
                            label = "FREUNDSCHAFTSLINK",
                            value = "Nicht verfügbar",
                            onCopy = viewModel::copyToClipboard
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        CopyableField(
                            label = "FREUNDSCHAFTSCODE",
                            value = "---",
                            onCopy = viewModel::copyToClipboard
                        )
                    }
                }

                // Status card (Suspension / Error)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Suspension",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            Text(
                                text = account.susLevel.displayName,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Error",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            Text(
                                text = if (account.hasError) "ja" else "nein",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Device IDs card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Geräte-IDs",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        CopyableField(
                            label = "SSAID",
                            value = if (account.ssaid == "nicht vorhanden") "Nicht verfügbar" else account.ssaid,
                            onCopy = viewModel::copyToClipboard,
                            isMonospace = true
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        CopyableField(
                            label = "GAID",
                            value = if (account.gaid == "nicht vorhanden") "Nicht verfügbar" else account.gaid,
                            onCopy = viewModel::copyToClipboard,
                            isMonospace = true
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        CopyableField(
                            label = "DEVICE ID",
                            value = if (account.deviceToken == "nicht vorhanden") "Nicht verfügbar" else account.deviceToken,
                            onCopy = viewModel::copyToClipboard,
                            isMonospace = true
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        CopyableField(
                            label = "APP SET ID",
                            value = if (account.appSetId == "nicht vorhanden") "Nicht verfügbar" else account.appSetId,
                            onCopy = viewModel::copyToClipboard,
                            isMonospace = true
                        )
                    }
                }

                // Facebook card (only if linked)
                if (account.hasFacebookLink) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "Facebook-Verbindung",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            CopyableField(
                                label = "USERNAME",
                                value = account.fbUsername ?: "---",
                                onCopy = viewModel::copyToClipboard
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            CopyableField(
                                label = "PASSWORT",
                                value = account.fbPassword ?: "---",
                                onCopy = viewModel::copyToClipboard
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            CopyableField(
                                label = "2FA CODE",
                                value = account.fb2FA ?: "---",
                                onCopy = viewModel::copyToClipboard
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            CopyableField(
                                label = "TEMP-MAIL",
                                value = account.fbTempMail ?: "---",
                                onCopy = viewModel::copyToClipboard
                            )
                        }
                    }
                }

                // Backup-Info card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Backup-Info",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        CopyableField(
                            label = "ERSTELLT AM",
                            value = account.getFormattedCreatedAt(),
                            onCopy = viewModel::copyToClipboard
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        CopyableField(
                            label = "BACKUP-PFAD",
                            value = account.backupPath,
                            onCopy = viewModel::copyToClipboard,
                            isMonospace = true
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        CopyableField(
                            label = "DATEI-EIGENTÜMER",
                            value = "${account.fileOwner}:${account.fileGroup}",
                            onCopy = viewModel::copyToClipboard,
                            isMonospace = true
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        CopyableField(
                            label = "BERECHTIGUNGEN",
                            value = account.filePermissions,
                            onCopy = viewModel::copyToClipboard,
                            isMonospace = true
                        )
                    }
                }

                // Back to list link
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "← Zurück zur Liste",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .clickable { navController.popBackStack() }
                        .padding(8.dp)
                )
            }
        }
    }

    // Dialogs
    if (uiState.showRestoreDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.hideRestoreDialog() },
            title = { Text("Wiederherstellung") },
            text = { Text("Account '${uiState.account?.fullName}' wiederherstellen?") },
            confirmButton = {
                TextButton(onClick = { viewModel.restoreAccount() }) {
                    Text("Ja")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideRestoreDialog() }) {
                    Text("Nein")
                }
            }
        )
    }

    if (uiState.showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.hideDeleteDialog() },
            title = { Text("Account löschen") },
            text = {
                Column {
                    Text("Account '${uiState.account?.fullName}' wirklich löschen?")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Dieser Account und alle zugehörigen Backups werden dauerhaft gelöscht.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteAccount { navController.popBackStack() }
                }) {
                    Text("Löschen", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideDeleteDialog() }) {
                    Text("Abbrechen")
                }
            }
        )
    }

    // Edit dialog - allows editing all fields per P5 spec (only affects DB, no side effects)
    if (uiState.showEditDialog) {
        uiState.account?.let { account ->
            var editName by remember { mutableStateOf(account.accountName) }
            var editUserId by remember { mutableStateOf(account.userId) }
            var editSsaid by remember { mutableStateOf(account.ssaid) }
            var editGaid by remember { mutableStateOf(account.gaid) }
            var editDeviceToken by remember { mutableStateOf(account.deviceToken) }
            var editAppSetId by remember { mutableStateOf(account.appSetId) }
            var editSusLevel by remember { mutableStateOf(account.susLevel) }
            var editHasError by remember { mutableStateOf(account.hasError) }
            var editHasFacebookLink by remember { mutableStateOf(account.hasFacebookLink) }
            var editFbUsername by remember { mutableStateOf(account.fbUsername ?: "") }
            var editFbPassword by remember { mutableStateOf(account.fbPassword ?: "") }
            var editFb2FA by remember { mutableStateOf(account.fb2FA ?: "") }
            var editFbTempMail by remember { mutableStateOf(account.fbTempMail ?: "") }
            var editBackupPath by remember { mutableStateOf(account.backupPath) }
            var susDropdownExpanded by remember { mutableStateOf(false) }

            AlertDialog(
                onDismissRequest = { viewModel.hideEditDialog() },
                title = { Text("Account bearbeiten") },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Basic info section
                        Text("Allgemein", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)

                        OutlinedTextField(
                            value = editName,
                            onValueChange = { editName = it },
                            label = { Text("Name") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Sus Level dropdown
                        ExposedDropdownMenuBox(
                            expanded = susDropdownExpanded,
                            onExpandedChange = { susDropdownExpanded = !susDropdownExpanded }
                        ) {
                            OutlinedTextField(
                                value = editSusLevel.displayName,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Sus Level") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = susDropdownExpanded) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                            )
                            ExposedDropdownMenu(
                                expanded = susDropdownExpanded,
                                onDismissRequest = { susDropdownExpanded = false }
                            ) {
                                SusLevel.entries.forEach { level ->
                                    DropdownMenuItem(
                                        text = { Text(level.displayName) },
                                        onClick = {
                                            editSusLevel = level
                                            susDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        // Error checkbox
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = editHasError,
                                onCheckedChange = { editHasError = it }
                            )
                            Text("Hat Error")
                        }

                        Divider()

                        // Device IDs section
                        Text("Geräte-IDs", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)

                        OutlinedTextField(
                            value = editUserId,
                            onValueChange = { editUserId = it },
                            label = { Text("User ID") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = editSsaid,
                            onValueChange = { editSsaid = it },
                            label = { Text("SSAID") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = editGaid,
                            onValueChange = { editGaid = it },
                            label = { Text("GAID") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = editDeviceToken,
                            onValueChange = { editDeviceToken = it },
                            label = { Text("Device Token") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = editAppSetId,
                            onValueChange = { editAppSetId = it },
                            label = { Text("App Set ID") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Divider()

                        // Facebook section
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = editHasFacebookLink,
                                onCheckedChange = { editHasFacebookLink = it }
                            )
                            Text("Facebook-Verbindung")
                        }

                        if (editHasFacebookLink) {
                            OutlinedTextField(
                                value = editFbUsername,
                                onValueChange = { editFbUsername = it },
                                label = { Text("Username") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = editFbPassword,
                                onValueChange = { editFbPassword = it },
                                label = { Text("Passwort") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = editFb2FA,
                                onValueChange = { editFb2FA = it },
                                label = { Text("2FA") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = editFbTempMail,
                                onValueChange = { editFbTempMail = it },
                                label = { Text("Temp-Mail") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Divider()

                        // Backup section
                        Text("Backup", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)

                        OutlinedTextField(
                            value = editBackupPath,
                            onValueChange = { editBackupPath = it },
                            label = { Text("Backup-Pfad") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.updateAccountFull(
                            name = editName,
                            userId = editUserId,
                            ssaid = editSsaid,
                            gaid = editGaid,
                            deviceToken = editDeviceToken,
                            appSetId = editAppSetId,
                            susLevel = editSusLevel,
                            hasError = editHasError,
                            hasFacebookLink = editHasFacebookLink,
                            fbUsername = if (editHasFacebookLink) editFbUsername.ifBlank { null } else null,
                            fbPassword = if (editHasFacebookLink) editFbPassword.ifBlank { null } else null,
                            fb2FA = if (editHasFacebookLink) editFb2FA.ifBlank { null } else null,
                            fbTempMail = if (editHasFacebookLink) editFbTempMail.ifBlank { null } else null,
                            backupPath = editBackupPath
                        )
                    }) {
                        Text("Speichern")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.hideEditDialog() }) {
                        Text("Abbrechen")
                    }
                }
            )
        }
    }

    // Restore success dialog with app launch option
    if (uiState.showRestoreSuccessDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.hideRestoreSuccessDialog() },
            title = { Text("Wiederherstellung erfolgreich!") },
            text = { Text("Monopoly Go jetzt starten?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.hideRestoreSuccessDialog()
                    val launchIntent = context.packageManager.getLaunchIntentForPackage("com.scopely.monopolygo")
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(launchIntent)
                    }
                }) {
                    Text("Ja")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideRestoreSuccessDialog() }) {
                    Text("Nein")
                }
            }
        )
    }

    // Show restore failure dialog
    uiState.restoreResult?.let { result ->
        if (result is com.mgomanager.app.data.model.RestoreResult.Failure) {
            AlertDialog(
                onDismissRequest = { viewModel.hideRestoreDialog() },
                title = { Text("Wiederherstellung fehlgeschlagen") },
                text = { Text(result.error) },
                confirmButton = {
                    TextButton(onClick = { viewModel.hideRestoreDialog() }) {
                        Text("OK")
                    }
                }
            )
        }
    }
}
