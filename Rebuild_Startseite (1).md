# Rebuild: Startseite (Accounts Screen) - bGO Account Manager

## Projekt-Kontext

Du arbeitest an der **Version 2.0** des bGO Account Managers (ehemals MGO Manager), einer Android Root-App zum Backup und Restore von Monopoly Go Accounts. Die App ist in **Kotlin** mit **Jetpack Compose**, **MVVM-Architektur**, **Hilt DI**, **Room Database** und **libsu** für Root-Zugriff entwickelt.

**Wichtig:** Die bestehende v1.0 Code-Basis bleibt größtenteils erhalten. Dieser Prompt fokussiert sich auf die **Neugestaltung der Startseite** (Accounts Screen) und die damit verbundenen Funktionen.

---

## Aufgabe: Startseite komplett überarbeiten

### 1. Neue UI-Struktur implementieren

#### **Header-System (Global für alle Screens)**

Erstelle eine wiederverwendbare Header-Komponente:

```kotlin
@Composable
fun AppHeader(
    currentScreen: String,
    onRefreshClick: () -> Unit,
    onHelpClick: () -> Unit
) {
    Column {
        // Main Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF6200EE))
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "babixGO",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconButton(onClick = onRefreshClick) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Aktualisieren",
                        tint = Color.White
                    )
                }
                IconButton(onClick = onHelpClick) {
                    Icon(
                        imageVector = Icons.Default.Help,
                        contentDescription = "Hilfe",
                        tint = Color.White
                    )
                }
            }
        }

        // Sub-Header (Screen Title)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF7C3FEE))
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Text(
                text = currentScreen,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
        }
    }
}
```

#### **Bottom Navigation Bar (Global, dauerhaft sichtbar)**

Implementiere eine feste Bottom Navigation mit Scaffold:

```kotlin
@Composable
fun MainScaffold(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = Color.White,
                modifier = Modifier.shadow(8.dp)
            ) {
                NavigationBarItem(
                    selected = currentRoute == "accounts",
                    onClick = { onNavigate("accounts") },
                    icon = { Icon(Icons.Default.List, "Accounts") },
                    label = { Text("Accounts") }
                )
                NavigationBarItem(
                    selected = currentRoute == "backup",
                    onClick = { onNavigate("backup") },
                    icon = { Icon(Icons.Default.Save, "Backup") },
                    label = { Text("Backup") }
                )
                NavigationBarItem(
                    selected = currentRoute == "settings",
                    onClick = { onNavigate("settings") },
                    icon = { Icon(Icons.Default.Settings, "Settings") },
                    label = { Text("Settings") }
                )
                NavigationBarItem(
                    selected = currentRoute == "log",
                    onClick = { onNavigate("log") },
                    icon = { Icon(Icons.Default.Description, "Log") },
                    label = { Text("Log") }
                )
            }
        }
    ) { paddingValues ->
        content(paddingValues)
    }
}
```

---

### 2. Accounts Screen - Neue Layout-Struktur

Der Accounts Screen besteht aus folgenden Sektionen:

#### **Sektion 1: Aktueller Account**

Zeigt den zuletzt wiederhergestellten Account mit Quick-Launch-Button:

```kotlin
@Composable
fun CurrentAccountSection(
    currentAccount: Account?,
    onLaunchGame: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "AKTUELLER ACCOUNT:",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (currentAccount != null) {
                Text(
                    text = currentAccount.accountName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "ID: ${currentAccount.userId}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onLaunchGame,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50)
                    )
                ) {
                    Icon(Icons.Default.PlayArrow, "Start")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Starte Monopoly Go")
                }
            } else {
                Text(
                    text = "Kein Account aktiv",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )
            }
        }
    }
}
```

#### **Sektion 2: Suche & Sortierung**

```kotlin
@Composable
fun SearchAndSortSection(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    sortOption: SortOption,
    onSortChange: (SortOption) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Search Field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Suche") },
                leadingIcon = { Icon(Icons.Default.Search, "Suche") },
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Sort Dropdown
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = "Sortiere nach: ${sortOption.displayName}",
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    SortOption.values().forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.displayName) },
                            onClick = {
                                onSortChange(option)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

enum class SortOption(val displayName: String) {
    NAME("Name"),
    PREFIX("Aktuelles Präfix"),
    LAST_PLAYED("Zuletzt gespielt"),
    CREATED_AT("Erstellt am")
}
```

#### **Sektion 3: Account-Liste (1 Account pro Zeile)**

```kotlin
@Composable
fun AccountListItem(
    account: Account,
    onMenuClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // Account Name
                Text(
                    text = account.accountName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                // User ID
                Text(
                    text = "ID: ${account.userId}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Last Played
                Text(
                    text = "Zuletzt gespielt am: ${formatDate(account.lastPlayedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Freundschaftsdaten
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    FriendshipIndicator(
                        label = "Freundschaftslink",
                        isPresent = account.friendshipLink != null
                    )
                    FriendshipIndicator(
                        label = "Freundschaftscode",
                        isPresent = account.friendshipCode != null
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Status (Sus & Error)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    StatusIndicator(
                        label = "Sus",
                        value = when (account.susLevel) {
                            0 -> "0"
                            3 -> "3"
                            7 -> "7"
                            99 -> "Perm"
                            else -> account.susLevel.toString()
                        },
                        color = when (account.susLevel) {
                            0 -> Color(0xFF4CAF50)
                            3 -> Color(0xFFFF9800)
                            7 -> Color(0xFFFFB74D)
                            else -> Color(0xFFF44336)
                        }
                    )
                    StatusIndicator(
                        label = "Error",
                        value = if (account.hasError) "Ja" else "Nein",
                        color = if (account.hasError) 
                            Color(0xFFF44336) else Color(0xFF4CAF50)
                    )
                }
            }

            // Menu Button (öffnet Detail-Screen)
            IconButton(onClick = onMenuClick) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Details"
                )
            }
        }
    }
}

@Composable
fun FriendshipIndicator(label: String, isPresent: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            text = if (isPresent) "✓" else "✗",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = if (isPresent) Color(0xFF4CAF50) else Color(0xFFF44336)
        )
    }
}

@Composable
fun StatusIndicator(label: String, value: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
    }
}
```

---

### 3. Datenbank-Erweiterungen

#### **Account Entity erweitern**

Füge neue Felder zur `AccountEntity` hinzu:

```kotlin
@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // Bestehende Felder...
    val accountName: String,
    val prefix: String = "",
    val createdAt: Long,
    val lastPlayedAt: Long,
    val userId: String,
    val gaid: String? = "nicht vorhanden",
    val deviceToken: String? = "nicht vorhanden",
    val appSetId: String? = "nicht vorhanden",
    val ssaid: String? = "nicht vorhanden",
    val susLevel: Int = 0,
    val hasError: Boolean = false,
    val backupPath: String,
    val fileOwner: String,
    val fileGroup: String,
    val filePermissions: String,

    // NEU: Freundschaftsdaten
    val friendshipLink: String? = null,
    val friendshipCode: String? = null,

    // NEU: Facebook-Daten (umbenennen für Klarheit)
    val hasFacebookLink: Boolean = false,
    val fbUsername: String? = null,
    val fbPassword: String? = null,
    val fb2FA: String? = null,
    val fbTempMail: String? = null
)
```

#### **Migration erstellen**

```kotlin
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "ALTER TABLE accounts ADD COLUMN friendshipLink TEXT DEFAULT NULL"
        )
        database.execSQL(
            "ALTER TABLE accounts ADD COLUMN friendshipCode TEXT DEFAULT NULL"
        )
    }
}

// In AppDatabase.kt
@Database(
    entities = [AccountEntity::class, LogEntity::class],
    version = 2, // Version erhöhen!
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    // ...
}
```

#### **DataStore für "Aktueller Account"**

Erweitere die `SettingsDataStore` um Speicherung des zuletzt gespielten Accounts:

```kotlin
data class AppSettings(
    val accountPrefix: String = "MGO_",
    val backupRootPath: String = "/storage/emulated/0/mgo/backups/",
    val currentSessionId: String = UUID.randomUUID().toString(),
    val appStartCount: Int = 0,

    // NEU: Aktueller Account
    val currentAccountId: Long? = null // ID des zuletzt restoren Accounts
)

// In SettingsDataStore.kt
suspend fun setCurrentAccount(accountId: Long) {
    context.dataStore.edit { prefs ->
        prefs[CURRENT_ACCOUNT_ID] = accountId
    }
}

suspend fun getCurrentAccountId(): Long? {
    return context.dataStore.data.map { prefs ->
        prefs[CURRENT_ACCOUNT_ID]
    }.first()
}
```

---

### 4. ViewModel-Logik

#### **AccountsViewModel erweitern**

```kotlin
@HiltViewModel
class AccountsViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val settingsDataStore: SettingsDataStore,
    private val rootUtil: RootUtil
) : ViewModel() {

    // State Flows
    private val _accounts = MutableStateFlow<List<Account>>(emptyList())
    val accounts: StateFlow<List<Account>> = _accounts.asStateFlow()

    private val _currentAccount = MutableStateFlow<Account?>(null)
    val currentAccount: StateFlow<Account?> = _currentAccount.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _sortOption = MutableStateFlow(SortOption.NAME)
    val sortOption: StateFlow<SortOption> = _sortOption.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    init {
        loadAccounts()
        loadCurrentAccount()

        // Auto-Sortierung & Filterung
        viewModelScope.launch {
            combine(
                accountRepository.getAllAccounts(),
                _searchQuery,
                _sortOption
            ) { accounts, query, sort ->
                accounts
                    .filter { it.accountName.contains(query, ignoreCase = true) }
                    .sortedWith(getSortComparator(sort))
            }.collect { sortedAccounts ->
                _accounts.value = sortedAccounts
            }
        }
    }

    private fun loadCurrentAccount() {
        viewModelScope.launch {
            val currentId = settingsDataStore.getCurrentAccountId()
            if (currentId != null) {
                _currentAccount.value = accountRepository.getAccountById(currentId)
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun onSortOptionChange(option: SortOption) {
        _sortOption.value = option
        // Sortierung in DataStore speichern
        viewModelScope.launch {
            settingsDataStore.saveSortOption(option)
        }
    }

    fun refreshAccounts() {
        viewModelScope.launch {
            _isRefreshing.value = true
            // Accounts neu laden
            loadAccounts()
            // Statistiken neu berechnen
            calculateStatistics()
            _isRefreshing.value = false
        }
    }

    fun launchMonopolyGo(accountId: Long) {
        viewModelScope.launch {
            try {
                val command = "am start -n com.scopely.monopolygo/.MainActivity"
                val result = rootUtil.executeCommand(command)
                if (result.isSuccess) {
                    // Log Eintrag erstellen
                    logRepository.log(
                        operation = "LAUNCH_GAME",
                        level = LogLevel.INFO,
                        accountName = _currentAccount.value?.accountName,
                        message = "Monopoly Go gestartet mit Account: ${_currentAccount.value?.accountName}"
                    )
                } else {
                    // Fehler-Handling
                }
            } catch (e: Exception) {
                // Error-Handling
            }
        }
    }

    private fun getSortComparator(sortOption: SortOption): Comparator<Account> {
        return when (sortOption) {
            SortOption.NAME -> compareBy { it.accountName }
            SortOption.PREFIX -> compareBy { it.prefix }
            SortOption.LAST_PLAYED -> compareByDescending { it.lastPlayedAt }
            SortOption.CREATED_AT -> compareByDescending { it.createdAt }
        }
    }
}
```

---

### 5. Restore-Logik erweitern

Beim Restore eines Accounts muss dieser als "Aktueller Account" gespeichert werden:

```kotlin
// In RestoreBackupUseCase.kt
suspend fun restoreBackup(accountId: Long): Result<Unit> {
    return try {
        // ... bestehende Restore-Logik ...

        // NEU: Account als aktuellen Account setzen
        settingsDataStore.setCurrentAccount(accountId)

        // lastPlayedAt aktualisieren
        accountRepository.updateLastPlayed(accountId, System.currentTimeMillis())

        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }
}
```

---

### 6. Hilfe-Button Funktionalität

Erstelle einen einfachen Hilfe-Dialog (Platzhalter):

```kotlin
@Composable
fun HelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Hilfe") },
        text = {
            Column {
                Text("bGO Account Manager v2.0", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Diese App ermöglicht das Backup und Restore von Monopoly Go Accounts.")
                Spacer(modifier = Modifier.height(16.dp))
                Text("Features:", fontWeight = FontWeight.Bold)
                Text("• Account-Verwaltung")
                Text("• Backup & Restore")
                Text("• Freundschaftsdaten-Tracking")
                Text("• Sus-Level-Verwaltung")
                Spacer(modifier = Modifier.height(16.dp))
                Text("Bei Fragen siehe GitHub Repository.", fontSize = 12.sp, color = Color.Gray)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        }
    )
}
```

---

### 7. Kompletter Accounts Screen

```kotlin
@Composable
fun AccountsScreen(
    viewModel: AccountsViewModel = hiltViewModel(),
    onNavigateToDetail: (Long) -> Unit
) {
    val accounts by viewModel.accounts.collectAsState()
    val currentAccount by viewModel.currentAccount.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val sortOption by viewModel.sortOption.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()

    var showHelpDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
    ) {
        // Header
        AppHeader(
            currentScreen = "Accounts",
            onRefreshClick = { viewModel.refreshAccounts() },
            onHelpClick = { showHelpDialog = true }
        )

        // Content mit Pull-to-Refresh
        SwipeRefresh(
            state = rememberSwipeRefreshState(isRefreshing),
            onRefresh = { viewModel.refreshAccounts() }
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp) // Platz für Bottom Nav
            ) {
                // Current Account Section
                item {
                    CurrentAccountSection(
                        currentAccount = currentAccount,
                        onLaunchGame = {
                            currentAccount?.id?.let { 
                                viewModel.launchMonopolyGo(it) 
                            }
                        }
                    )
                }

                // Search & Sort Section
                item {
                    SearchAndSortSection(
                        searchQuery = searchQuery,
                        onSearchChange = { viewModel.onSearchQueryChange(it) },
                        sortOption = sortOption,
                        onSortChange = { viewModel.onSortOptionChange(it) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Account List
                items(
                    items = accounts,
                    key = { it.id }
                ) { account ->
                    AccountListItem(
                        account = account,
                        onMenuClick = { onNavigateToDetail(account.id) }
                    )
                }

                // Empty State
                if (accounts.isEmpty() && searchQuery.isNotEmpty()) {
                    item {
                        EmptyState(message = "Kein Account mit diesem Namen gefunden.")
                    }
                } else if (accounts.isEmpty()) {
                    item {
                        EmptyState(
                            message = "Noch keine Backups vorhanden.",
                            hint = "Erstelle dein erstes Backup im 'Backup'-Tab!"
                        )
                    }
                }
            }
        }
    }

    // Help Dialog
    if (showHelpDialog) {
        HelpDialog(onDismiss = { showHelpDialog = false })
    }
}

@Composable
fun EmptyState(message: String, hint: String? = null) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = Color.Gray.copy(alpha = 0.3f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        if (hint != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )
        }
    }
}
```

---

## Testing-Anforderungen

### Unit Tests

```kotlin
@Test
fun `search filters accounts correctly`() {
    // Given
    val accounts = listOf(
        Account(accountName = "MGO_Main01", ...),
        Account(accountName = "ALT_Test", ...)
    )

    // When
    val filtered = accounts.filter { 
        it.accountName.contains("MGO", ignoreCase = true) 
    }

    // Then
    assertEquals(1, filtered.size)
    assertEquals("MGO_Main01", filtered[0].accountName)
}

@Test
fun `sort by name works correctly`() {
    // Test sortierung
}

@Test
fun `current account is loaded on init`() {
    // Test dass aktueller Account geladen wird
}
```

### Integration Tests

- Teste Navigation zwischen Screens
- Teste Refresh-Funktionalität
- Teste Suche mit verschiedenen Queries
- Teste alle Sortieroptionen

### UI Tests

- Teste Account-Liste Rendering
- Teste Button-Interaktionen
- Teste Help-Dialog
- Teste Empty States

---

## Checkliste für erfolgreiche Implementation

- [ ] Datenbank-Migration von v1 zu v2 durchgeführt
- [ ] Neue Felder `friendshipLink` und `friendshipCode` in Entity
- [ ] DataStore erweitert um `currentAccountId`
- [ ] Restore-Logik speichert aktuellen Account
- [ ] Header-Komponente erstellt und wiederverwendbar
- [ ] Bottom Navigation implementiert (fixed)
- [ ] "Aktueller Account"-Sektion zeigt letzten Restore
- [ ] "Starte Monopoly Go"-Button startet Spiel
- [ ] Suche funktioniert live (case-insensitive)
- [ ] Sortierung mit 4 Optionen (Name, Präfix, Zuletzt gespielt, Erstellt am)
- [ ] Sortierung wird in DataStore persistiert
- [ ] Account-Liste zeigt alle Informationen (Name, ID, Datum, Freundschaft, Status)
- [ ] Freundschaftsdaten mit ✓/✗ Icons angezeigt
- [ ] Sus-Level farbcodiert (0=Grün, 3=Orange, 7=Hell-Orange, Perm=Rot)
- [ ] Error-Status farbcodiert (Ja=Rot, Nein=Grün)
- [ ] [...]-Button öffnet Account-Detail-Screen
- [ ] Refresh-Button aktualisiert Liste
- [ ] Help-Button zeigt Dialog
- [ ] Pull-to-Refresh implementiert
- [ ] Empty States für "Keine Accounts" und "Keine Suchergebnisse"
- [ ] UI entspricht HTML-Mockup
- [ ] Alle Tests erfolgreich

---

## Wichtige Hinweise

1. **Bestehenden Code nicht überschreiben**: Die Backup/Restore-Logik aus v1.0 bleibt unverändert
2. **Rückwärtskompatibilität**: Migration muss alte Datenbanken ohne Datenverlust upgraden
3. **Performance**: LazyColumn mit `key` für effizientes Rendering bei vielen Accounts
4. **Root-Befehle**: Monopoly Go Start-Command: `am start -n com.scopely.monopolygo/.MainActivity`
5. **Logging**: Alle Aktionen (Launch, Refresh) sollen geloggt werden
6. **Error Handling**: Graceful Degradation wenn Root fehlt oder Monopoly Go nicht installiert

---

## Nächste Schritte (nicht in diesem Prompt)

Nach erfolgreicher Implementation dieser Startseite folgen:
- Account-Detail-Screen
- Backup-Screen
- Settings-Screen
- Log-Screen

---

**Viel Erfolg bei der Implementation! 🚀**
