<div align="center">

<a href="https://github.com/dom-53/Inkita">
    <img src="./.github/assets/inkita-logo.png" alt="Inkita logo" title="Inkita logo" width="80"/>
</a>

# Inkita

 Unofficial Android reader for your self-hosted **Kavita** library.

[![CI](https://img.shields.io/github/actions/workflow/status/dom-53/Inkita/release.yml?labelColor=27303D)](https://github.com/dom-53/Inkita/actions/workflows/release.yml)
![Platform](https://img.shields.io/badge/platform-Android-blue)
![Kavita](https://img.shields.io/badge/API-Kavita_0.8.8.7-lightgrey)

<br>

[//]: # ([![Inkita Stable]&#40;https://img.shields.io/github/release/dom-53/Inkita.svg?maxAge=3600&label=Stable&labelColor=06599d&color=043b69&#41;]&#40;https://github.com/dom-53/Inkita/releases&#41;)
[![Inkita Preview](https://img.shields.io/github/v/release/dom-53/Inkita.svg?maxAge=3600&label=Preview&labelColor=2c2c47&color=1c1c39&include_prereleases)](https://github.com/dom-53/Inkita/releases)


</div>

---

## Overview

Inkita is a Kotlin/Jetpack Compose Android app to browse and read from your Kavita server. It aims for fast navigation, a clean reader, and tight API integration (collections, progress, metadata).

**Tech:** Kotlin, Jetpack Compose (Material 3), Retrofit/OkHttp, WorkManager, Room/DataStore.
**Formats:** EPUB (primary); other formats planned.
**Status:** Preview (0.x); APIs and UI may change.

---

## Features

- Browse libraries, collections, tags, genres with filters.
- Series detail with covers, metadata, tags, related items, specials.
- Reader with progress sync, offline mode, downloaded page handling, “delete after reading” depth.
- Downloads: queue, per-volume/page downloads; foreground notifications via a shared manager.
- Network-aware: connectivity monitor, offline toggle, WorkManager constraints.
- Update check: fetches `updates.json` (preview/stable channels) and can download a new APK.

---

## Download

- **Preview** (alpha/beta): universal APKs are published in [Releases](https://github.com/dom-53/Inkita/releases).
- **Update feed:** `https://dom-53.github.io/Inkita/updates.json` (app uses it for update checks).
- **Stable:** not published yet (0.x).

---

## Connecting to your Kavita server

Inkita ships without any preset server configuration. After the first launch:

1. Open **Settings → Kavita**.
2. Fill in your server URL credentials and your Kavita API key.
3. Save the configuration and restart app; the app will then authenticate and sync your libraries.

Without these details the app cannot reach your server, so make sure to keep them up to date (especially when rotating API keys).

---

## Getting Started (dev)

1. **Prereqs:** Android Studio Giraffe+, JDK 17.
2. **Configure Kavita:** In-app settings → set server URL and API key/token.
3. **Run:** `./gradlew assembleDebug` or launch from Android Studio (preview/release buildTypes available).

> Requires your own Kavita server; no content is bundled.

---

## Contributing

Contributions are welcome (bugfixes, UX polish, docs). Please:
- Keep changes Compose-friendly and minimal.
- Align with Kavita API contracts.
- Add concise comments only where needed.
- Before opening an issue, check if it already exists.
- Before opening a PR, run `./gradlew detekt spotlessApply`.

---

## Support

If you find Inkita useful, you can support development on Ko-fi: https://ko-fi.com/dom53

---

## Disclaimer

Inkita is an unofficial community project, not affiliated with the Kavita team. Use at your own risk; APIs and behavior may change in 0.x releases.

---

## Note from the author

This is my first Android app in Kotlin; parts of the code may not be perfectly optimized yet. I’m steadily improving and refactoring to get it into the best shape possible. Thanks for your patience and feedback!
