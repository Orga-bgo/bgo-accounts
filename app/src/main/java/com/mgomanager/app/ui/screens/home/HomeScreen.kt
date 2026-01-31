package com.mgomanager.app.ui.screens.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.mgomanager.app.data.model.Account
import com.mgomanager.app.data.model.BackupResult
import com.mgomanager.app.ui.components.BackupDialog
import com.mgomanager.app.ui.components.GlobalHeader
import com.mgomanager.app.ui.navigation.Screen

@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var sortDropdownExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            GlobalHeader(subTitle = "Accounts")
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // Current Account Section
            CurrentAccountSection(
                currentAccount = uiState.currentAccount,
                isLoading = uiState.isLoading,
                onLaunchMonopolyGo = { accountId ->
                    viewModel.launchMonopolyGoWithAccountState(accountId)
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Search and Sort Section
            SearchAndSortSection(
                searchQuery = uiState.searchQuery,
                sortOption = uiState.sortOption,
                sortDirection = uiState.sortDirection,
                onSearchQueryChange = { viewModel.onSearchQueryChange(it) },
                onSortOptionChange = { viewModel.onSortOptionChange(it) },
                onToggleSortDirection = { viewModel.toggleSortDirection() },
                sortDropdownExpanded = sortDropdownExpanded,
                onSortDropdownExpandedChange = { sortDropdownExpanded = it }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Account List Section
            Text(
                text = "Accounts (${uiState.totalCount})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            AccountListSection(
                accounts = uiState.accounts,
                searchQuery = uiState.searchQuery,
                onAccountClick = { accountId ->
                    navController.navigate(Screen.Detail.createRoute(accountId))
                }
            )
        }
    }

    // Dialogs
    BackupDialogs(
        uiState = uiState,
        viewModel = viewModel
    )
}

@Composable
fun CurrentAccountSection(
    currentAccount: Account?,
    isLoading: Boolean,
    onLaunchMonopolyGo: (Long) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "AKTUELLER ACCOUNT:",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (currentAccount != null) {
                Text(
                    text = currentAccount.fullName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "ID: ${currentAccount.userId}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )

                Text(
                    text = "Zuletzt gespielt am: ${currentAccount.getFormattedLastPlayedAt()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { onLaunchMonopolyGo(currentAccount.id) },
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth(),
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
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Starte Monopoly Go")
                    }
                }
            } else {
                Text(
                    text = "Noch keine Daten vorhanden..",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )
            }
        }
    }
}

@Composable
fun SearchAndSortSection(
    searchQuery: String,
    sortOption: SortOption,
    sortDirection: SortDirection,
    onSearchQueryChange: (String) -> Unit,
    onSortOptionChange: (SortOption) -> Unit,
    onToggleSortDirection: () -> Unit,
    sortDropdownExpanded: Boolean,
    onSortDropdownExpandedChange: (Boolean) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Search Field
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Account suchen...") },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = "Suchen")
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Löschen")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Sort Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Sort Option Dropdown
            Box(modifier = Modifier.weight(1f)) {
                OutlinedButton(
                    onClick = { onSortDropdownExpandedChange(true) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Sortiere nach: ${sortOption.displayName}",
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Start
                    )
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = "Auswählen"
                    )
                }

                DropdownMenu(
                    expanded = sortDropdownExpanded,
                    onDismissRequest = { onSortDropdownExpandedChange(false) }
                ) {
                    SortOption.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.displayName) },
                            onClick = {
                                onSortOptionChange(option)
                                onSortDropdownExpandedChange(false)
                            },
                            leadingIcon = {
                                if (sortOption == option) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = "Ausgewählt",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        )
                    }
                }
            }

            // Sort Direction Toggle
            OutlinedButton(
                onClick = onToggleSortDirection,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = if (sortDirection == SortDirection.ASC) {
                        Icons.Default.ArrowUpward
                    } else {
                        Icons.Default.ArrowDownward
                    },
                    contentDescription = if (sortDirection == SortDirection.ASC) "Aufsteigend" else "Absteigend"
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(sortDirection.name)
            }
        }
    }
}

@Composable
fun AccountListSection(
    accounts: List<Account>,
    searchQuery: String,
    onAccountClick: (Long) -> Unit
) {
    when {
        accounts.isEmpty() && searchQuery.isNotBlank() -> {
            // No search results
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.SearchOff,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Kein Account mit diesem Namen gefunden.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        accounts.isEmpty() -> {
            // No accounts at all
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.FolderOff,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Noch keine Backups vorhanden.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        else -> {
            // Account list
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(accounts, key = { it.id }) { account ->
                    AccountListCard(
                        account = account,
                        onClick = { onAccountClick(account.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun AccountListCard(
    account: Account,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Account Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = account.fullName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "ID: ${account.shortUserId}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Text(
                    text = "Zuletzt: ${account.getFormattedLastPlayedAt()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            // Status Indicators
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (account.hasError) {
                    Icon(
                        Icons.Default.Error,
                        contentDescription = "Fehler",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
                if (account.susLevel.value > 0) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = "Sus",
                        tint = account.getBorderColor(),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = "Details",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }
        }
    }
}

@Composable
fun BackupDialogs(
    uiState: HomeUiState,
    viewModel: HomeViewModel
) {
    // Backup Dialog
    if (uiState.showBackupDialog) {
        BackupDialog(
            onDismiss = { viewModel.hideBackupDialog() },
            onConfirm = { name, hasFb, fbUser, fbPass, fb2fa, fbMail ->
                viewModel.createBackup(name, hasFb, fbUser, fbPass, fb2fa, fbMail)
            }
        )
    }

    // Backup Result Dialog
    uiState.backupResult?.let { result ->
        when (result) {
            is BackupResult.Success -> {
                AlertDialog(
                    onDismissRequest = { viewModel.clearBackupResult() },
                    title = { Text("Backup erfolgreich!") },
                    text = { Text("Account '${result.account.fullName}' wurde erfolgreich gesichert.") },
                    confirmButton = {
                        TextButton(onClick = { viewModel.clearBackupResult() }) {
                            Text("OK")
                        }
                    }
                )
            }

            is BackupResult.Failure -> {
                AlertDialog(
                    onDismissRequest = { viewModel.clearBackupResult() },
                    title = { Text("Backup fehlgeschlagen") },
                    text = { Text(result.error) },
                    confirmButton = {
                        TextButton(onClick = { viewModel.clearBackupResult() }) {
                            Text("OK")
                        }
                    }
                )
            }

            is BackupResult.PartialSuccess -> {
                AlertDialog(
                    onDismissRequest = { viewModel.clearBackupResult() },
                    title = { Text("Backup teilweise erfolgreich") },
                    text = {
                        Text("Backup erstellt, aber folgende IDs fehlen:\n${result.missingIds.joinToString(", ")}")
                    },
                    confirmButton = {
                        TextButton(onClick = { viewModel.clearBackupResult() }) {
                            Text("OK")
                        }
                    }
                )
            }

            is BackupResult.DuplicateUserId -> {
                // Handled by duplicateUserIdDialog
            }
        }
    }

    // Duplicate User ID Dialog
    uiState.duplicateUserIdDialog?.let { info ->
        AlertDialog(
            onDismissRequest = { viewModel.cancelDuplicateBackup() },
            title = { Text("Doppelte User ID") },
            text = { Text("User ID bereits als '${info.existingAccountName}' vorhanden.") },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmDuplicateBackup() }) {
                    Text("Fortfahren")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelDuplicateBackup() }) {
                    Text("Abbrechen")
                }
            }
        )
    }
}
