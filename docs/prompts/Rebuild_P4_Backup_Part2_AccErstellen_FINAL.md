# Rebuild: Backup – Tab 2 „Account erstellen“ (P4 FINAL)

## Kontext
Du implementierst in **bGO Account Manager v2.0** den Tab **„Account erstellen“** im Backup-Screen.  
Stack: Kotlin, MVVM, Jetpack Compose, Room, DataStore, libsu (Root).

Dieser Prompt ist **final** und ist kompatibel mit:
- **P3 FINAL** (Backup Tab 1 Regeln)
- Startseite-Regeln („Start = Start mit Restore“)

---

## 0) Harte Regeln (bindend)

### 0.1 Voraussetzungen
- Flow läuft nur, wenn Root verfügbar ist und Monopoly GO installiert ist (App-Start-Gate).

### 0.2 DB-Insert Timing (wichtig)
- **Es gibt keinen DB-Insert/Update bevor nicht:**
  - UserID extrahiert wurde **und**
  - SSAID (AndroidId) bekannt ist  
- SSAID-Auswahl wird nur im **UiState** gehalten, nicht persistiert.
- **Einziger DB-Write**: am Ende nach Schritt 9 (nach erfolgreichem Backup+Extraktion).

### 0.3 Duplikate-Regeln
- **User ID existiert bereits (DB)** → **Abbruch** mit Meldung **wie im Repo**.
- **Name existiert bereits (DB oder Ordner)** → nicht abbrechen, sondern suffix:
  - `Name`, `Name_1`, `Name_2`, … bis frei.

### 0.4 Abbruchkriterien (nur diese)
Abbruch erfolgt nur, wenn einer dieser Schritte fehlschlägt:
1) App-Daten löschen (`pm clear` / Root-Wipe)
2) SSAID patchen in `settings_ssaid.xml`
3) Backup-Kopieroperationen aus `/data/data/...` oder `settings_ssaid.xml`
4) Extraktion `userId`
5) Extraktion SSAID

Andere Felder optional → dürfen `null` sein.

### 0.5 Logging & Fehler-UX
- Jeder Fehler:
  - Log-Level **ERROR**
  - muss enthalten: **Step**, **Aktion**, **Ursache** (Exception / exitCode / stderr)
- UI zeigt:
  - **„Da ist etwas schief gelaufen.. Prüfe den Log“**
  - optional Retry

---

## 1) Wizard Flow (Tab 2)

### Step 1: Account Name + Präfix
- Nutzer gibt Account-Namen ein
- Präfix aus Settings vorfüllen
- Name validieren (nicht leer, keine Pfadzeichen)
- **Name-Kollision prüfen** (DB + Ordner) → suffix `_1/_2/...` automatisch anwenden
- Danach Weiter

### Step 2: Warnung bestätigen
- Warnung: „Monopoly GO wird zurückgesetzt. Alle lokalen Daten werden gelöscht.“
- Buttons: Abbrechen / Ich verstehe → Weiter

### Step 3: SSAID Auswahl
UI: „Möchtest du eine neue Android ID (SSAID) setzen?“
- Option A: **Ja** → generiere neue SSAID (UUID-ähnlich, 16 hex / passend zur bestehenden Implementierung)
- Option B: **Nein** → lese aktuelle SSAID aus `settings_ssaid.xml` (ABX→XML)
- Ergebnis: `selectedSsaid` im State setzen  
**NICHT in DB speichern.**

### Step 4: Monopoly GO App-Daten löschen
- Root Command:
  - `pm clear com.scopely.monopolygo`
  - alternativ zusätzlich sicherstellen, dass `/data/data/com.scopely.monopolygo/` leer ist (repo-konform)
- Wenn Fehler → Abbruch + Log + UI Meldung

### Step 5: Warten / Stabilisieren
- Kurze Verzögerung (z.B. 1–3s) oder bis Condition erfüllt (Ordner neu angelegt / package ready)

### Step 6: SSAID patchen
- Patch `settings_ssaid.xml` so, dass `package="com.scopely.monopolygo"` den Wert `selectedSsaid` bekommt
- Wenn Fehler → Abbruch

### Step 7: Monopoly GO starten (Initialisierung)
- Start via Root:
  - `am start -n com.scopely.monopolygo/.MainActivity`
- Optional: warte X Sekunden, damit das Spiel initial Dateien erzeugt

### Step 8: Monopoly GO stoppen
- Stop via Root:
  - `am force-stop com.scopely.monopolygo`

### Step 9: Backup durchführen + Extraktion + SSAID prüfen
Jetzt wird der normale Backup-Core aus **P3 FINAL** wiederverwendet:

- Kopiere:
  - `/data/data/.../shared_prefs`
  - `/data/data/.../cache/DiskBasedCacheDirectory`
  - `/data/system/users/0/settings_ssaid.xml`
- Extrahiere `userId` aus playerprefs (Pflicht)
- Extrahiere SSAID (Pflicht)  
  (Sollte identisch zu `selectedSsaid` sein; wenn nicht, logge WARNING und speichere die tatsächlich gefundene SSAID.)

### Step 10: UserID Duplikat-Check
- Prüfe nach Extraktion: existiert `userId` bereits in DB?
  - Ja → Abbruch mit Repo-Meldung + Log

### Step 11: DB Insert (einziger Write)
- Schreibe **alle Daten gemeinsam** in die DB:
  - accountName (final suffix-name)
  - prefix
  - backupPath
  - createdAt
  - lastPlayedAt (initial optional 0 oder now – repo-konform)
  - extracted IDs
  - androidId (SSAID)
  - optional Social (bei Tab2 i.d.R. leer)

### Step 12: Erfolg
Erfolgsscreen mit Buttons:
- **Account anzeigen** → Detailseite
- **Monopoly Go starten** → **Start mit Restore**
  - SSAID setzen
  - Account-Daten nach `/data/data/...` kopieren
  - `am start ...`
- **Zur Übersicht**

---

## 2) Wiederverwendung (Pflicht)
- Nutze existierende UseCases/Services aus dem Repo:
  - `CreateBackupUseCase` (für Copy + Extract + DB handling, falls vorhanden)
  - `RestoreBackupUseCase` (für Start mit Restore)
  - `IdExtractor` / ABX Tools
- Tab2 darf nur den zusätzlichen Teil liefern:
  - wipe + ssaid patch + initial start/stop

---

## 3) Entities / Attribute (Pflichtdokumentation)

### Übernommen
- `AccountEntity` (bestehende Struktur)
- Backup-Ordnerstruktur (`backupPath`)
- Logging (LogEntity/Repository)
- Root utilities / command runner

### Neu hinzufügen
- Keine neuen Pflicht-Entity-Felder nur für Tab 2.
- Falls `androidId`/`backupPath`/Extracted IDs noch nicht in Entity existieren: siehe P3 FINAL Migration-Regeln.

### Nicht mehr benötigt
- Keine.

---

## 4) Akzeptanzkriterien
- [ ] Name-Kollision → `_1`, `_2` ohne Abbruch
- [ ] UserID-Kollision → Abbruch mit Repo-Meldung
- [ ] Kein DB-Write vor UserID+SSAID
- [ ] Abbruch nur bei definierten Steps
- [ ] Fehler → Log(ERROR) + UI Text „Da ist etwas schief gelaufen.. Prüfe den Log“
- [ ] Erfolg → Start Button führt „Start mit Restore“ aus

---

## 5) Tests

Schreibe umfassende Unit‑ und Integrationstests für den Flow zum Erstellen eines Accounts. Stelle sicher, dass alle Abbruchkriterien korrekt greifen, insbesondere bei Fehlern in den Schritten App‑Daten löschen, SSAID patchen, Kopieroperationen und Extraktionen. Teste die Duplikatslogik für Name und User ID sowie das genaue Timing des DB‑Inserts; vor Abschluss von Schritt 9 darf kein Datenbank‑Write erfolgen. Simuliere Erfolg und Fehler durch gemockte Root‑ und Dateisystem‑Operationen. Alle Tests müssen in der CI/CD‑Pipeline erfolgreich sein.

Wenn alles umgesetzt ist und alle Tests bestanden sind, **lies die Datei `Rebuild_P5_Account_Detail_FINAL.md` im Verzeichnis `docs/prompts`** und führe alle darin beschriebenen Aufgaben aus.

**Ende des Prompts**
