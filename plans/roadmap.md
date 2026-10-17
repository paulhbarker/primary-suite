# Primary Suite: roadmap

> Status: **Phase 0 not started.** Update the status line and the progress log at the bottom whenever a phase starts or finishes.

## Goal

Turn LuckyTap, a single-purpose NFC companion for the Lucky Tap game, into Primary Suite: one Android app with admin tools for every game in [simple-games](../../simple-games). When the app opens, it lists the games you can administer, which is all of them until roles exist. Lucky Tap is the first game with tools. Jeopardy, State Switch and the rest follow one at a time in later phases.

The existing WiFi, WebSocket, NFC and settings code is reused, not rewritten. Every behaviour gets a fast JVM test.

## Decisions

| Topic | Decision |
|---|---|
| Name | Primary Suite. Launcher label "Primary Suite". |
| Identity | `applicationId` and Kotlin package become `com.primarysuite.app`. It installs as a new app, so uninstall LuckyTap from devices once. The GitHub repo is renamed to `primary-suite`. |
| UI toolkit | Jetpack Compose with one activity and Navigation Compose. The XML layouts, `MainActivity` and `SettingsActivity` are deleted once the Lucky Tap port works. |
| Theme | The current LuckyTap dark palette (`colors.xml`) and the socafe font, ported to a Compose `MaterialTheme`. |
| Game catalog | Hardcoded in the app as a typed list (slug, name, blurb, icon, whether tools exist). No server endpoint yet. Revisit when roles arrive. |
| Games listed | Every game the server hosts: Lucky Tap, Jeopardy!, State Switch, Reel Rush, Block Stacker, Button Debugger. Only Lucky Tap opens. The others show "Tools coming soon". Spin the Wheel is left out because it has no server engine. |
| Roles | None for now. Every game is listed as administrable. |
| WiFi | App-wide. The home screen shows the target network and whether the phone is on it. Auto-connect logic stays as it is in `WifiController`. |
| Server status | The home screen probes `http://{ip}:{port}/` over HTTP. Any HTTP response, a 404 included, counts as reachable. It does not open a socket. |
| WebSocket | One socket per game screen, `ws://{ip}:{port}/ws/{slug}`, opened when the screen opens and closed when it closes. Reuses `WebSocketManager` and its backoff. |
| NFC | Optional in the manifest (`required="false"`). Only screens that need it (Lucky Tap) check it and prompt you to enable it. |
| Lucky Tap scope | Parity with today (Read, Check-in, Check-out, Write, Reset game, Clear scans) **plus** extras the server already supports: live state from `lucky-tap-full-state`, number of winners, Check in all (1-50), and Scan all. |
| Tests | JVM only, no emulator. ViewModel and logic tests use JUnit, `kotlinx-coroutines-test` and fakes. Compose UI tests run on Robolectric. Socket tests use OkHttp `MockWebServer`. Target: `./gradlew testDebugUnitTest` finishes in under 60 s with a warm daemon. |
| Where tests run | Android SDK command-line tools in WSL, and a GitHub Actions job that runs unit tests on every push and PR. |
| Device testing | An older phone connected over USB to Windows, driven from WSL with the Windows `adb.exe` (`/mnt/c/Users/paul/AppData/Local/Android/sdk/platform-tools/adb.exe`). No `usbipd` needed. Automated tests stay on the JVM. The phone is for a short manual smoke check at the end of each phase with UI or hardware changes, plus the real NFC and WiFi paths, which tests can't cover. It also checks the old-API code paths (`minSdk 23`, legacy WiFi). |
| Versioning | release-please. The rename commit is a breaking change (`feat!:`), so open release PR #4 becomes 2.0.0. Release once Lucky Tap reaches parity with the extras. |
| Workflow | Commit straight to `main`, one conventional commit per logical step. Tests pass before every push. |

## Architecture target

```
com.primarysuite.app
├── MainActivity.kt            single activity, NavHost, NFC foreground dispatch
├── core/
│   ├── config/                AppPreferences, ServerConfig (builds ws/http URLs)
│   ├── wifi/                  WifiController behind a WifiConnector interface
│   ├── net/                   WebSocketManager, GameSocket(slug), ServerProbe
│   ├── nfc/                   NfcHelper, NdefCodec (pure parsing), NfcStatus
│   └── protocol/              GameMessage {type, payload} encode/decode
├── catalog/                   Game, GameCatalog (hardcoded list)
├── ui/
│   ├── theme/                 colours and the socafe font
│   ├── home/                  HomeScreen, HomeViewModel
│   ├── settings/              SettingsScreen, SettingsViewModel
│   ├── comingsoon/            ComingSoonScreen
│   └── components/            StatusRow, GameCard, ConfirmDialog
└── games/
    └── luckytap/              LuckyTapScreen, LuckyTapViewModel, LuckyTapMessages, LuckyTapState
```

Each later game adds a folder under `games/` and flips `hasTools` in the catalog. Nothing else in the app changes.

Navigation routes are `home`, `settings`, `game/{slug}`. The `game/{slug}` route looks the slug up in the catalog and shows the game's screen, or `ComingSoonScreen` if the game has no tools yet.

## Lucky Tap protocol (from `simple-games/backend/cmd/lucky_tap.go`)

What the app sends on `/ws/lucky-tap`:

| Type | Payload | Use |
|---|---|---|
| `card-scanned` | `{number}` | Read mode |
| `nfc-check-in` | `{number}` | Check-in mode |
| `nfc-check-out` | `{number}` | Check-out mode |
| `admin-reset-game` | `{}` | Reset weights and check-ins |
| `admin-clear-scans` | `{}` | Clear the current round's scans |
| `admin-set-num-winners` | `{number}` (> 0) | New in phase 5 |
| `admin-check-in-all` | `{}` | Checks in cards 1-50. New in phase 5 |
| `admin-scan-all` | `{}` | Scans every checked-in card. New in phase 5 |
| `admin-get-full-state` | `{}` | Asks for a state refresh. New in phase 5 |

What the app reads (phase 5): `lucky-tap-full-state` (`checkedInNumbers`, `likelihoods`, `currentCollection`, `numWinners`), `checked-in-cards-update`, `card-activated`, `cards-activated-batch`, `current-collection-cleared`, `selected-number-final`, `draw-error`. The server sends full state on connect.

## Phases

Phases run in order. Each one ends with tests passing locally and in CI, a push to `main`, and an update to the progress log.

### Phase 0: tooling and test baseline

- [ ] Install the Android SDK command-line tools in WSL (`~/Android/Sdk`, platform 36, build-tools). Point `local.properties` `sdk.dir` at it.
- [ ] `./gradlew assembleDebug` and `./gradlew testDebugUnitTest` succeed in WSL.
- [ ] Add test dependencies to the version catalog: `kotlinx-coroutines-test`, Turbine, Robolectric, OkHttp `mockwebserver`, `androidx.test:core`.
- [ ] Replace the template `ExampleUnitTest` and `ExampleInstrumentedTest` with one real smoke test.
- [ ] New workflow `.github/workflows/test.yml` runs `testDebugUnitTest` on push and PR with Gradle caching. It uses dummy WiFi values, since the build fails when the SSID is blank.
- [ ] Record the test suite's run time in the progress log.
- [ ] Connect the old phone, note its model and Android version in the progress log, and add a `scripts/device-smoke.sh` that builds the debug APK, installs it with `adb.exe install -r`, and launches it.

### Phase 1: rename to Primary Suite

- [ ] Change `namespace` and `applicationId` to `com.primarysuite.app` and move the sources to the new package.
- [ ] Set the app name to "Primary Suite" in `strings.xml`.
- [ ] Rename the build properties from `luckytap.*` to `primarysuite.*` and the CI env vars from `LUCKYTAP_*` to `PRIMARYSUITE_*`. Update `local.properties` and the workflow files. The GitHub secret names (`DEFAULT_WIFI_SSID` and the rest) stay as they are.
- [ ] Update the README, the GitHub Pages landing page (`docs/index.html`), the install QR code, and the APK name in `build-release.yml`.
- [ ] Rename the GitHub repo to `primary-suite` and update the `origin` URL.
- [ ] Device smoke check: the new app installs next to the old LuckyTap, launches, and Lucky Tap still works end to end.
- [ ] Commit as `feat!: rename app to Primary Suite` so release-please bumps to 2.0.0.

### Phase 2: pull the logic out of the activities (no visible change)

The XML UI keeps working throughout this phase. The aim is to make the reused code testable and independent of any one game.

- [ ] Create `ServerConfig`, which builds `ws://{ip}:{port}/ws/{slug}` and `http://{ip}:{port}/` from preferences. Tests cover overrides, defaults and bad input.
- [ ] Create `GameMessage` encode and decode for `{type, payload}`, with tests.
- [ ] Make `WebSocketManager` take its URL per connection and an injected `OkHttpClient` and dispatcher. Wrap it in a `GameSocket(slug)`. Test connect, send, receive, close and backoff against `MockWebServer`.
- [ ] Put `WifiController` behind a `WifiConnector` interface and provide a `FakeWifiConnector` for tests.
- [ ] Split `NfcHelper` into pure NDEF text-record parsing and building (`NdefCodec`, tested) and the thin Android tag I/O around it.
- [ ] Hide `AppPreferences` behind an interface and add an in-memory fake.
- [ ] Inject dependencies into `NfcAppViewModel` and cover its current behaviour with tests before moving it. These tests act as the parity spec for phase 4.

### Phase 3: Compose shell, home screen, settings

- [ ] Add Compose (BOM), Material 3, Navigation Compose, and `lifecycle-viewmodel-compose`.
- [ ] Port the dark palette and the socafe font into `ui/theme`.
- [ ] Add `GameCatalog` with the six games. Test that slugs are unique and that only Lucky Tap has tools.
- [ ] Add `HomeViewModel`, which combines WiFi state, the server probe and the catalog into one UI state. Test every combination with fakes.
- [ ] Build `HomeScreen`: a status block for WiFi and the server, then the game list. Games without tools are dimmed and labelled "Tools coming soon".
- [ ] Port `SettingsScreen` and `SettingsViewModel` from `SettingsActivity`, keeping validation and the encrypted password. After saving, the server probe re-runs and open sockets reconnect.
- [ ] Add `ComingSoonScreen`.
- [ ] Make the new single `MainActivity` the launcher and route Lucky Tap to the old screen for the moment, so the app stays usable between phases.
- [ ] Device smoke check: the home screen shows WiFi and server status correctly with the Pi on and off, and settings changes take effect.
- [ ] Add Robolectric Compose tests: the list renders every game, tapping Lucky Tap navigates, tapping another game shows Coming Soon, the settings gear opens Settings, settings validation errors show, and Back returns home.

### Phase 4: Lucky Tap in Compose (parity)

- [ ] Add `LuckyTapViewModel`, built from the phase 2 logic. It owns a `GameSocket("lucky-tap")`, opens it when the screen opens, and closes it when the screen closes.
- [ ] Move NFC foreground dispatch into `MainActivity` and forward tags only while the Lucky Tap screen is listening.
- [ ] Build `LuckyTapScreen` with status rows for NFC and the socket, mode buttons for Read, Check-in, Check-out and Write, the fullscreen scanning state with its animated bar, the success number animation and sound, and the write flow (1-100).
- [ ] Reset game and Clear scans ask for confirmation.
- [ ] If NFC is missing or off, show a prompt to enable it. The rest of the app keeps working.
- [ ] Set NFC to `required="false"` in the manifest.
- [ ] Port the phase 2 parity tests to `LuckyTapViewModel` and add Robolectric UI tests for each mode, cancelling a scan, losing the connection mid-scan, and the confirmation dialogs.
- [ ] Device check with real cards: every mode, writing a blank tag, NFC turned off and back on, and walking out of WiFi range mid-scan.
- [ ] Delete the old `NfcAppViewModel`, `SettingsActivity`, the XML layouts and the unused resources.

### Phase 5: Lucky Tap extras

- [ ] Parse the server messages listed above into a `LuckyTapState` reducer, a pure function tested against JSON fixtures copied from `lucky_tap.go`.
- [ ] Add a live panel showing the checked-in count and numbers, the current round's scans, each card's weight, and the last winners or draw error.
- [ ] Add a number-of-winners stepper that sends `admin-set-num-winners` and ignores values of 0 or less.
- [ ] Add Check in all (1-50) and Scan all buttons, both with confirmation.
- [ ] Add pull to refresh, which sends `admin-get-full-state`.
- [ ] Add reducer tests for every message type and UI tests for the panel and its controls.

### Phase 6: release 2.0.0

- [ ] Run a full on-device check on the old phone against the Pi (`10.0.0.1`): WiFi auto-connect, every Lucky Tap mode, every extra, and the projected display reacting.
- [ ] Update the README for the suite: architecture, how to add a game, how to run the tests.
- [ ] Merge the release-please PR, which should then read 2.0.0, and check that the signed APK and landing page publish.

### Later: one phase per game

Each game gets its own plan in `plans/<game>.md` before work starts, covering which of the web admin's functions belong on the phone, its message protocol, and its tests. Proposed order:

1. Jeopardy! (host controls, score adjustments)
2. State Switch (game type, timeout, move limit, reset)
3. Reel Rush
4. Block Stacker
5. Button Debugger
6. Roles: limit the list to the games a user may administer. This needs a server-side source of truth.

## Open questions

- None yet. Add new ones here as they come up, and move them into Decisions once they're settled.

## Progress log

| Date | Phase | Notes |
|---|---|---|
| | | Plan written. |
