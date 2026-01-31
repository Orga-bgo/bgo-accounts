# Rebuild: Account-Detailseite – bGO Account Manager v2.0 (P5 FINAL)

## Kontext
Du implementierst die **Account-Detailseite** der bGO Account Manager App (v2.0).

Stack:
- Kotlin
- MVVM
- Jetpack Compose (Material3)
- Room
- Hilt
- libsu (Root)

Die Detailseite ist die **zentrale Verwaltungsseite für einen einzelnen Account**.

⚠️ Logs sind **nicht** Teil dieser Seite und sind ausschließlich über das **Bottom-Menü → Log** erreichbar.

---

## 0) Harte Regeln (bindend)

### 0.1 Aktionen
Die Detailseite besitzt **exakt drei Aktionen**:
- **Restore**
- **Edit**
- **Delete**

Keine weiteren Buttons oder Quick-Actions.

---

### 0.2 Restore (Start mit Restore)
- Restore bedeutet **immer**:
  1) SSAID (Android ID) aus der **DB** setzen
  2) Account-Daten aus dem Backup-Pfad nach `/data/data/com.scopely.monopolygo/` kopieren
  3) Monopoly GO starten (`am start -n com.scopely.monopolygo/.MainActivity`)
- Restore nutzt **ausschließlich DB-Werte**
- Es findet **keine** erneute Extraktion statt

**Fehlerverhalten:**
- Bei Fehler:
  - Log-Level **ERROR**
  - UI-Toast: **„Da ist etwas schief gelaufen.. Prüfe den Log“**

---

### 0.3 Edit (rein DB-seitig)
- **Alle Felder** eines Accounts sind editierbar
- Änderungen:
  - wirken **nur auf die DB**
  - haben **keine direkten Seiteneffekte**
- Änderungen werden **erst beim nächsten Restore** aktiv

Beispiele editierbarer Felder:
- Accountname
- User ID
- Android ID (SSAID)
- extrahierte IDs (Unity, GAID, etc.)
- Social-Daten
- Backup-Pfad

⚠️ Edit darf **keine** Root-Operationen, keine Kopieraktionen und kein Restore auslösen.

---

### 0.4 Delete
- Löscht:
  - DB-Eintrag
  - zugehörigen Backup-Ordner
- Kein Undo

**Warnung (Pflicht):**
> „Dieser Account und alle zugehörigen Backups werden dauerhaft gelöscht.“

- Buttons: Abbrechen / Löschen

---

### 0.5 Copy-to-Clipboard
- **Jeder Eintrag** (Key-Value-Zeile) ist klickbar
- Klick kopiert **den Value** in die Zwischenablage
- Optional: kurzer Toast „Kopiert“

---

## 1) UI-Struktur

### 1.1 Header
- Titel: Accountname
- Zurück-Button
- Keine weiteren Icons (kein Help, kein Refresh)

---

### 1.2 Inhalt
Darstellung als vertikale Liste / Cards:

Empfohlene Gruppierung:
- Basis
- IDs / Tokens
- Social
- Backup / Meta

Jeder Eintrag:
```
Label: Value
```
→ Klick = Copy

---

### 1.3 Aktionen (Bottom oder Floating)
- **Restore**
- **Edit**
- **Delete**

Reihenfolge empfohlen:
1) Restore (Primary)
2) Edit
3) Delete (Destructive)

---

## 2) Edit-Modus

### 2.1 Verhalten
- Wechsel in Edit-Modus via Button
- Alle Felder werden editierbar (TextField)
- Buttons:
  - Speichern
  - Abbrechen

### 2.2 Speichern
- Validierung:
  - Pflichtfelder dürfen nicht leer sein (z.B. Accountname, userId)
- Bei Speichern:
  - Update des AccountEntity in der DB
  - Kein Restore, kein Root-Zugriff
- Erfolg: Rückkehr zur Detailansicht

---

## 3) Restore-Implementierung

- Reuse von bestehendem:
  - `RestoreBackupUseCase`
- Reihenfolge:
  1) SSAID setzen
  2) Backup-Daten kopieren
  3) App starten
- Jeder Schritt wird geloggt (INFO)
- Fehler → Log(ERROR) + Toast

---

## 4) Delete-Implementierung

- Schritte:
  1) Bestätigungsdialog anzeigen
  2) Backup-Ordner löschen
  3) DB-Eintrag löschen
- Reihenfolge ist wichtig:
  - Erst Dateien, dann DB
- Fehler:
  - Log(ERROR)
  - UI-Meldung

---

## 5) Entities / Attribute (Pflicht – Regel F)

### Übernommen
- `AccountEntity` (alle Felder)
- Backup-Verzeichnisstruktur
- Restore- & Root-Utilities
- Log-System

### Neu
- Keine neuen Entities
- Keine neuen Pflichtfelder

### Nicht mehr benötigt
- Keine

---

## 6) Akzeptanzkriterien
- [ ] Exakt 3 Aktionen: Restore / Edit / Delete
- [ ] Restore = Start mit Restore
- [ ] Restore nutzt nur DB-Werte
- [ ] Edit ändert nur DB, ohne Seiteneffekte
- [ ] Delete zeigt Warnung und löscht DB + Backup
- [ ] Alle Felder klickbar → Copy
- [ ] Fehler → Toast + Log-Verweis
- [ ] Logs nur über Bottom-Menü erreichbar

---

## 7) Tests

Erstelle detaillierte Unit‑ und UI‑Tests für die Account‑Detailseite. Überprüfe, dass exakt drei Aktionen vorhanden sind (Restore, Edit, Delete) und dass jede Aktion die spezifizierte Logik ausführt: Restore nutzt ausschließlich Daten aus der Datenbank zum Start‑mit‑Restore, Edit ändert nur DB‑Felder ohne Seiteneffekte, Delete löscht Dateien und DB‑Eintrag in korrekter Reihenfolge nach Bestätigung. Teste, dass alle Felder klickbar und kopierbar sind, Fehlerzustände korrekt geloggt werden und nur über das Bottom‑Menü auf Logs zugegriffen werden kann. Mocke DB‑ und Dateisystem‑Zugriffe. Alle Tests müssen in der CI/CD‑Pipeline erfolgreich ausgeführt werden.

Wenn alles umgesetzt ist und alle Tests bestanden sind, **lies die Datei `Rebuild_P6_Mehr_Menu_Einstellungen_FINAL.md` im Verzeichnis `docs/prompts`** und führe alle darin beschriebenen Aufgaben aus.

**Ende des Prompts**
