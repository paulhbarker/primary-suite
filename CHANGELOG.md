# Changelog

## [2.0.0](https://github.com/paulhbarker/primary-suite/compare/v1.0.2...v2.0.0) (2026-10-18)


### ⚠ BREAKING CHANGES

* new applicationId, so it installs as a separate app. Uninstall LuckyTap from devices. local.properties keys must be renamed from luckytap.* to primarysuite.*.

### Features

* **block-stacker:** admin screen with settings, status and message log ([21c1c97](https://github.com/paulhbarker/primary-suite/commit/21c1c9790e788b02c6996cc45f87a2532f62f656))
* **block-stacker:** open the Block Stacker admin from the home screen ([748645f](https://github.com/paulhbarker/primary-suite/commit/748645fa4ceb9e85560d810ab7f7ce75857b5a38))
* **button-debug:** button debugger screen, a full port of the web page ([30926f5](https://github.com/paulhbarker/primary-suite/commit/30926f5a65e671fd4f7ce3fa3765837df9672d48))
* **button-debug:** open the Button Debugger from the home screen ([8b07cd5](https://github.com/paulhbarker/primary-suite/commit/8b07cd570fd6a4ee74428a52376254297705a57c))
* game list home screen and settings in Compose ([6541032](https://github.com/paulhbarker/primary-suite/commit/6541032c81ed3f0619a574e6bdfabaf823bb90a7))
* game-button stand-ins fire on touch, not release ([926493b](https://github.com/paulhbarker/primary-suite/commit/926493bf70d88b3df85e8ae73e21995e12c55678))
* **home:** give each game its own colour on the game list ([f2b163e](https://github.com/paulhbarker/primary-suite/commit/f2b163efd2483d9735d3bde36076b1fc181805dc))
* **jeopardy:** drain points screen with a hold button per player ([1a276cf](https://github.com/paulhbarker/primary-suite/commit/1a276cfc7c3600f8a1486e4349ac562181bfdeae))
* **jeopardy:** host controller matching the web host ([58606aa](https://github.com/paulhbarker/primary-suite/commit/58606aa98aa9e6edf3dadf75f9ee9e072f05282d))
* **jeopardy:** host protocol, board list and connection ([df182bd](https://github.com/paulhbarker/primary-suite/commit/df182bdaf9bd62ece0c040f6a75916eb5791d847))
* live Lucky Tap game state and admin controls ([d6ee35f](https://github.com/paulhbarker/primary-suite/commit/d6ee35fd0adf7f8e82c9f6140ce94b844d99529d))
* Lucky Tap scanning station in Compose ([bf32ae2](https://github.com/paulhbarker/primary-suite/commit/bf32ae2433585e9e103553cc90f0b5a14fbbbca5))
* new Primary Suite icon, four arcade buttons on Simple Games navy ([29eeb9a](https://github.com/paulhbarker/primary-suite/commit/29eeb9a0cd0130022ec7beb246779c543335b170))
* **reel-rush:** admin screen matching the web admin ([0af33a4](https://github.com/paulhbarker/primary-suite/commit/0af33a42881faf7560fb6dbefc8082edb41db564))
* **reel-rush:** open the Reel Rush admin from the home screen ([d2d638d](https://github.com/paulhbarker/primary-suite/commit/d2d638d17487fb6427216eb3ad41044943a84709))
* rename app to Primary Suite ([2cb9e99](https://github.com/paulhbarker/primary-suite/commit/2cb9e99b92e5b634b0803a2bcda11c025fafa075))
* square icon with rounded corners, red download button from icon palette ([7625412](https://github.com/paulhbarker/primary-suite/commit/7625412ad1be8f861b37f95d5461406e5db7c501))
* **state-switch:** admin screen matching the web admin ([cc2126a](https://github.com/paulhbarker/primary-suite/commit/cc2126a83272f450be0b903dd020dcbae49aef4f))
* use real app icon on install landing page ([94eba6e](https://github.com/paulhbarker/primary-suite/commit/94eba6e30d4f4039925938b32f795824272b8be1))


### Bug Fixes

* **jeopardy:** drain screen buttons start neutral, only the held one turns red ([b0fe800](https://github.com/paulhbarker/primary-suite/commit/b0fe8004e7769e188bc626b4fc10818acb204ac5))
* **net:** reconnect like the web, and bind to the event Wi-Fi on Android 6-9 ([923fdae](https://github.com/paulhbarker/primary-suite/commit/923fdaea0b531946421eca152ac4ca1e0f71ad29))
* **wifi:** keep the Wi-Fi password out of the system log ([911aacf](https://github.com/paulhbarker/primary-suite/commit/911aacf7fafa5bfa5669717394d183803e058710))

## [1.0.2](https://github.com/paulhbarker/LuckyTap/compare/v1.0.1...v1.0.2) (2026-05-08)


### Bug Fixes

* wire WiFi/WS defaults from CI secrets into BuildConfig ([5bda39a](https://github.com/paulhbarker/LuckyTap/commit/5bda39ab75807129ba0bdf8425803c56edd8eb89))

## [1.0.1](https://github.com/paulhbarker/LuckyTap/compare/v1.0.0...v1.0.1) (2026-05-08)


### Bug Fixes

* resolve three production-only crash/bug issues ([57ba7c1](https://github.com/paulhbarker/LuckyTap/commit/57ba7c1775e966d0478260749bcfe1fda16bbd73))

## 1.0.0 (2026-05-08)


### Features

* add NFC enabled state detection with prompt to enable ([2367c2f](https://github.com/paulhbarker/LuckyTap/commit/2367c2f6f67d4aed127026b4f86053b267ef5567))
* NFC auto-return from settings + app icon updates ([f3b4a01](https://github.com/paulhbarker/LuckyTap/commit/f3b4a0118baba4585b3c7b92b4fdc0cb26ec0667))
* redesign UI with Material 3 dark theme and connection status card ([7299ed2](https://github.com/paulhbarker/LuckyTap/commit/7299ed26e451233a295a51af4be2ba875e9aefc8))


### Bug Fixes

* add missing ACCESS_NETWORK_STATE to AndroidManifest ([271dc0a](https://github.com/paulhbarker/LuckyTap/commit/271dc0a71afb58f210b7f9f582376301816f114c))
* add top padding to main and settings screens ([014607f](https://github.com/paulhbarker/LuckyTap/commit/014607fdf11853bc0964acdbc946b5236a8a8c59))
* auto-reconnect WiFi when target network is lost ([9f201ae](https://github.com/paulhbarker/LuckyTap/commit/9f201ae35c072b1955323c781e62d39bd3cde41c))
* **compat:** maximize Android backward compatibility down to API 23 ([9253367](https://github.com/paulhbarker/LuckyTap/commit/9253367e36e5925d1ef7c5112e21219b72c2bee0))
* correct WebSocket endpoint path to /ws/lucky-tap ([b37495f](https://github.com/paulhbarker/LuckyTap/commit/b37495fcd867df75994893185265bffdea626ff2))
* correct WiFi connected state on API 29+ and add missing manifest permission ([443b323](https://github.com/paulhbarker/LuckyTap/commit/443b32377c359f3567d0978497f519559ce5e564))
* gate all WebSocket connections behind WiFi connectivity check ([65ac687](https://github.com/paulhbarker/LuckyTap/commit/65ac687dcd1771d53da5a2156ed4cb67bca5ce25))
* maintain CONNECTING state during WiFi retry cycle ([51aa3b7](https://github.com/paulhbarker/LuckyTap/commit/51aa3b79127f9b0cce438e7f4c3b250be987ae36))
* match Write NFC text field style with Settings fields ([648a9fc](https://github.com/paulhbarker/LuckyTap/commit/648a9fca0d7512771ddc7dfc099491e004a57197))
* only signal WiFi connected when actually on target network ([27c7e2b](https://github.com/paulhbarker/LuckyTap/commit/27c7e2bbf22e7eecb66abbb29847c468f0e88f27))
* resolve all Gradle/AGP deprecation warnings ([15ce75d](https://github.com/paulhbarker/LuckyTap/commit/15ce75d208905c4c528ed6ff832c3dd7cf2d9310))
* resolve WiFi connection failures on startup ([089c9da](https://github.com/paulhbarker/LuckyTap/commit/089c9daf63a587fc6be03747feb98d4ec53e8c4c))
* settings hint overlap, hide sections when disconnected, simplify status text ([c55eb30](https://github.com/paulhbarker/LuckyTap/commit/c55eb306341226cbd450a618611526e628da4590))
