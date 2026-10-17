# Agent notes

## Memory: WSL has 8 GB

Builds run in WSL with `memory=8GB` (`C:\Users\paul\.wslconfig`). Running out freezes the whole VM, so keep Gradle within it.

- **Test workers are capped** in `app/build.gradle.kts`: 2 forks, each with a 1 GB heap. A Robolectric/Compose worker uses about 700 MB. The old setting (`nproc / 2` = 8 uncapped forks) is what filled memory. Don't raise it. `-PtestForks=N` overrides it for one run if needed.
- **Daemons:** the Gradle daemon has `-Xmx2048m` and the Kotlin daemon `-Xmx1024m` (`gradle.properties`). Both stay resident between builds. Run `./gradlew --stop` when you're done for a while.
- **One heavy task per command.** Don't chain `assembleRelease` (R8) with test runs, and don't loop the full suite in a single command. Run them as separate commands.
- **Measured peaks** of total WSL memory in use: the full test suite with 2 forks peaks at 4.1 GB and takes 16 s; `assembleDebug assembleRelease` peaks at 3.1 GB.
- To check a new heavy task, sample `free -m` in the background while it runs before you repeat it.

## Building

- The Android SDK is at `~/Android/Sdk`, and `ANDROID_HOME` is set in `~/.bashrc`.
- `./gradlew testDebugUnitTest` runs every test (JVM only: JUnit, Robolectric, MockWebServer).
- `scripts/device-smoke.sh` installs on a USB phone using the Windows `adb.exe`.
- The plan and progress log are in `plans/roadmap.md`.
