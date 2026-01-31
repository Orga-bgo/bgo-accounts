# Rebuild App-Start - bGO Account Manager v2.0

## Kontext
Du bist ein Android-Entwickler und arbeitest an der **Version 2.0** der "bGO Account Manager" App. Diese Aufgabe fokussiert sich auf die **App-Start-Logik** und unterscheidet zwischen zwei Szenarien:

1. **Erster Start nach Installation** (Onboarding)
2. **Normaler App-Start** (regulärer Betrieb nach Onboarding)

Die App basiert auf:
- **Kotlin** mit **MVVM-Architektur**
- **Jetpack Compose** + **Material3**
- **Hilt** (Dependency Injection)
- **Room Database** + **DataStore**
- **libsu** (Root-Zugriff via TopJohnWu)

Repository: `github.com/Orga-bgo/bgo-accounts`

---

## 1. Architektur-Übersicht

### 1.1 App-Start-Flow

```
App Start
    ↓
SplashScreen (500ms)
    ↓
├─ Ist erster Start? ────→ JA → OnboardingFlow
│                                     ↓
│                              SetupCompletedFlag
│                                     ↓
│                              Navigate to Home
│
└─ NEIN → SystemChecks
              ↓
        Navigate to Home
```

### 1.2 Zustände verwalten via DataStore

```kotlin
data class AppState(
    // Onboarding-Status
    val isFirstLaunch: Boolean = true,
    val onboardingCompleted: Boolean = false,
    val onboardingStep: Int = 0,  // 0=Start, 1=Import, 2=Prefix, 3=SSH, 4=Backup, 5=Root

    // Setup-Daten
    val defaultPrefix: String? = null,
    val backupDirectory: String? = null,
    val sshEnabled: Boolean = false,
    val sshHost: String? = null,
    val sshPort: Int = 22,
    val sshUsername: String? = null,
    val sshPassword: String? = null,  // Verschlüsselt speichern!

    // System-Status
    val monopolyGoUid: Int? = null,
    val monopolyGoInstalled: Boolean = false,
    val rootAccessGranted: Boolean = false,
    val dataDataPermissionsGranted: Boolean = false,

    // Letzte Prüfung
    val lastSystemCheckTimestamp: Long = 0L
)
```

---

## 2. Erster Start - Onboarding Flow

### 2.1 Onboarding-Screens (Reihenfolge)

**Screen 1: Willkommen**
- Zeige App-Logo und Willkommenstext
- Button: "Los geht's"

**Screen 2: Import-Check**
- Prüfe ob ZIP-Datei im Import-Ordner existiert
- Pfad: `/storage/emulated/0/Download/bgo_accounts_import.zip`
- Falls vorhanden: Biete Import-Funktion an
- Falls nicht: Überspringen-Option

**Screen 3: Präfix-Eingabe**
- Frage nach Standard-Präfix für neue Accounts
- Beispiel: "MGO_", "ALT_", etc.
- Optional: "Später festlegen" Button

**Screen 4: SSH-Setup (Optional)**
- Aktiviere Remote-Backup via SSH
- Felder: Host, Port, Username, Passwort
- "Überspringen" Button

**Screen 5: Backup-Ordner**
- Wähle Backup-Verzeichnis
- Standard: `/storage/emulated/0/bgo_backups/`
- SAF (Storage Access Framework) Picker

**Screen 6: Root & Berechtigungen**
- Fordere Root-Zugriff an
- Prüfe `/data/data/` Berechtigungen
- Speichere UID von Monopoly Go (falls installiert)

**Screen 7: Fertig**
- Zeige Zusammenfassung
- Button: "App starten"

---

## 3. Detaillierte Onboarding-Implementierung

### 3.1 OnboardingViewModel.kt

```kotlin
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val appStateRepository: AppStateRepository,
    private val rootUtil: RootUtil,
    private val fileUtil: FileUtil,
    private val importUtil: ImportUtil
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    data class OnboardingUiState(
        val currentStep: OnboardingStep = OnboardingStep.WELCOME,
        val importZipFound: Boolean = false,
        val importZipPath: String? = null,
        val prefix: String = "",
        val sshEnabled: Boolean = false,
        val sshHost: String = "",
        val sshPort: String = "22",
        val sshUsername: String = "",
        val sshPassword: String = "",
        val backupDirectory: String = "/storage/emulated/0/bgo_backups/",
        val rootAccessGranted: Boolean = false,
        val dataDataPermissionsChecked: Boolean = false,
        val monopolyGoInstalled: Boolean = false,
        val monopolyGoUid: Int? = null,
        val isLoading: Boolean = false,
        val error: String? = null
    )

    enum class OnboardingStep {
        WELCOME,
        IMPORT_CHECK,
        PREFIX_SETUP,
        SSH_SETUP,
        BACKUP_DIRECTORY,
        ROOT_PERMISSIONS,
        COMPLETE
    }

    init {
        checkImportZip()
    }

    // ============================================================
    // Screen 2: Import-Check
    // ============================================================

    private fun checkImportZip() {
        viewModelScope.launch {
            val importPath = "/storage/emulated/0/Download/bgo_accounts_import.zip"
            val exists = fileUtil.fileExists(importPath)

            _uiState.update { 
                it.copy(
                    importZipFound = exists,
                    importZipPath = if (exists) importPath else null
                ) 
            }
        }
    }

    fun importFromZip() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            try {
                val zipPath = _uiState.value.importZipPath ?: return@launch
                val result = importUtil.importFromZip(zipPath)

                if (result.isSuccess) {
                    // Import erfolgreich → gehe weiter
                    _uiState.update { 
                        it.copy(
                            isLoading = false,
                            error = null
                        ) 
                    }
                    nextStep()
                } else {
                    _uiState.update { 
                        it.copy(
                            isLoading = false,
                            error = "Import fehlgeschlagen: ${result.exceptionOrNull()?.message}"
                        ) 
                    }
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        error = "Fehler beim Import: ${e.message}"
                    ) 
                }
            }
        }
    }

    fun skipImport() {
        nextStep()
    }

    // ============================================================
    // Screen 3: Präfix-Setup
    // ============================================================

    fun onPrefixChanged(prefix: String) {
        _uiState.update { it.copy(prefix = prefix) }
    }

    fun savePrefixAndContinue() {
        viewModelScope.launch {
            appStateRepository.setDefaultPrefix(_uiState.value.prefix)
            nextStep()
        }
    }

    fun skipPrefix() {
        nextStep()
    }

    // ============================================================
    // Screen 4: SSH-Setup
    // ============================================================

    fun onSshEnabledChanged(enabled: Boolean) {
        _uiState.update { it.copy(sshEnabled = enabled) }
    }

    fun onSshHostChanged(host: String) {
        _uiState.update { it.copy(sshHost = host) }
    }

    fun onSshPortChanged(port: String) {
        _uiState.update { it.copy(sshPort = port) }
    }

    fun onSshUsernameChanged(username: String) {
        _uiState.update { it.copy(sshUsername = username) }
    }

    fun onSshPasswordChanged(password: String) {
        _uiState.update { it.copy(sshPassword = password) }
    }

    fun saveSshAndContinue() {
        viewModelScope.launch {
            if (_uiState.value.sshEnabled) {
                // Validierung
                if (_uiState.value.sshHost.isBlank() || 
                    _uiState.value.sshUsername.isBlank() ||
                    _uiState.value.sshPassword.isBlank()) {
                    _uiState.update { 
                        it.copy(error = "Bitte fülle alle SSH-Felder aus") 
                    }
                    return@launch
                }

                // Speichere SSH-Daten
                appStateRepository.setSshConfig(
                    enabled = true,
                    host = _uiState.value.sshHost,
                    port = _uiState.value.sshPort.toIntOrNull() ?: 22,
                    username = _uiState.value.sshUsername,
                    password = _uiState.value.sshPassword  // TODO: Verschlüsseln!
                )
            }
            nextStep()
        }
    }

    fun skipSsh() {
        nextStep()
    }

    // ============================================================
    // Screen 5: Backup-Ordner
    // ============================================================

    fun onBackupDirectoryChanged(directory: String) {
        _uiState.update { it.copy(backupDirectory = directory) }
    }

    fun saveBackupDirectoryAndContinue() {
        viewModelScope.launch {
            // Erstelle Ordner falls nicht vorhanden
            val result = fileUtil.createDirectory(_uiState.value.backupDirectory)

            if (result.isSuccess) {
                appStateRepository.setBackupDirectory(_uiState.value.backupDirectory)
                nextStep()
            } else {
                _uiState.update { 
                    it.copy(error = "Fehler beim Erstellen des Ordners: ${result.exceptionOrNull()?.message}") 
                }
            }
        }
    }

    // ============================================================
    // Screen 6: Root & Berechtigungen
    // ============================================================

    fun requestRootAccess() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            // 1. Root-Zugriff prüfen
            val rootGranted = rootUtil.requestRootAccess()

            if (!rootGranted) {
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        error = "Root-Zugriff verweigert. Die App benötigt Root-Rechte."
                    ) 
                }
                return@launch
            }

            // 2. Prüfe /data/data/ Berechtigungen
            val dataDataAccessible = checkDataDataPermissions()

            // 3. Prüfe Monopoly Go Installation + UID
            val (installed, uid) = checkMonopolyGo()

            // 4. Speichere Ergebnisse
            appStateRepository.setSystemStatus(
                rootGranted = rootGranted,
                dataDataPermissions = dataDataAccessible,
                monopolyGoInstalled = installed,
                monopolyGoUid = uid
            )

            _uiState.update { 
                it.copy(
                    isLoading = false,
                    rootAccessGranted = rootGranted,
                    dataDataPermissionsChecked = dataDataAccessible,
                    monopolyGoInstalled = installed,
                    monopolyGoUid = uid
                ) 
            }

            nextStep()
        }
    }

    private suspend fun checkDataDataPermissions(): Boolean {
        return withContext(Dispatchers.IO) {
            // Prüfe Lese-/Schreibrechte auf /data/data/
            val testPath = "/data/data/com.scopely.monopolygo"
            rootUtil.executeCommand("ls $testPath").isSuccess
        }
    }

    private suspend fun checkMonopolyGo(): Pair<Boolean, Int?> {
        return withContext(Dispatchers.IO) {
            // Prüfe ob Monopoly Go installiert ist
            val packageName = "com.scopely.monopolygo"

            // Methode 1: Via PackageManager
            val installed = try {
                val pm = context.packageManager
                pm.getPackageInfo(packageName, 0)
                true
            } catch (e: Exception) {
                false
            }

            // Methode 2: UID via Root ermitteln
            val uid = if (installed) {
                val result = rootUtil.executeCommand("stat -c '%u' /data/data/$packageName")
                result.getOrNull()?.trim()?.toIntOrNull()
            } else {
                null
            }

            Pair(installed, uid)
        }
    }

    // ============================================================
    // Screen 7: Fertig
    // ============================================================

    fun completeOnboarding() {
        viewModelScope.launch {
            appStateRepository.setOnboardingCompleted(true)
            _uiState.update { it.copy(currentStep = OnboardingStep.COMPLETE) }
        }
    }

    // ============================================================
    // Navigation
    // ============================================================

    fun nextStep() {
        _uiState.update { state ->
            val nextStep = when (state.currentStep) {
                OnboardingStep.WELCOME -> OnboardingStep.IMPORT_CHECK
                OnboardingStep.IMPORT_CHECK -> OnboardingStep.PREFIX_SETUP
                OnboardingStep.PREFIX_SETUP -> OnboardingStep.SSH_SETUP
                OnboardingStep.SSH_SETUP -> OnboardingStep.BACKUP_DIRECTORY
                OnboardingStep.BACKUP_DIRECTORY -> OnboardingStep.ROOT_PERMISSIONS
                OnboardingStep.ROOT_PERMISSIONS -> OnboardingStep.COMPLETE
                OnboardingStep.COMPLETE -> OnboardingStep.COMPLETE
            }
            state.copy(currentStep = nextStep)
        }
    }

    fun previousStep() {
        _uiState.update { state ->
            val prevStep = when (state.currentStep) {
                OnboardingStep.WELCOME -> OnboardingStep.WELCOME
                OnboardingStep.IMPORT_CHECK -> OnboardingStep.WELCOME
                OnboardingStep.PREFIX_SETUP -> OnboardingStep.IMPORT_CHECK
                OnboardingStep.SSH_SETUP -> OnboardingStep.PREFIX_SETUP
                OnboardingStep.BACKUP_DIRECTORY -> OnboardingStep.SSH_SETUP
                OnboardingStep.ROOT_PERMISSIONS -> OnboardingStep.BACKUP_DIRECTORY
                OnboardingStep.COMPLETE -> OnboardingStep.ROOT_PERMISSIONS
            }
            state.copy(currentStep = prevStep)
        }
    }
}
```

---

## 4. Normaler App-Start - System-Checks

### 4.1 SystemCheckViewModel.kt

```kotlin
@HiltViewModel
class SystemCheckViewModel @Inject constructor(
    private val appStateRepository: AppStateRepository,
    private val rootUtil: RootUtil,
    private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(SystemCheckUiState())
    val uiState: StateFlow<SystemCheckUiState> = _uiState.asStateFlow()

    data class SystemCheckUiState(
        val isChecking: Boolean = true,
        val checks: List<SystemCheck> = emptyList(),
        val allChecksPassed: Boolean = false,
        val criticalError: String? = null
    )

    data class SystemCheck(
        val id: String,
        val title: String,
        val status: CheckStatus,
        val message: String? = null
    )

    enum class CheckStatus {
        PENDING,    // Noch nicht geprüft
        CHECKING,   // Wird gerade geprüft
        PASSED,     // Erfolgreich
        WARNING,    // Warnung (nicht kritisch)
        FAILED      // Fehlgeschlagen (kritisch)
    }

    init {
        performSystemChecks()
    }

    fun performSystemChecks() {
        viewModelScope.launch {
            _uiState.update { it.copy(isChecking = true) }

            val checks = mutableListOf<SystemCheck>()

            // Check 1: Root-Zugriff
            checks.add(SystemCheck(
                id = "root",
                title = "Root-Zugriff",
                status = CheckStatus.CHECKING
            ))
            _uiState.update { it.copy(checks = checks.toList()) }

            val rootGranted = rootUtil.isRootAvailable()
            checks[0] = checks[0].copy(
                status = if (rootGranted) CheckStatus.PASSED else CheckStatus.FAILED,
                message = if (rootGranted) "Root-Zugriff verfügbar" else "Root-Zugriff verweigert"
            )
            _uiState.update { it.copy(checks = checks.toList()) }

            if (!rootGranted) {
                _uiState.update { 
                    it.copy(
                        isChecking = false,
                        allChecksPassed = false,
                        criticalError = "Root-Zugriff erforderlich!"
                    ) 
                }
                return@launch
            }

            // Check 2: Monopoly Go Installation
            checks.add(SystemCheck(
                id = "monopoly_go",
                title = "Monopoly Go",
                status = CheckStatus.CHECKING
            ))
            _uiState.update { it.copy(checks = checks.toList()) }

            val (installed, hasChanged) = checkMonopolyGoStatus()
            checks[1] = checks[1].copy(
                status = if (installed) CheckStatus.PASSED else CheckStatus.WARNING,
                message = when {
                    installed && hasChanged -> "Installation erkannt (UID aktualisiert)"
                    installed -> "Installiert"
                    else -> "Nicht installiert (optional)"
                }
            )
            _uiState.update { it.copy(checks = checks.toList()) }

            // Check 3: UID-Aktualisierung (falls Monopoly Go installiert)
            if (installed) {
                checks.add(SystemCheck(
                    id = "uid",
                    title = "UID-Status",
                    status = CheckStatus.CHECKING
                ))
                _uiState.update { it.copy(checks = checks.toList()) }

                val uidCheck = updateMonopolyGoUid()
                checks[2] = checks[2].copy(
                    status = if (uidCheck) CheckStatus.PASSED else CheckStatus.FAILED,
                    message = if (uidCheck) "UID gespeichert" else "UID konnte nicht ermittelt werden"
                )
                _uiState.update { it.copy(checks = checks.toList()) }
            }

            // Check 4: Backup-Verzeichnis
            checks.add(SystemCheck(
                id = "backup_dir",
                title = "Backup-Verzeichnis",
                status = CheckStatus.CHECKING
            ))
            _uiState.update { it.copy(checks = checks.toList()) }

            val backupDirAccessible = checkBackupDirectory()
            checks[checks.size - 1] = checks.last().copy(
                status = if (backupDirAccessible) CheckStatus.PASSED else CheckStatus.WARNING,
                message = if (backupDirAccessible) "Zugriff OK" else "Verzeichnis nicht erreichbar"
            )
            _uiState.update { it.copy(checks = checks.toList()) }

            // Check 5: /data/data Berechtigungen
            checks.add(SystemCheck(
                id = "data_data",
                title = "/data/data Zugriff",
                status = CheckStatus.CHECKING
            ))
            _uiState.update { it.copy(checks = checks.toList()) }

            val dataDataAccess = checkDataDataAccess()
            checks[checks.size - 1] = checks.last().copy(
                status = if (dataDataAccess) CheckStatus.PASSED else CheckStatus.FAILED,
                message = if (dataDataAccess) "Zugriff OK" else "Zugriff verweigert"
            )
            _uiState.update { it.copy(checks = checks.toList()) }

            // Finale Bewertung
            val allPassed = checks.none { it.status == CheckStatus.FAILED }

            _uiState.update { 
                it.copy(
                    isChecking = false,
                    allChecksPassed = allPassed,
                    criticalError = if (!allPassed) "Einige kritische Checks fehlgeschlagen" else null
                ) 
            }

            // Speichere Zeitstempel
            appStateRepository.setLastSystemCheckTimestamp(System.currentTimeMillis())
        }
    }

    // ============================================================
    // Check-Funktionen
    // ============================================================

    private suspend fun checkMonopolyGoStatus(): Pair<Boolean, Boolean> {
        return withContext(Dispatchers.IO) {
            val packageName = "com.scopely.monopolygo"

            // Ist installiert?
            val installed = try {
                context.packageManager.getPackageInfo(packageName, 0)
                true
            } catch (e: Exception) {
                false
            }

            // Hat sich Status geändert?
            val previousStatus = appStateRepository.getMonopolyGoInstalled()
            val hasChanged = previousStatus != installed

            // Speichere neuen Status
            appStateRepository.setMonopolyGoInstalled(installed)

            Pair(installed, hasChanged)
        }
    }

    private suspend fun updateMonopolyGoUid(): Boolean {
        return withContext(Dispatchers.IO) {
            val packageName = "com.scopely.monopolygo"

            // Ermittle UID via Root
            val result = rootUtil.executeCommand("stat -c '%u' /data/data/$packageName")
            val uid = result.getOrNull()?.trim()?.toIntOrNull()

            if (uid != null) {
                // Prüfe ob UID sich geändert hat
                val previousUid = appStateRepository.getMonopolyGoUid()
                if (previousUid != uid) {
                    // UID hat sich geändert → speichern
                    appStateRepository.setMonopolyGoUid(uid)

                    // Optional: Log-Eintrag
                    logRepository.log(
                        operation = "UID_CHANGED",
                        message = "Monopoly Go UID aktualisiert: $previousUid → $uid"
                    )
                }
                true
            } else {
                false
            }
        }
    }

    private suspend fun checkBackupDirectory(): Boolean {
        return withContext(Dispatchers.IO) {
            val backupDir = appStateRepository.getBackupDirectory()

            if (backupDir == null) {
                false
            } else {
                // Prüfe ob Verzeichnis existiert und beschreibbar ist
                val dirExists = File(backupDir).exists()
                val canWrite = File(backupDir).canWrite()

                dirExists && canWrite
            }
        }
    }

    private suspend fun checkDataDataAccess(): Boolean {
        return withContext(Dispatchers.IO) {
            val testPath = "/data/data/com.scopely.monopolygo"
            rootUtil.executeCommand("ls $testPath").isSuccess
        }
    }

    // ============================================================
    // Retry & Continue
    // ============================================================

    fun retryChecks() {
        performSystemChecks()
    }

    fun continueToApp() {
        // Navigation zur Home-Screen erfolgt in der Activity/NavHost
    }
}
```

---

## 5. Compose UI - Onboarding Screens

### 5.1 OnboardingScreen.kt (Haupt-Container)

```kotlin
@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel = hiltViewModel(),
    onComplete: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    // Wenn Onboarding abgeschlossen → Navigate
    LaunchedEffect(uiState.currentStep) {
        if (uiState.currentStep == OnboardingViewModel.OnboardingStep.COMPLETE) {
            delay(500)  // Kurze Verzögerung für Animationen
            onComplete()
        }
    }

    Scaffold { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (uiState.currentStep) {
                OnboardingViewModel.OnboardingStep.WELCOME -> 
                    WelcomeScreen(onNext = { viewModel.nextStep() })

                OnboardingViewModel.OnboardingStep.IMPORT_CHECK -> 
                    ImportCheckScreen(
                        zipFound = uiState.importZipFound,
                        zipPath = uiState.importZipPath,
                        isLoading = uiState.isLoading,
                        error = uiState.error,
                        onImport = { viewModel.importFromZip() },
                        onSkip = { viewModel.skipImport() }
                    )

                OnboardingViewModel.OnboardingStep.PREFIX_SETUP -> 
                    PrefixSetupScreen(
                        prefix = uiState.prefix,
                        onPrefixChanged = { viewModel.onPrefixChanged(it) },
                        onSave = { viewModel.savePrefixAndContinue() },
                        onSkip = { viewModel.skipPrefix() }
                    )

                OnboardingViewModel.OnboardingStep.SSH_SETUP -> 
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
                        onSkip = { viewModel.skipSsh() }
                    )

                OnboardingViewModel.OnboardingStep.BACKUP_DIRECTORY -> 
                    BackupDirectoryScreen(
                        directory = uiState.backupDirectory,
                        onDirectoryChanged = { viewModel.onBackupDirectoryChanged(it) },
                        onSave = { viewModel.saveBackupDirectoryAndContinue() }
                    )

                OnboardingViewModel.OnboardingStep.ROOT_PERMISSIONS -> 
                    RootPermissionsScreen(
                        isLoading = uiState.isLoading,
                        rootGranted = uiState.rootAccessGranted,
                        dataDataChecked = uiState.dataDataPermissionsChecked,
                        monopolyGoInstalled = uiState.monopolyGoInstalled,
                        monopolyGoUid = uiState.monopolyGoUid,
                        error = uiState.error,
                        onRequestRoot = { viewModel.requestRootAccess() }
                    )

                OnboardingViewModel.OnboardingStep.COMPLETE -> 
                    CompleteScreen(
                        onFinish = { viewModel.completeOnboarding() }
                    )
            }

            // Progress Indicator (oben)
            LinearProgressIndicator(
                progress = getProgressForStep(uiState.currentStep),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
                color = Color(0xFF6200EE)
            )
        }
    }
}

fun getProgressForStep(step: OnboardingViewModel.OnboardingStep): Float {
    return when (step) {
        OnboardingViewModel.OnboardingStep.WELCOME -> 0.0f
        OnboardingViewModel.OnboardingStep.IMPORT_CHECK -> 0.14f
        OnboardingViewModel.OnboardingStep.PREFIX_SETUP -> 0.28f
        OnboardingViewModel.OnboardingStep.SSH_SETUP -> 0.42f
        OnboardingViewModel.OnboardingStep.BACKUP_DIRECTORY -> 0.57f
        OnboardingViewModel.OnboardingStep.ROOT_PERMISSIONS -> 0.71f
        OnboardingViewModel.OnboardingStep.COMPLETE -> 1.0f
    }
}
```

### 5.2 Beispiel: RootPermissionsScreen.kt

```kotlin
@Composable
fun RootPermissionsScreen(
    isLoading: Boolean,
    rootGranted: Boolean,
    dataDataChecked: Boolean,
    monopolyGoInstalled: Boolean,
    monopolyGoUid: Int?,
    error: String?,
    onRequestRoot: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_root),
            contentDescription = null,
            modifier = Modifier
                .size(80.dp)
                .align(Alignment.CenterHorizontally),
            tint = Color(0xFF6200EE)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Root-Berechtigungen",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Die App benötigt Root-Zugriff, um Backups zu erstellen und wiederherzustellen.",
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            color = Color(0xFF666666),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Status-Liste
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
                    subtitle = "Nicht installiert (optional)"
                )
            }
        }

        // Error
        error?.let {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = it,
                color = Color.Red,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Button
        Button(
            onClick = onRequestRoot,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading && !rootGranted,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF6200EE)
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = Color.White
                )
            } else {
                Text(
                    text = if (rootGranted) "Weiter" else "Root-Zugriff anfordern",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
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
            tint = if (checked) Color(0xFF4CAF50) else Color(0xFF666666),
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
                    color = Color(0xFF666666)
                )
            }
        }
    }
}
```

---

## 6. Compose UI - System Check Screen

### 6.1 SystemCheckScreen.kt

```kotlin
@Composable
fun SystemCheckScreen(
    viewModel: SystemCheckViewModel = hiltViewModel(),
    onComplete: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    // Wenn alle Checks erfolgreich → automatisch weiter
    LaunchedEffect(uiState.allChecksPassed, uiState.isChecking) {
        if (uiState.allChecksPassed && !uiState.isChecking) {
            delay(1000)  // Zeige Erfolg kurz an
            onComplete()
        }
    }

    Scaffold { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFFF5F5F5))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(48.dp))

                // App-Logo
                Icon(
                    painter = painterResource(R.drawable.ic_app_logo),
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = Color(0xFF6200EE)
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "babixGO",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF6200EE)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (uiState.isChecking) "System wird geprüft..." else "System-Check abgeschlossen",
                    fontSize = 16.sp,
                    color = Color(0xFF666666)
                )

                Spacer(modifier = Modifier.height(48.dp))

                // Check-Liste
                LazyColumn(
                    modifier = Modifier.weight(1f)
                ) {
                    items(uiState.checks) { check ->
                        SystemCheckItem(check = check)
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Error oder Continue Button
                if (uiState.criticalError != null) {
                    Text(
                        text = uiState.criticalError!!,
                        color = Color.Red,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    Button(
                        onClick = { viewModel.retryChecks() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF6200EE)
                        )
                    ) {
                        Text("Erneut versuchen")
                    }
                } else if (!uiState.isChecking) {
                    Button(
                        onClick = onComplete,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4CAF50)
                        )
                    ) {
                        Text("App starten")
                    }
                }
            }
        }
    }
}

@Composable
fun SystemCheckItem(check: SystemCheckViewModel.SystemCheck) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status-Icon
            when (check.status) {
                SystemCheckViewModel.CheckStatus.PENDING -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                }
                SystemCheckViewModel.CheckStatus.CHECKING -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = Color(0xFF6200EE)
                    )
                }
                SystemCheckViewModel.CheckStatus.PASSED -> {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(24.dp)
                    )
                }
                SystemCheckViewModel.CheckStatus.WARNING -> {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFFFF9800),
                        modifier = Modifier.size(24.dp)
                    )
                }
                SystemCheckViewModel.CheckStatus.FAILED -> {
                    Icon(
                        Icons.Default.Error,
                        contentDescription = null,
                        tint = Color(0xFFF44336),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = check.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )

                check.message?.let {
                    Text(
                        text = it,
                        fontSize = 14.sp,
                        color = Color(0xFF666666),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}
```

---

## 7. MainActivity & Navigation

### 7.1 MainActivity.kt

```kotlin
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var appStateRepository: AppStateRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            BabixGoTheme {
                val navController = rememberNavController()
                var startDestination by remember { mutableStateOf<String?>(null) }

                // Bestimme Start-Destination
                LaunchedEffect(Unit) {
                    startDestination = determineStartDestination()
                }

                startDestination?.let { destination ->
                    NavHost(
                        navController = navController,
                        startDestination = destination
                    ) {
                        composable("splash") {
                            SplashScreen()
                        }

                        composable("onboarding") {
                            OnboardingScreen(
                                onComplete = {
                                    navController.navigate("systemCheck") {
                                        popUpTo("onboarding") { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable("systemCheck") {
                            SystemCheckScreen(
                                onComplete = {
                                    navController.navigate("home") {
                                        popUpTo("systemCheck") { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable("home") {
                            AccountsScreen(navController = navController)
                        }

                        // Weitere Screens...
                    }
                }
            }
        }
    }

    private suspend fun determineStartDestination(): String {
        val isFirstLaunch = appStateRepository.isFirstLaunch()
        val onboardingCompleted = appStateRepository.isOnboardingCompleted()

        return when {
            isFirstLaunch || !onboardingCompleted -> "onboarding"
            else -> "systemCheck"
        }
    }
}
```

---

## 8. AppStateRepository Implementation

### 8.1 AppStateRepository.kt

```kotlin
class AppStateRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    companion object {
        // Onboarding
        val IS_FIRST_LAUNCH = booleanPreferencesKey("is_first_launch")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")

        // Setup
        val DEFAULT_PREFIX = stringPreferencesKey("default_prefix")
        val BACKUP_DIRECTORY = stringPreferencesKey("backup_directory")

        // SSH
        val SSH_ENABLED = booleanPreferencesKey("ssh_enabled")
        val SSH_HOST = stringPreferencesKey("ssh_host")
        val SSH_PORT = intPreferencesKey("ssh_port")
        val SSH_USERNAME = stringPreferencesKey("ssh_username")
        val SSH_PASSWORD = stringPreferencesKey("ssh_password")  // TODO: Verschlüsseln!

        // System
        val MONOPOLY_GO_UID = intPreferencesKey("monopoly_go_uid")
        val MONOPOLY_GO_INSTALLED = booleanPreferencesKey("monopoly_go_installed")
        val ROOT_ACCESS_GRANTED = booleanPreferencesKey("root_access_granted")
        val DATA_DATA_PERMISSIONS = booleanPreferencesKey("data_data_permissions")
        val LAST_SYSTEM_CHECK = longPreferencesKey("last_system_check")
    }

    // Onboarding
    suspend fun isFirstLaunch(): Boolean {
        return dataStore.data.first()[IS_FIRST_LAUNCH] ?: true
    }

    suspend fun isOnboardingCompleted(): Boolean {
        return dataStore.data.first()[ONBOARDING_COMPLETED] ?: false
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { it[ONBOARDING_COMPLETED] = completed }
        if (completed) {
            dataStore.edit { it[IS_FIRST_LAUNCH] = false }
        }
    }

    // Setup
    suspend fun setDefaultPrefix(prefix: String) {
        dataStore.edit { it[DEFAULT_PREFIX] = prefix }
    }

    suspend fun getDefaultPrefix(): String? {
        return dataStore.data.first()[DEFAULT_PREFIX]
    }

    suspend fun setBackupDirectory(directory: String) {
        dataStore.edit { it[BACKUP_DIRECTORY] = directory }
    }

    suspend fun getBackupDirectory(): String? {
        return dataStore.data.first()[BACKUP_DIRECTORY]
    }

    // SSH
    suspend fun setSshConfig(
        enabled: Boolean,
        host: String,
        port: Int,
        username: String,
        password: String
    ) {
        dataStore.edit { prefs ->
            prefs[SSH_ENABLED] = enabled
            prefs[SSH_HOST] = host
            prefs[SSH_PORT] = port
            prefs[SSH_USERNAME] = username
            prefs[SSH_PASSWORD] = password  // TODO: Verschlüsseln!
        }
    }

    // System
    suspend fun setSystemStatus(
        rootGranted: Boolean,
        dataDataPermissions: Boolean,
        monopolyGoInstalled: Boolean,
        monopolyGoUid: Int?
    ) {
        dataStore.edit { prefs ->
            prefs[ROOT_ACCESS_GRANTED] = rootGranted
            prefs[DATA_DATA_PERMISSIONS] = dataDataPermissions
            prefs[MONOPOLY_GO_INSTALLED] = monopolyGoInstalled
            monopolyGoUid?.let { prefs[MONOPOLY_GO_UID] = it }
        }
    }

    suspend fun getMonopolyGoUid(): Int? {
        return dataStore.data.first()[MONOPOLY_GO_UID]
    }

    suspend fun setMonopolyGoUid(uid: Int) {
        dataStore.edit { it[MONOPOLY_GO_UID] = uid }
    }

    suspend fun getMonopolyGoInstalled(): Boolean {
        return dataStore.data.first()[MONOPOLY_GO_INSTALLED] ?: false
    }

    suspend fun setMonopolyGoInstalled(installed: Boolean) {
        dataStore.edit { it[MONOPOLY_GO_INSTALLED] = installed }
    }

    suspend fun setLastSystemCheckTimestamp(timestamp: Long) {
        dataStore.edit { it[LAST_SYSTEM_CHECK] = timestamp }
    }

    suspend fun getLastSystemCheckTimestamp(): Long {
        return dataStore.data.first()[LAST_SYSTEM_CHECK] ?: 0L
    }
}
```

---

## 9. Import-Utility

### 9.1 ImportUtil.kt

```kotlin
class ImportUtil @Inject constructor(
    private val accountRepository: AccountRepository,
    private val context: Context
) {

    suspend fun importFromZip(zipPath: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                // 1. ZIP entpacken
                val tempDir = File(context.cacheDir, "import_temp")
                tempDir.mkdirs()

                ZipInputStream(FileInputStream(zipPath)).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val file = File(tempDir, entry.name)
                        if (entry.isDirectory) {
                            file.mkdirs()
                        } else {
                            file.outputStream().use { output ->
                                zis.copyTo(output)
                            }
                        }
                        entry = zis.nextEntry
                    }
                }

                // 2. accounts.json einlesen
                val accountsFile = File(tempDir, "accounts.json")
                if (!accountsFile.exists()) {
                    return@withContext Result.failure(Exception("accounts.json nicht gefunden"))
                }

                val json = accountsFile.readText()
                val accounts = Json.decodeFromString<List<AccountEntity>>(json)

                // 3. Accounts in DB importieren
                accounts.forEach { account ->
                    accountRepository.insertAccount(account)
                }

                // 4. Aufräumen
                tempDir.deleteRecursively()

                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}
```

---

## 10. Testing-Checkliste

### Unit Tests
- [ ] OnboardingViewModel: Alle Steps funktionieren
- [ ] OnboardingViewModel: Präfix wird gespeichert
- [ ] OnboardingViewModel: SSH-Daten werden gespeichert
- [ ] SystemCheckViewModel: Root-Check funktioniert
- [ ] SystemCheckViewModel: UID-Update funktioniert
- [ ] AppStateRepository: Alle Getter/Setter funktionieren
- [ ] ImportUtil: ZIP-Import funktioniert

### Integration Tests
- [ ] Onboarding-Flow: Kompletter Durchlauf
- [ ] System-Checks: Alle Checks durchlaufen
- [ ] Navigation: Von Splash → Onboarding → SystemCheck → Home
- [ ] DataStore: Persistenz nach App-Neustart

### UI Tests
- [ ] Onboarding: Alle Screens sind bedienbar
- [ ] Onboarding: Progress-Bar zeigt korrekte Werte
- [ ] SystemCheck: Checks werden angezeigt
- [ ] SystemCheck: Retry funktioniert
- [ ] SystemCheck: Auto-Navigation bei Erfolg

---

## 11. Akzeptanzkriterien

✅ **Erster Start (Onboarding)**:
- [ ] Willkommens-Screen wird angezeigt
- [ ] Import-Check erkennt ZIP-Dateien
- [ ] Präfix wird gespeichert
- [ ] SSH-Setup ist optional
- [ ] Backup-Verzeichnis wird erstellt
- [ ] Root-Zugriff wird erfolgreich angefordert
- [ ] Monopoly Go UID wird erkannt (falls installiert)
- [ ] Onboarding kann komplett durchlaufen werden

✅ **Normaler Start (System-Checks)**:
- [ ] Root-Zugriff wird geprüft
- [ ] Monopoly Go Installation wird erkannt
- [ ] UID wird aktualisiert (falls geändert)
- [ ] Backup-Verzeichnis ist erreichbar
- [ ] /data/data Zugriff funktioniert
- [ ] Bei Erfolg: Auto-Navigation zur Home-Screen
- [ ] Bei Fehler: Retry-Option verfügbar

✅ **Allgemein**:
- [ ] Keine Crashes
- [ ] Alle Daten werden persistent gespeichert
- [ ] UI ist responsiv und flüssig

---

## 12. Offene Fragen

1. **SSH-Passwort**: Soll das Passwort verschlüsselt werden? Wenn ja, welche Verschlüsselungsmethode? (z.B. Android Keystore)
2. **Import-ZIP Format**: Welches Format hat die ZIP-Datei genau? (accounts.json + Backup-Ordner?)
3. **Backup-Verzeichnis**: Soll SAF (Storage Access Framework) verwendet werden, oder reicht ein einfacher Pfad?
4. **System-Check Häufigkeit**: Wie oft sollen System-Checks durchgeführt werden? (Jeder Start, täglich, wöchentlich?)
5. **UID-Änderung**: Was soll passieren, wenn sich die UID ändert? (Nur Log, oder User-Benachrichtigung?)

---

## 13) Tests

Schreibe umfangreiche instrumentierte Unit- und UI‑Tests für alle oben genannten Akzeptanzkriterien und Szenarien (Onboarding, normaler Start). Simuliere Root‑Checks, Monopoly GO‑Installation, UID‑Änderungen und Fehlerfälle. Verwende Mocking/Stubbing, insbesondere für libsu und DataStore, um sowohl Erfolgs‑ als auch Fehlersituationen zu prüfen. Die Tests müssen sicherstellen, dass der Splash‑Screen korrekt angezeigt wird, die App‑Start‑Gate‑Logik funktioniert und alle System‑Checks zuverlässig greifen. Alle Tests werden automatisch in der CI/CD‑Pipeline ausgeführt und müssen ohne Fehler bestehen.

Wenn alles umgesetzt ist und alle Tests bestanden sind, **lies die Datei `Rebuild_P2_Startseite.md` im Verzeichnis `docs/prompts`** und führe alle darin beschriebenen Aufgaben aus.

**Ende des Prompts**
