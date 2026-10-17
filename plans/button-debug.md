# Button Debugger admin

> Status: **B1 done** (protocol, analysis, view model, screen, tests). Checking against the Pi waits for the device testing at the end of the project. Update the status line and the progress log as phases finish.

## Goal

The phone version of the web Button Debugger (`frontend/src/games/button-debug/ButtonDebug.tsx` and `analysis.ts`): live button tiles, edge timing, noise and burst analysis, problem warnings, Wi-Fi button stats, round-trip time, the LED test, the debounce setting and the per-button colour picker. It is a full port: the verdicts on the phone must match the web's, so `analysis.ts` is ported to pure Kotlin functions with tests that mirror it.

## Decisions

| Topic | Decision |
|---|---|
| Scope | Everything on the web page: connection badge and RTT, warning banners, summary line, button tiles, LED test, debounce, session buttons, Wi-Fi buttons, edge log (last 200, newest first), the "full check" how-to. |
| Not included | The site `Navigation` bar. |
| Socket | The page opens `${WEBSOCKET_BASE_URL}/button-debug`. `WEBSOCKET_BASE_URL` ends in `/ws`, so the URL is `/ws/button-debug`: the slug is `button-debug` and the existing `GameSocketFactory` works unchanged. |
| Analysis | `ButtonDebugAnalysis.kt` follows `analysis.ts` function by function (`applyEvent`, `applyDelivered`, `applyWifi`, `problems`) and the page's reducer (`ButtonDebugSession.kt`). Edge verdicts and kinds stay raw strings, so a verdict from a newer server shows as it is. |
| Problem texts | Kept as English text in `ButtonDebugAnalysis.kt`, like `analysis.ts`, so they can be compared word for word in unit tests. All other labels are in `strings_buttondebug.xml`. |
| Batching | The web queues messages and renders once per animation frame. Here each message updates a `StateFlow`, which Compose already conflates to one render per frame. The socket events are consumed on a background dispatcher (see Open questions: the core event buffer). |
| Round trip | A `button-debug-ping` with `{t}` is sent on connect and every 2 s. The pong's `t` is subtracted from the clock when the message is read. The clock is injected (`clockMs`), so tests control it. The RTT badge shows only while connected, as on the web. |
| Ping failures | Not reported. User actions that fail to send show "Not connected to the game server." |
| Colours | `ButtonColorStore` (load / save the colour by button id). `PrefsButtonColorStore` backs it with `SharedPreferences` using the web's key `button-debug-colors-v3` and the same JSON, so the format matches. Defaults: 1 green, 2 yellow, 3 white, 4 blue. Tiles sort in box order: blue, white, yellow, green, red. |
| Pulse | A tile's lamp pulses when a press arrives live, not when history is replayed, as on the web. Wi-Fi tiles flash the same way. |
| Debounce | Presets 5, 10, 20, 30, 50 and a free field, 0 to 500 ms. The Set button is disabled while the text isn't a number in range (the web ignores it silently). |
| Controls offline | The LED, debounce and session buttons are disabled while offline. The web leaves them enabled and the send is lost. |
| Log layout | Two lines per row on the phone instead of the web's six-column table. Same fields, same text. The Wi-Fi row time is `HH:mm:ss`. |
| Layout | One column, at most 640 dp wide, centred on tablets. The log is lazy, so 200 rows stay cheap. |
| Screen | Stays on while open, since the page is for tapping buttons. |

## Protocol (from `backend/cmd/button_debug.go`, `buttonbox.go`)

On connect the server sends `button-debug-snapshot` (box info, buttons, status, up to 600 edges, up to 300 presses). No handshake. While at least one debugger is open the server polls the box every 50 ms and sends `button-debug-status` on change, or once a second.

| Direction | Type | Payload |
|---|---|---|
| in | `button-debug-snapshot` | `{box, boxError?, chip?, buttons[{index,id,switchPin,ledPin}], status?, events[], presses[]}`. Also broadcast after a reset: the page replays it from scratch. |
| in | `button-debug-status` | `{down[], levels[], levelErr?, enabled[], leds[], ledMode, quietMs, stuck[], activeGames[]\|null, droppedEdges}` |
| in | `button-debug-edge` | `{button, id, pin, kind (edge/settle/poll), pressed, tsUs, gapUs, lagUs, kernelGap?, burstUs?, verdict, seq?}` |
| in | `button-debug-press` | `{id, seq?, source (box/wifi), remote?, hubUs, rssi?, sentMs?, freeHeap?, atMs}` |
| in | `button-debug-pong` | the ping's payload, echoed |
| out | `button-debug-ping` | `{t}` (clock in ms) |
| out | `button-debug-led-mode` | `{mode}`: `game`, `follow`, `on`, `off` |
| out | `button-debug-chase` | none |
| out | `button-debug-enable-all` | none |
| out | `button-debug-debounce` | `{ms}` (0 to 500) |
| out | `button-debug-reset` | none (clears the server's history for every open debugger) |

Nothing here disturbs a projected display. The debugger does drive the LEDs and the box, and the server hands the LEDs back to the games when the last debugger closes. Opening the page also shows which other games are open, because they receive the same presses.

Verdicts (`analysis.ts`): `press`, `pending`, `bounce`, `disabled`, `late-press`, `release`, `holding`, `blip`, `lost-press`, `lost-release`.

## Code

- `ButtonDebugProtocol.kt`: constants, `EdgeEvent`, `DebugPress`, `DebugButton`, `DebugStatus`, `DebugSnapshot` with lenient parsers, `ButtonDebugCommands`, `parseDebounce`.
- `ButtonDebugAnalysis.kt`: `Stats`, `Burst`, `applyEvent`, `applyDelivered`, `problems`, `WifiStats`, `applyWifi`.
- `ButtonDebugSession.kt`: the page's reducer (`DebugSession`: snapshot replay, edges, presses, log, derived counts).
- `ButtonDebugFormat.kt`: `fmtUs`, `fmtMs`, log row text, wave geometry.
- `ButtonColors.kt`: `ButtonColor`, defaults, box order, `ButtonColorStore`, `PrefsButtonColorStore`.
- `ButtonDebugViewModel.kt`: socket, ping loop, actions, errors.
- `ButtonDebugScreen.kt`, `ButtonDebugTile.kt`: route, screen, tile, waves.

## Phases

### B1: debugger screen
- [x] Protocol, parsing and commands, with tests
- [x] Analysis port and session reducer, with tests that mirror `analysis.ts`
- [x] View model, with tests (connect, snapshot, live events, ping and RTT, every action, failures, offline)
- [x] Screen, with Robolectric tests (every control, banners, tiles, colour picker, log)

### B2: against the Pi (with the final device testing)
- [ ] Tiles light and count with real presses on the box, including fast tapping
- [ ] Chase, Follow press, All on and All off drive the LEDs; the games get the LEDs back when the screen closes
- [ ] Debounce changes apply (`quietMs` follows)
- [ ] Wi-Fi button presses show up with RSSI and lost-sequence counts
- [ ] Colours survive an app restart

## Open questions

- The core `WebSocketManager` buffers 64 events and uses `tryEmit`, so a burst over 64 messages with a slow collector drops messages. The debugger collects on a background dispatcher to avoid it, but raising that buffer (for example to 1024) would make it safe for every game.

## Progress log

| Date | Phase | Notes |
|---|---|---|
| 2026-10-17 | B1 | Built the full port. 141 tests. Verified layout with rendered screenshots (scratch test deleted). Found the socket's 64-event buffer could drop edges in a burst, so edges are handled on a background dispatcher. |
