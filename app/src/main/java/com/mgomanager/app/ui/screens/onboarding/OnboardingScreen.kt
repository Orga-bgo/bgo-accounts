package com.mgomanager.app.ui.screens.onboarding

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.delay

/**
 * Main Onboarding Screen Container
 */
@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel = hiltViewModel(),
    onComplete: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // SAF folder picker launcher
    val safPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            // Take persistable permission for the selected folder
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(it, takeFlags)
        }
        viewModel.onSAFFolderSelected(uri)
    }

    // Launch SAF picker when requested
    LaunchedEffect(uiState.showSAFPicker) {
        if (uiState.showSAFPicker) {
            safPickerLauncher.launch(null)
        }
    }

    // Navigate only when onboarding is marked as completed (via explicit user action)
    LaunchedEffect(uiState.onboardingMarkedComplete) {
        if (uiState.onboardingMarkedComplete) {
            onComplete()
        }
    }

    Scaffold { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (uiState.currentStep) {
                OnboardingStep.WELCOME ->
                    WelcomeScreen(onNext = { viewModel.nextStep() })

                OnboardingStep.IMPORT_CHECK ->
                    ImportCheckScreen(
                        zipFound = uiState.importZipFound,
                        zipPath = uiState.importZipPath,
                        isLoading = uiState.isLoading,
                        error = uiState.error,
                        onImport = { viewModel.importFromZip() },
                        onSkip = { viewModel.skipImport() },
                        onBack = { viewModel.previousStep() }
                    )

                OnboardingStep.PREFIX_SETUP ->
                    PrefixSetupScreen(
                        prefix = uiState.prefix,
                        onPrefixChanged = { viewModel.onPrefixChanged(it) },
                        onSave = { viewModel.savePrefixAndContinue() },
                        onSkip = { viewModel.skipPrefix() },
                        onBack = { viewModel.previousStep() }
                    )

                OnboardingStep.SSH_SETUP ->
                    SshSetupScreen(
                        enabled = uiState.sshEnabled,
                        host = uiState.sshHost,
                        port = uiState.sshPort,
                        username = uiState.sshUsername,
                        password = uiState.sshPassword,
                        error = uiState.error,
                        onEnabledChanged = { viewModel.onSshEnabledChanged(it) },
                        onHostChanged = { viewModel.onSshHostChanged(it) },
                        onPortChanged = { viewModel.onSshPortChanged(it) },
                        onUsernameChanged = { viewModel.onSshUsernameChanged(it) },
                        onPasswordChanged = { viewModel.onSshPasswordChanged(it) },
                        onSave = { viewModel.saveSshAndContinue() },
                        onSkip = { viewModel.skipSsh() },
                        onBack = { viewModel.previousStep() }
                    )

                OnboardingStep.BACKUP_DIRECTORY ->
                    BackupDirectoryScreen(
                        backupDirectoryUri = uiState.backupDirectoryUri,
                        isLoading = uiState.isLoading,
                        error = uiState.error,
                        onRequestSAFPicker = { viewModel.requestSAFPicker() },
                        onSave = { viewModel.saveBackupDirectoryAndContinue() },
                        onBack = { viewModel.previousStep() }
                    )

                OnboardingStep.ROOT_PERMISSIONS ->
                    RootPermissionsScreen(
                        isLoading = uiState.isLoading,
                        rootGranted = uiState.rootAccessGranted,
                        dataDataChecked = uiState.dataDataPermissionsChecked,
                        monopolyGoInstalled = uiState.monopolyGoInstalled,
                        monopolyGoUid = uiState.monopolyGoUid,
                        error = uiState.error,
                        onRequestRoot = { viewModel.requestRootAccess() },
                        onBack = { viewModel.previousStep() }
                    )

                OnboardingStep.COMPLETE ->
                    CompleteScreen(
                        onFinish = { viewModel.completeOnboarding() }
                    )
            }

            // Progress Indicator
            LinearProgressIndicator(
                progress = getProgressForStep(uiState.currentStep),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

fun getProgressForStep(step: OnboardingStep): Float {
    return when (step) {
        OnboardingStep.WELCOME -> 0.0f
        OnboardingStep.IMPORT_CHECK -> 0.14f
        OnboardingStep.PREFIX_SETUP -> 0.28f
        OnboardingStep.SSH_SETUP -> 0.42f
        OnboardingStep.BACKUP_DIRECTORY -> 0.57f
        OnboardingStep.ROOT_PERMISSIONS -> 0.71f
        OnboardingStep.COMPLETE -> 1.0f
    }
}

// ============================================================
// Screen 1: Willkommen
// ============================================================

@Composable
fun WelcomeScreen(onNext: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.AccountCircle,
            contentDescription = null,
            modifier = Modifier.size(120.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Willkommen bei",
            fontSize = 20.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )

        Text(
            text = "babixGO",
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Der Account Manager für Monopoly GO",
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = onNext,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = "Los geht's",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// ============================================================
// Screen 2: Import-Check
// ============================================================

@Composable
fun ImportCheckScreen(
    zipFound: Boolean,
    zipPath: String?,
    isLoading: Boolean,
    error: String?,
    onImport: () -> Unit,
    onSkip: () -> Unit,
    onBack: () -> Unit
) {
    OnboardingScreenLayout(
        icon = Icons.Default.FileDownload,
        title = "Daten importieren",
        description = if (zipFound)
            "Eine Import-Datei wurde gefunden.\nMöchtest du deine Accounts importieren?"
        else
            "Keine Import-Datei gefunden.\nDu kannst später Accounts über das Menü importieren.",
        error = error,
        onBack = onBack
    ) {
        if (zipFound) {
            Text(
                text = "Gefunden: $zipPath",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Button(
                onClick = onImport,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading,
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(Icons.Default.Upload, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Importieren")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onSkip,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Überspringen")
            }
        } else {
            Button(
                onClick = onSkip,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Weiter")
            }
        }
    }
}

// ============================================================
// Screen 3: Präfix-Setup
// ============================================================

@Composable
fun PrefixSetupScreen(
    prefix: String,
    onPrefixChanged: (String) -> Unit,
    onSave: () -> Unit,
    onSkip: () -> Unit,
    onBack: () -> Unit
) {
    OnboardingScreenLayout(
        icon = Icons.Default.Label,
        title = "Standard-Präfix",
        description = "Lege einen Standard-Präfix für neue Accounts fest.\nBeispiel: MGO_, ALT_, MAIN_",
        onBack = onBack
    ) {
        OutlinedTextField(
            value = prefix,
            onValueChange = onPrefixChanged,
            label = { Text("Präfix") },
            placeholder = { Text("z.B. MGO_") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onSave,
            modifier = Modifier.fillMaxWidth(),
            enabled = prefix.isNotBlank(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Speichern & Weiter")
        }

        Spacer(modifier = Modifier.height(12.dp))

        TextButton(
            onClick = onSkip,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Später festlegen")
        }
    }
}

// ============================================================
// Screen 4: SSH-Setup
// ============================================================

@Composable
fun SshSetupScreen(
    enabled: Boolean,
    host: String,
    port: String,
    username: String,
    password: String,
    error: String?,
    onEnabledChanged: (Boolean) -> Unit,
    onHostChanged: (String) -> Unit,
    onPortChanged: (String) -> Unit,
    onUsernameChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onSave: () -> Unit,
    onSkip: () -> Unit,
    onBack: () -> Unit
) {
    var passwordVisible by remember { mutableStateOf(false) }

    OnboardingScreenLayout(
        icon = Icons.Default.Cloud,
        title = "SSH Remote-Backup",
        description = "Optional: Aktiviere automatische Backups auf einen SSH-Server.",
        error = error,
        onBack = onBack
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "SSH aktivieren",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Switch(
                checked = enabled,
                onCheckedChange = onEnabledChanged
            )
        }

        AnimatedVisibility(visible = enabled) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = host,
                    onValueChange = onHostChanged,
                    label = { Text("Host") },
                    placeholder = { Text("192.168.1.100 oder domain.com") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = port,
                    onValueChange = onPortChanged,
                    label = { Text("Port") },
                    placeholder = { Text("22") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = username,
                    onValueChange = onUsernameChanged,
                    label = { Text("Benutzername") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = onPasswordChanged,
                    label = { Text("Passwort") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (passwordVisible) "Passwort verbergen" else "Passwort anzeigen"
                            )
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (enabled) {
            Button(
                onClick = onSave,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Speichern & Weiter")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onSkip,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(if (enabled) "Überspringen" else "Weiter ohne SSH")
        }
    }
}

// ============================================================
// Screen 5: Backup-Ordner (SAF-only)
// ============================================================

@Composable
fun BackupDirectoryScreen(
    backupDirectoryUri: android.net.Uri?,
    isLoading: Boolean,
    error: String?,
    onRequestSAFPicker: () -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit
) {
    OnboardingScreenLayout(
        icon = Icons.Default.Folder,
        title = "Backup-Verzeichnis",
        description = "Wähle den Ordner, in dem deine Account-Backups gespeichert werden sollen.",
        error = error,
        onBack = onBack
    ) {
        // Display selected folder or placeholder
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (backupDirectoryUri != null)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (backupDirectoryUri != null) Icons.Default.CheckCircle else Icons.Default.FolderOpen,
                    contentDescription = null,
                    tint = if (backupDirectoryUri != null)
                        Color(0xFF4CAF50)
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (backupDirectoryUri != null) "Ordner ausgewählt" else "Kein Ordner ausgewählt",
                        fontWeight = FontWeight.Medium,
                        color = if (backupDirectoryUri != null)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (backupDirectoryUri != null) {
                        Text(
                            text = backupDirectoryUri.lastPathSegment ?: backupDirectoryUri.toString(),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                            maxLines = 1
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // SAF picker button
        OutlinedButton(
            onClick = onRequestSAFPicker,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading,
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.FolderOpen, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (backupDirectoryUri != null) "Anderen Ordner wählen" else "Ordner auswählen")
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Continue button - only enabled when a folder is selected
        Button(
            onClick = onSave,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading && backupDirectoryUri != null,
            shape = RoundedCornerShape(12.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text("Weiter")
            }
        }
    }
}

// ============================================================
// Screen 6: Root & Berechtigungen
// ============================================================

@Composable
fun RootPermissionsScreen(
    isLoading: Boolean,
    rootGranted: Boolean,
    dataDataChecked: Boolean,
    monopolyGoInstalled: Boolean,
    monopolyGoUid: Int?,
    error: String?,
    onRequestRoot: () -> Unit,
    onBack: () -> Unit
) {
    OnboardingScreenLayout(
        icon = Icons.Default.Security,
        title = "Root-Berechtigungen",
        description = "Die App benötigt Root-Zugriff, um Backups zu erstellen und wiederherzustellen.",
        error = error,
        onBack = onBack
    ) {
        // Status list
        if (rootGranted) {
            ChecklistItem(
                title = "Root-Zugriff",
                checked = true
            )
            ChecklistItem(
                title = "/data/data Zugriff",
                checked = dataDataChecked
            )
            if (monopolyGoInstalled) {
                ChecklistItem(
                    title = "Monopoly Go erkannt",
                    checked = true,
                    subtitle = "UID: ${monopolyGoUid ?: "Unbekannt"}"
                )
            } else {
                ChecklistItem(
                    title = "Monopoly Go",
                    checked = false,
                    subtitle = "Nicht installiert"
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onRequestRoot,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading && !rootGranted,
            shape = RoundedCornerShape(12.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Icon(
                    imageVector = if (rootGranted) Icons.Default.CheckCircle else Icons.Default.Security,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (rootGranted) "Weiter" else "Root-Zugriff anfordern")
            }
        }
    }
}

@Composable
fun ChecklistItem(
    title: String,
    checked: Boolean,
    subtitle: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (checked) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (checked) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            subtitle?.let {
                Text(
                    text = it,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}

// ============================================================
// Screen 7: Fertig
// ============================================================

@Composable
fun CompleteScreen(onFinish: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(120.dp),
            tint = Color(0xFF4CAF50)
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Alles fertig!",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Die App ist einsatzbereit.\nDu kannst jetzt mit der Verwaltung deiner Accounts beginnen.",
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = onFinish,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF4CAF50)
            )
        ) {
            Text(
                text = "App starten",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// ============================================================
// Shared Layout Component
// ============================================================

@Composable
fun OnboardingScreenLayout(
    icon: ImageVector,
    title: String,
    description: String,
    error: String? = null,
    onBack: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
            .padding(top = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Back button
        onBack?.let {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
            ) {
                IconButton(onClick = it) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Zurück"
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = title,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = description,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.fillMaxWidth()
        )

        // Error display
        AnimatedVisibility(
            visible = error != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            error?.let {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        content()

        Spacer(modifier = Modifier.height(24.dp))
    }
}
