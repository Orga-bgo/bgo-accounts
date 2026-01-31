# bGO Account Manager – Android Root Backup Tool

Eine Android‑App zum Erstellen und Wiederherstellen von Monopoly GO‑Account‑Backups mit Root‑Zugriff.  Diese README wurde für Version 2.0 angepasst und fasst die wichtigsten Informationen zum Projekt sowie den neuen Rebuild‑Prozess zusammen.

## Features

- ✅ **Backup System**: Vollständige Backups von Monopoly GO‑Accounts
- ✅ **Restore System**: Wiederherstellung von Backups mit Berechtigungen
- ✅ **ID‑Extraktion**: Automatische Extraktion von User ID, GAID, Device Token, App Set ID, SSAID
- ✅ **Account‑Management**: Übersicht, Detail‑Ansicht, Bearbeiten, Löschen
- ✅ **Facebook‑Integration**: Optionale Speicherung von Facebook‑Login‑Daten
- ✅ **Statistics**: Anzeige von Gesamtanzahl, Fehlern und verdächtigen Accounts
- ✅ **Logging System**: Session‑basierte Logs mit Fehlerprotokollierung
- ✅ **Settings**: Anpassbare Backup‑Pfade und Account‑Präfixe

## Technologie‑Stack

- **Sprache**: Kotlin
- **Architektur**: MVVM (Model‑View‑ViewModel)
- **UI‑Framework**: Jetpack Compose mit Material3
- **Dependency Injection**: Hilt
- **Datenbank**: Room Database
- **Root Access**: libsu (TopJohnWu)
- **Storage**: DataStore für App‑Einstellungen

## Voraussetzungen

- **Root‑Zugriff**: Die App benötigt Root‑Rechte
- **Android Version**: Android 9 (API 28) oder höher
- **Monopoly GO**: Muss installiert sein

## Installation

1. Lade die Debug‑APK aus den GitHub Actions Artifacts herunter.
2. Installiere die APK auf einem gerooteten Gerät.
3. Erteile Root‑Berechtigung, wenn danach gefragt wird.
4. Erteile Storage‑Permissions.

## Build

### Debug‑APK

```bash
./gradlew assembleDebug
```

### Via GitHub Actions

Bei jedem Push auf `main` oder `claude/*`‑Branches wird automatisch eine Debug‑APK erstellt und als Artifact hochgeladen.

## Projekt‑Struktur

```
com.mgomanager.app/
├── data/
│   ├── local/
│   │   ├── database/        # Room Entities & DAOs
│   │   └── preferences/     # DataStore
│   ├── repository/          # Repository Pattern
│   └── model/               # Domain Models
├── domain/
│   ├── usecase/             # Business Logic
│   └── util/                # Utilities (Root, Permissions, ID Extraction)
├── ui/
│   ├── screens/             # Compose Screens
│   ├── components/          # Reusable UI‑Komponenten
│   ├── theme/               # Material3 Theme
│   └── navigation/          # Navigation Graph
└── di/                      # Hilt Modules
```

## Dokumentation und Prompts

Die vollständige Spezifikation der Version 1.x befindet sich weiterhin in `docs/prompts/CLAUDE.md`. Für Version 2.0 wurden die ursprünglichen Entwicklungsprompts durch sechs **Rebuild‑Prompts** ersetzt, die schrittweise abgearbeitet werden müssen. Jede Rebuild‑Datei beschreibt einen Teilbereich der Anwendung und endet mit einem umfassenden Testkatalog. Nachdem alle Aufgaben implementiert und die Tests bestanden wurden, weist jede Datei auf die nächste Rebuild‑Datei hin.

### Rebuild‑Prompts (v2.0)

| Datei                                 | Inhalt                                                                                                           |
|---------------------------------------|------------------------------------------------------------------------------------------------------------------|
| **Rebuild_P1_Appstart.md**            | Splash‑Screen, Onboarding und System‑Checks beim App‑Start.                                                      |
| **Rebuild_P2_Startseite.md**          | Accounts‑Übersicht, Suche/Sortierung, aktueller Account und Navigation zur Detailseite.                           |
| **Rebuild_P3_Backup_FINAL.md**        | Definition des vollständigen Backup‑Flows einschließlich Abbruch‑ und Duplikatsregeln.                           |
| **Rebuild_P4_Backup_Part2_AccErstellen_FINAL.md** | Anlegen neuer Accounts mit Extraktion von User ID und SSAID sowie Handling von Namensduplikaten.           |
| **Rebuild_P5_Account_Detail_FINAL.md** | Account‑Detailseite mit Restore/Edit/Delete‑Aktionen und Kopier‑Funktion für Felder.                              |
| **Rebuild_P6_Mehr_Menu_Einstellungen_FINAL.md**    | Mehr‑Menü und Einstellungen mit Export/Import, Präfix & Backup‑Pfad, SSH‑Test und automatischem Server‑Check. |

Die Rebuild‑Prompts befinden sich im Verzeichnis `docs/prompts/` und müssen **in der oben angegebenen Reihenfolge** bearbeitet werden. Nach Abschluss eines Prompts sind die darin beschriebenen Tests auszuführen; erst wenn alle Tests erfolgreich sind, darf mit dem nächsten Prompt fortgefahren werden.

## Sicherheitshinweise

⚠️ **Diese App ist ausschließlich für den persönlichen Gebrauch gedacht.**

- Backups enthalten sensible Spieldaten.
- Facebook‑Credentials werden unverschlüsselt gespeichert.
- Es findet keine Netzwerkkommunikation mit externen Servern statt.
- Alle Operationen erfolgen lokal auf dem Gerät.

## Lizenz

Persönliches Tool – nicht für kommerzielle Nutzung vorgesehen.
