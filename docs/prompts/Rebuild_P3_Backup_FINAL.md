# Rebuild: Backup-Seite – bGO Account Manager v2.0 (P3 FINAL)

## Kontext
Du bist ein Android-Entwickler und implementierst in **v2.0** die **Backup-Seite**.  
Die App ist Root-basiert (libsu) und nutzt:
- Kotlin + MVVM
- Jetpack Compose (Material3)
- Hilt
- Room + DataStore
- Root Commands

Repository: `github.com/Orga-bgo/bgo-accounts`

Diese Spezifikation ist **final** und ersetzt frühere Backup-Prompts.

---

## 0) Harte Regeln (bindend)

### 0.1 Voraussetzungen
- Backup-Flows dürfen **nur** ausgeführt werden, wenn:
  - Root verfügbar ist
  - Monopoly GO installiert ist  
(Diese Checks passieren beim App-Start. Backup geht von einem gültigen Zustand aus.)

### 0.2 Abbruchkriterien (nur diese)
Der Flow wird **abgebrochen**, wenn einer dieser Punkte fehlschlägt:
1) Kopieren von App-Daten aus `/data/data/com.scopely.monopolygo/`
   - `shared_prefs`
   - `DiskBasedCacheDirectory`
2) Kopieren von `settings_ssaid.xml`
3) Extraktion der **User ID**
4) Extraktion der **Android ID (SSAID)**

Alle anderen Felder sind optional und dürfen `null` sein.

### 0.3 Logging & UX bei Fehler
- Jeder Fehler:
  - Log-Level **ERROR**
  - Muss enthalten:
    - **Step**
    - **Aktion**
    - **Fehlerursache** (Exception / ExitCode / stderr)
- UI:
  - Meldung: **„Da ist etwas schief gelaufen.. Prüfe den Log“**
  - Optional: „Erneut versuchen“

---

## 1) Validierung & Duplikate

### 1.1 User ID
- **Wenn User ID bereits in der DB existiert**:
  - Flow **abbrechen**
  - Meldung **wie im bestehenden Repo** (gleiche UX / Text)

### 1.2 Account-Name
- **Wenn Account-Name bereits existiert** (DB oder Backup-Ordner):
  - **Nicht abbrechen**
  - Automatisch umbenennen:
    - `Name`
    - `Name_1`
    - `Name_2`
    - … bis frei

---

## 2) DB-Insert Regel (wichtig)

- **Nach Schritt 5** (Extraktion von User ID + SSAID) werden:
  - **alle Daten gemeinsam** in die DB geschrieben
- Es gibt **keinen** früheren Insert.
- Danach gilt der Account als **gültig**.

Gespeichert werden:
- Account-Basisdaten
- Backup-Pfad
- Alle extrahierten IDs
- Social-Daten (falls vorhanden)

---

## 3) Screen-Struktur

### Tabs
```
┌─────────────────────────────────────┐
│  Account sichern  │  Account erstellen │
└─────────────────────────────────────┘
```

---

## 4) Tab 1: Account sichern (Wizard)

### Workflow
```
1) Name eingeben
2) Social Media Verbindung?
   ├─ Nein → 3
   └─ Ja → Platform → Daten
3) Backup durchführen
4) PlayerPrefs extrahieren (User ID etc.)
5) SSAID extrahieren
6) DB-Insert
7) Erfolg
```

---

## 5) Backup-Schritte (Root)

### 5.1 Kopieren
Kopiere:
- `/data/data/com.scopely.monopolygo/shared_prefs/`
- `/data/data/com.scopely.monopolygo/cache/DiskBasedCacheDirectory/`
- `/data/system/users/0/settings_ssaid.xml`

Ziel:
`{backupDir}/{accountName}/`

### 5.2 Extraktion PlayerPrefs
Pflichtfeld:
- `userId` → **wenn fehlt: Abbruch**

Optional:
- deviceToken
- googleAdId
- appSetId
- pushToken
- unityCloudUserId
- unityPlayerSessionId
- unityPlayerSessionCount

### 5.3 SSAID
- `abx2xml`
- Extraktion für `package="com.scopely.monopolygo"`
- Pflichtfeld → sonst Abbruch

---

## 6) Social-Daten
- Social-Daten sind **optional**
- Wenn vorhanden:
  - Werden **zusammen mit allen anderen Daten** gespeichert
  - Passwort-Speicherung **wie im bestehenden Repo**
  - Keine zusätzliche Verschlüsselung nötig (lokale App)

---

## 7) Erfolg & Aktionen

### Erfolgsmeldung
„Backup erfolgreich!“

### Aktionen
- **Account anzeigen** → Detailseite
- **Monopoly Go starten** → **Start mit Restore**
  - SSAID setzen
  - Account-Daten nach `/data/data/...` kopieren
  - `am start -n com.scopely.monopolygo/.MainActivity`
- **Zur Übersicht**

---

## 8) Tab 2: Account erstellen
Unverändert gemäß:
`Rebuild_Backup_Part2.md`

(DB-Regeln, Logging und Start-mit-Restore gelten identisch.)

---

## 9) Entities / Attribute (Pflichtdokumentation)

### Übernommen
- `AccountEntity`
- Backup- & Root-Utilities
- Log-System

### Neu (falls noch nicht vorhanden)
- Keine neuen Pflichtfelder durch diesen Prompt

### Nicht mehr benötigt
- Keine

---

## 10) Akzeptanzkriterien
- [ ] User ID Duplikat → Abbruch mit Repo-Meldung
- [ ] Name Duplikat → `_1`, `_2`, …
- [ ] DB-Insert erst nach Schritt 5
- [ ] Abbruch nur bei definierten Fehlern
- [ ] Fehler → Log + Meldung
- [ ] Erfolg → Start mit Restore

---

## 11) Tests

Implementiere ausführliche Unit‑ und Integrationstests für den gesamten Backup‑Flow. Überprüfe sämtliche Abbruchbedingungen, Duplikatsregeln und das richtige Timing der DB‑Operationen. Mocke Root‑Zugriffe und das Dateisystem, verifiziere, dass bei Fehlern passende Log‑Einträge geschrieben und der Flow korrekt abgebrochen wird. Teste, dass bei Erfolg automatisch in den Restore‑Flow gewechselt und der DB‑Insert erst nach Schritt 5 durchgeführt wird. Alle Tests müssen in der CI/CD‑Pipeline ohne Fehler durchlaufen.

Wenn alles umgesetzt ist und alle Tests bestanden sind, **lies die Datei `Rebuild_P4_Backup_Part2_AccErstellen_FINAL.md` im Verzeichnis `docs/prompts`** und führe alle darin beschriebenen Aufgaben aus.

**Ende des Prompts**
