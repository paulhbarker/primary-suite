# Reel Rush admin

> Status: **R2 mostly done** (checked on the phone against the Pi). Left: web admin changes appearing in the app.

## Goal

Run Reel Rush from the host's phone with the same controls as the web admin (`/reel-rush/admin`, `frontend/src/games/reel-rush/admin/Admin.tsx`). The server stays authoritative; the projector display and the physical buttons stay as they are.

## Decisions

| Topic | Decision |
|---|---|
| Scope | Everything in the web admin: the phase card with its buttons (open lobby, start, close round, next round, replay, end game, reset), the players list with Remove, the last round or final standings, and the settings card (save and discard). |
| Not included | The links to `/reel-rush` and `/reel-rush/buttons`, and the clock-ping sync (the admin never uses it; only the display does). The `event` messages (sounds and flashes for the display) are ignored. |
| Handshake | The server treats every new socket as a display until it gets `hello {role: "admin"}`. The app sends it on every connect, including reconnects, like the web. The server answers with the current state. |
| Layout | Two tabs: **Game** (phase card, players, last round) and **Settings**. The web shows them side by side on wide screens; a phone has no room. One column, at most 640 dp wide. |
| Confirmations | End game and Reset ask first (the web uses `confirm()`). Everything else acts at once, as on the web. |
| Settings form | Like the web: edits stay local until Save settings; Discard throws them away; the form follows the server until you edit. The web sends whatever you typed and lets the server answer with an error. The app also checks the ranges locally (same ranges as `Settings.Validate`), shows the problem under the field and disables Save. A server error still reaches the snackbar. |
| Settings message | The full settings object, as the web sends. The server merges it into its settings. |
| Seconds fields | Shortest/longest round and pause are typed in seconds and sent as milliseconds, rounded, as on the web. |
| Joined count | Start is disabled below 2 joined players and says so, like the web. |
| Extras | Screen stays on while open (a game runs for a long time and the phone must not drop the socket). Local range checks with inline messages. Controls are disabled while offline. |
| Offline | The Offline badge shows once state has arrived and the socket drops. Until the first state the screen says "Connecting...". |

## Protocol (from `backend/cmd/reelrush/{messages,view,settings,engine}.go`)

On connect the server sends `state` (as a display). After `hello` it sends `state` again. Every admin command that succeeds is followed by a `state` broadcast to everyone; one that fails gets an `error` to the sender only.

| Direction | Type | Payload |
|---|---|---|
| out | `hello` | `{role: "admin"}` |
| out | `settings` | full settings (below) |
| out | `open-lobby` | none (idle or final; ignored in lobby; error otherwise) |
| out | `remove-player` | `{slot}` (lobby only) |
| out | `start-game` | none (needs 2 joined players) |
| out | `close-round` | none (window phase only) |
| out | `next-round` | none (results phase) |
| out | `replay-round` | none |
| out | `end-game` | none |
| out | `reset` | none |
| in | `state` | the whole game, below |
| in | `error` | `{message}` |
| in | `event`, `clock-pong` | ignored |

Not sent: `clock-ping`.

Settings: `reels` 2 to 5, `rounds` 1 to 50, `hardMode`, `assist`, `rowDecoys`, `roundMinMs` and `roundMaxMs` 1000 to 30000 (max not below min), `nearMisses` 0 to 4, `spinSpeed` 15 to 40, `dwellMinMs` and `dwellMaxMs` 100 to 5000 (max not below min), `displayLagMs` 0 to 300, `volume` 0 to 1 (web step 0.05), `names` 4 strings of up to 10 characters (the server trims, upper-cases, and replaces empty ones with GREEN, YELLOW, WHITE, BLUE).

State: `phase` (`idle lobby intro spinning window results final`), `settings`, `round`, `rounds`, `suddenDeath`, `players[4]` (`slot name color joined score fouls status reactionMs? rank?`), `result?`, `history[]`, `final?`. The `timeline` (reel layout for the display) is ignored. A round result has `round suddenDeath voided entries[] winners`; an entry has `slot rank? reactionMs? points foul closedOut`. Final has `standings[]` (`slot rank score bestMs?`), `winners`, `tiebreak`, `titles[]` (`key label slots value`).

Next-round button label, as on the web: voided result gives "Replay the round"; sudden death gives "Show the winner" with exactly one winner and "Another sudden death" otherwise; the last regular round gives "Finish the game"; else "Next round".

## Code

- `games/reelrush/ReelRushProtocol.kt`: phases, settings, players, results, final, state parsing, commands.
- `games/reelrush/ReelRushSettingsForm.kt`: the editable settings (text for numbers), validation, conversion to settings.
- `games/reelrush/ReelRushViewModel.kt`: socket, hello on connect, state, actions, errors.
- `games/reelrush/ReelRushScreen.kt`: route, tabs, game tab, settings tab.

## Phases

### R1: admin screen
- [x] Protocol, parsing and commands, with tests
- [x] Settings form and validation, with tests
- [x] View model, with tests (connect and hello, follow state, every action, failures, offline)
- [x] Screen, with Robolectric tests (every control per phase, confirmations, settings form, offline lock)

### R2: against the Pi (with the final device testing)
- [x] The admin gets state after hello and after a reconnect
- [x] A full game from the phone: lobby (players join on the buttons), start, rounds, close round, replay, final, back to the lobby
- [x] Settings save reaches the display; a bad value shows the server's error
- [ ] Changes from the web admin appear in the app

## Open questions

- None blocking. The clock-ping is left out because the admin page never reads the clock; say so if the phone should ever act as a display.

## Progress log

| Date | Phase | Notes |
|---|---|---|
| 2026-10-17 | R1 | Built the admin screen (Game and Settings tabs), view model, settings form with local range checks. 64 tests. The admin needs the `hello {role: admin}` handshake, sent on every connect. |
| 2026-10-17 | R2 | On the Galaxy S10+ (SM-G975U), Android 12 (API 31) against the Pi: hello, lobby, start, next round, end game and settings save all worked with no server errors. Not checked: web admin changes in the app. |
