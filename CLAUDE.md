# CLAUDE.md – bGO Account Manager v2.0 Rebuild Guide

Dieses Dokument fasst die Rebuild-Prompts (P1–P6) zusammen und dient als zentrale Anleitung für die App-Implementierung.

## Tech-Stack & Architektur
- Kotlin, MVVM, Jetpack Compose (Material3)
- Hilt (DI)
- Room + DataStore
- libsu (Root)

## Globale Regeln
- **App-Start-Gates:** Weiter nur wenn **Root verfügbar** *und* **Monopoly GO installiert**.
- **Logging:** Bei Fehlern Log-Level **ERROR** mit Step/Aktion/Ursache; UI meldet: „Da ist etwas schief gelaufen.. Prüfe den Log“ (oder „Da lief etwas schief .. Prüfe den Log.“ je nach Screen-Spezifikation).
- **Start mit Restore:** Immer SSAID aus DB setzen → Backup-Daten nach `/data/data/com.scopely.monopolygo/` kopieren → App starten (`am start -n com.scopely.monopolygo/.MainActivity`).
- **Keine zusätzlichen Quick-Actions:** Actions exakt wie spezifiziert pro Screen.

## P1 – App-Start & Onboarding
- **Splash (500ms)** → First launch? → **Onboarding** oder **System Checks** → Home.
- **Onboarding-Schritte:** Willkommen → Import-Check (ZIP) → Präfix → SSH (optional) → Backup-Ordner → Root & Permissions → Fertig.
- DataStore verwaltet AppState inkl. Onboarding-Status, Prefix, Backup-Pfad, SSH-Config, System-Check-Flags.

## P2 – Startseite (Accounts)
- **Aktueller Account:** Account mit neuestem `lastPlayedAt`; wenn keiner → „Noch keine Daten vorhanden..“.
- **„Starte Monopoly Go“:** setzt SSAID, kopiert Account-Daten, startet App; Fehler → Toast „Da lief etwas schief .. Prüfe den Log.“
- **Suche & Sortierung:** Sort-Option + ASC/DESC, letzte Such-Query persistieren.
- **Navigation:** Klick auf Card → Detailseite. Keine Quick-Actions.
- **Global Header:** babixGO + Help-Icon (Help → PopUp „Platzhalter – Folgt noch..“). Kein Refresh.
- **DataStore Keys:** LAST_SEARCH_QUERY, ACCOUNTS_SORT_OPTION, ACCOUNTS_SORT_DIRECTION.

## P3 – Backup (Tab 1: Account sichern)
- Abbruch **nur** bei Fehlern in Kopieren, SSAID-Extraktion, UserId-Extraktion.
- **UserID-Duplikat:** Abbruch (Repo-Meldung). **Name-Duplikat:** automatisch suffix `_1`, `_2`, …
- **DB-Insert:** erst nach erfolgreicher UserId+SSAID-Extraktion (keine früheren Writes).
- **Erfolg:** Buttons „Account anzeigen“, „Monopoly Go starten“ (Start mit Restore), „Zur Übersicht“.

## P4 – Backup (Tab 2: Account erstellen)
- **DB-Write erst am Ende** (nach Backup + Extraktion).
- **Flow:** Name+Präfix → Warnung → SSAID-Auswahl (neu/aktuell) → App-Daten löschen → warten → SSAID patchen → App starten/stoppen → Backup+Extraktion → UserID-Duplikat-Check → DB-Insert → Erfolg.
- Abbruch nur bei definierten Steps (wipe/patch/copy/extract).

## P5 – Account-Detailseite
- **Exakt drei Aktionen:** Restore, Edit, Delete.
- **Edit:** ändert nur DB, keine Side-Effects. Änderungen wirken beim nächsten Restore.
- **Delete:** löscht Backup-Ordner, dann DB-Eintrag, mit Warn-Dialog.
- **Copy-to-Clipboard:** jede Key-Value-Zeile kopierbar.
- **Logs:** nur über Bottom-Menü → Log.

## P6 – Mehr-Menü / Einstellungen
- **Keine** Backups/Restores/Account-Edits/Auto-Root-Aktionen.
- **Allgemein:** Standard-Präfix (DataStore, sofort speichern, validieren).
- **Backup & Speicher:** Backup-Pfad via SAF ändern, keine automatische Verschiebung.
- **Import/Export:** ZIP Export aller Backups; Import mit Merge (Name suffix, UserID-Kollision → skip + Log).
- **SSH:** Toggle, Konfig-Felder, manueller Test (Toast Erfolg / Standardfehler), Auto-Check nur beim App-Start.
- **Über die App:** App-Name, Version, Build, kurzer Text.

## Entities / Persistenz – Überblick
- **AccountEntity bleibt Basis** (id, accountName, userId, createdAt, lastPlayedAt, androidId, backupPath, weitere IDs).
- **DataStore:** Onboarding-Flags, Prefix, Backup-Pfad, SSH-Settings, Last Search Query, Sort Option & Direction.
- **Keine neuen Entities** in P3–P6.
