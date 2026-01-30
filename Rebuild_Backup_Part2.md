# Rebuild Backup - Part 2 (Tab: Account erstellen) - bGO Account Manager v2.0

## Kontext
Du bist ein Android-Entwickler und implementierst in v2.0 die Backup-Seite weiter. Diese Aufgabe betrifft ausschließlich den Tab **"Account erstellen"** innerhalb der Backup-Seite.

Die App ist Root-basiert (libsu) und arbeitet mit:
- Kotlin + MVVM
- Jetpack Compose (Material3)
- Hilt
- Room + DataStore
- Root Commands

Repository: `github.com/Orga-bgo/bgo-accounts`

---

## Ziel
Implementiere den kompletten Flow **"Account erstellen"** mit einem Wizard-UI (mehrere Schritte) und Root-Operationen.

Der Flow erzeugt einen neuen, leeren Monopoly-Go-Account, indem:
- Monopoly Go App-Daten gelöscht werden
- optional eine neue SSAID (Android ID) generiert und gesetzt wird
- Monopoly Go einmal gestartet wird, damit es neue Daten erzeugt
- dann die neuen Daten gesichert und ausgelesen werden (wie im normalen Backup-Flow)

---

## 1. UI / Wizard Schritte

### Tab: "Account erstellen"
Wizard-Schritte:

1) **Name eingeben**
- Textfeld: Präfix ist bereits vorgefüllt (aus Settings/defaultPrefix)
- Validierung: Name darf nicht leer sein

2) **Warnung bestätigen**
- Text: "Achtung! Es werden alle Monopoly Go Daten gelöscht"
- Checkbox oder Confirm-Button: "Ich bestätige" (muss aktiv bestätigt werden)

3) **SSAID neu generieren?**
- Radio: [ ] Ja  [ ] Nein

3.1) Wenn Ja:
- Generiere eine zufällige Android ID (SSAID, Hex oder UUID-ähnlich)
- Speichere diese SSAID in die DB für den neu anzulegenden Account

3.2) Wenn Nein:
- Extrahiere die SSAID aus `settings_ssaid.xml`
- Speichere diese SSAID in die DB für den neu anzulegenden Account

4) **Löschen der Monopoly Go App Daten**
- Root Command: vollständiges Löschen der App Daten
- Ziel: `/data/data/com.scopely.monopolygo/` komplett entfernen

5) **Wartebildschirm**
- Text: "Bitte warten bis du wieder auf diesem Bildschirm zurückkehrst"
- Warte 3 Sekunden

6) **SSAID setzen in settings_ssaid.xml**
- Workflow:
  - `/data/system/users/0/settings_ssaid.xml` nach `/data/local/tmp/settings_ssaid.xml` kopieren
  - `abx2xml` ausführen, um lesbares XML zu erzeugen
  - In der XML: beim Setting Eintrag für `package="com.scopely.monopolygo"` die Werte `value` und `defaultValue` auf die SSAID aus der DB setzen
  - XML speichern
  - `xml2abx` ausführen, um zurück in ABX zu konvertieren
  - resultierende Datei zurück nach `/data/system/users/0/settings_ssaid.xml` kopieren

Beispiel-Zielknoten:
```xml
<setting id="xxx" name="10487" value="42f684fa824f2b28" package="com.scopely.monopolygo" defaultValue="42f684fa824f2b28" defaultSysSet="false" tag="null" />
```

7) **Monopoly Go starten**
- Root Command:
  `am start -n com.scopely.monopolygo/.MainActivity`
- Warte 7 Sekunden

8) **Monopoly Go beenden**
- Root Command:
  `am force-stop com.scopely.monopolygo`

9) **Normaler Backup-Flow Teilschritte ausführen**
Führe anschließend die folgenden Schritte aus dem normalen Backup-Flow aus:
- Schritt 3: Backup der Dateien/Ordner (shared_prefs, DiskBasedCacheDirectory, settings_ssaid.xml) in `{backupDir}/{accountName}/`
- Schritt 4: Extrahiere Strings aus `shared_prefs/com.scopely.monopolygo.v2.playerprefs.xml` und schreibe in DB
- Schritt 6: Erfolgsmeldung + Aktionen

---

## 2. Datenbank / Entities

### 2.1 AccountEntity Erweiterungen (falls noch nicht vorhanden)
Der Account muss mindestens diese Felder besitzen:
- `accountName: String`
- `prefix: String`
- `androidId: String?` (SSAID)
- `backupPath: String?`
- plus die extrahierten Felder aus dem normalen Backup-Flow (userId, deviceToken, etc.)

Wichtig: Beim "Account erstellen" ist die `userId` vor Schritt 9 noch unbekannt, daher darf sie initial leer sein oder nullable sein. Wenn in v1.0 `userId` non-null ist, dann:
- zunächst Account ohne DB-Insert anlegen, bis Schritt 4 (Extraktion) abgeschlossen ist, oder
- lege ein temporäres Placeholder-Value ab und update später (nicht empfohlen)

Empfehlung: Speichere erst nach Abschluss von Schritt 4 in DB (wenn `userId` bekannt ist).

---

## 3. ViewModel: CreateAccountFlow

### 3.1 State

```kotlin
data class CreateAccountUiState(
    val step: CreateAccountStep = CreateAccountStep.NAME,
    val accountName: String = "",
    val prefix: String = "",

    val warningConfirmed: Boolean = false,

    val regenerateSsaid: Boolean? = null, // null = nicht gewählt

    val ssaidFromDb: String? = null,      // final SSAID, die benutzt wird

    val progress: Float = 0f,
    val status: String = "",
    val isRunning: Boolean = false,

    val createdAccountId: Long? = null,
    val error: String? = null
)

enum class CreateAccountStep {
    NAME,
    WARNING,
    SSAID_CHOICE,
    EXECUTE,
    SUCCESS
}
```

### 3.2 Commands / Root Actions
Implementiere folgende Root-Operationen als einzelne, testbare Funktionen:

1. `readCurrentSsaid(): String`
- liest SSAID aus `/data/system/users/0/settings_ssaid.xml`
- nutzt `abx2xml` und `grep` (wie im normalen Backup)

2. `generateRandomSsaid(): String`
- generiert zufällige 16-hex SSAID (z.B. 16 bytes hex)

3. `wipeMonopolyGoData()`
- löscht `/data/data/com.scopely.monopolygo/` vollständig

4. `setSsaidInSettings(newSsaid: String)`
- kopiert settings_ssaid.xml nach tmp
- abx2xml
- ersetzt `value` und `defaultValue` für package com.scopely.monopolygo
- xml2abx
- kopiert zurück

5. `launchMonopolyGo()`
- `am start -n com.scopely.monopolygo/.MainActivity`

6. `stopMonopolyGo()`
- `am force-stop com.scopely.monopolygo`

7. Danach: reuse vom normalen Backup Flow:
- `performBackupFiles(accountName)` (shared_prefs, cache, ssaid)
- `extractAndPersistValues(accountName)`
- `showSuccess()`

---

## 4. Root Workflow (Pseudo-Code)

```kotlin
suspend fun createAccountFlow() {
    // Vorbedingungen
    require(warningConfirmed)
    require(accountName.isNotBlank())

    updateStatus(0.05f, "SSAID vorbereiten...")

    val ssaid = if (regenerateSsaid == true) {
        val newSsaid = generateRandomSsaid()
        // in state merken (später in DB)
        newSsaid
    } else {
        readCurrentSsaid()
    }

    updateStatus(0.15f, "Monopoly Go Daten löschen...")
    wipeMonopolyGoData()

    updateStatus(0.25f, "Bitte warten...")
    delay(3000)

    updateStatus(0.40f, "SSAID setzen...")
    setSsaidInSettings(ssaid)

    updateStatus(0.55f, "Monopoly Go starten...")
    launchMonopolyGo()

    updateStatus(0.70f, "Warte 7 Sekunden...")
    delay(7000)

    updateStatus(0.80f, "Monopoly Go beenden...")
    stopMonopolyGo()

    updateStatus(0.85f, "Backup erstellen...")
    performBackupFiles(accountName)

    updateStatus(0.92f, "Account-Daten extrahieren...")
    val extracted = extractValuesFromPlayerPrefs(accountName)

    updateStatus(0.97f, "In Datenbank speichern...")
    val accountId = insertOrUpdateAccount(
        accountName = accountName,
        androidId = ssaid,
        extracted = extracted
    )

    updateStatus(1.0f, "Fertig")
    showSuccess(accountId)
}
```

---

## 5. Implementierungsdetails

### 5.1 Generierung einer zufälligen SSAID
- Form: 16 hex Zeichen (oder 16 bytes hex, also 32 hex Zeichen) – entscheide dich für eine feste Länge
- Empfehlung: **16 bytes** → 32 hex characters

Beispiel:
`42f684fa824f2b28d9a1c9e3b1f0a1c2`

### 5.2 settings_ssaid.xml Patchen

**Wichtig:** Es gibt mehrere `<setting ...>` Einträge. Du musst den passenden Eintrag für Monopoly Go finden:
- `package="com.scopely.monopolygo"`

Dann:
- setze `value="<ssaid>"`
- setze `defaultValue="<ssaid>"`

Nutze eine robuste XML-Update-Methode:
- Entweder über Kotlin XML Parser (DocumentBuilder) und Node Updates
- Oder über Shell `sed` / `perl` (weniger robust)

Empfohlen: Kotlin XML Parser, da du ohnehin schon einen XmlParser in Part 1 hast.

### 5.3 Löschen der App Daten
Nutze Root:
- `rm -rf /data/data/com.scopely.monopolygo/*`
- optional zusätzlich:
  - `rm -rf /data/user/0/com.scopely.monopolygo/*` (falls vorhanden)
  - `pm clear com.scopely.monopolygo` (falls funktioniert)

Implementiere es defensiv:
- Prüfe Exit Codes
- Logge Fehler

---

## 6. Compose UI Anforderungen

### 6.1 Wizard Layout
- Oben: "Schritt x von y" (ähnlich wie im Backup-Flow)
- Content je Step
- Buttons:
  - Zurück (außer Step 1)
  - Weiter / Start / Bestätigen je nach Schritt

### 6.2 Step 2 Warnung
- Große Warn-Card (rot/grau)
- Checkbox: "Ich bestätige" muss angehakt sein, sonst ist "Weiter" disabled

### 6.3 Wartebildschirm
- Vollbild Card + CircularProgressIndicator
- Text wie spezifiziert

### 6.4 Progress Screen (Execute)
- Live Status Text
- LinearProgressIndicator
- Optional: Liste der aktuellen Aktion

### 6.5 Success Screen
- Zeige: "Account erstellt"
- Buttons:
  - "Account anzeigen"
  - "Monopoly Go starten"
  - "Zur Übersicht"

---

## 7. Wiederverwendung aus Part 1

Du darfst und sollst Code aus `Rebuild_Backup.md` wiederverwenden:
- File-Copy Funktionen (shared_prefs, cache, settings_ssaid.xml)
- Extractor für playerprefs XML
- Success Screen UI

---

## 8. Testing-Checkliste

### Unit Tests
- [ ] generateRandomSsaid liefert korrekte Länge und Hex
- [ ] SSAID Parsing aus settings_ssaid.xml funktioniert
- [ ] XML Patch ersetzt value/defaultValue korrekt

### Integration Tests (Root-Device)
- [ ] Flow mit regenerateSsaid = Ja
- [ ] Flow mit regenerateSsaid = Nein
- [ ] Monopoly Go startet und wird beendet
- [ ] Backup wird erstellt
- [ ] playerprefs Werte werden extrahiert

### UI Tests
- [ ] Wizard Navigation
- [ ] Warnung muss bestätigt werden
- [ ] Progress und Waiting Screen sichtbar
- [ ] Success Buttons funktionieren

---

## 9. Akzeptanzkriterien

- [ ] Tab "Account erstellen" ist vollständig implementiert
- [ ] Präfix ist im Namen-Feld vorgefüllt
- [ ] Warnung muss aktiv bestätigt werden
- [ ] SSAID Wahl (Ja/Nein) funktioniert
- [ ] SSAID wird korrekt gesetzt (value + defaultValue)
- [ ] Monopoly Go Daten werden vollständig gelöscht
- [ ] Monopoly Go wird gestartet (7s) und beendet
- [ ] Backup-Dateien werden kopiert
- [ ] playerprefs Werte werden extrahiert und in DB gespeichert
- [ ] Success Screen zeigt 3 Aktionen

---

## 10. Offene Fragen

1. Soll die SSAID Länge genau 16 oder 32 Hex Zeichen sein?
2. Soll vor dem Löschen ein optionales Safety-Backup angeboten werden?
3. Welche Fehler sind kritisch genug, um den Flow abzubrechen? (z.B. abx2xml fehlgeschlagen)
4. Muss der "Account erstellen" Flow automatisch auch `lastRestoredAccountId` setzen?

---

**Ende des Prompts**
