# Jeopardy! host controller

> Status: **Done.** Checked on the phone against the Pi.

## Goal

Host a Simple Games Jeopardy! game from the app, with the same controls as the web host controller (`/jeopardy/host`, `frontend/src/games/jeopardy/host/`). The server stays authoritative. The app shows its state and sends the host's commands, as the web host does. Board editing and the projector display stay on the web.

## Decisions

| Topic | Decision |
|---|---|
| Scope | Everything in the web host: lobby, round intro, board, clue (buzzers, responder picker, judging, reveal, cancel), Daily Double wager, round end, game over, players panel and player dialog, score log, undo, settings, end game and reset game. |
| Not included | Keyboard shortcuts (no keyboard on a phone). The "Edit boards" and "Open display" links. The board editor. |
| Layout | Adaptive. Narrower than 600 dp: one scrolling column with the game panel, then players, then the log, like the web on a phone. 600 dp and wider (tablet or landscape): game on the left, players and log in a 360 dp column on the right, like the web on a laptop. |
| Look | The app's dark theme for the surrounding controls, with Jeopardy accents where the web uses them: TV-blue board tiles (`#05127D`), gold values (`#FFCC00`), and a gold "DD" marker. Player rows and the "is answering" banner use the player's button colour and ink from the server. |
| Images | Image clues and responses load from the Pi with Coil (`imageUrl` is relative to `http://host:port`). Captions show above and below, as on the web. |
| Extras | The phone vibrates when a player buzzes in (`buzz` event). The screen stays on while the Jeopardy screen is open. |
| Connection | `/ws/jeopardy`, with `jeopardy-hello {role: "host"}` sent on **every** (re)connect, because the server treats a connection as a display until it says hello. The host hello also claims the button box. |
| Boards | `GET http://host:port/api/jeopardy/boards` (the same API the web uses). Refreshed on opening the lobby and with a button. |
| Errors | `jeopardy-error {message}` appears as a snackbar. The server resends state after an error, so nothing needs undoing locally. |
| Countdown | `deadline` (unix ms), `deadlineKind` (read, buzz or answer) and `deadlineTotalMs`, corrected by `serverTime - now` from the latest state, like `useCountdown.ts`. |

## Protocol (from `backend/cmd/jeopardy/messages.go`, `view.go`)

The host sends:

| Type | Payload | Used in |
|---|---|---|
| `jeopardy-hello` | `{role:"host"}` | every connect |
| `jeopardy-load-board` | `{boardId}` | lobby |
| `jeopardy-set-control` | `{playerId}` (`""` = random/clear) | lobby first pick, player dialog, DD wagerer |
| `jeopardy-start-game` | none | lobby |
| `jeopardy-next` | none | round intro |
| `jeopardy-select-clue` | `{col,row}` | board |
| `jeopardy-end-round` | none | board ("End round early", confirmed) |
| `jeopardy-arm` | none | clue |
| `jeopardy-select-responder` | `{playerId}` | clue and armed ("Answering") |
| `jeopardy-judge` | `{correct}` | answering, dd-clue |
| `jeopardy-dd-wager` | `{amount}` or `{trueDailyDouble:true}` | dd-wager |
| `jeopardy-reveal` | none | clue, armed, answering, dd-clue ("End clue & reveal") |
| `jeopardy-cancel-clue` | none | clue or armed with no scoring yet, dd-wager |
| `jeopardy-continue` | none | reveal |
| `jeopardy-next-round` | none | round end |
| `jeopardy-end-game`, `jeopardy-reset-game` | none | menu (confirmed); reset also from game over |
| `jeopardy-undo` | none | header |
| `jeopardy-update-settings` | `Settings` | settings dialog |
| `jeopardy-add-player` | `{name}` | players panel |
| `jeopardy-rename-player` | `{playerId,name}` | player dialog |
| `jeopardy-adjust-score` | `{playerId, delta}` / `{playerId, score}` / `{playerId, delta:-5, drain:true}` | player dialog |
| `jeopardy-assign-button` | `{playerId}` (`""` cancels) | player dialog, assign banner |
| `jeopardy-clear-button` | `{playerId}` | player dialog |
| `jeopardy-move-player` | `{playerId,index}` | player dialog |
| `jeopardy-remove-player` | `{playerId}` | player dialog (confirmed) |

The server sends `jeopardy-welcome`, `jeopardy-state` (`StateView`; the host view includes the responses, Daily Double positions, wager bounds, `canUndo`, `log`, `assigningButtonFor` and `lastButton`), `jeopardy-event` (`{kind, playerId?, …}`; the app uses only `buzz` and `button-assigned`), and `jeopardy-error`.

Phases are `lobby`, `round-intro`, `board`, `clue`, `armed`, `answering`, `dd-wager`, `dd-clue`, `reveal`, `round-end` and `game-over`.

## Screens (mirroring `host/*.tsx`)

- **Header** (`Host.tsx`): Jeopardy! wordmark and board name, a phase badge (green while the buzzers are open), "Offline" when the socket is down, undo, settings, and an overflow menu with End game (except in the lobby and at game over) and Reset game, both confirmed.
- **Assign banner**: "Press the physical button for *name* now…" with Cancel, while `assigningButtonFor` is set.
- **Lobby** (`LobbyPanel.tsx`): board picker with refresh, a note when the loaded board is no longer in the list, first pick (Random or a player), and Start game (enabled once a board is loaded).
- **Game** (`GamePanel.tsx`):
  - **Round intro:** the category now showing; "Reveal category n of m: title", then "Start the round".
  - **Board:** the round name, "Pick: *control* / anyone", a 6×5 grid of category titles, values and DD markers (tap a tile to select it), and "End round early" (confirmed).
  - **Clue, armed, answering, dd-wager, dd-clue, reveal:** a header with the category and value (or Daily Double and the wager). The clue sits above the correct response. The response is blurred while reading (clue and dd-wager) and a tap peeks; peeking resets for each clue. Then a timer bar, and controls that depend on the phase:
    - **clue:** a locked-out list, Open buzzers (or "Open buzzers now" in automatic mode; in none mode, "pick who answers"), and the responder picker.
    - **armed:** a "Buzzers open" banner and the responder picker.
    - **answering:** a banner in the player's colour, then Correct and Incorrect.
    - **dd-wager:** the wager form (who plays it, an amount within the min and max, Show clue, True Daily Double / Max).
    - **dd-clue:** a wager banner, then Correct and Incorrect.
    - **reveal:** the result, then "Back to the board".
    - "End clue & reveal" appears in clue, armed, answering and dd-clue. "Back to board (unplayed)" appears in clue and armed while nobody is locked out, and in dd-wager.
  - **Round end:** "Start next round" or "Show final standings".
  - **Game over:** the winner, and New game (reset).
- **Players** (`PlayersPanel.tsx`): one row per player with a control star, name, "locked out", a phone icon, a button badge with a colour swatch and the button id, and the score (red when negative). The answering player is highlighted, and disconnected players are dimmed. An Add button opens a name dialog. Below the list, "Last button press: #n (name / not assigned) at time".
- **Drain points** (app only, requested after J4): a red "Drain points" button under the scores opens a full-screen view with one button per player, each taking an equal share of the height and showing the player's name, button colour and live score. Pressing and holding a button drains that player (−5 every 250 ms, the same `adjust-score … drain` ticks as the dialog) until released.
- **Player dialog:** rename; −step and +step (the step is the clue value, else the round's first value, else 200); hold to drain (−5 every 250 ms while held); set score; give or clear control; assign, cancel or clear the button; move up or down; Remove (confirmed); Done.
- **Score log:** collapsible, newest first, with time, text and a coloured delta.
- **Settings** (`SettingsDialog.tsx`):
  - **Rules:** Daily Doubles, deduct for incorrect, lowest score picks first, reveal categories one at a time, show responses on screen (stored as `hideResponses`).
  - **Buzzers:** default mode (manual, automatic or none), early-buzz lockout with its milliseconds, re-open after an incorrect answer, buzz window and answer time in seconds (0 means no limit), and in automatic mode the reading time per character and the minimum reading time.
  - **Audio:** volume slider.
  - Saving sends the whole settings object.

## Architecture

```
games/jeopardy/
├── JeopardyProtocol.kt     StateView and related models, parsed from JSON; outgoing message builders
├── JeopardyBoardsApi.kt    GET /api/jeopardy/boards over OkHttp
├── Countdown.kt            remaining time and fraction from the deadline and clock offset (pure)
├── JeopardyHostViewModel.kt  socket, hello on connect, state, errors, events, commands
├── JeopardyHostScreen.kt   header, banner, adaptive layout
├── LobbyPanel.kt, GamePanel.kt, CluePanel.kt, PlayersPanel.kt, PlayerDialog.kt, SettingsSheet.kt
└── JeopardyTheme.kt        TV colours, money() and roundName()
```

Shared changes:

- `WebSocketManager` emits `SocketEvent.Connected` on each open, so games can send hellos.
- The nav host routes `game/jeopardy`.
- `GameCatalog` sets `hasTools = true` for Jeopardy.

## Tests

All tests run on the JVM, within the limits in `AGENTS.md`.

- **Protocol parsing** against fixtures shaped like the Go `StateView`: lobby, board with DD markers, each clue phase, the host-only fields, missing or optional fields, and image sides.
- **Countdown maths:** clock offset, expiry, a total of zero, and a missing deadline.
- **View model:** hello on every connect, each command's JSON, errors become snackbars, `buzz` vibrates, and the state replaces on each message.
- **UI (Robolectric):** the lobby (boards, first pick, start), every game phase's controls, the response blur and peek, the DD wager bounds, the player dialog (step, set score, drain ticks, control, assign, move, remove confirmation), the settings round trip, the end and reset confirmations, the assign banner, and both layouts.

## Phases

### J1: plumbing
- [x] `SocketEvent.Connected`, and a hello on every connect.
- [x] Protocol models and parser, with tests.
- [x] Boards API, tested with MockWebServer.
- [x] Countdown, with tests.
- [x] `JeopardyHostViewModel`, with tests.

### J2: host shell, lobby, players
- [x] Header, menu, confirmations, snackbar errors, assign banner, adaptive layout, keep screen on, vibrate on buzz.
- [x] Lobby panel.
- [x] Players panel, add dialog, player dialog (with drain), last button press, score log.
- [x] Route `game/jeopardy`, and catalog `hasTools = true`.
- [x] UI tests.

### J3: game flow
- [x] Round intro, board grid, clue panel (blur and peek, timer, responder picker, judge, reveal, cancel), DD wager, reveal, round end, game over.
- [x] Image sides through Coil.
- [x] UI tests for each phase.

### J4: settings
- [x] Settings sheet with every field, sending `jeopardy-update-settings`, with tests.

### J5: check against the Pi
- [x] Play a full game against the real server on the phone, with the display on the projector and phones or box buttons as buzzers. Needs the phone and the Pi.

## Notes from building it

- **Text fields in dialogs use the state-based API** (`TextFieldState`). The value-based `OutlinedTextField` never goes idle inside a dialog window under Robolectric: the test hangs, then runs out of memory. Use `rememberTextFieldState` for any new text field in a dialog.
- **Library versions held back.** Coil is pinned to 3.3.0: 3.4 needs Kotlin 2.3 or later (the project uses 2.2), and 3.6 needs `compileSdk` 37.
- **Device check needed for the response blur.** The image renders used to check the screens skip `Modifier.blur`, so check that the response is really hidden while reading, on Android 12 or later (blur) and below (hidden).

## Progress log

| Date | Phase | Notes |
|---|---|---|
| 2026-10-17 | | Plan written from the web host source and the server protocol. |
| 2026-10-17 | J1 | Protocol, boards API, countdown, and a view model that says hello on every connect and ignores the public view until welcomed. 29 new tests; 181 in total. |
| 2026-10-17 | J2–J4 | Host shell, lobby, players, player dialog with drain, score log, every game phase, Daily Double wager, image sides and settings. 36 Robolectric UI tests against fixture states; 218 tests in 19 s. Screens checked as rendered images: phone and tablet layouts, board, reading, answering, Daily Double. |
| 2026-10-17 | extra | Added a Drain points button under the scores, opening a view with one full-height-share hold button per player. Its hold-to-repeat behaviour is shared with the dialog's drain button. 222 tests. |
| 2026-10-17 | J5 | On the Galaxy S10+ (SM-G975U), Android 12 (API 31) against the Pi with the display: clues, buzzers (vibrates), judging, Daily Double, drain screen, undo, settings, end round and end game. After 10 min asleep, the host screen reconnected and said hello again 3 s after waking. |
