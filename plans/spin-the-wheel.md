# Spin the Wheel admin

> Status: **done** (checked on the phone against the Pi).

## Goal

Run Spin the Wheel from the host's phone with the same controls as the web admin (`/spin-the-wheel/admin`, `frontend/src/games/spin-the-wheel/admin/Admin.tsx`). The server stays authoritative; the TV and the button box stay as they are.

## Decisions

| Topic | Decision |
|---|---|
| Scope | Everything in the web admin: the status card (phase, Spin, Lock, last result), force the next result, history with per-value counts and Clear, the odds (weight per value, Reset to even), and the settings card (save and discard). |
| Not included | The links to `/spin-the-wheel` and `/spin-the-wheel/buttons`, and the clock sync (only the TV uses it). |
| Handshake | The server treats every new socket as a display until it gets `hello {role: "admin"}`. The app sends it on every connect, including reconnects, like the web. The server answers with the current state. |
| Layout | Three tabs: **Wheel** (status, Spin, Lock, force next, history), **Odds** and **Settings**. One column, at most 640 dp wide. |
| Spin | Disabled while spinning, as on the web. During the celebration it works: the server cuts the celebration short, animates it out, then spins. |
| Odds | Act at once, like the web: each + or - sends all ten weights. The last weight above 0 can't go down. |
| Confirmations | Clear history asks first (the web uses `confirm()`). Everything else acts at once. |
| Settings form | Like Reel Rush: edits stay local until Save; Discard throws them away; the form follows the server until you edit. Ranges are checked locally (same as `Settings.Validate`) with the problem under the field, and Save is disabled while any field is bad. The full settings object is sent, as the web does. |
| Seconds fields | Typed in seconds, sent as milliseconds rounded, as on the web. |
| History times | The Pi's clock can be days off, so a result's time is moved by the difference between the server's `serverTime` and the phone's clock when the state arrived. |
| Value colours | The wheel's Vegas casino palette (`VALUE_COLORS` in `frontend/src/games/spin-the-wheel/wheel.ts`), so chips match the TV. |
| Extras | Screen stays on while open. Controls are disabled while offline. |
| Offline | The Offline badge shows once state has arrived and the socket drops. Until the first state the screen says "Connecting...". |

## Protocol (from `backend/cmd/spinwheel/{messages,engine}.go`)

On connect the server sends `state` (as a display). After `hello` it sends `state` again. Every admin command that succeeds is followed by a `state` broadcast to everyone; one that fails gets an `error` to the sender only.

| Direction | Type | Payload |
|---|---|---|
| out | `hello` | `{role: "admin"}` |
| out | `spin` | none (error while spinning) |
| out | `set-locked` | `{locked}` |
| out | `force-next` | `{value}` or `{value: null}` to clear |
| out | `set-weights` | `{weights: [10]}` in `values` order, each 0 to 10, at least one above 0 |
| out | `set-settings` | `{spinMinMs, spinMaxMs, celebrateMs, volume}` |
| out | `clear-history` | none |
| in | `state` | below |
| in | `error` | `{message}` |
| in | `clock-pong` | ignored |

Settings: `spinMinMs` and `spinMaxMs` 3000 to 15000 (max not below min), `celebrateMs` 1000 to 30000, `volume` 0 to 1 (web step 0.05). Defaults 8000, 10000, 10000, 0.8.

State: `phase` (`armed spinning landed`), `locked`, `wedges[20]`, `values[10]`, `weights[10]` (in `values` order), `forcedNext` (value or null), `settings`, `angle`, `spin?` (`id startAt durationMs fromAngle toAngle wedge value`, while spinning or landed), `lastResult?`, `history[]` (newest first, at most 100), `serverTime`. A result is `id value wedge at button rigged`; `button` is the box button 1 to 4 (green, yellow, white, blue) or 0 for the admin.

Status line, as on the web: "Locked: buttons do nothing" when armed and locked, else "Ready: any button spins", "Spinning..." or "Landed" (with the value).

## Code

- `games/spinwheel/SpinWheelProtocol.kt`: phases, settings, results, state parsing, commands, value colours.
- `games/spinwheel/WheelSettingsForm.kt`: the editable settings, validation, conversion.
- `games/spinwheel/SpinWheelViewModel.kt`: socket, hello on connect, state, actions, errors.
- `games/spinwheel/SpinWheelScreen.kt`: route, tabs, confirm dialog; `SpinWheelPanels.kt`: the three tabs.

## Tests

- Protocol: state parsing (with and without spin, forced value, history), commands, settings JSON.
- Settings form: round trip, ranges, max below min, seconds parsing.
- View model: connects with WiFi, hello on every connect, follows state, sends each command, errors.
- Screen: connecting, status lines, Spin disabled while spinning, lock toggle, force next and clear, odds buttons, clear history confirm, settings save and discard.
- Navigation: the game opens its own socket.

## Progress

- [x] Code and tests (33 new tests)
- [x] On the phone against the Pi: state arrives, Spin plays on the TV, the result and history come back
