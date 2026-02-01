package com.mgomanager.app.ui.screens.settings

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.mgomanager.app.ui.components.DuplicateResolveDialog
import com.mgomanager.app.ui.components.GlobalHeader
import com.mgomanager.app.ui.components.SectionCard
import com.mgomanager.app.ui.components.SaveIconButton
import com.mgomanager.app.ui.navigation.Screen
import com.mgomanager.app.ui.theme.StatusGreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var prefixInput by remember { mutableStateOf("") }

    // SSH settings state
    var sshKeyPathInput by remember { mutableStateOf("") }
    var sshServerInput by remember { mutableStateOf("") }
    var sshBackupPathInput by remember { mutableStateOf("") }
    var sshPasswordInput by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    LaunchedEffect(uiState) {
        prefixInput = uiState.accountPrefix
        sshKeyPathInput = uiState.sshPrivateKeyPath
        sshServerInput = uiState.sshServer
        sshBackupPathInput = uiState.sshBackupPath
        sshPasswordInput = uiState.sshPassword
    }

    // Refresh root status when screen is loaded
    LaunchedEffect(Unit) {
        viewModel.refreshRootStatus()
    }

    // SAF Directory Picker launcher for backup directory
    val backupDirPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            viewModel.onBackupDirectoryPicked(it)
        }
    }

    // SAF Export launcher (CreateDocument)
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        uri?.let {
            viewModel.onExportDocumentCreated(it)
        }
    }

    // SAF Import launcher (OpenDocument)
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            viewModel.onImportZipSelected(it)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Use GlobalHeader with subTitle (P6 modernization)
        GlobalHeader(subTitle = "Einstellungen")

        // Content area
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section 1: Allgemein (P6 spec)
            SectionCard(title = "Allgemein") {
                OutlinedTextField(
                    value = prefixInput,
                    onValueChange = {
                        prefixInput = it
                        viewModel.resetPrefixSaved()
                    },
                    label = { Text("Standard-Präfix") },
                    modifier = Modifier.fillMaxWidth(),
                    isError = uiState.prefixError != null,
                    supportingText = {
                        uiState.prefixError?.let {
                            Text(
                                text = it,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    },
                    trailingIcon = {
                        SaveIconButton(
                            saved = uiState.prefixSaved,
                            onSave = { viewModel.updatePrefix(prefixInput) }
                        )
                    }
                )
            }

            // Section 2: Backup & Speicher (P6 spec) - with SAF directory picker
            SectionCard(title = "Backup & Speicher") {
                Text(
                    text = "Aktueller Backup-Pfad:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )

                Text(
                    text = uiState.backupRootPath,
                    style = MaterialTheme.typography.bodyMedium
                )

                OutlinedButton(
                    onClick = { backupDirPickerLauncher.launch(null) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Backup-Verzeichnis ändern")
                }

                Text(
                    text = "Bestehende Backups werden nicht verschoben.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            // Section 3: Import / Export (P6 spec) - with SAF
            SectionCard(title = "Import / Export") {
                val timestamp = remember {
                    SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                }

                Button(
                    onClick = {
                        exportLauncher.launch("mgo_export_$timestamp.zip")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isExporting
                ) {
                    if (uiState.isExporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Exportiere...")
                    } else {
                        Text("Alle Backups exportieren")
                    }
                }

                OutlinedButton(
                    onClick = { viewModel.showImportWarning() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isImporting
                ) {
                    if (uiState.isImporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Importiere...")
                    } else {
                        Text("Backups importieren")
                    }
                }
            }

            // Section 4: SSH / Server (P6 spec)
            SectionCard(title = "SSH / Server") {
                OutlinedTextField(
                    value = sshKeyPathInput,
                    onValueChange = {
                        sshKeyPathInput = it
                        viewModel.resetSshKeyPathSaved()
                    },
                    label = { Text("Private Key Pfad") },
                    placeholder = { Text("/storage/emulated/0/.ssh/id_ed25519") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    trailingIcon = {
                        SaveIconButton(
                            saved = uiState.sshKeyPathSaved,
                            onSave = { viewModel.updateSshPrivateKeyPath(sshKeyPathInput) }
                        )
                    }
                )

                OutlinedTextField(
                    value = sshServerInput,
                    onValueChange = {
                        sshServerInput = it
                        viewModel.resetSshServerSaved()
                    },
                    label = { Text("Server (user@host:port)") },
                    placeholder = { Text("user@192.168.1.100:22") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    trailingIcon = {
                        SaveIconButton(
                            saved = uiState.sshServerSaved,
                            onSave = { viewModel.updateSshServer(sshServerInput) }
                        )
                    }
                )

                OutlinedTextField(
                    value = sshBackupPathInput,
                    onValueChange = {
                        sshBackupPathInput = it
                        viewModel.resetSshBackupPathSaved()
                    },
                    label = { Text("Server Backup-Pfad") },
                    placeholder = { Text("/home/user/monopolygo/backups/") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    trailingIcon = {
                        SaveIconButton(
                            saved = uiState.sshBackupPathSaved,
                            onSave = { viewModel.updateSshBackupPath(sshBackupPathInput) }
                        )
                    }
                )

                // Password field
                OutlinedTextField(
                    value = sshPasswordInput,
                    onValueChange = {
                        sshPasswordInput = it
                        viewModel.resetSshPasswordSaved()
                    },
                    label = { Text("Passwort (optional)") },
                    placeholder = { Text("SSH-Passwort") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        Row {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (passwordVisible) "Passwort verbergen" else "Passwort anzeigen"
                                )
                            }
                            SaveIconButton(
                                saved = uiState.sshPasswordSaved,
                                onSave = { viewModel.updateSshPassword(sshPasswordInput) }
                            )
                        }
                    }
                )

                // Authentication method selector
                Text(
                    text = "Authentifizierung",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = uiState.sshAuthMethod == "key_only",
                        onClick = { viewModel.updateSshAuthMethod("key_only") }
                    )
                    Text(
                        text = "Nur Key",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    RadioButton(
                        selected = uiState.sshAuthMethod == "password_only",
                        onClick = { viewModel.updateSshAuthMethod("password_only") }
                    )
                    Text(
                        text = "Nur Passwort",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    RadioButton(
                        selected = uiState.sshAuthMethod == "try_both",
                        onClick = { viewModel.updateSshAuthMethod("try_both") }
                    )
                    Text(
                        text = "Beides",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                // Auto-sync checkboxes
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = uiState.sshAutoCheckOnStart,
                        onCheckedChange = { viewModel.updateSshAutoCheckOnStart(it) }
                    )
                    Text(
                        text = "App-Start prüfen",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Checkbox(
                        checked = uiState.sshAutoUploadOnExport,
                        onCheckedChange = { viewModel.updateSshAutoUploadOnExport(it) }
                    )
                    Text(
                        text = "Export auto-upload",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                // Test button and last sync info
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { viewModel.testSshConnection() },
                        enabled = !uiState.isSshTesting && sshServerInput.isNotBlank()
                    ) {
                        if (uiState.isSshTesting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text("SSH Testen")
                        }
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Letzter Sync:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Text(
                            text = viewModel.formatLastSyncTime(),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // System card
            SectionCard(title = "System") {
                OutlinedButton(
                    onClick = { navController.navigate(Screen.Logs.route) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Logs anzeigen")
                }

                OutlinedButton(
                    onClick = { navController.navigate(Screen.IdCompare.route) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("ID-Vergleich")
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (uiState.isRootAvailable) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.errorContainer
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Root-Status")
                        Text(if (uiState.isRootAvailable) "Verfügbar" else "Nicht verfügbar")
                    }
                }
            }

            // About card (P6 spec) - with Logs and ID-Vergleich buttons
            SectionCard(title = "Über die App") {
                Text(
                    text = "bGO Account Manager",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "Version 2.0.0",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "Build: 1",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "bGO Account Manager – lokales Backup-Tool für Monopoly GO Accounts.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "App-Starts: ${uiState.appStartCount}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )

                // Buttons for Logs and ID-Vergleich (P6 requirement)
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { navController.navigate(Screen.Logs.route) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Logs öffnen")
                    }

                    OutlinedButton(
                        onClick = { navController.navigate(Screen.IdCompare.route) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("ID-Vergleich")
                    }
                }
            }

            // Back link
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Zurück zur Liste",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clickable { navController.popBackStack() }
                    .padding(8.dp)
            )
        }
    }

    // Export result dialog
    uiState.exportResult?.let { result ->
        AlertDialog(
            onDismissRequest = { viewModel.clearExportResult() },
            title = { Text("Export") },
            text = { Text(result) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearExportResult() }) {
                    Text("OK")
                }
            }
        )
    }

    // Import result dialog
    uiState.importResult?.let { result ->
        AlertDialog(
            onDismissRequest = { viewModel.clearImportResult() },
            title = { Text("Import") },
            text = { Text(result) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearImportResult() }) {
                    Text("OK")
                }
            }
        )
    }

    // SSH test result dialog
    uiState.sshTestResult?.let { result ->
        AlertDialog(
            onDismissRequest = { viewModel.clearSshTestResult() },
            title = { Text("SSH-Verbindungstest") },
            text = { Text(result) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearSshTestResult() }) {
                    Text("OK")
                }
            }
        )
    }

    // Import warning dialog (P6 spec requirement) - now triggers SAF picker
    if (uiState.showImportWarning) {
        AlertDialog(
            onDismissRequest = { viewModel.hideImportWarning() },
            title = { Text("Backups importieren") },
            text = {
                Text("Beim Import können bestehende Backups überschrieben werden.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.hideImportWarning()
                        importLauncher.launch(arrayOf("application/zip"))
                    }
                ) {
                    Text("Importieren")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideImportWarning() }) {
                    Text("Abbrechen")
                }
            }
        )
    }

    // DuplicateResolveDialog for interactive conflict resolution (DEVIATION FROM P6)
    if (uiState.showDuplicateResolveDialog && uiState.importDuplicates.isNotEmpty()) {
        DuplicateResolveDialog(
            duplicates = uiState.importDuplicates,
            onConfirm = { decisions ->
                viewModel.applyConflictDecisions(decisions)
            },
            onDismiss = {
                viewModel.cancelImportConflictResolution()
            }
        )
    }
}
