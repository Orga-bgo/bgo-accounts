# Rebuild Account-Detail-Seite (Final) - bGO Account Manager v2.0

## Kontext
Du bist ein Android-Entwickler und arbeitest an der **Version 2.0** der "bGO Account Manager" App. Diese Aufgabe fokussiert sich auf die **Account-Detail-Seite** mit dem finalen Layout-Design.

Die App basiert auf:
- **Kotlin** mit **MVVM-Architektur**
- **Jetpack Compose** + **Material3**
- **Hilt** (Dependency Injection)
- **Room Database** + **DataStore**
- **libsu** (Root-Zugriff)

Repository: `github.com/Orga-bgo/bgo-accounts`

---

## 1. Screen-Struktur (Final Design)

### 1.1 Header
```
┌─────────────────────────────────────┐
│ ← babixGO          [🔄 Refresh] [❓ Help] │  ← Hauptheader (lila #6200EE)
│ Account Details                     │  ← Sub-Header (heller lila #7C3FEE)
└─────────────────────────────────────┘
```

### 1.2 Content Layout

```
┌─────────────────────────────────────┐
│ ACCOUNTNAME: MGO_Main01             │  ← Große, fette Überschrift
├─────────────────────────────────────┤
│ [🔄 Restore] [✏️ Edit] [🗑️ Delete]  │  ← 3 Action-Buttons (Zeile)
├─────────────────────────────────────┤
│ User ID: 1140407373  [📋]           │  ← Große ID mit Copy-Button
├─────────────────────────────────────┤
│ Backup erstellt am: 30.01.2026 14:30│
│ Zuletzt gespielt am: 30.01.2026 20:15│
├─────────────────────────────────────┤
│ F-Link: ✅ Aktiv                    │
│ F-Code: ABC123  [📋]                │
├─────────────────────────────────────┤
│ Verbunden mit: [Facebook ✅] [Google ❌]│
│                                     │
│ ZUGANGSDATEN / SOCIAL               │
│ Platform: Facebook                  │
│ ID: fb_user_12345  [📋]             │
│ E-Mail: user@temp.com  [📋]         │
│ Passwort: ••••••••  [👁️]            │
│ 2FA-Code: 123456  [📋]              │
├─────────────────────────────────────┤
│ APP SETTINGS                        │
│ Device Token: 198aab99-...  [📋]    │
│ Google Ad ID: 1bbae05f-...  [📋]    │
│ App Set ID: cb52ea67-...  [📋]      │
│ Push Token: eESOgXn0S...  [📋]      │
├─────────────────────────────────────┤
│ GERÄTE IDs                          │
│ Android ID (SSAID): 42f684fa...  [📋]│
├─────────────────────────────────────┤
│ UNITY IDs                           │
│ Cloud User ID: ce983305d1...  [📋] │
│ Player Session ID: 75537311...  [📋]│
│ Session Count: 30                   │
└─────────────────────────────────────┘
```

---

## 2. Detaillierte Sektionen

### 2.1 Account-Name Header

```kotlin
@Composable
fun AccountNameHeader(accountName: String) {
    Text(
        text = "ACCOUNTNAME: $accountName",
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        color = Color(0xFF212121),
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    )
    Divider()
}
```

### 2.2 Action-Buttons (Restore, Edit, Delete)

```kotlin
@Composable
fun ActionButtons(
    onRestore: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Restore Button
        Button(
            onClick = onRestore,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF6200EE)
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("🔄 Restore", fontSize = 14.sp)
        }

        // Edit Button
        OutlinedButton(
            onClick = onEdit,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, Color(0xFF6200EE))
        ) {
            Text("✏️ Edit", fontSize = 14.sp, color = Color(0xFF6200EE))
        }

        // Delete Button
        OutlinedButton(
            onClick = onDelete,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, Color(0xFFF44336))
        ) {
            Text("🗑️ Delete", fontSize = 14.sp, color = Color(0xFFF44336))
        }
    }
    Divider()
}
```

### 2.3 User ID (prominent)

```kotlin
@Composable
fun UserIdSection(
    userId: String,
    onCopy: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "User ID",
                fontSize = 14.sp,
                color = Color(0xFF666666),
                fontWeight = FontWeight.Medium
            )
            Text(
                text = userId,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF212121),
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        IconButton(onClick = onCopy) {
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = "Kopieren",
                tint = Color(0xFF6200EE),
                modifier = Modifier.size(24.dp)
            )
        }
    }
    Divider()
}
```

### 2.4 Zeitstempel-Sektion

```kotlin
@Composable
fun TimestampSection(
    createdAt: Long,
    lastPlayedAt: Long
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Backup erstellt am:",
                fontSize = 14.sp,
                color = Color(0xFF666666)
            )
            Text(
                text = formatDate(createdAt),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF212121)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Zuletzt gespielt am:",
                fontSize = 14.sp,
                color = Color(0xFF666666)
            )
            Text(
                text = formatDate(lastPlayedAt),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF212121)
            )
        }
    }
    Divider()
}

fun formatDate(timestamp: Long): String {
    val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMAN)
    return dateFormat.format(Date(timestamp))
}
```

### 2.5 Friendship-Link & Code Sektion

```kotlin
@Composable
fun FriendshipSection(
    friendshipLink: String?,
    friendshipCode: String?,
    onCopyCode: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        // F-Link Status
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "F-Link: ",
                fontSize = 14.sp,
                color = Color(0xFF666666)
            )
            Text(
                text = if (friendshipLink != null) "✅ Aktiv" else "❌ Nicht vorhanden",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = if (friendshipLink != null) Color(0xFF4CAF50) else Color(0xFFF44336)
            )
        }

        // F-Code
        if (friendshipCode != null) {
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "F-Code: ",
                        fontSize = 14.sp,
                        color = Color(0xFF666666)
                    )
                    Text(
                        text = friendshipCode,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF212121)
                    )
                }

                IconButton(onClick = onCopyCode) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "F-Code kopieren",
                        tint = Color(0xFF6200EE)
                    )
                }
            }
        }
    }
    Divider()
}
```

### 2.6 Social Media Verbindung (Facebook / Google)

```kotlin
@Composable
fun SocialConnectionSection(
    socialPlatform: String? // "facebook" oder "google"
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Verbunden mit: ",
            fontSize = 14.sp,
            color = Color(0xFF666666)
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Facebook Badge
        SocialBadge(
            platform = "Facebook",
            isConnected = socialPlatform == "facebook"
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Google Badge
        SocialBadge(
            platform = "Google",
            isConnected = socialPlatform == "google"
        )
    }
    Divider()
}

@Composable
fun SocialBadge(
    platform: String,
    isConnected: Boolean
) {
    Surface(
        color = if (isConnected) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            1.dp,
            if (isConnected) Color(0xFF4CAF50) else Color(0xFFF44336)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = platform,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = if (isConnected) Color(0xFF2E7D32) else Color(0xFFC62828)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = if (isConnected) "✅" else "❌",
                fontSize = 12.sp
            )
        }
    }
}
```

### 2.7 Zugangsdaten / Social (Expandable)

```kotlin
@Composable
fun SocialCredentialsSection(
    platform: String?,
    socialId: String?,
    socialEmail: String?,
    socialPassword: String?,
    social2faCode: String?,
    onCopy: (String, String) -> Unit
) {
    if (platform == null || socialId == null) {
        return // Nicht anzeigen, wenn keine Social-Verbindung
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF5F5F5))
            .padding(16.dp)
    ) {
        Text(
            text = "ZUGANGSDATEN / SOCIAL",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF666666),
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Platform
        InfoRow("Platform", platform.capitalize())

        // ID
        IdFieldCompact("ID", socialId) { onCopy("Social ID", socialId) }

        // E-Mail
        socialEmail?.let {
            IdFieldCompact("E-Mail", it) { onCopy("E-Mail", it) }
        }

        // Passwort
        socialPassword?.let {
            PasswordFieldCompact("Passwort", it)
        }

        // 2FA-Code
        social2faCode?.let {
            IdFieldCompact("2FA-Code", it) { onCopy("2FA-Code", it) }
        }
    }
    Divider()
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "$label:",
            fontSize = 13.sp,
            color = Color(0xFF666666)
        )
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF212121)
        )
    }
}

@Composable
fun IdFieldCompact(
    label: String,
    value: String,
    onCopy: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$label:",
            fontSize = 13.sp,
            color = Color(0xFF666666),
            modifier = Modifier.weight(0.3f)
        )
        Text(
            text = value.take(15) + if (value.length > 15) "..." else "",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF212121),
            modifier = Modifier.weight(0.5f)
        )
        IconButton(
            onClick = onCopy,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = "Kopieren",
                tint = Color(0xFF6200EE),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun PasswordFieldCompact(
    label: String,
    password: String
) {
    var isVisible by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$label:",
            fontSize = 13.sp,
            color = Color(0xFF666666),
            modifier = Modifier.weight(0.3f)
        )
        Text(
            text = if (isVisible) password else "••••••••",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF212121),
            modifier = Modifier.weight(0.5f)
        )
        IconButton(
            onClick = { isVisible = !isVisible },
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = if (isVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                contentDescription = if (isVisible) "Verstecken" else "Anzeigen",
                tint = Color(0xFF6200EE),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
```

### 2.8 App Settings Sektion

```kotlin
@Composable
fun AppSettingsSection(
    deviceToken: String,
    googleAdId: String,
    appSetId: String,
    pushToken: String?,
    onCopy: (String, String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = "APP SETTINGS",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF666666),
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        IdFieldCompact("Device Token", deviceToken) {
            onCopy("Device Token", deviceToken)
        }

        IdFieldCompact("Google Ad ID", googleAdId) {
            onCopy("Google Ad ID", googleAdId)
        }

        IdFieldCompact("App Set ID", appSetId) {
            onCopy("App Set ID", appSetId)
        }

        pushToken?.let {
            IdFieldCompact("Push Token", it) {
                onCopy("Push Token", it)
            }
        }
    }
    Divider()
}
```

### 2.9 Geräte IDs Sektion

```kotlin
@Composable
fun DeviceIdsSection(
    ssaid: String,
    onCopy: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = "GERÄTE IDs",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF666666),
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        IdFieldCompact("Android ID (SSAID)", ssaid, onCopy)
    }
    Divider()
}
```

### 2.10 Unity IDs Sektion

```kotlin
@Composable
fun UnityIdsSection(
    cloudUserId: String?,
    playerSessionId: String?,
    sessionCount: Int?,
    onCopy: (String, String) -> Unit
) {
    if (cloudUserId == null && playerSessionId == null && sessionCount == null) {
        return // Nicht anzeigen, wenn keine Unity-Daten vorhanden
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = "UNITY IDs",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF666666),
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        cloudUserId?.let {
            IdFieldCompact("Cloud User ID", it) {
                onCopy("Cloud User ID", it)
            }
        }

        playerSessionId?.let {
            IdFieldCompact("Player Session ID", it) {
                onCopy("Player Session ID", it)
            }
        }

        sessionCount?.let {
            InfoRow("Session Count", it.toString())
        }
    }
}
```

---

## 3. Vollständiger Screen

### 3.1 AccountDetailScreen.kt

```kotlin
@Composable
fun AccountDetailScreen(
    accountId: Long,
    onNavigateBack: () -> Unit,
    viewModel: AccountDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            Column {
                MainHeader(
                    onNavigateBack = onNavigateBack,
                    onRefresh = { viewModel.refreshAccount() },
                    onHelp = { /* Help Dialog */ }
                )
                SubHeader(title = "Account Details")
            }
        }
    ) { paddingValues ->
        if (uiState.isLoading) {
            LoadingIndicator()
        } else if (uiState.account != null) {
            val account = uiState.account!!

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Account-Name Header
                item {
                    AccountNameHeader(account.accountName)
                }

                // Action Buttons
                item {
                    ActionButtons(
                        onRestore = { viewModel.restoreAccount() },
                        onEdit = { viewModel.enterEditMode() },
                        onDelete = { viewModel.showDeleteConfirmation() }
                    )
                }

                // User ID (prominent)
                item {
                    UserIdSection(
                        userId = account.userId,
                        onCopy = { viewModel.copyToClipboard("User ID", account.userId) }
                    )
                }

                // Zeitstempel
                item {
                    TimestampSection(
                        createdAt = account.createdAt,
                        lastPlayedAt = account.lastPlayedAt
                    )
                }

                // Friendship
                item {
                    FriendshipSection(
                        friendshipLink = account.friendshipLink,
                        friendshipCode = account.friendshipCode,
                        onCopyCode = {
                            account.friendshipCode?.let {
                                viewModel.copyToClipboard("F-Code", it)
                            }
                        }
                    )
                }

                // Social Connection
                item {
                    SocialConnectionSection(
                        socialPlatform = account.socialPlatform
                    )
                }

                // Zugangsdaten / Social
                item {
                    SocialCredentialsSection(
                        platform = account.socialPlatform,
                        socialId = account.socialId,
                        socialEmail = account.socialEmail,
                        socialPassword = account.socialPassword,
                        social2faCode = account.social2faCode,
                        onCopy = viewModel::copyToClipboard
                    )
                }

                // App Settings
                item {
                    AppSettingsSection(
                        deviceToken = account.deviceToken,
                        googleAdId = account.gaid,
                        appSetId = account.appSetId,
                        pushToken = account.pushToken,
                        onCopy = viewModel::copyToClipboard
                    )
                }

                // Geräte IDs
                item {
                    DeviceIdsSection(
                        ssaid = account.ssaid,
                        onCopy = { viewModel.copyToClipboard("SSAID", account.ssaid) }
                    )
                }

                // Unity IDs
                item {
                    UnityIdsSection(
                        cloudUserId = account.unityCloudUserId,
                        playerSessionId = account.unityPlayerSessionId,
                        sessionCount = account.unityPlayerSessionCount,
                        onCopy = viewModel::copyToClipboard
                    )
                }

                // Bottom Padding
                item {
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }

    // Delete Confirmation Dialog
    if (uiState.showDeleteDialog) {
        DeleteConfirmationDialog(
            accountName = uiState.account?.accountName ?: "",
            onConfirm = {
                viewModel.deleteAccount()
                onNavigateBack()
            },
            onDismiss = { viewModel.hideDeleteConfirmation() }
        )
    }
}
```

---

## 4. ViewModel

### 4.1 State

```kotlin
data class AccountDetailUiState(
    val account: Account? = null,
    val isLoading: Boolean = true,
    val showDeleteDialog: Boolean = false,
    val error: String? = null
)
```

### 4.2 ViewModel Implementation

```kotlin
@HiltViewModel
class AccountDetailViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val clipboardUtil: ClipboardUtil,
    private val rootUtil: RootUtil,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val accountId: Long = savedStateHandle["accountId"] ?: 0L

    private val _uiState = MutableStateFlow(AccountDetailUiState())
    val uiState = _uiState.asStateFlow()

    init {
        loadAccount()
    }

    private fun loadAccount() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            accountRepository.getAccountById(accountId)
                .collect { account ->
                    _uiState.update {
                        it.copy(
                            account = account,
                            isLoading = false
                        )
                    }
                }
        }
    }

    fun refreshAccount() {
        loadAccount()
    }

    fun copyToClipboard(label: String, value: String) {
        clipboardUtil.copy(label, value)
    }

    fun restoreAccount() {
        viewModelScope.launch {
            val account = _uiState.value.account ?: return@launch
            accountRepository.restoreAccount(account)
        }
    }

    fun enterEditMode() {
        // Navigate to Edit Screen
    }

    fun showDeleteConfirmation() {
        _uiState.update { it.copy(showDeleteDialog = true) }
    }

    fun hideDeleteConfirmation() {
        _uiState.update { it.copy(showDeleteDialog = false) }
    }

    fun deleteAccount() {
        viewModelScope.launch {
            val account = _uiState.value.account ?: return@launch
            accountRepository.deleteAccount(account)
        }
    }
}
```

---

## 5. Delete Confirmation Dialog

```kotlin
@Composable
fun DeleteConfirmationDialog(
    accountName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = Color(0xFFF44336),
                modifier = Modifier.size(48.dp)
            )
        },
        title = {
            Text(
                text = "Account löschen?",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = "Möchtest du den Account "$accountName" wirklich löschen? Diese Aktion kann nicht rückgängig gemacht werden."
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFF44336)
                )
            ) {
                Text("Löschen")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen")
            }
        }
    )
}
```

---

## 6. Testing-Checkliste

### Unit Tests
- [ ] ViewModel lädt Account korrekt
- [ ] Copy-to-Clipboard funktioniert
- [ ] Delete-Dialog wird angezeigt
- [ ] Delete führt Löschung durch

### Integration Tests
- [ ] Navigation von Accounts-Screen
- [ ] Alle Sektionen werden angezeigt
- [ ] Social-Sektion nur bei Verbindung sichtbar
- [ ] Unity-Sektion nur bei Daten sichtbar

### UI Tests
- [ ] Action-Buttons funktionieren
- [ ] Copy-Buttons kopieren korrekt
- [ ] Passwort-Toggle funktioniert
- [ ] Delete-Dialog Confirm/Cancel

---

## 7. Akzeptanzkriterien

✅ **Layout:**
- [ ] Header wie Accounts-Screen
- [ ] Account-Name als große Überschrift
- [ ] 3 Action-Buttons in einer Zeile
- [ ] User ID prominent mit Copy-Button
- [ ] Alle Sektionen klar getrennt

✅ **Sektionen:**
- [ ] Zeitstempel (Erstellt, Zuletzt gespielt)
- [ ] F-Link & F-Code
- [ ] Social Connection Badges (FB ✅ / GO ❌)
- [ ] Zugangsdaten / Social (nur bei Verbindung)
- [ ] App Settings (4-5 IDs)
- [ ] Geräte IDs (SSAID)
- [ ] Unity IDs (optional)

✅ **Interaktionen:**
- [ ] Restore-Button funktioniert
- [ ] Edit-Button navigiert zu Edit-Screen
- [ ] Delete-Button zeigt Bestätigungsdialog
- [ ] Alle Copy-Buttons kopieren IDs
- [ ] Passwort-Toggle funktioniert

---

## 8. Offene Fragen

1. **Edit-Screen**: Separater Screen oder Inline-Editing?
2. **Restore-Funktion**: Direkt ausführen oder Wizard?
3. **F-Link**: Soll dieser klickbar sein (Browser öffnen)?
4. **Social-Badges**: Sollen diese klickbar sein (Details anzeigen)?

---

**Ende des Prompts**
