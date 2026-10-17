# State Switch admin

> Status: **S1 done.** Checking against the Pi waits for the device testing at the end of the project. Update the status line and the progress log as phases finish.

## Goal

Run the State Switch game from the app with the same controls as the web admin (`/state-switch/admin`, `frontend/src/games/state-switch/StateSwitchAdmin.tsx`), plus a Switch button that stands in for the physical button. The server stays authoritative; the projector display stays on the web.

## Decisions

| Topic | Decision |
|---|---|
| Scope | Everything in the web admin: game selection, button timeout, enforce limit, button limit, Reset Actuations, Reset All. |
| Not included | The "Open display" link. No live status card (which side is showing, press count): the screen shows settings only, like the web. |
| Extra | A big **Switch** button that sends `button-press`, as the physical button does. The server applies the same timeout and limit. The button is disabled, and says why ("Cooling down…", "Limit reached"), while the server would ignore a press. |
| Sliders | Sent once, on release. The web sends on every step while dragging. The value label follows the drag. |
| Reset All | Asks for confirmation. Reset Actuations acts at once, as on the web. |
| Reset defaults | Matches what the web really does: it sends `state-switch-reset` (the server resets to a 0 s timeout) and then its own defaults, so the game ends on Fast / Slow, 2.5 s, no limit, limit 20. The app sends the same two messages. |
| Other admins | The app always follows the server's latest settings broadcast. The web admin only reads them once, on load. |
| Screen | Stays on while open, since the Switch button is used all through a game. |
| Layout | One column, at most 560 dp wide, centred on tablets. |

## Protocol (from `backend/cmd/state_switch.go`)

On connect the server sends `state-switch-game-state` then `state-switch-game-settings`. No handshake.

| Direction | Type | Payload |
|---|---|---|
| in | `state-switch-game-settings` | `{game, timeout (seconds, float), limitEnabled, limit}` |
| in | `state-switch-game-state` | `{actuations, current (0 or 1)}` |
| in | `button-state` | `{disabled}`, true while the cooldown runs |
| out | `state-switch-game-settings` | the full settings |
| out | `state-switch-reset` | none |
| out | `state-switch-reset-actuations` | none |
| out | `button-press` | none |

Game types, in the web's order with its labels: `fast-slow` Fast / Slow, `go-stop` Stop / Go, `loud-soft`, `left-right`, `staccato-legato`, `clapping-marching`, `standing-sitting`, `singing-humming`, `random`. An unknown type from the server is kept and shown as its slug rather than replaced.

Timeout is 0 to 9 s in steps of 0.1; limit is 0 to 20 in steps of 2. The timeout goes out as an exact tenth (`2.3`, not the float's `2.299999952316284`).

## Code

- `games/stateswitch/StateSwitchProtocol.kt`: `GameType`, `StateSwitchSettings` (parse, `toJson`, rounding, `WEB_DEFAULTS`), `StateSwitchState`, `StateSwitchCommands`.
- `games/stateswitch/StateSwitchViewModel.kt`: socket, `StateSwitchUiState` (`canSwitch`, `limitReached`), `updateSettings`, `switch`, `resetActuations`, `resetAll`, errors.
- `games/stateswitch/StateSwitchScreen.kt`: route and screen.
- Shared with Jeopardy, moved to `core/ui/Controls.kt`: `Picker` (now with `enabled`) and `OfflineBadge`.

## Phases

### S1: admin screen
- [x] Protocol, parsing and commands, with tests
- [x] View model, with tests (connect, follow broadcasts, send, switch rules, both resets, failures, offline)
- [x] Screen, with Robolectric tests (every control, release-only sliders, confirmation, offline lock)
- [x] Home screen opens it on its own socket

### S2: against the Pi (with the final device testing)
- [ ] Every setting reaches the display; slider releases send once
- [ ] The Switch button flips the display, respects the timeout and limit, and the physical button still works
- [ ] Reset All leaves Fast / Slow, 2.5 s, no limit
- [ ] Changes from the web admin appear in the app

## Progress log

| Date | Phase | Notes |
|---|---|---|
| 2026-10-17 | S1 | Built the admin screen. 253 tests. Caught the timeout float being sent as `2.299999952316284`. |
