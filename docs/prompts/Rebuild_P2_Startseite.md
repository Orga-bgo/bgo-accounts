# Rebuild: Startseite (Accounts Screen) – bGO Account Manager v2.0 (P2)

## Kontext
Du bist ein Android-Entwickler und implementierst **Version 2.0** des bGO Account Managers (Android, Root-basiert).  
Stack: **Kotlin**, **MVVM**, **Jetpack Compose + Material3**, **Hilt**, **Room**, **DataStore**, **libsu**.

Repository: `github.com/Orga-bgo/bgo-accounts`

Dieses Prompt beschreibt ausschließlich die **Startseite / Accounts Screen** (Liste, Suche, Sortierung, „aktueller Account“, Navigation zur Detailseite).

---

## Wichtige Vorgaben (Fix)
A) **App-Start-Gates**: Die App darf nur fortsetzen, wenn **Root verfügbar** ist **und** **Monopoly GO installiert** ist. (Diese Prüfung passiert beim App-Start/Onboarding; auf der Startseite wird nur der Status konsumiert.)

B) **Refresh entfernen**: Der Refresh-Button wird **nicht** mehr implementiert.  
Der **Help-Button** öffnet vorerst ein PopUp mit Text: **„Platzhalter – Folgt noch..“**

C) **„Aktueller Account“ Definition**:  
- Nutze immer den Account mit dem **neusten** `lastPlayedAt` („Zuletzt gespielt am …“).  
- Wenn **kein** Account einen gültigen `lastPlayedAt` hat bzw. noch nie gespielt wurde: zeige **„Noch keine Daten vorhanden..“**

C2) **„Starte Monopoly Go“ Aktion** (vom „Aktueller Account“-Block):
- Setzt die korrekte **Android ID (SSAID)** in `settings_ssaid.xml`
- Kopiert die Account-Daten in `/data/data/com.scopely.monopolygo/…`
- Startet anschließend Monopoly GO
- Bei Fehler: **Toast** anzeigen: **„Da lief etwas schief .. Prüfe den Log.“**

D) Suche/Sortierung:
- Sortierung **asc/desc** muss unterstützt werden
- **Letzte Such-Query merken** (persistieren)
- **Keine weiteren Filter** (z.B. Sus/Error Filter) auf der Startseite

E) Navigation:
- **Klick auf Account-Karte** öffnet **direkt** die Detailseite.
- Keine Quick-Actions/Overflow-Menüs auf der Karte (die gibt es auf der Detailseite).

F) In **jedem** Prompt muss klar dokumentiert sein:
- Welche **Entities/Attribute neu hinzukommen**
- Welche **nicht mehr benötigt** werden
- Welche **übernommen** werden

---

## 1) Globale UI-Komponenten

### 1.1 Header-System (Global)
Wiederverwendbare Header-Komponente mit **Main Header + Sub Header**.
- Im Header: **babixGO** + **Help-Icon**
- Kein Refresh-Icon mehr.

**Help-Verhalten:**  
Beim Klick auf Help: `AlertDialog` oder `ModalBottomSheet` mit Text: **„Platzhalter – Folgt noch..“**

### 1.2 Bottom Navigation (Global)
Bottom Navigation bleibt fest sichtbar im `Scaffold`.  
Tabs: **Accounts**, **Backup**, **Mehr**, **Log**.  
(„Mehr“ öffnet Drawer gemäß `Rebuild_Mehr_Menu.md`)

---

## 2) Startseite Layout (Accounts Screen)

### 2.1 Sektion: „Aktueller Account“
Zeige den Account mit dem **größten `lastPlayedAt`** (neueste Aktivität).

UI (Beispiel):
- Titel: **AKTUELLER ACCOUNT:**
- Wenn vorhanden:
  - Accountname
  - „ID: …“
  - „Zuletzt gespielt am: …“
  - Button: **Starte Monopoly Go**
- Wenn nicht vorhanden:
  - Text: **„Noch keine Daten vorhanden..“** (kein Button)

**Button-Verhalten**: ruft `viewModel.launchMonopolyGoWithAccountState(accountId)` auf (siehe Abschnitt 4.4).

### 2.2 Sektion: Suche & Sortierung
- Suchfeld (OutlinedTextField)
- Sortierung:
  - Dropdown: Sortiere nach (NAME, LAST_PLAYED, CREATED_AT, USER_ID)
  - Toggle/Chip/Button für Richtung: **ASC / DESC**
- Die UI soll **die letzte Such-Query** aus DataStore vorausfüllen.

### 2.3 Sektion: Account-Liste
- `LazyColumn` mit Cards
- Pro Account eine Card, klickbar (ganze Card)
- Anzeige pro Card:
  - Account Name (bold)
  - User ID
  - Zuletzt gespielt am
  - (Optional) kleine Indikatoren (Freundschaft/Sus/Error) nur wenn du sie bereits sauber aus Entity hast — aber **keine** Aktionen hier.

**OnClick**: Navigiert zur Detailseite: `onNavigateToDetail(account.id)`.

### 2.4 Empty States
- Wenn keine Accounts existieren: „Noch keine Backups vorhanden.“
- Wenn Suche aktiv und keine Treffer: „Kein Account mit diesem Namen gefunden.“

---

## 3) Datenmodell / Entities / Persistenz (F – Pflicht)

### 3.1 Übernehmen (bestehend)
**AccountEntity / Account Model** (bestehende Felder bleiben erhalten), insbesondere:
- `id`, `accountName`, `userId`, `createdAt`, `lastPlayedAt`
- weitere Felder (IDs, Social, BackupPath, etc.) werden in anderen Prompts gepflegt und dürfen hier nur gelesen werden.

### 3.2 Neu hinzufügen
**DataStore / Settings (neu):**
- `LAST_SEARCH_QUERY: String` – letzte Suche, wird beim Tippen (debounced) gespeichert
- `ACCOUNTS_SORT_OPTION: String` – aktuelle Sortierung (Enum als String)
- `ACCOUNTS_SORT_DIRECTION: String` – `"ASC"` oder `"DESC"`

Optional (falls noch nicht vorhanden):
- `SYSTEM_STATUS_CACHE` oder Zugriff auf ein `SystemCheck`-State (nur read-only, keine neue Persistenz nötig)

### 3.3 Nicht mehr benötigt (Startseite-spezifisch)
- **Refresh-Button** und dazugehörige Refresh-Logik (`isRefreshing`, `refreshAccounts()` UI-Trigger) wird aus dieser Startseiten-Implementierung entfernt.
- `currentAccountId` aus DataStore wird **für die Anzeige des aktuellen Accounts nicht verwendet** (stattdessen: newest `lastPlayedAt`).  
  Hinweis: Falls `currentAccountId` für andere Flows (Restore-Tracking) weiter gebraucht wird, darf er im Projekt verbleiben – Startseite ignoriert ihn.

---

## 4) ViewModel & Logik

### 4.1 UiState
```kotlin
data class AccountsUiState(
    val accounts: List<Account> = emptyList(),
    val currentAccount: Account? = null,

    val searchQuery: String = "",
    val sortOption: SortOption = SortOption.LAST_PLAYED,
    val sortDirection: SortDirection = SortDirection.DESC,

    val errorMessage: String? = null
)

enum class SortOption { NAME, LAST_PLAYED, CREATED_AT, USER_ID }
enum class SortDirection { ASC, DESC }
```

### 4.2 Datenfluss
- Accounts kommen aus `accountRepository.getAllAccounts()` (Flow).
- Search + Sort werden per `combine` angewendet.
- `currentAccount` wird berechnet als: `accounts.maxByOrNull { it.lastPlayedAt }` **aber nur**, wenn `lastPlayedAt > 0`.

### 4.3 Persistenz: letzte Query + Sort
- Beim Start: lade aus DataStore `LAST_SEARCH_QUERY`, `ACCOUNTS_SORT_OPTION`, `ACCOUNTS_SORT_DIRECTION` und setze initialen State.
- Beim Ändern:
  - `onSearchQueryChange(query)` speichert (debounced) in DataStore.
  - `onSortOptionChange(option)` speichert in DataStore.
  - `toggleSortDirection()` speichert in DataStore.

### 4.4 Monopoly GO starten mit Account-Zustand (C2)
Implementiere im ViewModel eine Funktion:
```kotlin
fun launchMonopolyGoWithAccountState(accountId: Long)
```

Sie muss:
1) Account aus DB laden
2) Restore-/Apply-Schritte ausführen (Root required):
   - SSAID in `/data/system/users/0/settings_ssaid.xml` auf Account-SSAID setzen
   - Backup-Ordner des Accounts nach `/data/data/com.scopely.monopolygo/` kopieren (shared_prefs, cache etc.)
3) Monopoly GO starten: `am start -n com.scopely.monopolygo/.MainActivity`

**Fehlerbehandlung:**
- Jeder Schritt schreibt in den Log (LogRepository / LogScreen)
- Bei irgendeinem Fehler:  
  - Toast: **„Da lief etwas schief .. Prüfe den Log.“**
  - (Optional) `errorMessage` setzen, aber Toast ist Pflicht.

**Wiederverwendung:**
- Wenn im Repo bereits eine Restore-UseCase existiert (z.B. `RestoreBackupUseCase`), dann **reuse** und erweitere ihn so, dass er auch als „Start Game with Account“ genutzt werden kann.

---

## 5) Compose Umsetzung (Kurz)

### 5.1 Clickable Card
- Ganze Card klickbar (`Modifier.clickable { ... }`)
- Kein „More“-Button

### 5.2 Help PopUp
- `AlertDialog` mit Text: **„Platzhalter – Folgt noch..“**
- Button: OK

---

## 6) Akzeptanzkriterien
- [ ] Kein Refresh-Icon / keine Refresh-Logik im Accounts Screen
- [ ] Help öffnet PopUp: „Platzhalter – Folgt noch..“
- [ ] „Aktueller Account“ ist immer der mit neuestem `lastPlayedAt`
- [ ] Wenn kein gültiger `lastPlayedAt`: „Noch keine Daten vorhanden..“
- [ ] „Starte Monopoly Go“ setzt SSAID, kopiert Account-Daten nach `/data/data/...` und startet Monopoly GO
- [ ] Bei Fehler: Toast „Da lief etwas schief .. Prüfe den Log.“
- [ ] Suche + Sortierung mit ASC/DESC
- [ ] Letzte Such-Query wird gespeichert und beim Start wieder geladen
- [ ] Klick auf Account-Card navigiert zur Detailseite

---

## 7) Offene Fragen (minimal)
1) Welches Feld gilt als „Account-Daten“ für Kopie nach `/data/data/...` exakt? (nur `shared_prefs` + `cache` oder weitere Ordner)
2) Soll `lastPlayedAt` bei „Starte Monopoly Go“ aktualisiert werden (vor oder nach Start)?

---

## 8) Tests

Erstelle umfassende Unit‑ und UI‑Tests für alle Akzeptanzkriterien der Startseite. Teste die Such‑ und Sortierlogik inklusive Speicherung der letzten Such‑Query, die Ermittlung des „aktuellen Accounts“ anhand des neuesten `lastPlayedAt`, das korrekte Patchen der SSAID beim Starten von Monopoly GO sowie die Fehlermeldungs‑Logik bei Fehlern. Nutze Jetpack‑Compose‑Testing und Mocking für Root‑ und Dateisystem‑Operationen. Stelle sicher, dass kein Refresh‑Icon vorhanden ist und dass Navigation zur Detailseite funktioniert. Alle Tests müssen in der CI/CD‑Pipeline erfolgreich ausgeführt werden.

Wenn alles umgesetzt ist und alle Tests bestanden sind, **lies die Datei `Rebuild_P3_Backup_FINAL.md` im Verzeichnis `docs/prompts`** und führe alle darin beschriebenen Aufgaben aus.

**Ende des Prompts**
