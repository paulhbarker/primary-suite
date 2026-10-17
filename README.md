# Primary Suite

An Android admin app for the [Simple Games](https://github.com/paulhbarker/simple-games) live event games. It connects to the event WiFi network and talks to the game server over WebSocket.

Primary Suite started as LuckyTap, an NFC companion for the Lucky Tap game. It is being reworked into a suite with tools for every game; see [`plans/roadmap.md`](plans/roadmap.md) for progress. Today it has the Lucky Tap tools described below.

## Features

- **NFC Read & Write** — Read numbered NFC tags (NDEF text records) or write numbers (1–100) to blank tags
- **Check-In / Check-Out** — Scan NFC tags to register player check-ins and check-outs, reported instantly to the server
- **Live WebSocket Connection** — Sends scan events (`card-scanned`, `nfc-check-in`, `nfc-check-out`) to a backend server with automatic reconnection and exponential backoff
- **Automatic WiFi Management** — Connects to a configurable target WiFi network on launch, with retry logic and support for both legacy (API 23–28) and modern (API 29+) WiFi APIs
- **Connection Status Dashboard** — Material 3 dark-themed UI with a live status card showing NFC, WiFi, and WebSocket connection states
- **Admin Controls** — Reset game state or clear all scans via the server from within the app
- **Configurable Settings** — Override WiFi SSID/password and WebSocket IP/port at runtime; defaults are baked in from `local.properties` at build time
- **Secure Storage** — WiFi password overrides are stored in `EncryptedSharedPreferences` (AES-256-GCM) backed by the Android Keystore
- **Broad Compatibility** — Supports Android 6.0 (API 23) through Android 15 (API 36), with Java 8+ API desugaring

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
| **MainActivity** | `MainActivity.kt` | UI, NFC foreground dispatch, permissions, WiFi/NFC prompts |
| **SettingsActivity** | `SettingsActivity.kt` | Runtime overrides for WiFi and the server address |
| **NfcAppViewModel** | `games/luckytap/NfcAppViewModel.kt` | Lucky Tap scanning state machine and messages |
| **WebSocketManager** | `core/net/WebSocketManager.kt` | One game's socket (`/ws/{slug}`): connect, send, receive, reconnect with backoff |
| **GameMessage** | `core/protocol/GameMessage.kt` | `{type, payload}` encoding and decoding |
| **WifiController** | `core/wifi/WifiController.kt` | Joins the event network via `WifiNetworkSpecifier` (API 29+) or `WifiConfiguration` (legacy) |
| **NfcHelper / NdefCodec** | `core/nfc/` | NDEF tag I/O and text-record parsing |
| **AppPreferences / ServerConfig** | `core/config/` | Settings storage (`EncryptedSharedPreferences` for the password) and server URLs |

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
