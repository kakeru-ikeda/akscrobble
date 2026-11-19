# Changelog

## [1.3.0](https://github.com/kakeru-ikeda/akscrobble/compare/v1.2.1...v1.3.0) (2026-11-19)


### Features

* **auth:** replace WebView with copy-URL external browser flow ([#23](https://github.com/kakeru-ikeda/akscrobble/issues/23)) ([af52519](https://github.com/kakeru-ikeda/akscrobble/commit/af525195b23fc8abca0314ed03f6b75188b9d858))
* Last.fm の署名を scrobsign に移し、API キー入力方式を廃止する ([#22](https://github.com/kakeru-ikeda/akscrobble/issues/22)) ([4d356bd](https://github.com/kakeru-ikeda/akscrobble/commit/4d356bdec4f6d93c7a3f110af158593856f4caba))

## [1.2.1](https://github.com/kakeru-ikeda/akscrobble/compare/v1.2.0...v1.2.1) (2026-11-09)


### Bug Fixes

* **setup:** show ADB option button on the setup screen ([#18](https://github.com/kakeru-ikeda/akscrobble/issues/18)) ([3b7a19a](https://github.com/kakeru-ikeda/akscrobble/commit/3b7a19ad8b13046ffc0af122b0f4236ebd7c7c3c))

## [1.2.0](https://github.com/kakeru-ikeda/akscrobble/compare/v1.1.3...v1.2.0) (2026-11-01)


### Features

* **ui:** redesign UI with setup flow and per-source toggles ([#16](https://github.com/kakeru-ikeda/akscrobble/issues/16)) ([828d7a2](https://github.com/kakeru-ikeda/akscrobble/commit/828d7a2ef38a865ba98544d4e77a39c01460f697))

## [1.1.3](https://github.com/kakeru-ikeda/akscrobble/compare/v1.1.2...v1.1.3) (2026-11-01)


### Bug Fixes

* **update:** do not show update dialogs on a destroyed activity ([#14](https://github.com/kakeru-ikeda/akscrobble/issues/14)) ([d5fb03f](https://github.com/kakeru-ikeda/akscrobble/commit/d5fb03fd6f501d17e9254f50e9d28aa84f549148))

## [1.1.2](https://github.com/kakeru-ikeda/akscrobble/compare/v1.1.1...v1.1.2) (2026-11-01)


### Bug Fixes

* **update:** resume the update after the install permission screen ([#12](https://github.com/kakeru-ikeda/akscrobble/issues/12)) ([b08dbad](https://github.com/kakeru-ikeda/akscrobble/commit/b08dbad7c43a94ad029e067e222a6f7ba37e586f))

## [1.1.1](https://github.com/kakeru-ikeda/akscrobble/compare/v1.1.0...v1.1.1) (2026-11-01)


### Bug Fixes

* show the app version on the main screen ([#11](https://github.com/kakeru-ikeda/akscrobble/issues/11)) ([b13e766](https://github.com/kakeru-ikeda/akscrobble/commit/b13e7665ec3f6c3abe4bb163a06203e791b63443))


### CI

* tag releases as vX.Y.Z without the component name ([#9](https://github.com/kakeru-ikeda/akscrobble/issues/9)) ([56b6766](https://github.com/kakeru-ikeda/akscrobble/commit/56b6766775642dbe37262ba336b1786e10b79d2e))

## [1.1.0](https://github.com/kakeru-ikeda/akscrobble/compare/akscrobble-v1.0.2...akscrobble-v1.1.0) (2026-11-01)


### Features

* self-update via the update server ([#7](https://github.com/kakeru-ikeda/akscrobble/issues/7)) ([17bb69b](https://github.com/kakeru-ikeda/akscrobble/commit/17bb69b480f38d014521e3c943eb6b1dbf963552))


### Bug Fixes

* **ci:** use the current fetch_sdk.sh when building an older tag ([#6](https://github.com/kakeru-ikeda/akscrobble/issues/6)) ([5324dcb](https://github.com/kakeru-ikeda/akscrobble/commit/5324dcb0101686d9a853326346201a3d4a28a5f8))


### CI

* add release-please, signed CI build and update server publish ([#5](https://github.com/kakeru-ikeda/akscrobble/issues/5)) ([19730cd](https://github.com/kakeru-ikeda/akscrobble/commit/19730cdf7c085c94f7dfb441ae55f1426e5dcd08))
