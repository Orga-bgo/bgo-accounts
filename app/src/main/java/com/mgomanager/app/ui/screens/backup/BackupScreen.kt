package com.mgomanager.app.ui.screens.backup

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.mgomanager.app.ui.components.GlobalHeader
import com.mgomanager.app.ui.navigation.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    navController: NavController,
    backupViewModel: BackupViewModel = hiltViewModel(),
    accountCreateViewModel: AccountCreateViewModel = hiltViewModel()
) {
    val backupUiState by backupViewModel.uiState.collectAsState()
    val createUiState by accountCreateViewModel.uiState.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            GlobalHeader(subTitle = "Backup")
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Tab Row
            TabRow(
                selectedTabIndex = selectedTab
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = {
                        selectedTab = 0
                        backupViewModel.selectTab(0)
                    },
                    text = { Text("Account sichern") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = {
                        selectedTab = 1
                        backupViewModel.selectTab(1)
                    },
                    text = { Text("Account erstellen") }
                )
            }

            // Tab Content
            when (selectedTab) {
                0 -> BackupTab1Content(
                    uiState = backupUiState,
                    viewModel = backupViewModel,
                    navController = navController
                )
                1 -> BackupTab2Content(
                    uiState = createUiState,
                    viewModel = accountCreateViewModel,
                    navController = navController
                )
            }
        }
    }
}

@Composable
fun BackupTab1Content(
    uiState: BackupUiState,
    viewModel: BackupViewModel,
    navController: NavController
) {
    when (uiState.currentStep) {
        BackupWizardStep.NAME_INPUT -> NameInputStep(
            accountName = uiState.accountName,
            prefix = uiState.accountPrefix,
            onAccountNameChange = viewModel::onAccountNameChange,
            onNext = viewModel::proceedFromNameInput,
            error = uiState.errorMessage
        )
        BackupWizardStep.SOCIAL_MEDIA -> SocialMediaStep(
            hasFacebookLink = uiState.hasFacebookLink,
            onHasFacebookLinkChange = viewModel::onHasFacebookLinkChange,
            onNext = viewModel::proceedFromSocialMedia,
            onBack = viewModel::goBack
        )
        BackupWizardStep.FACEBOOK_DETAILS -> FacebookDetailsStep(
            fbUsername = uiState.fbUsername,
            fbPassword = uiState.fbPassword,
            fb2FA = uiState.fb2FA,
            fbTempMail = uiState.fbTempMail,
            onFbUsernameChange = viewModel::onFbUsernameChange,
            onFbPasswordChange = viewModel::onFbPasswordChange,
            onFb2FAChange = viewModel::onFb2FAChange,
            onFbTempMailChange = viewModel::onFbTempMailChange,
            onNext = viewModel::proceedFromFacebookDetails,
            onBack = viewModel::goBack
        )
        BackupWizardStep.BACKUP_PROGRESS -> BackupProgressStep(
            progressMessage = uiState.progressMessage
        )
        BackupWizardStep.SUCCESS -> BackupSuccessStep(
            account = uiState.createdAccount,
            onViewAccount = {
                viewModel.getCreatedAccountId()?.let { accountId ->
                    navController.navigate(Screen.Detail.createRoute(accountId))
                }
            },
            onLaunchMonopolyGo = viewModel::launchMonopolyGoWithCreatedAccount,
            onGoToOverview = {
                viewModel.resetForNewBackup()
                navController.navigate(Screen.Home.route) {
                    popUpTo(Screen.Home.route) { inclusive = true }
                }
            },
            isLoading = uiState.isLoading
        )
        BackupWizardStep.ERROR -> BackupErrorStep(
            errorMessage = uiState.errorMessage ?: "Unbekannter Fehler",
            duplicateInfo = uiState.duplicateUserIdInfo,
            onRetry = viewModel::retryBackup,
            onGoBack = viewModel::resetForNewBackup
        )
    }
}

@Composable
fun NameInputStep(
    accountName: String,
    prefix: String,
    onAccountNameChange: (String) -> Unit,
    onNext: () -> Unit,
    error: String?
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        Icon(
            Icons.Default.Backup,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Account sichern",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Gib einen Namen für den Account ein",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Prefix display
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Präfix:",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = prefix,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Account name input
        OutlinedTextField(
            value = accountName,
            onValueChange = onAccountNameChange,
            label = { Text("Accountname") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = {
                Icon(Icons.Default.Person, contentDescription = null)
            },
            isError = error != null,
            supportingText = error?.let { { Text(it) } }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Preview of full name
        if (accountName.isNotBlank()) {
            Text(
                text = "Voller Name: $prefix$accountName",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth(),
            enabled = accountName.isNotBlank()
        ) {
            Text("Weiter")
            Spacer(modifier = Modifier.width(8.dp))
            Icon(Icons.Default.ArrowForward, contentDescription = null)
        }
    }
}

@Composable
fun SocialMediaStep(
    hasFacebookLink: Boolean,
    onHasFacebookLinkChange: (Boolean) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        Icon(
            Icons.Default.Share,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Social Media Verbindung",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Ist der Account mit Facebook verbunden?",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Yes/No selection
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedCard(
                onClick = { onHasFacebookLinkChange(true) },
                modifier = Modifier.weight(1f),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = if (hasFacebookLink)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = if (hasFacebookLink)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Ja",
                        fontWeight = if (hasFacebookLink) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }

            OutlinedCard(
                onClick = { onHasFacebookLinkChange(false) },
                modifier = Modifier.weight(1f),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = if (!hasFacebookLink)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = null,
                        tint = if (!hasFacebookLink)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Nein",
                        fontWeight = if (!hasFacebookLink) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Zurück")
            }

            Button(
                onClick = onNext,
                modifier = Modifier.weight(1f)
            ) {
                Text("Weiter")
                Spacer(modifier = Modifier.width(8.dp))
                Icon(Icons.Default.ArrowForward, contentDescription = null)
            }
        }
    }
}

@Composable
fun FacebookDetailsStep(
    fbUsername: String,
    fbPassword: String,
    fb2FA: String,
    fbTempMail: String,
    onFbUsernameChange: (String) -> Unit,
    onFbPasswordChange: (String) -> Unit,
    onFb2FAChange: (String) -> Unit,
    onFbTempMailChange: (String) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Facebook-Daten",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Optional: Speichere deine Facebook-Zugangsdaten",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = fbUsername,
            onValueChange = onFbUsernameChange,
            label = { Text("Nutzername / E-Mail") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) }
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = fbPassword,
            onValueChange = onFbPasswordChange,
            label = { Text("Passwort") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) }
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = fb2FA,
            onValueChange = onFb2FAChange,
            label = { Text("2FA-Code (optional)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Security, contentDescription = null) }
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = fbTempMail,
            onValueChange = onFbTempMailChange,
            label = { Text("Temp-Mail (optional)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) }
        )

        Spacer(modifier = Modifier.weight(1f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Zurück")
            }

            Button(
                onClick = onNext,
                modifier = Modifier.weight(1f)
            ) {
                Text("Backup starten")
                Spacer(modifier = Modifier.width(8.dp))
                Icon(Icons.Default.Backup, contentDescription = null)
            }
        }
    }
}

@Composable
fun BackupProgressStep(
    progressMessage: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(64.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = progressMessage,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun BackupSuccessStep(
    account: com.mgomanager.app.data.model.Account?,
    onViewAccount: () -> Unit,
    onLaunchMonopolyGo: () -> Unit,
    onGoToOverview: () -> Unit,
    isLoading: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = Color(0xFF4CAF50)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Backup erfolgreich!",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        account?.let {
            Text(
                text = it.fullName,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(48.dp))

        // Action buttons
        Button(
            onClick = onViewAccount,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        ) {
            Icon(Icons.Default.Visibility, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Account anzeigen")
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onLaunchMonopolyGo,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF4CAF50)
            )
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = Color.White
                )
            } else {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Monopoly Go starten")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onGoToOverview,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        ) {
            Icon(Icons.Default.Home, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Zur Übersicht")
        }
    }
}

@Composable
fun BackupErrorStep(
    errorMessage: String,
    duplicateInfo: DuplicateInfo?,
    onRetry: () -> Unit,
    onGoBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Error,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.error
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Backup fehlgeschlagen",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error
        )

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Text(
                text = errorMessage,
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                textAlign = TextAlign.Center
            )
        }

        duplicateInfo?.let { info ->
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "User ID: ${info.userId}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Erneut versuchen")
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onGoBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Abbrechen")
        }
    }
}

// ============================================================
// Tab 2: Account erstellen
// ============================================================

@Composable
fun BackupTab2Content(
    uiState: AccountCreateUiState,
    viewModel: AccountCreateViewModel,
    navController: NavController
) {
    when (uiState.currentStep) {
        AccountCreateStep.NAME_INPUT -> CreateNameInputStep(
            accountName = uiState.accountName,
            prefix = uiState.accountPrefix,
            onAccountNameChange = viewModel::onAccountNameChange,
            onNext = viewModel::proceedFromNameInput,
            error = uiState.errorMessage
        )
        AccountCreateStep.WARNING -> WarningStep(
            onConfirm = viewModel::confirmWarning,
            onCancel = viewModel::cancelWarning
        )
        AccountCreateStep.SSAID_SELECTION -> SsaidSelectionStep(
            ssaidOption = uiState.ssaidOption,
            currentSsaid = uiState.currentSsaid,
            isLoading = uiState.isLoading,
            onSsaidOptionChange = viewModel::onSsaidOptionChange,
            onNext = viewModel::proceedFromSsaidSelection,
            onBack = viewModel::goBackFromSsaidSelection
        )
        AccountCreateStep.PROGRESS_WIPE,
        AccountCreateStep.PROGRESS_WAIT,
        AccountCreateStep.PROGRESS_PATCH,
        AccountCreateStep.PROGRESS_START,
        AccountCreateStep.PROGRESS_STOP,
        AccountCreateStep.PROGRESS_BACKUP -> CreateProgressStep(
            progressMessage = uiState.progressMessage,
            progressPercent = uiState.progressPercent
        )
        AccountCreateStep.SUCCESS -> CreateSuccessStep(
            account = uiState.createdAccount,
            onViewAccount = {
                viewModel.getCreatedAccountId()?.let { accountId ->
                    navController.navigate(Screen.Detail.createRoute(accountId))
                }
            },
            onLaunchMonopolyGo = viewModel::launchMonopolyGoWithCreatedAccount,
            onGoToOverview = {
                viewModel.resetForNewAccount()
                navController.navigate(Screen.Home.route) {
                    popUpTo(Screen.Home.route) { inclusive = true }
                }
            },
            isLoading = uiState.isLoading
        )
        AccountCreateStep.ERROR -> CreateErrorStep(
            errorMessage = uiState.errorMessage ?: "Unbekannter Fehler",
            duplicateInfo = uiState.duplicateUserIdInfo,
            onRetry = viewModel::retryAccountCreation,
            onGoBack = viewModel::resetForNewAccount
        )
    }
}

@Composable
fun CreateNameInputStep(
    accountName: String,
    prefix: String,
    onAccountNameChange: (String) -> Unit,
    onNext: () -> Unit,
    error: String?
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        Icon(
            Icons.Default.AddCircle,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Neuen Account erstellen",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Erstelle einen neuen Monopoly GO Account mit frischer SSAID",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Prefix display
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Präfix:",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = prefix,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Account name input
        OutlinedTextField(
            value = accountName,
            onValueChange = onAccountNameChange,
            label = { Text("Accountname") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = {
                Icon(Icons.Default.Person, contentDescription = null)
            },
            isError = error != null,
            supportingText = error?.let { { Text(it) } }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Preview of full name
        if (accountName.isNotBlank()) {
            Text(
                text = "Voller Name: $prefix$accountName",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth(),
            enabled = accountName.isNotBlank()
        ) {
            Text("Weiter")
            Spacer(modifier = Modifier.width(8.dp))
            Icon(Icons.Default.ArrowForward, contentDescription = null)
        }
    }
}

@Composable
fun WarningStep(
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Warning,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = Color(0xFFFF9800) // Orange
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Achtung!",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFFF9800)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFFFFF3E0)
            )
        ) {
            Text(
                text = "Monopoly GO wird zurückgesetzt. Alle lokalen Daten werden gelöscht.",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = Color(0xFF795548)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onConfirm,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFFF9800)
            )
        ) {
            Text("Ich verstehe")
            Spacer(modifier = Modifier.width(8.dp))
            Icon(Icons.Default.ArrowForward, contentDescription = null)
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Abbrechen")
        }
    }
}

@Composable
fun SsaidSelectionStep(
    ssaidOption: SsaidOption,
    currentSsaid: String?,
    isLoading: Boolean,
    onSsaidOptionChange: (SsaidOption) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        Icon(
            Icons.Default.Fingerprint,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Android ID (SSAID)",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Möchtest du eine neue Android ID setzen?",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        if (isLoading) {
            CircularProgressIndicator()
        } else {
            // Option: New SSAID
            OutlinedCard(
                onClick = { onSsaidOptionChange(SsaidOption.NEW) },
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = if (ssaidOption == SsaidOption.NEW)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.surface
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = ssaidOption == SsaidOption.NEW,
                        onClick = { onSsaidOptionChange(SsaidOption.NEW) }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            "Ja, neue SSAID generieren",
                            fontWeight = if (ssaidOption == SsaidOption.NEW) FontWeight.Bold else FontWeight.Normal
                        )
                        Text(
                            "Empfohlen für neuen Account",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Option: Current SSAID
            OutlinedCard(
                onClick = { onSsaidOptionChange(SsaidOption.CURRENT) },
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = if (ssaidOption == SsaidOption.CURRENT)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.surface
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = ssaidOption == SsaidOption.CURRENT,
                        onClick = { onSsaidOptionChange(SsaidOption.CURRENT) }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            "Nein, aktuelle SSAID behalten",
                            fontWeight = if (ssaidOption == SsaidOption.CURRENT) FontWeight.Bold else FontWeight.Normal
                        )
                        currentSsaid?.let {
                            Text(
                                "Aktuell: $it",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        } ?: Text(
                            "Konnte nicht gelesen werden",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f),
                enabled = !isLoading
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Zurück")
            }

            Button(
                onClick = onNext,
                modifier = Modifier.weight(1f),
                enabled = !isLoading
            ) {
                Text("Starten")
                Spacer(modifier = Modifier.width(8.dp))
                Icon(Icons.Default.PlayArrow, contentDescription = null)
            }
        }
    }
}

@Composable
fun CreateProgressStep(
    progressMessage: String,
    progressPercent: Float
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(64.dp),
            progress = progressPercent
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = progressMessage,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        LinearProgressIndicator(
            progress = progressPercent,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "${(progressPercent * 100).toInt()}%",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

@Composable
fun CreateSuccessStep(
    account: com.mgomanager.app.data.model.Account?,
    onViewAccount: () -> Unit,
    onLaunchMonopolyGo: () -> Unit,
    onGoToOverview: () -> Unit,
    isLoading: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = Color(0xFF4CAF50)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Account erstellt!",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        account?.let {
            Text(
                text = it.fullName,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(48.dp))

        // Action buttons
        Button(
            onClick = onViewAccount,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        ) {
            Icon(Icons.Default.Visibility, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Account anzeigen")
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onLaunchMonopolyGo,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF4CAF50)
            )
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = Color.White
                )
            } else {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Monopoly Go starten")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onGoToOverview,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        ) {
            Icon(Icons.Default.Home, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Zur Übersicht")
        }
    }
}

@Composable
fun CreateErrorStep(
    errorMessage: String,
    duplicateInfo: DuplicateInfo?,
    onRetry: () -> Unit,
    onGoBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Error,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.error
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Erstellung fehlgeschlagen",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error
        )

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Text(
                text = errorMessage,
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                textAlign = TextAlign.Center
            )
        }

        duplicateInfo?.let { info ->
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "User ID: ${info.userId}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Erneut versuchen")
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onGoBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Abbrechen")
        }
    }
}
