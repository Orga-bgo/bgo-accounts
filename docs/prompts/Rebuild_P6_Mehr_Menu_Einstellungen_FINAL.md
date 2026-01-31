# Rebuild: Mehr-Menü / Einstellungen – bGO Account Manager v2.0 (P6 FINAL)

## Kontext
Du implementierst das **Mehr-Menü / Einstellungen** der bGO Account Manager App (v2.0).

Stack:
- Kotlin
- MVVM
- Jetpack Compose (Material3)
- DataStore
- Room (read-only aus Sicht dieses Screens)
- libsu (nur indirekt über UseCases)

Das Mehr-Menü ist ein **Konfigurations- und Tool-Bereich**.  
Es enthält **keine** Account-spezifischen Aktionen.

---

## 0) Harte Regeln (bindend)

### 0.1 Was dieses Menü NICHT darf
Das Mehr-Menü darf **nicht**:
- Backups erstellen oder restoren
- Accounts editieren oder löschen
- Root-Operationen automatisch ausführen
- Daten still verändern

Alle Aktionen müssen **explizit** vom Nutzer ausgelöst werden.

---

### 0.2 Fehler- & UX-Standard
- Bei Fehlern:
  - Log-Level **ERROR**
  - UI-Meldung: **„Da ist etwas schief gelaufen.. Prüfe den Log“**
- Keine eigenen Fehlertexte

---

### 0.3 Entities / DB
- Keine neuen Entities
- Keine direkten DB-Schreiboperationen auf `AccountEntity`
- Nur DataStore + explizite UseCases (Export/Import/SSH)

---

## 1) Struktur des Mehr-Menüs

Empfohlene Sektionen (Scrollable List):

1) **Allgemein**
2) **Backup & Speicher**
3) **Import / Export**
4) **SSH / Server**
5) **Über die App**

---

## 2) Sektion: Allgemein

### 2.1 Standard-Prefix
- TextField: „Standard-Präfix“
- Wert aus DataStore (`defaultPrefix`)
- Änderung:
  - wird **sofort** im DataStore gespeichert
  - wirkt **nur für zukünftige Backups**

Validierung:
- nicht leer
- keine Pfadzeichen

---

## 3) Sektion: Backup & Speicher

### 3.1 Backup-Verzeichnis
- Anzeige des aktuellen Backup-Pfads
- Button: „Backup-Verzeichnis ändern“

Verhalten:
- Öffnet SAF / Directory Picker
- Neuer Pfad wird im DataStore gespeichert
- **Keine** automatische Verschiebung bestehender Backups
- Hinweistext:
  > „Bestehende Backups werden nicht verschoben.“

---

## 4) Sektion: Import / Export

### 4.1 Export (ZIP)
- Button: „Alle Backups exportieren“
- Verhalten:
  - Erstellt ZIP aller Backup-Ordner + Metadaten
  - Speichert ZIP im Download-Ordner oder via SAF
- Während Export:
  - Progress-Anzeige
- Fehler:
  - Log(ERROR) + Standard-Fehlermeldung

### 4.2 Import (ZIP)
- Button: „Backups importieren“
- Vor Start **Pflicht-Warnung**:
  > „Beim Import können bestehende Backups überschrieben werden.“

- Verhalten:
  - ZIP auswählen
  - Entpacken in Backup-Verzeichnis
  - **Merge-Verhalten**:
    - vorhandene Accounts bleiben bestehen
    - neue Accounts werden ergänzt
    - Namenskollision → `_1`, `_2`, …
    - UserID-Kollision → Import dieses Accounts **überspringen** + Log(ERROR)
- DB:
  - Accounts aus ZIP werden in DB ergänzt (nach gleichen Regeln wie Backup)

---

## 5) Sektion: SSH / Server

### 5.1 SSH aktivieren
- Toggle: „SSH-Synchronisation aktivieren“
- Wert wird im DataStore gespeichert
- Aktivieren **führt keinen Test aus**

### 5.2 SSH-Konfiguration
Felder:
- Host
- Port
- Benutzername
- Passwort

Speicherung:
- Passwort wie im bestehenden Repo (lokal, kein zusätzlicher Crypto)

### 5.3 SSH testen (manuell)
- Button: „SSH-Verbindung testen“
- Führt **expliziten** Test aus:
  - Verbindung aufbauen
  - Testdatei schreiben/lesen (oder `ls`)
- Ergebnis:
  - Erfolg → Toast „Verbindung erfolgreich“
  - Fehler → Log(ERROR) + Standard-Fehlermeldung

### 5.4 Automatischer Server-Check
- Toggle: „Beim App-Start auf neuere Server-Backups prüfen“
- Wert aus DataStore (`sshAutoCheckOnStart`)
- Wird **nur** beim App-Start ausgewertet (siehe Appstart-Prompt)

---

## 6) Sektion: Über die App

Anzeige:
- App-Name
- Version
- Build-Nummer
- Kurzer Text:
  > „bGO Account Manager – lokales Backup-Tool für Monopoly GO Accounts.“

Optional:
- GitHub-Link (nur Anzeige)

---

## 7) Logging (Pflicht)

- Jede Aktion im Mehr-Menü loggt:
  - INFO bei Erfolg
  - ERROR bei Fehler
- Logs sind ausschließlich über **Bottom-Menü → Log** sichtbar

---

## 8) Akzeptanzkriterien

- [ ] Kein Backup/Restore/Account-Edit im Mehr-Menü
- [ ] Prefix & Backup-Pfad werden im DataStore gespeichert
- [ ] Export erzeugt ZIP aller Backups
- [ ] Import nutzt Merge-Verhalten (kein globales Replace)
- [ ] UserID-Kollision beim Import → überspringen + Log
- [ ] SSH-Test nur manuell
- [ ] Auto-Server-Check nur bei Appstart
- [ ] Einheitliche Fehlermeldung
- [ ] Keine neuen Entities

---

## 9) Tests

Implementiere umfassende Tests für das Mehr‑Menü bzw. den Einstellungen‑Screen. Überprüfe, dass keine verbotenen Aktionen wie Backup, Restore oder Account‑Editing ausgeführt werden und dass alle Konfigurationswerte (Präfix, Backup‑Pfad, SSH‑Einstellungen, Auto‑Server‑Check) korrekt im DataStore gespeichert und geladen werden. Teste Export und Import der Backups (inklusive Merge‑Verhalten und Behandlung von UserID‑Kollisionen), den manuell auslösbaren SSH‑Test und den automatischen Server‑Check beim App‑Start. Überprüfe, dass jede Aktion ein Log erzeugt und dass Fehlermeldungen einheitlich dargestellt werden. Alle Tests müssen in der CI/CD‑Pipeline ohne Fehler bestehen.

Wenn alles umgesetzt ist und alle Tests bestanden sind, sind alle Rebuild‑Prompts abgeschlossen; es gibt keine weitere Rebuild‑Datei zu bearbeiten.

**Ende des Prompts**
