# Agent notes

Primary Suite is the Android admin app for the Simple Games server (`~/projects/simple-games`, read its `AGENTS.md` too). Plans and progress logs are in `plans/` (`roadmap.md`, one file per game). The README covers the architecture and how to add a game.

## Memory: WSL has 8 GB, and running out freezes the VM

- **Run Gradle only through `scripts/gradle-locked.sh`** or `scripts/test.sh`, never `./gradlew` directly. The lock queues builds machine-wide, so parallel agents share one Gradle and one Kotlin daemon.
- `scripts/test.sh` runs the full suite (2 forks, about 4 GB peak, 25 s). `scripts/test.sh 'com.primarysuite.app.games.<pkg>.*'` runs one package in one fork. A result of "0 tests" is a failure.
- **One heavy task per command.** Don't chain `assembleRelease` with tests. Keep the fork cap (2 × 1 GB) and the daemon heaps in `gradle.properties` as they are.
- **Use a timeout** (`timeout 600 scripts/test.sh ...`). A hung Robolectric test holds the lock and blocks every other agent. If a run takes several minutes, kill its test worker JVM, not the daemon.
- Run `./gradlew --stop` when you finish, unless other agents are still working.

## Parallel agents

Give each agent its own git worktree (`git worktree add -b feat/<x> ~/projects/primary-suite-wt/<x> main`, then copy `local.properties` in). Agents touch only their own `games/<pkg>/`, tests, `strings_<pkg>.xml` and plan. The lead does the shared wiring: `GameCatalog`, `PrimarySuiteNavHost`, `MainActivity`, README and roadmap. Remove the worktrees when you're done.

## Git and releases

- Commit straight to `main` with conventional commits (`feat(jeopardy): ...`). Tests must pass before a push.
- `main` **rejects merge commits.** Bring branches in with `git cherry-pick` or a rebase, and merge PRs with squash.
- Release Please keeps a release PR open. Merging it tags the release and calls `build-release.yml`, which attaches `app-release.apk`. The install page links to `/latest/download/app-release.apk`, so keep that file name.
- CI secrets (`DEFAULT_WIFI_*`, signing) feed the release build. Never log secrets: the Wi-Fi password once leaked through a generated `toString`.

## Talking to the server

- **The server is authoritative.** Screens show what it sends and send exactly what the game's web admin sends. Read the Go handlers in `backend/cmd/` before writing a protocol.
- **Some games need a handshake on every (re)connect:** Jeopardy! sends `jeopardy-hello {role:"host"}`, Reel Rush sends `hello {role:"admin"}`. Until then the server treats the socket as a display.
- **Don't send messages that disturb the projected display.** Never send `admin-get-full-state` (Lucky Tap) or `block-stacker-get-state` unprompted.
- Constants copied from the server (Lucky Tap weights, drain step, slider ranges) must change in both places.
- Sockets retry forever (0.5, 1, 2, 4, then 5 s) and ping every 10 s, to match `frontend/src/websocketOptions.ts`.

## Tests: known traps

- In a `Dialog` under Robolectric, use `rememberTextFieldState`. A value-based `OutlinedTextField` never goes idle there, so the test hangs and then runs out of memory.
- Endless loops (ping tickers, `withInfiniteAnimationFrameMillis`) must be injectable or stopped in tests, or virtual time never ends.
- Inject dispatchers. A real `Dispatchers.IO` in a screen test races the assertions and fails on slow CI.
- `JSONObject` doesn't keep key order, so compare fields. Send floats as `value.toString().toDouble()`, or `2.3f` goes out as `2.299999952316284`.
- Kotlin test names can't contain `; : . / [ ]`. Button labels merge into the button node, so find buttons by test tag.
- Versions are pinned (Kotlin 2.2, compileSdk 36, Compose BOM 2026.06.01, Coil 3.3.0). Newer versions need Kotlin 2.3 or compileSdk 37.
- To look at a screen, render it with a scratch Robolectric test (`GraphicsMode.NATIVE`), then delete the test. The image viewer caches by path and may not see new files in `/tmp` straight away, so use a new file name, saved under the project's `build/`.

## Device and Pi

- **Phone:** use `scripts/device-smoke.sh` (Windows `adb.exe`) to build, install and launch.
  - Screenshot: `adb.exe exec-out screencap -p > build/x.png`. Logs: `adb.exe logcat -d --pid=$(adb.exe shell pidof com.primarysuite.app)`.
  - After a fresh install, the user must allow location and tap **Connect** for `GroveParkPrimary`.
  - A debug build blocks the release install (different signing key), so uninstall it first.
- **Pi:** `ssh paul@10.0.0.1`, service `gameserver` (`journalctl -u gameserver`).
  - The network is `GroveParkPrimary`: WPA2/AES, 2.4 GHz, no internet. The app binds its traffic to it.
  - The Pi's clock can be days behind, so don't trust its log dates.
- **Icon:** edit `scripts/icon/make_icon.py`, then run `scripts/icon/render.sh`.

## Writing

Use plain, direct English in code comments, strings, plans and commits. No em dashes, no filler.
