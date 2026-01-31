package com.mgomanager.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.mgomanager.app.ui.navigation.Screen
import com.mgomanager.app.ui.theme.StatusGreen

@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    var prefixInput by remember { mutableStateOf("") }
    var pathInput by remember { mutableStateOf("") }

    // SSH settings state
    var sshKeyPathInput by remember { mutableStateOf("") }
    var sshServerInput by remember { mutableStateOf("") }
    var sshBackupPathInput by remember { mutableStateOf("") }
    var sshPasswordInput by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    LaunchedEffect(uiState) {
        prefixInput = uiState.accountPrefix
        pathInput = uiState.backupRootPath
        sshKeyPathInput = uiState.sshPrivateKeyPath
        sshServerInput = uiState.sshServer
        sshBackupPathInput = uiState.sshBackupPath
        sshPasswordInput = uiState.sshPassword
    }

    // Refresh root status when screen is loaded
    LaunchedEffect(Unit) {
        viewModel.refreshRootStatus()
    }

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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Einstellungen",
                    style = MaterialTheme.typography.headlineMedium,
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
            // Section 1: Allgemein (P6 spec)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Allgemein",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

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
                            IconButton(onClick = { viewModel.updatePrefix(prefixInput) }) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Speichern",
                                    tint = if (uiState.prefixSaved) StatusGreen else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    )
                }
            }

            // Section 2: Backup & Speicher (P6 spec)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Backup & Speicher",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "Aktueller Backup-Pfad:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )

                    Text(
                        text = uiState.backupRootPath,
                        style = MaterialTheme.typography.bodyMedium
                    )

                    OutlinedTextField(
                        value = pathInput,
                        onValueChange = {
                            pathInput = it
                            viewModel.resetPathSaved()
                        },
                        label = { Text("Neuer Backup-Pfad") },
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            IconButton(onClick = { viewModel.updateBackupPath(pathInput) }) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Speichern",
                                    tint = if (uiState.pathSaved) StatusGreen else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        },
                        supportingText = {
                            Text(
                                text = "Bestehende Backups werden nicht verschoben.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    )
                }
            }

            // Section 3: Import / Export (P6 spec)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Import / Export",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Button(
                        onClick = { viewModel.exportData() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !uiState.isExporting
                    ) {
                        if (uiState.isExporting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
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
            }

            // Section 4: SSH / Server (P6 spec)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "SSH / Server",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

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
                            IconButton(onClick = { viewModel.updateSshPrivateKeyPath(sshKeyPathInput) }) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Speichern",
                                    tint = if (uiState.sshKeyPathSaved) StatusGreen else MaterialTheme.colorScheme.onSurface
                                )
                            }
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
                            IconButton(onClick = { viewModel.updateSshServer(sshServerInput) }) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Speichern",
                                    tint = if (uiState.sshServerSaved) StatusGreen else MaterialTheme.colorScheme.onSurface
                                )
                            }
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
                            IconButton(onClick = { viewModel.updateSshBackupPath(sshBackupPathInput) }) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Speichern",
                                    tint = if (uiState.sshBackupPathSaved) StatusGreen else MaterialTheme.colorScheme.onSurface
                                )
                            }
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
                                IconButton(onClick = { viewModel.updateSshPassword(sshPasswordInput) }) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = "Speichern",
                                        tint = if (uiState.sshPasswordSaved) StatusGreen else MaterialTheme.colorScheme.onSurface
                                    )
                                }
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
                                    strokeWidth = 2.dp
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
            }

            // System card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "System",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

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
                            Text(if (uiState.isRootAvailable) "✓ Verfügbar" else "✗ Nicht verfügbar")
                        }
                    }
                }
            }

            // About card (P6 spec)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Über die App",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "bGO Account Manager",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )

                    Text(
                        text = "Version 2.0.0",
                        style = MaterialTheme.typography.bodyMedium
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
                }
            }

            // Back link
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

    // Import warning dialog (P6 spec requirement)
    if (uiState.showImportWarning) {
        AlertDialog(
            onDismissRequest = { viewModel.hideImportWarning() },
            title = { Text("Backups importieren") },
            text = {
                Text("Beim Import können bestehende Backups überschrieben werden.")
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmImport() }) {
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
}
