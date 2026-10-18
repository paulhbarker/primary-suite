# Primary Suite: roadmap

> Status: **Phase 6 next** (release 2.0.0). It needs the phone for the on-device check. The device checks for phases 0 and 1 wait until the phone is connected. Update the status line and the progress log at the bottom whenever a phase starts or finishes.

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
| Games listed | Every game the server hosts: Lucky Tap, Jeopardy!, State Switch, Reel Rush, Block Stacker, Button Debugger. Every game now has tools (originally only Lucky Tap; the rest showed "Tools coming soon"). Spin the Wheel is left out because it has no server engine. |
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

- [x] Install the Android SDK command-line tools in WSL (`~/Android/Sdk`, platform 36, build-tools). Point `local.properties` `sdk.dir` at it.
- [x] `./gradlew assembleDebug` and `./gradlew testDebugUnitTest` succeed in WSL.
- [x] Add test dependencies to the version catalog: `kotlinx-coroutines-test`, Turbine, Robolectric, OkHttp `mockwebserver`, `androidx.test:core`.
- [x] Upgrade the foojay toolchain plugin from 0.10.0 to 1.0.0. The old version breaks on Gradle 9.
- [x] Run tests on a Java 17 launcher. The app still compiles for Java 11, but Robolectric needs Java 17 for SDK 35.
- [x] Replace the template `ExampleUnitTest` and `ExampleInstrumentedTest` with one real smoke test.
- [x] New workflow `.github/workflows/test.yml` runs `testDebugUnitTest` on push and PR with Gradle caching. It uses dummy WiFi values, since the build fails when the SSID is blank.
- [x] Record the test suite's run time in the progress log.
- [x] Add a `scripts/device-smoke.sh` that builds the debug APK, installs it with `adb.exe install -r`, and launches it.
- [ ] (Deferred until the phone is connected) Connect the old phone, run the script, and note the phone's model and Android version in the progress log.

### Phase 1: rename to Primary Suite

- [x] Change `namespace` and `applicationId` to `com.primarysuite.app` and move the sources to the new package.
- [x] Set the app name to "Primary Suite" in `strings.xml`.
- [x] Rename the build properties from `luckytap.*` to `primarysuite.*` and the CI env vars from `LUCKYTAP_*` to `PRIMARYSUITE_*`. Update `local.properties` and the workflow files. The GitHub secret names (`DEFAULT_WIFI_SSID` and the rest) stay as they are.
- [x] Update the README, the GitHub Pages landing page (`docs/index.html`), and the install QR code (now `primary-suite-install-qr.png`). The release APK keeps its `app-release.apk` name so the landing page's `/latest/download/` link still works.
- [x] Rename the GitHub repo to `primary-suite` and update the `origin` URL.
- [ ] (Deferred until the phone is connected) Device smoke check: the new app installs next to the old LuckyTap, launches, and Lucky Tap still works end to end.
- [x] Commit as `feat!: rename app to Primary Suite` so release-please bumps to 2.0.0.

### Phase 2: pull the logic out of the activities (no visible change)

The XML UI keeps working throughout this phase. The aim is to make the reused code testable and independent of any one game.

- [x] Create `ServerConfig`, which builds `ws://{ip}:{port}/ws/{slug}` and `http://{ip}:{port}/` from preferences. Tests cover overrides, defaults and bad input.
- [x] Create `GameMessage` encode and decode for `{type, payload}`, with tests.
- [x] Make `WebSocketManager` take its URL per connection and an injected `OkHttpClient` and dispatcher. `gameSocket(slug, ...)` builds one for a game. Test connect, send, receive, close and backoff against `MockWebServer`.
- [x] Put `WifiController` behind a `WifiConnector` interface and provide a `FakeWifiConnector` for tests.
- [x] Split `NfcHelper` into pure NDEF text-record parsing and building (`NdefCodec`, tested) and the thin Android tag I/O around it.
- [x] Hide `AppPreferences` behind an interface and add an in-memory fake.
- [x] Inject dependencies into `NfcAppViewModel` and cover its current behaviour with tests before moving it. These tests act as the parity spec for phase 4.

Bugs fixed on the way, each covered by a test:

- The "already connected" check compared against OkHttp's normalised `http://` form of the `ws://` URL, so it never matched, and every `connect()` replaced a healthy socket.
- Callbacks from a replaced socket could overwrite the new socket's state.
- `onClosing` set CLOSING after answering the server's close, which could overwrite the DISCONNECTED that `onClosed` had already set on another thread. The state then stayed CLOSING. This showed up as a test that failed 6 runs in 15.
- When the server closed the socket (a Pi restart, for example), the app only reconnected if OkHttp happened to report a failure rather than a clean close. It now reconnects either way.
- A scan the socket refused still set the number for display, which played the success sound.
- WifiController registered its legacy receiver on one context and unregistered it on another. It now uses the application context for both.

### Phase 3: Compose shell, home screen, settings

- [x] Add Compose (BOM), Material 3, Navigation Compose, and `lifecycle-viewmodel-compose`. Pinned to Compose BOM 2026.06.01 and Navigation 2.9.7, because newer versions need `compileSdk` 37.
- [x] Port the dark palette and the socafe font into `ui/theme`.
- [x] Add `GameCatalog` with the six games. Test that slugs are unique and that only Lucky Tap has tools.
- [x] Add `HomeViewModel`, which combines WiFi state, the server probe and the catalog into one UI state. Test every combination with fakes.
- [x] Build `HomeScreen`: a status block for WiFi and the server, then the game list. Games without tools are dimmed and labelled "Tools coming soon".
- [x] Port `SettingsScreen` and `SettingsViewModel` from `SettingsActivity`, keeping validation and the encrypted password. After saving, the server probe re-runs and open sockets reconnect.
- [x] Add `ComingSoonScreen`.
- [x] Make the new single `MainActivity` the launcher and route Lucky Tap to the old screen for the moment, so the app stays usable between phases.
- [ ] (Deferred until the phone is connected) Device smoke check: the home screen shows WiFi and server status correctly with the Pi on and off, and settings changes take effect.
- [x] Add Robolectric Compose tests: the list renders every game, tapping Lucky Tap navigates, tapping another game shows Coming Soon, the settings gear opens Settings, settings validation errors show, and Back returns home.

Also done in this phase:

- `PrimarySuiteApplication` and `AppContainer` own one `WifiController` for the whole app. Leaving Lucky Tap no longer drops WiFi.
- The WiFi setup order is a pure function (`nextWifiSetupAction`) with tests. `WifiSetupCoordinator` carries out each step for both activities, replacing the old activity's copy. A prompt the user declines isn't reopened on every resume, only when they tap the WiFi status.
- Saving settings rejoins WiFi when the SSID or password changed. Before, that only happened after a restart.
- Settings checks the server address (a bare host, no `http://` or port). The default WiFi password is no longer shown in the hint; the old screen displayed it.
- The old `MainActivity` is now `games/luckytap/LuckyTapActivity` and its settings button is hidden. `SettingsActivity` and its layout are deleted.

### Phase 4: Lucky Tap in Compose (parity)

- [x] Add `LuckyTapViewModel`, built from the phase 2 logic. It owns a `GameSocket("lucky-tap")`, opens it when the screen opens, and closes it when the screen closes.
- [x] Move NFC foreground dispatch into `MainActivity` and forward tags only while the Lucky Tap screen is listening.
- [x] Build `LuckyTapScreen` with status rows for NFC and the socket, mode buttons for Read, Check-in, Check-out and Write, the fullscreen scanning state with its animated bar, the success number animation and sound, and the write flow (1-100).
- [x] Reset game and Clear scans ask for confirmation.
- [x] If NFC is missing or off, show a prompt to enable it. The rest of the app keeps working.
- [x] Set NFC to `required="false"` in the manifest.
- [x] Port the phase 2 parity tests to `LuckyTapViewModel` and add Robolectric UI tests for each mode, cancelling a scan, losing the connection mid-scan, and the confirmation dialogs.
- [ ] (Deferred until the phone is connected) Device check with real cards: every mode, writing a blank tag, NFC turned off and back on, and walking out of WiFi range mid-scan.
- [x] Delete the old `NfcAppViewModel`, `SettingsActivity`, the XML layouts and the unused resources.

Also done in this phase:

- `NfcHub` (app-wide) holds the NFC state and routes tapped tags to whichever screen is listening. An `NfcTag` interface wraps Android's `Tag`, so the view model and tests never touch it.
- Writing a card only needs NFC, not the server, so cards can be prepared away from the event network. Scanning and admin still need NFC, WiFi and the server.
- The buttons are disabled while offline rather than hidden.
- WifiController's receiver for Android 6–9 (API 23–28) now also detects the network dropping, which the old activity used to handle.
- AppCompat, Material Components, ConstraintLayout and LiveData are removed, along with 23 unused strings. The release APK went from 3.9 MB to 2.8 MB.
- Test JVMs are capped at 2 forks with 1 GB heaps (see `AGENTS.md`). Eight uncapped forks ran WSL out of memory.

### Phase 5: Lucky Tap extras

- [x] Parse the server messages listed above into a `LuckyTapState` reducer, a pure function tested against JSON fixtures copied from `lucky_tap.go`.
- [x] Add a live panel showing the checked-in count and numbers, the current round's scans, each card's weight, and the last winners or draw error.
- [x] Add a number-of-winners stepper that sends `admin-set-num-winners` and ignores values of 0 or less.
- [x] Add Check in all (1-50) and Scan all buttons, both with confirmation.
- [x] Add pull to refresh, which sends `admin-get-full-state`.
- [x] Add reducer tests for every message type and UI tests for the panel and its controls.

Decisions and findings:

- **The app never requests a full state on its own.** `admin-get-full-state` is broadcast to every client, and the projected display resets to its idle card animation when it gets one (`SET_FULL_STATE` in `LuckyTapV2.tsx`). Refreshing is manual only, and the Game tab warns that it redraws the display.
- **No admin actions while a draw animates.** From `start-tumbler-animation` until `selected-number-final` or `draw-error`, refresh, winners per draw, Check in all, Scan all, Clear and Reset are disabled. Most of them make the server broadcast a full state.
- **The app applies the after-draw changes itself.** The server sends only `selected-number-final` after a draw: it clears the round and lowers each winner's weight by 25 without broadcasting. The reducer does the same. The constants mirror `InitialWeightLuckyTap` (100) and `DefaultReductionAmountLuckyTap` (25) in `lucky_tap.go`; update both places if either changes.
- **Admin needs only the server**, not NFC, so a phone without NFC can still run the game. Scanning still needs NFC, WiFi and the server.
- The Lucky Tap screen now has two tabs: Scanner (scan and write) and Game (live state and admin). Clear and Reset moved to Game.
- Odds are shown as a percentage of a fresh card's weight (100% = full odds). Cards scanned this round have a green outline and a count (×2).
- Winners per draw goes from 1 to 20; the server only rejects values below 1.

### Phase 6: release 2.0.0

- [ ] Run a full on-device check on the old phone against the Pi (`10.0.0.1`): WiFi auto-connect, every Lucky Tap mode, every extra, and the projected display reacting.
- [ ] Update the README for the suite: architecture, how to add a game, how to run the tests.
- [ ] Merge the release-please PR, which should then read 2.0.0, and check that the signed APK and landing page publish.

### Later: one phase per game

Each game gets its own plan in `plans/<game>.md` before work starts, covering which of the web admin's functions belong on the phone, its message protocol, and its tests. Proposed order:

1. Jeopardy! (host controls, score adjustments). See [`plans/jeopardy.md`](jeopardy.md).
2. State Switch (game type, timeout, move limit, reset). See [`plans/state-switch.md`](state-switch.md).
3. Reel Rush. See [`plans/reel-rush.md`](reel-rush.md).
4. Block Stacker. See [`plans/block-stacker.md`](block-stacker.md).
5. Button Debugger. See [`plans/button-debug.md`](button-debug.md).
6. Roles: limit the list to the games a user may administer. This needs a server-side source of truth.

## Open questions

- None yet. Add new ones here as they come up, and move them into Decisions once they're settled.

## Progress log

| Date | Phase | Notes |
|---|---|---|
| 2026-10-17 | | Plan written. |
| 2026-10-17 | 0 | Android SDK installed in WSL at `~/Android/Sdk` (platform 36, build-tools 36.0.0). `ANDROID_HOME` is set in `~/.bashrc`. The debug build and tests pass. Baseline: 3 Robolectric tests, `testDebugUnitTest --rerun` takes 5.4 s with a warm daemon (3.4 s in tests). The first cold run takes about 20 s. |
| 2026-10-17 | 1 | Renamed to Primary Suite (`com.primarysuite.app`) in `2cb9e99`. The GitHub repo is now `paulhbarker/primary-suite`; github.com redirects the old URLs, but the old Pages URL `paulhbarker.github.io/LuckyTap/` now returns 404, so printed copies of the old QR code are dead. Release PR #4 now reads 2.0.0. The Tests check doesn't run on release-please PRs because PRs opened with the default token don't trigger workflows (the run shows "action required"). That's harmless, since tests run on every push to `main`. |
| 2026-10-17 | 2 | Logic split into `core/` (config, net, protocol, wifi, nfc, ui) and `games/luckytap/`. 65 tests in 5.5 s with a warm daemon. The socket tests use a real local server; each takes under 0.4 s and they passed 25 out of 25 stress runs. The view model tests are the parity spec for phase 4. The XML UI is unchanged. |
| 2026-10-17 | 3 | Compose home, settings and coming-soon screens, with the old XML Lucky Tap screen behind the home screen. 108 tests: 14.6 s wall-clock with a warm daemon, 28 s of test time across parallel forks. The Robolectric Compose tests take about 1 s each after the first. Checked the screens by rendering them to PNG with Robolectric's native graphics. |
| 2026-10-17 | 4 | Lucky Tap is now Compose, and the XML screens are gone. 122 tests in 16 s with 2 test workers; the suite peaks at 4.1 GB of WSL memory. The earlier setting of 8 uncapped test JVMs, run in the same command as an R8 release build, ran WSL (8 GB) out of memory, so the forks are capped and the limits are recorded in `AGENTS.md`. |
| 2026-10-17 | 5 | Live game state, the winners stepper, Check in all, Scan all and refresh are done. 152 tests in 21.5 s. The release APK is 2.9 MB. |
| 2026-10-17 | 6 | Device check on a Galaxy S10+ (SM-G975U), Android 12 (API 31), against the Pi on `GroveParkPrimary` (now WPA2/AES only). The app joined the network on its own in about 9 s and sent its traffic over it. State Switch, Block Stacker, Reel Rush, Jeopardy! and the Button Debugger all worked against the live displays. After the phone slept for 10 min, the open game screen reconnected 3 s after waking. Fixed during the check: the Wi-Fi password was written to logcat, and the game-button stand-ins now fire on touch. Lucky Tap with real NFC cards still to confirm. |
