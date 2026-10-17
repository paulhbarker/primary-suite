# Block Stacker admin

> Status: **B1 done.** Checking against the Pi waits for the device testing at the end of the project. Update the status line and the progress log as phases finish.

## Goal

Run the Block Stacker admin from the app with the same controls as the web admin (`/block-stacker/admin`, `frontend/src/games/block-stacker/BlockStackerAdmin.tsx`): the four settings, Save Settings, Reset to Defaults, Test Button Press, the game status and the received-messages log. The game itself runs on the web display; the server only stores the settings and forwards button presses.

## Decisions

| Topic | Decision |
|---|---|
| Scope | Everything in the web admin: Max Levels, Initial Speed, Speed Increment, Game Enabled, Save Settings, Reset to Defaults, Test Button Press, Game Status, How to Play, WebSocket Message Log. |
| Not included | The "Players navigate to /block-stacker" hint (a web link). The status card's "Active Players" line: the server's state has no such field, so the web shows nothing for it. |
| When settings are sent | Only on **Save Settings**, as on the web. Editing changes a local draft. Sliders and the switch therefore do not send on release; Save does. |
| Inputs | The web uses number inputs. The phone uses sliders with a value label: Max Levels 1 to 50 (step 1), Initial Speed 0.1 to 10 (step 0.1), Speed Increment 0 to 5 (step 0.1), the same limits as the web inputs. A value from the server outside the range is kept until the user moves that slider. |
| Reset to Defaults | Local only, as on the web: it puts 15 / 1.7 / 0.8 / enabled into the draft. Nothing is sent until Save. |
| Server updates | The web overwrites its draft on every `block-stacker-game-state`. The app does too, unless the user has unsaved edits, which it keeps. |
| Connect | The server sends the full state unasked. The web also sends `block-stacker-get-state` on connect, but the server then broadcasts to every client, so the app does not send it. |
| Offline | Save and Test Button Press are disabled and say so with the Offline badge. Editing the draft stays possible, as on the web. |
| Failed sends | A snackbar "Not connected to the game server." (the web's toast). |
| Test Button Press | Sends `button-press` with `{id: 1}`, like the web. The server drops it when the saved Game Enabled is off; the display then ignores it. |
| Extras | An "Unsaved changes" note next to Save when the draft differs from the server. The Test Button Press button is large. |
| Log | The last 10 messages received, newest first, as `[time] type: payload`, like the web. Cleared when the screen closes. |
| Layout | One column, at most 560 dp wide, centred on tablets. |

## Protocol (from `backend/cmd/block_stacker.go`)

On connect the server sends `block-stacker-game-state`. No handshake.

| Direction | Type | Payload |
|---|---|---|
| in | `block-stacker-game-state` | `{settings: {maxLevels, initialSpeed, speedIncrement, gameEnabled}, state: {status, totalGamesPlayed, highScore, lastUpdated}}` |
| in | `button-press` | `{id: 1}`, the server's forward of a press to every client; only logged |
| out | `block-stacker-update-settings` | `{maxLevels, initialSpeed, speedIncrement, gameEnabled}`. The server replies with a broadcast of the full state. |
| out | `button-press` | `{id: 1}` |
| out (not sent) | `block-stacker-get-state` | `{}`. Makes the server broadcast the state to everyone. |

`status` is "waiting", "active" or "paused" (shown as sent). `lastUpdated` is an RFC 3339 time. The display only copies the settings from a broadcast, so a state broadcast does not disturb it. Speeds go out as exact tenths (`1.7`).

## Code

- `games/blockstacker/BlockStackerProtocol.kt`: `BlockStackerSettings` (parse, `toJson`, `DEFAULTS`), `BlockStackerState`, `BlockStackerFullState`, `BlockStackerCommands`.
- `games/blockstacker/BlockStackerViewModel.kt`: socket, `BlockStackerUiState` (draft, server settings, state, log, `dirty`), `edit`, `save`, `resetToDefaults`, `testPress`, errors.
- `games/blockstacker/BlockStackerScreen.kt`: route and screen.
- `res/values/strings_blockstacker.xml`.

## Phases

### B1: admin screen
- [x] Protocol, parsing and commands, with tests
- [x] View model, with tests (connect, follow broadcasts, keep unsaved edits, save, reset, test press, log, failures, offline)
- [x] Screen, with Robolectric tests (every control, Save sends the draft, offline lock)
- [ ] Home screen opens it on its own socket (the lead wires this)

### B2: against the Pi (with the final device testing)
- [ ] Save changes the display's speed and level count
- [ ] Game Enabled off stops the display starting games
- [ ] Test Button Press drops a block on the display
- [ ] Changes from the web admin appear in the app

## Open questions

- Sliders versus number fields: sliders are easier on a phone but coarser than typing. Say if exact typing is wanted.

## Progress log

| Date | Phase | Notes |
|---|---|---|
| 2026-10-17 | B1 | Built the admin screen. 28 tests. Checked the look with a rendered screenshot and fixed the dark Reset button text. |
