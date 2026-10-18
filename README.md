# Primary Suite

An Android admin app for the [Simple Games](https://github.com/paulhbarker/simple-games) live event games. It connects to the event WiFi network and talks to the game server over WebSocket.

It opens on a list of the Simple Games games, and each one has its admin tools: the same controls as the game's web admin page, built for a phone. Primary Suite started as LuckyTap, an NFC companion for the Lucky Tap game. The plans and progress logs are in [`plans/`](plans/), starting with [`plans/roadmap.md`](plans/roadmap.md).

## Features

- **Game list**: every Simple Games game, with WiFi and game server status.
- **Lucky Tap scanner**: read, check in and check out NFC cards, sent to the server as they're tapped, or write a number (1–100) to a blank card.
- **Lucky Tap game**: live checked-in cards with each card's odds and this round's scans, the last draw's winners, winners per draw, Check in all, Scan all, Clear scans and Reset game.
- **Jeopardy! host**: the web host controller on the phone: lobby, board, clues with a hidden-until-peeked response, buzzers, judging, Daily Double wagers, players (scores, control, buttons), a drain-points screen, undo and settings. The phone vibrates when someone buzzes in.
- **State Switch admin**: game type, button timeout and limit, Reset Actuations and Reset All, as on the web, plus a Switch button that flips the display like the physical button.
- **Reel Rush admin**: lobby, start, close and replay rounds, end and reset (confirmed), players with remove, round results and final standings, and every setting with local range checks.
- **Block Stacker admin**: max levels, speeds and game enabled (saved together, as on the web), a test button press, game status and a live message log.
- **Button Debugger**: the web's button diagnostics: live tiles per button, edge timing, noise and stuck-line checks, Wi-Fi button stats, round-trip time, LED tests, debounce and the edge log.
- **Game-button stand-ins** (State Switch's Switch, Block Stacker's test press) fire on touch, like the arcade buttons.
- **Automatic WiFi**: joins the event network on launch and sends the app's traffic over it, even though it has no internet. Game screens reconnect on their own after a drop or sleep (0.5, 1, 2, 4 s, then every 5 s, never giving up).
- **Settings**: override the WiFi network and server address; the password is stored encrypted (`EncryptedSharedPreferences`).
- **Android 6.0 (API 23) to 15 (API 36)**. NFC is optional; only Lucky Tap's scanner needs it.

## How It Works

```
┌──────────────┐  event WiFi (GroveParkPrimary)  ┌───────────────────────────┐
│ Primary Suite│ ◄──── WebSocket /ws/{slug} ───► │  Simple Games server      │
│  Android App │ ◄──── HTTP (boards, probe) ───► │  on the Pi, 10.0.0.1:8080 │
└──────────────┘                                 └───────────────────────────┘
        ▲ NFC (Lucky Tap only)                         ▲ button box, displays
┌──────────────┐
│  NFC cards   │
└──────────────┘
```

1. **Startup**: the app asks for location permission (Android needs it to join a WiFi network), joins the event network, and checks that the game server answers.
2. **Game screens**: each game opens its own socket to `/ws/{slug}` while its screen is open, and closes it on the way out. The server is authoritative: screens show what it sends, and send the same `{type, payload}` messages as the game's web admin. Jeopardy! and Reel Rush say hello as host/admin on every connect, as the web does.
3. **Lucky Tap**: in Read, Check-In or Check-Out mode, a tapped card's NDEF number is sent to the server (e.g. `{"type": "nfc-check-in", "payload": {"number": 42}}`). Write mode writes a number to a blank card.

### Architecture

| Component | File | Responsibility |
|---|---|---|
| **MainActivity** | `MainActivity.kt` | The only activity: Compose host, app-wide WiFi setup, NFC adapter and tag dispatch |
| **PrimarySuiteNavHost** | `ui/PrimarySuiteNavHost.kt` | Routes: home, settings, `game/{slug}` |
| **HomeViewModel** | `ui/home/` | WiFi and game server status, the game list |
| **SettingsViewModel** | `ui/settings/` | WiFi and server overrides with validation |
| **GameCatalog** | `catalog/GameCatalog.kt` | The games the suite lists, and which have tools |
| **ReelRushScreen / ReelRushViewModel** | `games/reelrush/` | Reel Rush admin over `/ws/reel-rush` |
| **ButtonDebugScreen / ButtonDebugViewModel** | `games/buttondebug/` | Button Debugger over `/ws/button-debug` |
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

1. Write `plans/<game>.md` first: the web admin's controls, the server's messages (read the Go code), and the tests. `plans/state-switch.md` is a short example.
2. Add `games/<pkg>/` with a protocol file (parsers and command builders), a view model taking `(wifi: WifiConnector, socketFactory: GameSocketFactory)`, and a stateless screen plus a `Route` composable. Reuse `core/ui` (`Picker`, `OfflineBadge`, `PressButton`).
3. Add its view model to `suiteViewModelFactory` and its route to the `game/{slug}` destination in `PrimarySuiteNavHost`, and set `hasTools = true` in `GameCatalog`.
4. Test the protocol, the view model (with `FakeGameConnection`) and the screen (Robolectric), and add a navigation test.

## Requirements

- **Android Studio** Ladybug or newer (AGP 9.2+)
- **JDK 11+**
- **Android SDK** with API 36 (compile) and API 23+ (minimum)
- An Android phone or tablet; **NFC** only for Lucky Tap scanning
- The **Simple Games server** on the configured IP/port (`/ws/{slug}` for each game)
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
scripts/test.sh                                        # the whole suite
scripts/test.sh 'com.primarysuite.app.games.jeopardy.*' # one package, one fork
```

All tests run on the JVM (JUnit, Robolectric, MockWebServer); no emulator or device is needed. CI runs them on every push and pull request. `scripts/test.sh` runs Gradle through `scripts/gradle-locked.sh`, which keeps to one build at a time (the WSL machine has 8 GB; see `AGENTS.md`), and prints a short summary with each failure.

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
