# Rebuild "Mehr" Menü - bGO Account Manager v2.0

## Kontext
Du bist ein Android-Entwickler und arbeitest an der **Version 2.0** der "bGO Account Manager" App. Diese Aufgabe fokussiert sich auf das **"Mehr"-Menü** (ehemals "Settings"), das als **Side Menu / Navigation Drawer** implementiert wird.

Die App basiert auf:
- **Kotlin** mit **MVVM-Architektur**
- **Jetpack Compose** + **Material3**
- **Hilt** (Dependency Injection)
- **Room Database** + **DataStore**
- **libsu** (Root-Zugriff)

Repository: `github.com/Orga-bgo/bgo-accounts`

---

## 1. Bottom Navigation Änderung

### 1.1 Alter Zustand (v1.0)
```
┌─────────────────────────────────────┐
│  📋 Accounts  💾 Backup  ⚙️ Settings  📝 Log
└─────────────────────────────────────┘
```

### 1.2 Neuer Zustand (v2.0)
```
┌─────────────────────────────────────┐
│  📋 Accounts  💾 Backup  ⚙️ Mehr  📝 Log
└─────────────────────────────────────┘
```

**Änderung:**
- Tab "Settings" → "Mehr"
- Icon bleibt: ⚙️
- **Funktion:** Öffnet ein **Side Menu** (Navigation Drawer)

---

## 2. Side Menu Struktur

### 2.1 Layout-Übersicht

```
┌─────────────────────────────┐
│ ⚙️ MEHR                    │
├─────────────────────────────┤
│                            │
│  ▸ 📁 Dateiverwaltung     │
│    Lokaler Im-/Export         │
│    Server Im-/Export          │
│                            │
│  ▸ ⚙️ Einstellungen       │
│    Backup-Ordner: /mgo/   │
│    Präfix: MGO_           │
│                            │
│  ▸ 🔍 ID-Vergleich        │
│                            │
│  ▸ ℹ️ Über                │
│    Version 1.0.0          │
│    HP: example.com        │
│                            │
└─────────────────────────────┘
```

### 2.2 Menü-Einträge (4 Hauptpunkte)

1. **📁 Dateiverwaltung**
   - Untertitel: "Lokaler Im-/Export, Server Im-/Export"
   - Navigation: `FileManagementScreen`

2. **⚙️ Einstellungen**
   - Untertitel: "Backup-Ordner: /mgo/, Präfix: MGO_"
   - Navigation: `SettingsScreen`

3. **🔍 ID-Vergleich**
   - Kein Untertitel
   - Navigation: `IdComparisonScreen`

4. **ℹ️ Über**
   - Untertitel: "Version 1.0.0, HP: example.com"
   - Navigation: `AboutScreen`

---

## 3. Feature 1: 📁 Dateiverwaltung

### 3.1 Screen-Übersicht

**Tab-Ansicht:**
```
┌─────────────────────────────────────┐
│  Lokaler Export  │  Server Export   │
└─────────────────────────────────────┘
```

### 3.2 Tab 1: Lokaler Export

**UI:**
```
Lokaler Export
┌─────────────────────────────────────┐
│ Pfad: /storage/emulated/0/mgo/      │
│ [ Pfad wählen... ]                  │
├─────────────────────────────────────┤
│                                     │
│ ┌─────────────────────┐             │
│ │ 📦 backup_30012026.zip            │
│ │ 📊 Größe: 24.5 MB                 │
│ │ 👥 Accounts: 4                    │
│ │ 📅 30.01.2026 14:30               │
│ │ [🔍 Vorschau] [🗑️ Löschen]        │
│ └─────────────────────┘             │
│                                     │
│ ┌─────────────────────┐             │
│ │ 📦 backup_28012026.zip            │
│ │ 📊 Größe: 18.2 MB                 │
│ │ 👥 Accounts: 3                    │
│ │ 📅 28.01.2026 09:15               │
│ │ [🔍 Vorschau] [🗑️ Löschen]        │
│ └─────────────────────┘             │
│                                     │
└─────────────────────────────────────┘
```

**Funktionen:**
- **Pfad wählen**: SAF Picker für Ordner-Auswahl
- **Liste aller ZIP-Backups** im gewählten Ordner
- **Card-Design** für jede Datei:
  - Icon: 📦
  - Dateiname
  - Größe (MB)
  - Anzahl Accounts (aus ZIP-Metadaten oder accounts.json)
  - Datum
  - Buttons: **Vorschau** und **Löschen**

**Vorschau-Dialog:**
```
┌─────────────────────────────────────┐
│ Backup-Vorschau                     │
├─────────────────────────────────────┤
│ Datei: backup_30012026.zip          │
│ Accounts:                           │
│  • MGO_Main01 (ID: 1140407373)      │
│  • MGO_Alt02 (ID: 9876543210)       │
│  • ALT_Test (ID: 1122334455)        │
│  • PRO_Player (ID: 5566778899)      │
├─────────────────────────────────────┤
│ [ Importieren ] [ Schließen ]       │
└─────────────────────────────────────┘
```

**FAB (Floating Action Button):**
- Icon: ➕
- Funktion: "Neues Backup erstellen"
- Aktion: Öffnet Dialog mit:
  - Account-Auswahl (Multi-Select)
  - Dateiname-Eingabe (vorausgefüllt mit Datum)
  - Button: "Backup erstellen"

### 3.3 Tab 2: Server Export

**Status: Nicht verbunden**
```
Server Export
┌─────────────────────────────────────┐
│ SSH Status: ⚫ Getrennt              │
│                                     │
│ [ SSH-Verbindung einrichten ]       │
└─────────────────────────────────────┘
```

**Status: Verbunden**
```
Server Export
┌─────────────────────────────────────┐
│ SSH Status: 🟢 Verbunden             │
│ Server: 192.168.1.100:22            │
│ Pfad: /home/user/mgo_backups/       │
├─────────────────────────────────────┤
│ Server-Dateien:                     │
│                                     │
│ ┌─────────────────────┐             │
│ │ 📦 backup_server_30.zip           │
│ │ 📊 Größe: 30.1 MB                 │
│ │ 👥 Accounts: 5                    │
│ │ 📅 30.01.2026 20:00               │
│ │ [📥 Download] [🗑️ Löschen]        │
│ └─────────────────────┘             │
│                                     │
└─────────────────────────────────────┘
```

**SSH-Wizard Flow (4 Schritte):**

**Schritt 1: Server-Adresse**
```
┌─────────────────────────────────────┐
│ SSH Einrichtung (1/4)               │
├─────────────────────────────────────┤
│ Server-Adresse:                     │
│ ┌─────────────────────┐             │
│ │ 192.168.1.100       │             │
│ └─────────────────────┘             │
│                                     │
│ Port:                               │
│ ┌─────────────────────┐             │
│ │ 22                  │             │
│ └─────────────────────┘             │
├─────────────────────────────────────┤
│ [ Zurück ] [ Weiter ]               │
└─────────────────────────────────────┘
```

**Schritt 2: Authentifizierung**
```
┌─────────────────────────────────────┐
│ SSH Einrichtung (2/4)               │
├─────────────────────────────────────┤
│ Benutzername:                       │
│ ┌─────────────────────┐             │
│ │ user                │             │
│ └─────────────────────┘             │
│                                     │
│ Passwort:                           │
│ ┌─────────────────────┐             │
│ │ ••••••••            │             │
│ └─────────────────────┘             │
├─────────────────────────────────────┤
│ [ Zurück ] [ Weiter ]               │
└─────────────────────────────────────┘
```

**Schritt 3: Test-Verbindung**
```
┌─────────────────────────────────────┐
│ SSH Einrichtung (3/4)               │
├─────────────────────────────────────┤
│ Verbindung wird getestet...         │
│                                     │
│ [Progress Spinner]                  │
│                                     │
│ ✅ Verbindung erfolgreich!          │
├─────────────────────────────────────┤
│ [ Zurück ] [ Weiter ]               │
└─────────────────────────────────────┘
```

**Schritt 4: Backup-Pfad**
```
┌─────────────────────────────────────┐
│ SSH Einrichtung (4/4)               │
├─────────────────────────────────────┤
│ Remote Backup-Pfad:                 │
│ ┌─────────────────────┐             │
│ │ /home/user/backups/ │             │
│ └─────────────────────┘             │
│                                     │
│ [ ] Automatisches Backup            │
├─────────────────────────────────────┤
│ [ Zurück ] [ Fertigstellen ]        │
└─────────────────────────────────────┘
```

---

## 4. Feature 2: ⚙️ Einstellungen

### 4.1 Screen-Übersicht

```
Einstellungen
┌─────────────────────────────────────┐
│ BACKUP-KONFIGURATION                │
├─────────────────────────────────────┤
│ Backup-Ordner                       │
│ /storage/emulated/0/mgo/backups/    │
│ [ Durchsuchen... ]                  │
├─────────────────────────────────────┤
│ Account-Präfix                      │
│ ┌──────────────┐                    │
│ │ MGO_         │                    │
│ └──────────────┘                    │
├─────────────────────────────────────┤
│ VERHALTEN                           │
├─────────────────────────────────────┤
│ [ ] System-Check bei App-Start      │
│                                     │
│ [ ] Automatischer Server-Upload     │
│     (benötigt SSH-Verbindung)       │
├─────────────────────────────────────┤
│ ERWEITERT                           │
├─────────────────────────────────────┤
│ [ ] Debug-Modus aktivieren          │
│                                     │
│ [ ] Root-Befehle loggen             │
│                                     │
│ [ Logs exportieren ]                │
└─────────────────────────────────────┘
```

**Felder:**
1. **Backup-Ordner**
   - Textfeld (readonly)
   - Button: "Durchsuchen..." (öffnet SAF Picker)
   - Speichern in DataStore

2. **Account-Präfix**
   - Textfeld (editable)
   - Standard: "MGO_"
   - Speichern in DataStore

3. **System-Check bei App-Start**
   - Checkbox
   - Aktiviert die System-Checks beim Start (aus Rebuild_Appstart.md)

4. **Automatischer Server-Upload**
   - Checkbox
   - Nur aktiv wenn SSH-Verbindung konfiguriert ist
   - Lädt Backups automatisch auf Server hoch

5. **Debug-Modus**
   - Checkbox
   - Aktiviert erweiterte Logs

6. **Root-Befehle loggen**
   - Checkbox
   - Loggt alle Root-Commands in die Log-DB

7. **Logs exportieren**
   - Button
   - Exportiert Logs als TXT oder CSV

---

## 5. Feature 3: 🔍 ID-Vergleich Tool

### 5.1 Screen-Übersicht

```
ID-Vergleich
┌─────────────────────────────────────┐
│ Vergleicht IDs zwischen lokalen     │
│ und Server-Accounts.                │
├─────────────────────────────────────┤
│ [ 🔄 Vergleich starten ]            │
├─────────────────────────────────────┤
│                                     │
│ ERGEBNIS:                           │
│                                     │
│ User IDs:                           │
│ • Lokal: 1140407373                 │
│ • Server: 1140407373                │
│ ✅ Übereinstimmung                  │
│                                     │
│ Device IDs:                         │
│ • 3 IDs gefunden                    │
│ ⚠️ 1 Abweichung                     │
│ [Details anzeigen]                  │
│                                     │
│ Session IDs:                        │
│ • 5 aktive Sessions                 │
│ ✅ Alle übereinstimmend             │
│                                     │
├─────────────────────────────────────┤
│ [ Export als CSV ] [ Details ]      │
└─────────────────────────────────────┘
```

**Funktionen:**
- **Vergleich starten**: Führt Vergleich aus
- **User IDs**: Zeigt `userId` aus DB (lokal vs. Server-Backup)
- **Device IDs**: Vergleicht `deviceToken`, `googleAdId`, `appSetId`
- **Session IDs**: Vergleicht `unityPlayerSessionId`, `pushToken`
- **Abweichungen**: Zeigt ⚠️ Icon + Anzahl
- **Details**: Öffnet Dialog mit vollständiger Liste
- **Export als CSV**: Exportiert Vergleich als CSV-Datei

**Details-Dialog:**
```
┌─────────────────────────────────────┐
│ Device ID Abweichungen              │
├─────────────────────────────────────┤
│ GoogleAdId:                         │
│ Lokal:  1bbae05f-b61e-47f0-...     │
│ Server: 2ccdf16g-c72g-58g1-...     │
│ ❌ Nicht identisch                  │
├─────────────────────────────────────┤
│ [ Lokal überschreiben ]             │
│ [ Server überschreiben ]            │
│ [ Schließen ]                       │
└─────────────────────────────────────┘
```

---

## 6. Feature 4: ℹ️ Über

### 6.1 Screen-Übersicht

```
Über babixGO
┌─────────────────────────────────────┐
│ Version: 2.0.0                      │
│ Build: 20260130                     │
│ App-Starts: 22                      │
├─────────────────────────────────────┤
│ 🌐 Homepage                         │
│ https://example.com                 │
│ [ Öffnen ]                          │
├─────────────────────────────────────┤
│ 📧 Support                          │
│ support@example.com                 │
│ [ E-Mail senden ]                   │
├─────────────────────────────────────┤
│ 📄 Lizenz                           │
│ MIT License                         │
│ [ Anzeigen ]                        │
├─────────────────────────────────────┤
│ 👨‍💻 Entwickler                       │
│ bGO Team                            │
├─────────────────────────────────────┤
│ 🔧 Technische Details               │
│ • Android Version: 14               │
│ • Root: ✅ Verfügbar                │
│ • libsu Version: 5.2.2              │
│ • Monopoly Go: ✅ Installiert       │
└─────────────────────────────────────┘
```

**Felder:**
1. **Version**: Aus BuildConfig
2. **Build**: Datum (aus BuildConfig oder Konstante)
3. **App-Starts**: Aus DataStore (Counter)
4. **Homepage**: Klickbar, öffnet Browser
5. **Support E-Mail**: Öffnet E-Mail-Client
6. **Lizenz**: Öffnet Dialog mit vollständigem Lizenztext
7. **Technische Details**:
   - Android Version (via `Build.VERSION.RELEASE`)
   - Root Status (via `rootUtil.isRootAvailable()`)
   - libsu Version (via Konstante)
   - Monopoly Go Status (via PackageManager)

---

## 7. Navigation Drawer Implementation (Compose)

### 7.1 ModalNavigationDrawer

```kotlin
@Composable
fun MainScaffold(
    navController: NavController
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                MehrMenuContent(
                    onNavigate = { destination ->
                        navController.navigate(destination)
                        scope.launch { drawerState.close() }
                    }
                )
            }
        }
    ) {
        Scaffold(
            topBar = { /* Header */ },
            bottomBar = {
                BottomNavigationBar(
                    onMehrClick = {
                        scope.launch { drawerState.open() }
                    }
                )
            }
        ) { paddingValues ->
            // Content
        }
    }
}
```

### 7.2 MehrMenuContent

```kotlin
@Composable
fun MehrMenuContent(
    onNavigate: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(300.dp)
            .background(Color.White)
    ) {
        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF6200EE))
                .padding(24.dp)
        ) {
            Text(
                text = "⚙️ MEHR",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Divider()

        // Menu Items
        MehrMenuItem(
            icon = "📁",
            title = "Dateiverwaltung",
            subtitle = "Lokaler Im-/Export
Server Im-/Export",
            onClick = { onNavigate("fileManagement") }
        )

        MehrMenuItem(
            icon = "⚙️",
            title = "Einstellungen",
            subtitle = "Backup-Ordner: /mgo/
Präfix: MGO_",
            onClick = { onNavigate("settings") }
        )

        MehrMenuItem(
            icon = "🔍",
            title = "ID-Vergleich",
            subtitle = null,
            onClick = { onNavigate("idComparison") }
        )

        MehrMenuItem(
            icon = "ℹ️",
            title = "Über",
            subtitle = "Version 2.0.0
HP: example.com",
            onClick = { onNavigate("about") }
        )
    }
}

@Composable
fun MehrMenuItem(
    icon: String,
    title: String,
    subtitle: String?,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$icon  $title",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }

        subtitle?.let {
            Text(
                text = it,
                fontSize = 12.sp,
                color = Color(0xFF666666),
                modifier = Modifier.padding(start = 32.dp, top = 4.dp)
            )
        }
    }

    Divider()
}
```

---

## 8. DataStore Erweiterungen

### 8.1 Neue Preferences Keys

```kotlin
companion object {
    // Bestehende Keys...

    // Einstellungen
    val BACKUP_DIRECTORY = stringPreferencesKey("backup_directory")
    val DEFAULT_PREFIX = stringPreferencesKey("default_prefix")
    val SYSTEM_CHECK_ON_START = booleanPreferencesKey("system_check_on_start")
    val AUTO_SERVER_UPLOAD = booleanPreferencesKey("auto_server_upload")
    val DEBUG_MODE = booleanPreferencesKey("debug_mode")
    val LOG_ROOT_COMMANDS = booleanPreferencesKey("log_root_commands")

    // SSH
    val SSH_HOST = stringPreferencesKey("ssh_host")
    val SSH_PORT = intPreferencesKey("ssh_port")
    val SSH_USERNAME = stringPreferencesKey("ssh_username")
    val SSH_PASSWORD = stringPreferencesKey("ssh_password")  // Verschlüsselt!
    val SSH_REMOTE_PATH = stringPreferencesKey("ssh_remote_path")

    // Über
    val APP_START_COUNT = intPreferencesKey("app_start_count")
}
```

---

## 9. Testing-Checkliste

### Unit Tests
- [ ] Navigation Drawer öffnet/schließt korrekt
- [ ] Alle Menü-Einträge navigieren korrekt
- [ ] SSH-Wizard alle 4 Schritte funktionieren
- [ ] ID-Vergleich findet Abweichungen

### Integration Tests
- [ ] Backup-Export als ZIP funktioniert
- [ ] SSH-Verbindung kann hergestellt werden
- [ ] Server-Upload funktioniert
- [ ] ID-Vergleich funktioniert (lokal vs. Server)

### UI Tests
- [ ] Drawer öffnet via "Mehr"-Tab
- [ ] Alle Screens sind erreichbar
- [ ] FAB erstellt neues Backup
- [ ] Settings werden gespeichert

---

## 10. Akzeptanzkriterien

✅ **Navigation**:
- [ ] Bottom Nav Tab "Settings" heißt jetzt "Mehr"
- [ ] Tab öffnet Navigation Drawer (nicht inline Screen)
- [ ] Drawer zeigt 4 Menü-Einträge mit Icons und Untertiteln

✅ **Dateiverwaltung**:
- [ ] Lokaler Export zeigt ZIP-Liste mit Cards
- [ ] Vorschau-Dialog zeigt Account-Namen
- [ ] FAB erstellt neues Backup
- [ ] Server Export zeigt SSH-Status
- [ ] SSH-Wizard hat 4 Schritte

✅ **Einstellungen**:
- [ ] Backup-Ordner kann geändert werden
- [ ] Präfix kann gesetzt werden
- [ ] Alle Checkboxen funktionieren
- [ ] Werte werden in DataStore gespeichert

✅ **ID-Vergleich**:
- [ ] Vergleich startet und zeigt Ergebnisse
- [ ] Abweichungen werden markiert (⚠️)
- [ ] Export als CSV funktioniert

✅ **Über**:
- [ ] Alle Informationen werden angezeigt
- [ ] Homepage-Link öffnet Browser
- [ ] Support-E-Mail öffnet Client
- [ ] Technische Details sind korrekt

---

## 11. Offene Fragen

1. **SSH-Library**: Welche Library soll für SSH verwendet werden? (z.B. JSch, SSHJ)
2. **ZIP-Format**: Welches Format hat die Export-ZIP? (accounts.json + backup-Ordner?)
3. **ID-Vergleich**: Sollen nur aktive Accounts verglichen werden?
4. **Server-Upload**: Automatisch nach jedem Backup oder manuell triggern?
5. **Lizenztext**: Welche Lizenz hat die App? (MIT, GPL, Proprietary?)

---

**Ende des Prompts**
