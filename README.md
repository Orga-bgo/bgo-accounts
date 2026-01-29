# MGO Manager - Android Root Backup Tool

Eine Android-App zum Erstellen und Wiederherstellen von Monopoly Go Account-Backups mit Root-Zugriff.

## Features

- ✅ **Backup System**: Vollständige Backups von Monopoly Go Accounts
- ✅ **Restore System**: Wiederherstellung von Backups mit Berechtigungen
- ✅ **ID Extraktion**: Automatische Extraktion von User ID, GAID, Device Token, App Set ID, SSAID
- ✅ **Account Management**: Übersicht, Detail-Ansicht, Bearbeiten, Löschen
- ✅ **Facebook Integration**: Optionale Speicherung von Facebook-Login-Daten
- ✅ **Statistics**: Anzeige von Gesamtanzahl, Fehler und Sus-Accounts
- ✅ **Logging System**: Session-basierte Logs mit Fehlerprotokollierung
- ✅ **Settings**: Anpassbare Backup-Pfade und Account-Präfixe

## Technologie Stack

- **Sprache**: Kotlin
- **Architektur**: MVVM (Model-View-ViewModel)
- **UI Framework**: Jetpack Compose mit Material3
- **Dependency Injection**: Hilt
- **Datenbank**: Room Database
- **Root Access**: libsu (TopJohnWu)
- **Storage**: DataStore für App-Einstellungen

## Voraussetzungen

- **Root-Zugriff**: Die App benötigt Root-Rechte
- **Android Version**: Android 9 (API 28) oder höher
- **Monopoly Go**: Muss installiert sein

## Installation

1. Lade die Debug APK aus den GitHub Actions Artifacts herunter
2. Installiere die APK auf einem gerooteten Gerät
3. Erteile Root-Berechtigung wenn gefragt
4. Erteile Storage-Permissions

## Build

### Debug APK

```bash
./gradlew assembleDebug
```

### Via GitHub Actions

Bei jedem Push auf `main` oder `claude/*` Branches wird automatisch eine Debug APK erstellt und als Artifact hochgeladen.

## Projekt-Struktur

```
com.mgomanager.app/
├── data/
│   ├── local/
│   │   ├── database/        # Room Entities & DAOs
│   │   └── preferences/     # DataStore
│   ├── repository/          # Repository Pattern
│   └── model/               # Domain Models
├── domain/
│   ├── usecase/             # Business Logic
│   └── util/                # Utilities (Root, Permissions, ID Extraction)
├── ui/
│   ├── screens/             # Compose Screens
│   ├── components/          # Reusable UI Components
│   ├── theme/               # Material3 Theme
│   └── navigation/          # Navigation Graph
└── di/                      # Hilt Modules
```

## Dokumentation

Die vollständige Spezifikation befindet sich in `docs/prompts/CLAUDE.md`.

Entwicklungsprompts:
- [Prompt 1: Project Setup & Base Architecture](docs/prompts/Initial_Entwicklungsprompt_1.md)
- [Prompt 2: Data Model & Database](docs/prompts/Initial_Entwicklungsprompt_2.md)
- [Prompt 3: Backup Logic](docs/prompts/Initial_Entwicklungsprompt_3.md)
- [Prompt 4: Restore Logic](docs/prompts/Initial_Entwicklungsprompt_4.md)
- [Prompt 5: UI Overview & Navigation](docs/prompts/Initial_Entwicklungsprompt_5.md)
- [Prompt 6: UI Detail & Editing](docs/prompts/Initial_Entwicklungsprompt_6.md)
- [Prompt 7: Settings & Logging](docs/prompts/Initial_Entwicklungsprompt_7.md)

## Sicherheitshinweise

⚠️ **Diese App ist ausschließlich für den persönlichen Gebrauch gedacht.**

- Backups enthalten sensible Spieldaten
- Facebook-Credentials werden unverschlüsselt gespeichert
- Keine Netzwerkkommunikation
- Alle Operationen erfolgen lokal auf dem Gerät

## Lizenz

Persönliches Tool - Nicht für kommerzielle Nutzung

## Entwickler

Entwickelt mit KI-Unterstützung (Claude) basierend auf strukturierten Entwicklungsprompts.

---

**Version**: 1.0.0
**Build**: See GitHub Actions
