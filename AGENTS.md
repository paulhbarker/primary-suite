# Agent notes

## Memory: WSL has 8 GB

Builds run in WSL with `memory=8GB` (`C:\Users\paul\.wslconfig`). Running out freezes the whole VM, so keep Gradle within it.

- **Test workers are capped** in `app/build.gradle.kts`: 2 forks, each with a 1 GB heap. A Robolectric/Compose worker uses about 700 MB. The old setting (`nproc / 2` = 8 uncapped forks) is what filled memory. Don't raise it. `-PtestForks=N` overrides it for one run if needed.
- **Daemons:** the Gradle daemon has `-Xmx2048m` and the Kotlin daemon `-Xmx1024m` (`gradle.properties`). Both stay resident between builds. Run `./gradlew --stop` when you're done for a while.
- **One heavy task per command.** Don't chain `assembleRelease` (R8) with test runs, and don't loop the full suite in a single command. Run them as separate commands.
- **Measured peaks** of total WSL memory in use: the full test suite with 2 forks peaks at 4.1 GB and takes 16 s; `assembleDebug assembleRelease` peaks at 3.1 GB.
- To check a new heavy task, sample `free -m` in the background while it runs before you repeat it.

## Parallel agents

Several agents may work at once, each in its own git worktree under `~/projects/primary-suite-wt/`. They must never run Gradle at the same time:

- **Always run Gradle through `scripts/gradle-locked.sh`** (or `scripts/test.sh`, which uses it). It takes a machine-wide `flock`, so builds queue and share one Gradle daemon and one Kotlin daemon. Never call `./gradlew` directly, and never `./gradlew --stop` while other agents are working.
- **Run only your own tests:** `scripts/test.sh 'com.primarysuite.app.games.<pkg>.*'` runs matching classes in one fork. Leave the full suite to whoever merges.
- **Compile check:** `scripts/gradle-locked.sh compileDebugUnitTestKotlin` compiles main and test code without running anything.
- **No release builds** (`assembleRelease`, R8) from agents; the merge step does one.
- **Rendered screenshots** (Robolectric `GraphicsMode.NATIVE`) are fine through the same scripts. Delete any scratch test before committing.

## Building

- The Android SDK is at `~/Android/Sdk`, and `ANDROID_HOME` is set in `~/.bashrc`.
- `./gradlew testDebugUnitTest` runs every test (JVM only: JUnit, Robolectric, MockWebServer).
- `scripts/device-smoke.sh` installs on a USB phone using the Windows `adb.exe`.
- The plan and progress log are in `plans/roadmap.md`.
