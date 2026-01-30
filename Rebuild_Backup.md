# Rebuild Backup-Seite - bGO Account Manager v2.0

## Kontext
Du bist ein Android-Entwickler und arbeitest an der **Version 2.0** der "bGO Account Manager" App. Diese Aufgabe fokussiert sich auf die **Backup-Seite** mit zwei Hauptfunktionen:
1. **Account sichern** (Backup eines bestehenden Accounts)
2. **Account erstellen** (Neuen Account anlegen)

Die App basiert auf:
- **Kotlin** mit **MVVM-Architektur**
- **Jetpack Compose** + **Material3**
- **Hilt** (Dependency Injection)
- **Room Database** + **DataStore**
- **libsu** (Root-Zugriff via TopJohnWu)

Repository: `github.com/Orga-bgo/bgo-accounts`

---

## 1. Screen-Struktur

### 1.1 Header (wie auf Accounts-Screen)
```
┌─────────────────────────────────────┐
│ babixGO          [🔄 Refresh] [❓ Help] │  ← Hauptheader (lila #6200EE)
│ Backup                              │  ← Sub-Header (heller lila #7C3FEE)
└─────────────────────────────────────┘
```

### 1.2 Tab-Ansicht (Material3 Tabs)
```
┌─────────────────────────────────────┐
│  Account sichern  │  Account erstellen │
└─────────────────────────────────────┘
```

- **Tab 1**: Account sichern (Backup-Flow)
- **Tab 2**: Account erstellen (Neu-Erstellung)
- Aktiver Tab: Lila (#6200EE)
- Inaktiver Tab: Grau (#757575)

---

## 2. Tab 1: Account sichern - Workflow

### 2.1 Workflow-Übersicht
```
Schritt 1: Name eingeben
    ↓
Schritt 2: Social Media Verbindung?
    ↓
├─ JA → Schritt 2.1: Facebook oder Google wählen
│           ↓
│       ├─ Facebook → Schritt 2.2.1: FB-Daten eingeben
│       └─ Google   → Schritt 2.2.2: GO-Daten eingeben
│
└─ NEIN → Weiter zu Schritt 3
    ↓
Schritt 3: Backup durchführen (mit Progress)
    ↓
Schritt 4: Daten extrahieren & in DB schreiben
    ↓
Schritt 5: Android ID auslesen
    ↓
Schritt 6: Erfolgsmeldung + Aktionen
```

### 2.2 Schritt-Details

#### **Schritt 1: Name eingeben**
- Textfeld für Account-Name
- Validierung: Name darf nicht leer sein
- Zeige Präfix-Vorschlag (aus Settings)
- Button: "Weiter"

#### **Schritt 2: Social Media Verbindung**
- Frage: "Ist der Account mit Facebook oder Google verbunden?"
- Radio Buttons: [ ] Ja  [ ] Nein
- Bei "Nein": Springe zu Schritt 3
- Bei "Ja": Zeige Schritt 2.1

#### **Schritt 2.1: Platform wählen**
- Radio Buttons: [ ] Facebook  [ ] Google
- **Nur eine Auswahl möglich**
- Button: "Weiter"

#### **Schritt 2.2.1: Facebook-Daten eingeben**
- **Felder:**
  - FB-ID (String)
  - FB-Passwort (Password, verschlüsselt speichern)
  - FB-E-Mail (Email)
  - FB-2FA-Code (String, optional)

- **Datenbank-Felder:**
  - `social_platform: String` → "facebook"
  - `social_id: String` → FB-ID
  - `social_password: String` → FB-Passwort (verschlüsselt)
  - `social_email: String` → FB-E-Mail
  - `social_2fa_code: String?` → FB-2FA-Code (nullable)

#### **Schritt 2.2.2: Google-Daten eingeben**
- **Felder:**
  - GO-ID (String)
  - GO-Passwort (Password, verschlüsselt speichern)
  - GO-E-Mail (Email)
  - GO-2FA-Code (String, optional)

- **Datenbank-Felder:**
  - `social_platform: String` → "google"
  - `social_id: String` → GO-ID
  - `social_password: String` → GO-Passwort (verschlüsselt)
  - `social_email: String` → GO-E-Mail
  - `social_2fa_code: String?` → GO-2FA-Code (nullable)

#### **Schritt 3: Backup durchführen**
- **Fortschrittsanzeige** (LinearProgressIndicator + Text)
- **Kopiere folgende Ordner/Dateien:**
  1. `/data/data/com.scopely.monopolygo/shared_prefs/` → `{backupDir}/{accountName}/shared_prefs/`
  2. `/data/data/com.scopely.monopolygo/cache/DiskBasedCacheDirectory/` → `{backupDir}/{accountName}/cache/`
  3. `/data/system/users/0/settings_ssaid.xml` → `{backupDir}/{accountName}/settings_ssaid.xml`

- **Progress-Text-Beispiele:**
  - "Kopiere shared_prefs..."
  - "Kopiere Cache-Verzeichnis..."
  - "Kopiere settings_ssaid.xml..."
  - "Backup abgeschlossen"

- **Root-Befehle:**
```bash
# Erstelle Backup-Ordner
mkdir -p {backupDir}/{accountName}/shared_prefs
mkdir -p {backupDir}/{accountName}/cache

# Kopiere Dateien
cp -r /data/data/com.scopely.monopolygo/shared_prefs/* {backupDir}/{accountName}/shared_prefs/
cp -r /data/data/com.scopely.monopolygo/cache/DiskBasedCacheDirectory/* {backupDir}/{accountName}/cache/
cp /data/system/users/0/settings_ssaid.xml {backupDir}/{accountName}/settings_ssaid.xml
```

#### **Schritt 4: Daten extrahieren**
- **Quelle:** `{backupDir}/{accountName}/shared_prefs/com.scopely.monopolygo.v2.playerprefs.xml`

- **Zu extrahierende Strings:**

**User ID:**
```xml
<string name="Scopely.Attribution.UserId">1140407373</string>
<string name="ScopelyProfile.UserId">1140407373</string>
```
→ Speichere als `userId: String`

**Device IDs:**
```xml
<string name="Scopely.Attribution.DeviceToken">198aab99-d769-4dc2-96f4-2fcf0f33fec4</string>
<string name="Scopely.Analytics.DeviceToken">198aab99-d769-4dc2-96f4-2fcf0f33fec4</string>
<string name="LastOpenedDeviceToken">198aab99-d769-4dc2-96f4-2fcf0f33fec4</string>
<string name="GoogleAdId">1bbae05f-b61e-47f0-b01f-d31601cd2a3c</string>
<string name="AppSetId">cb52ea67-ba06-dd6d-598a-441f19252c12</string>
<string name="Scopely.Analytics.PushToken">eESOgXn0SVmx0hlQ5YJ9F8%3AAPA91bGfSeT6TICTyc4FWnLeyh9pq35E0dPTWyVT5L5kksvXo9Pq2HSoOBDncn2_OYEzTdy91DP_YXeEYFeSDpmFECoeyeRvxLUQb00RnXeHPqkpJRbmfQM</string>
<string name="LastOpenedPushToken">eESOgXn0SVmx0hlQ5YJ9F8%3AAPA91bGfSeT6TICTyc4FWnLeyh9pq35E0dPTWyVT5L5kksvXo9Pq2HSoOBDncn2_OYEzTdy91DP_YXeEYFeSDpmFECoeyeRvxLUQb00RnXeHPqkpJRbmfQM</string>
```
→ Speichere als:
- `deviceToken: String`
- `googleAdId: String`
- `appSetId: String`
- `pushToken: String`

**Unity IDs:**
```xml
<string name="unity.cloud_userid">ce983305d1c2e25a5aee305f987d8cb1</string>
<string name="unity.player_sessionid">7553731180743279676</string>
<string name="unity.player_session_count">30</string>
```
→ Speichere als:
- `unityCloudUserId: String`
- `unityPlayerSessionId: String`
- `unityPlayerSessionCount: Int`

#### **Schritt 5: Android ID auslesen**
- **Verwende ABX → XML Konvertierung**

**Shell-Script Workflow:**
```bash
#!/system/bin/sh

INPUT_ABX="/data/system/users/0/settings_ssaid.xml"
TMP_XML="/data/local/tmp/settings_ssaid_decoded.xml"

# Root prüfen
if [ "$(id -u)" -ne 0 ]; then
    echo "Dieses Script muss als root ausgeführt werden."
    exit 1
fi

# Datei prüfen
if [ ! -f "$INPUT_ABX" ]; then
    echo "ABX-Datei nicht gefunden: $INPUT_ABX"
    exit 1
fi

echo "[*] Wandle ABX → XML um..."
abx2xml "$INPUT_ABX" "$TMP_XML"

if [ ! -f "$TMP_XML" ]; then
    echo "Fehler: XML wurde nicht erzeugt."
    exit 1
fi

echo "[*] Extrahiere SSAID für com.scopely.monopolygo..."

SSOID=$(grep -oP 'package="com\.scopely\.monopolygo"[^>]+value="\K[^"]+' "$TMP_XML")

if [ -z "$SSOID" ]; then
    echo "Keine SSAID für com.scopely.monopolygo gefunden."
    exit 1
fi

echo "[+] SSAID:"
echo "$SSOID"

# Cleanup
rm "$TMP_XML"
```

→ Speichere als `androidId: String` (SSAID-Wert)

#### **Schritt 6: Erfolgsmeldung**
- **UI:**
```
┌─────────────────────────────────────┐
│         ✅ Backup erfolgreich!        │
│                                     │
│  Account "{accountName}" wurde      │
│  erfolgreich gesichert.             │
│                                     │
│  [Account anzeigen]                 │
│  [Monopoly Go starten]              │
│  [Zur Übersicht]                    │
└─────────────────────────────────────┘
```

- **Buttons:**
  1. **Account anzeigen** → Navigate zu Account-Detail-Screen
  2. **Monopoly Go starten** → Starte `com.scopely.monopolygo` via Root
  3. **Zur Übersicht** → Navigate zu Accounts-Screen

---

## 3. Datenbank-Erweiterungen

### 3.1 Neue Felder in AccountEntity

```kotlin
@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // Bestehende Felder
    val accountName: String,
    val userId: String,
    val prefix: String,
    val createdAt: Long,
    val lastPlayedAt: Long,
    val susLevel: Int = 0,
    val hasError: Boolean = false,
    val friendshipLink: String? = null,
    val friendshipCode: String? = null,

    // NEU: Social Media Daten
    val socialPlatform: String? = null,      // "facebook" oder "google"
    val socialId: String? = null,
    val socialPassword: String? = null,       // Verschlüsselt!
    val socialEmail: String? = null,
    val social2faCode: String? = null,

    // NEU: Device IDs
    val deviceToken: String? = null,
    val googleAdId: String? = null,
    val appSetId: String? = null,
    val pushToken: String? = null,

    // NEU: Unity IDs
    val unityCloudUserId: String? = null,
    val unityPlayerSessionId: String? = null,
    val unityPlayerSessionCount: Int? = null,

    // NEU: Android ID (SSAID)
    val androidId: String? = null,

    // Backup-Pfad
    val backupPath: String? = null
)
```

### 3.2 Migration

```kotlin
val MIGRATION_X_Y = object : Migration(X, Y) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Social Media
        database.execSQL("ALTER TABLE accounts ADD COLUMN socialPlatform TEXT")
        database.execSQL("ALTER TABLE accounts ADD COLUMN socialId TEXT")
        database.execSQL("ALTER TABLE accounts ADD COLUMN socialPassword TEXT")
        database.execSQL("ALTER TABLE accounts ADD COLUMN socialEmail TEXT")
        database.execSQL("ALTER TABLE accounts ADD COLUMN social2faCode TEXT")

        // Device IDs
        database.execSQL("ALTER TABLE accounts ADD COLUMN deviceToken TEXT")
        database.execSQL("ALTER TABLE accounts ADD COLUMN googleAdId TEXT")
        database.execSQL("ALTER TABLE accounts ADD COLUMN appSetId TEXT")
        database.execSQL("ALTER TABLE accounts ADD COLUMN pushToken TEXT")

        // Unity IDs
        database.execSQL("ALTER TABLE accounts ADD COLUMN unityCloudUserId TEXT")
        database.execSQL("ALTER TABLE accounts ADD COLUMN unityPlayerSessionId TEXT")
        database.execSQL("ALTER TABLE accounts ADD COLUMN unityPlayerSessionCount INTEGER")

        // Android ID
        database.execSQL("ALTER TABLE accounts ADD COLUMN androidId TEXT")

        // Backup Path
        database.execSQL("ALTER TABLE accounts ADD COLUMN backupPath TEXT")
    }
}
```

---

## 4. BackupViewModel - Wichtige Funktionen

### 4.1 Backup durchführen (Schritt 3)

```kotlin
fun startBackup() {
    viewModelScope.launch {
        _uiState.update { 
            it.copy(
                currentStep = BackupStep.BACKUP_PROGRESS,
                isBackupRunning = true,
                backupProgress = 0f,
                error = null
            ) 
        }

        try {
            val backupDir = appStateRepository.getBackupDirectory() 
                ?: throw Exception("Backup-Verzeichnis nicht konfiguriert")

            val accountName = _uiState.value.accountName
            val accountPath = "$backupDir/$accountName"

            // Schritt 1: Ordner erstellen
            updateProgress(0.1f, "Erstelle Backup-Ordner...")
            createBackupDirectories(accountPath)

            // Schritt 2: shared_prefs kopieren
            updateProgress(0.3f, "Kopiere shared_prefs...")
            copySharedPrefs(accountPath)

            // Schritt 3: Cache kopieren
            updateProgress(0.6f, "Kopiere Cache-Verzeichnis...")
            copyCache(accountPath)

            // Schritt 4: settings_ssaid.xml kopieren
            updateProgress(0.8f, "Kopiere settings_ssaid.xml...")
            copySettingsSsaid(accountPath)

            // Schritt 5: Daten extrahieren
            updateProgress(0.9f, "Extrahiere Account-Daten...")
            val extractedData = extractAccountData(accountPath)

            // Schritt 6: Android ID auslesen
            updateProgress(0.95f, "Lese Android ID aus...")
            val androidId = extractAndroidId(accountPath)
            val finalData = extractedData.copy(androidId = androidId)

            // Schritt 7: In DB speichern
            updateProgress(1.0f, "Speichere in Datenbank...")
            val accountId = saveAccountToDatabase(accountPath, finalData)

            // Erfolg!
            _uiState.update { 
                it.copy(
                    currentStep = BackupStep.SUCCESS,
                    isBackupRunning = false,
                    backupSuccessful = true,
                    createdAccountId = accountId,
                    extractedData = finalData,
                    backupStatus = "Backup abgeschlossen!"
                ) 
            }

        } catch (e: Exception) {
            _uiState.update { 
                it.copy(
                    isBackupRunning = false,
                    backupSuccessful = false,
                    error = "Backup fehlgeschlagen: ${e.message}"
                ) 
            }
        }
    }
}

private fun updateProgress(progress: Float, status: String) {
    _uiState.update { 
        it.copy(
            backupProgress = progress,
            backupStatus = status
        ) 
    }
}

private suspend fun createBackupDirectories(accountPath: String) {
    val command = "mkdir -p $accountPath/shared_prefs && mkdir -p $accountPath/cache"
    rootUtil.executeCommand(command).getOrThrow()
    delay(300)
}

private suspend fun copySharedPrefs(accountPath: String) {
    val command = "cp -r /data/data/com.scopely.monopolygo/shared_prefs/* $accountPath/shared_prefs/"
    rootUtil.executeCommand(command).getOrThrow()
    delay(500)
}

private suspend fun copyCache(accountPath: String) {
    val command = "cp -r /data/data/com.scopely.monopolygo/cache/DiskBasedCacheDirectory/* $accountPath/cache/"
    rootUtil.executeCommand(command).getOrThrow()
    delay(500)
}

private suspend fun copySettingsSsaid(accountPath: String) {
    val command = "cp /data/system/users/0/settings_ssaid.xml $accountPath/settings_ssaid.xml"
    rootUtil.executeCommand(command).getOrThrow()
    delay(300)
}
```

### 4.2 Daten extrahieren (Schritt 4)

```kotlin
private suspend fun extractAccountData(accountPath: String): ExtractedData {
    return withContext(Dispatchers.IO) {
        val xmlPath = "$accountPath/shared_prefs/com.scopely.monopolygo.v2.playerprefs.xml"

        // Lese XML-Datei via Root
        val xmlContent = rootUtil.executeCommand("cat $xmlPath").getOrThrow()

        // Parse XML
        val doc = xmlParser.parseXml(xmlContent)

        // Extrahiere User ID
        val userId = xmlParser.getStringValue(doc, "Scopely.Attribution.UserId") 
            ?: xmlParser.getStringValue(doc, "ScopelyProfile.UserId") 
            ?: throw Exception("User ID nicht gefunden")

        // Extrahiere Device IDs
        val deviceToken = xmlParser.getStringValue(doc, "Scopely.Attribution.DeviceToken") 
            ?: throw Exception("Device Token nicht gefunden")

        val googleAdId = xmlParser.getStringValue(doc, "GoogleAdId") 
            ?: throw Exception("Google Ad ID nicht gefunden")

        val appSetId = xmlParser.getStringValue(doc, "AppSetId") 
            ?: throw Exception("App Set ID nicht gefunden")

        val pushToken = xmlParser.getStringValue(doc, "Scopely.Analytics.PushToken") 
            ?: xmlParser.getStringValue(doc, "LastOpenedPushToken") 
            ?: throw Exception("Push Token nicht gefunden")

        // Extrahiere Unity IDs
        val unityCloudUserId = xmlParser.getStringValue(doc, "unity.cloud_userid") 
            ?: throw Exception("Unity Cloud User ID nicht gefunden")

        val unityPlayerSessionId = xmlParser.getStringValue(doc, "unity.player_sessionid") 
            ?: throw Exception("Unity Player Session ID nicht gefunden")

        val unityPlayerSessionCount = xmlParser.getStringValue(doc, "unity.player_session_count")
            ?.toIntOrNull() 
            ?: throw Exception("Unity Player Session Count nicht gefunden")

        ExtractedData(
            userId = userId,
            deviceToken = deviceToken,
            googleAdId = googleAdId,
            appSetId = appSetId,
            pushToken = pushToken,
            unityCloudUserId = unityCloudUserId,
            unityPlayerSessionId = unityPlayerSessionId,
            unityPlayerSessionCount = unityPlayerSessionCount,
            androidId = ""  // Wird in Schritt 5 gesetzt
        )
    }
}
```

### 4.3 Android ID auslesen (Schritt 5)

```kotlin
private suspend fun extractAndroidId(accountPath: String): String {
    return withContext(Dispatchers.IO) {
        val abxPath = "$accountPath/settings_ssaid.xml"
        val xmlPath = "/data/local/tmp/settings_ssaid_decoded.xml"

        // Konvertierung mit abx2xml
        rootUtil.executeCommand("abx2xml $abxPath $xmlPath").getOrThrow()

        // SSAID extrahieren via grep
        val grepCommand = "grep -oP 'package="com\\.scopely\\.monopolygo"[^>]+value="\\K[^"]+' $xmlPath"
        val result = rootUtil.executeCommand(grepCommand).getOrThrow()

        val ssaid = result.trim()

        if (ssaid.isEmpty()) {
            throw Exception("SSAID nicht gefunden")
        }

        // Cleanup
        rootUtil.executeCommand("rm $xmlPath")

        ssaid
    }
}
```

---

## 5. Helper Classes

### 5.1 XmlParser.kt

```kotlin
class XmlParser @Inject constructor() {

    fun parseXml(xmlContent: String): Document {
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val inputStream = ByteArrayInputStream(xmlContent.toByteArray())
        return builder.parse(inputStream)
    }

    fun getStringValue(doc: Document, name: String): String? {
        val nodeList = doc.getElementsByTagName("string")

        for (i in 0 until nodeList.length) {
            val node = nodeList.item(i) as Element
            val attrName = node.getAttribute("name")

            if (attrName == name) {
                return node.textContent
            }
        }

        return null
    }
}
```

### 5.2 EncryptionUtil.kt

```kotlin
class EncryptionUtil @Inject constructor() {

    fun encrypt(plaintext: String): String {
        // TODO: Implementiere AES-Verschlüsselung mit Android Keystore
        // Für Prototyp: Base64-Encoding (NICHT SICHER!)
        return Base64.encodeToString(plaintext.toByteArray(Charsets.UTF_8), Base64.DEFAULT)
    }

    fun decrypt(encrypted: String): String {
        // TODO: Implementiere AES-Entschlüsselung
        return String(Base64.decode(encrypted, Base64.DEFAULT), Charsets.UTF_8)
    }
}
```

---

## 6. Compose UI - Beispiele

### 6.1 BackupProgressStep.kt

```kotlin
@Composable
fun BackupProgressStep(
    progress: Float,
    status: String,
    isRunning: Boolean,
    error: String?
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (error == null) {
            if (progress >= 1.0f && !isRunning) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = Color(0xFF4CAF50)
                )
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.size(80.dp),
                    color = Color(0xFF6200EE),
                    strokeWidth = 6.dp
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = status,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = Color(0xFF6200EE),
                trackColor = Color(0xFFE0E0E0)
            )

            Text(
                text = "${(progress * 100).toInt()}%",
                fontSize = 14.sp,
                color = Color(0xFF666666),
                modifier = Modifier.padding(top = 8.dp)
            )
        } else {
            Icon(
                Icons.Default.Error,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = Color(0xFFF44336)
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Backup fehlgeschlagen",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFF44336)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = error,
                fontSize = 16.sp,
                color = Color(0xFF666666),
                textAlign = TextAlign.Center
            )
        }
    }
}
```

### 6.2 BackupSuccessStep.kt

```kotlin
@Composable
fun BackupSuccessStep(
    accountName: String,
    accountId: Long,
    onShowAccount: () -> Unit,
    onLaunchGame: () -> Unit,
    onBackToOverview: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(100.dp),
            tint = Color(0xFF4CAF50)
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "✅ Backup erfolgreich!",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Account "$accountName" wurde erfolgreich gesichert.",
            fontSize = 16.sp,
            color = Color(0xFF666666),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = onShowAccount,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF6200EE)
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("Account anzeigen", fontSize = 16.sp)
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onLaunchGame,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, Color(0xFF4CAF50))
        ) {
            Text("Monopoly Go starten", fontSize = 16.sp, color = Color(0xFF4CAF50))
        }

        Spacer(modifier = Modifier.height(12.dp))

        TextButton(
            onClick = onBackToOverview,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Zur Übersicht", fontSize = 16.sp, color = Color(0xFF6200EE))
        }
    }
}
```

---

## 7. Testing-Checkliste

### Unit Tests
- [ ] BackupViewModel: Alle Steps funktionieren
- [ ] XmlParser: String-Extraktion funktioniert
- [ ] EncryptionUtil: Ver-/Entschlüsselung
- [ ] AndroidId-Extraktion

### Integration Tests
- [ ] Backup-Flow ohne Social Media
- [ ] Backup-Flow mit Facebook
- [ ] Backup-Flow mit Google
- [ ] Account wird in DB gespeichert

### UI Tests
- [ ] Tab-Wechsel
- [ ] Alle Input-Felder
- [ ] Progress-Anzeige
- [ ] Erfolgs-Screen

---

## 8. Akzeptanzkriterien

✅ **Tab-Navigation**: Funktioniert
✅ **Account sichern**: 6-Schritte-Flow
✅ **Daten-Extraktion**: 13 Felder
✅ **Android ID**: ABX → XML
✅ **Erfolgs-Screen**: 3 Aktionen

---

## 9. Offene Fragen

1. **Passwort-Verschlüsselung**: Android Keystore?
2. **2FA-Code**: Dauerhaft speichern?
3. **Account erstellen**: Tab 2 Funktion?
4. **ABX-Tool**: Verfügbarkeit?
5. **Backup-Validierung**: Nötig?

---

**Ende des Prompts**
