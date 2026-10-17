# Primary Suite

An Android admin app for the [Simple Games](https://github.com/paulhbarker/simple-games) live event games. It connects to the event WiFi network and talks to the game server over WebSocket.

Primary Suite started as LuckyTap, an NFC companion for the Lucky Tap game. It is being reworked into a suite with tools for every game; see [`plans/roadmap.md`](plans/roadmap.md) for progress. Today it has the Lucky Tap tools described below.

## Features

- **Game list**: every Simple Games game, with WiFi and game server status. Lucky Tap, Jeopardy!, State Switch, Reel Rush and Block Stacker have tools; the others are coming.
- **Jeopardy! host**: the web host controller on the phone: lobby, board, clues with a hidden-until-peeked response, buzzers, judging, Daily Double wagers, players (scores, control, buttons, drain), undo and settings. The phone vibrates when someone buzzes in.
- **State Switch admin**: game type, button timeout and limit, Reset Actuations and Reset All, as on the web, plus a Switch button that flips the display like the physical button.
- **Reel Rush admin**: lobby, start, close and replay rounds, end and reset (confirmed), players with remove, round results and final standings, and every setting with local range checks.
- **Block Stacker admin**: max levels, speeds and game enabled (saved together, as on the web), a test button press, game status and a live message log.
- **Lucky Tap scanner**: read, check in and check out NFC cards, sent to the server as they're tapped, or write a number (1–100) to a blank card.
- **Lucky Tap game**: live checked-in cards with each card's odds and this round's scans, the last draw's winners, winners per draw, Check in all, Scan all, Clear scans and Reset game.
- **Automatic WiFi**: joins the event network on launch and reconnects to the server with backoff.
- **Settings**: override the WiFi network and server address; the password is stored encrypted (`EncryptedSharedPreferences`).
- **Android 6.0 (API 23) to 15 (API 36)**. NFC is only needed for scanning.

## How It Works

```
┌──────────────┐      WiFi       ┌──────────────┐    WebSocket     ┌──────────────┐
│  NFC Tag     │ ◄──── tap ────► │ Primary Suite│ ◄──────────────► │  Backend     │
│  (NDEF)      │                 │  Android App │    /ws/lucky-tap  │  Server      │
└──────────────┘                 └──────────────┘                   └──────────────┘
```

1. **Startup** — The app requests permissions, checks NFC availability, connects to the target WiFi network, then opens a WebSocket to the backend.
2. **Scanning** — The user selects a mode (Read, Check-In, or Check-Out). The screen enters a fullscreen scanning state with an animated scan bar. When an NFC tag is tapped, the NDEF payload is read.
3. **Reporting** — The scanned number is sent to the backend as a JSON WebSocket message (e.g. `{"type": "nfc-check-in", "payload": {"number": 42}}`). A success animation displays the number briefly, then scanning resumes.
4. **Writing** — In Write mode, the user enters a number (1–100) and taps a blank tag to write an NDEF text record.

### Architecture

| Component | File | Responsibility |
|---|---|---|
| **MainActivity** | `MainActivity.kt` | The only activity: Compose host, app-wide WiFi setup, NFC adapter and tag dispatch |
| **PrimarySuiteNavHost** | `ui/PrimarySuiteNavHost.kt` | Routes: home, settings, `game/{slug}` |
| **HomeViewModel** | `ui/home/` | WiFi and game server status, the game list |
| **SettingsViewModel** | `ui/settings/` | WiFi and server overrides with validation |
| **GameCatalog** | `catalog/GameCatalog.kt` | The games the suite lists, and which have tools |
| **ReelRushScreen / ReelRushViewModel** | `games/reelrush/` | Reel Rush admin over `/ws/reel-rush` |
| **BlockStackerScreen / BlockStackerViewModel** | `games/blockstacker/` | Block Stacker admin over `/ws/block-stacker` |
| **StateSwitchScreen / StateSwitchViewModel** | `games/stateswitch/` | State Switch admin over `/ws/state-switch` |
| **JeopardyHostScreen / JeopardyHostViewModel** | `games/jeopardy/` | Jeopardy! host controller over `/ws/jeopardy` |
| **LuckyTapScreen / LuckyTapViewModel** | `games/luckytap/` | Lucky Tap scanning station: read, check-in/out, write, reset, clear |
| **WebSocketManager** | `core/net/WebSocketManager.kt` | One game's socket (`/ws/{slug}`): connect, send, receive, reconnect with backoff |
| **ServerProbe** | `core/net/ServerProbe.kt` | HTTP check that the game server answers |
| **GameMessage** | `core/protocol/GameMessage.kt` | `{type, payload}` encoding and decoding |
| **WifiController / WifiSetupCoordinator** | `core/wifi/` | Joins the event network; permission and settings prompts |
| **NfcHub / NfcHelper / NdefCodec** | `core/nfc/` | NFC state and tag routing, NDEF tag I/O, text-record parsing |
| **AppPreferences / ServerConfig** | `core/config/` | Settings storage (`EncryptedSharedPreferences` for the password) and server URLs |

### Adding a game

1. Add its screen under `games/<slug>/`, using `gameSocket(slug, ...)` for its channel.
2. Set `hasTools = true` for it in `GameCatalog` and add its route in `PrimarySuiteNavHost` (the `game/{slug}` destination).

## Requirements

- **Android Studio** Ladybug or newer (AGP 9.2+)
- **JDK 11+**
- **Android SDK** with API 36 (compile) and API 23+ (minimum)
- An **NFC-capable** Android device (NFC is required — `android.hardware.nfc`)
- A **backend WebSocket server** listening on the configured IP/port at `/ws/lucky-tap`
- A **WiFi network** the app can connect to (typically a dedicated event network)

## Setup

### 1. Clone the repository

```bash
git clone https://github.com/paulhbarker/primary-suite.git
cd primary-suite
```

### 2. Configure `local.properties`

Add your environment-specific values to `local.properties` in the project root (this file is gitignored):

```properties
# Android SDK location (usually auto-set by Android Studio)
sdk.dir=/path/to/Android/sdk

# WiFi network the app should auto-connect to
primarysuite.wifi.ssid=YourNetworkName
primarysuite.wifi.password=YourNetworkPassword

# WebSocket server address
primarysuite.ws.ip=10.0.0.1
primarysuite.ws.port=8080
```

These values become compile-time defaults via `BuildConfig`. Users can override them at runtime from the in-app Settings screen.

### 3. Open in Android Studio

Open the project folder in Android Studio. Gradle sync will download all dependencies automatically.

## Build

### Debug

```bash
./gradlew assembleDebug
```

The APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

### Tests

```bash
./gradlew testDebugUnitTest
```

All tests run on the JVM (JUnit, Robolectric, MockWebServer); no emulator or device is needed. CI runs them on every push and pull request.

To try a debug build on a USB-connected phone from WSL, run `scripts/device-smoke.sh`. It builds, installs and launches the app using the Windows `adb.exe`.

### Release

```bash
./gradlew assembleRelease
```

Release builds have ProGuard minification and resource shrinking enabled. Configure signing in `local.properties`:

```properties
primarysuite.storeFile=primarysuite-release.jks
primarysuite.storePassword=your_keystore_password
```

## License

<!-- Add your license here -->

This project is licensed under the [MIT License](LICENSE).
