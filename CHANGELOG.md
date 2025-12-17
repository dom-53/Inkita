# Changelog

All notable changes to this project will be documented here.

## Unreleased

### Added
- Auth/config: Kavita settings now accept only server URL + API key (username/password/login removed). API key is stored in encrypted prefs; UI masks saved keys and requires re-entry to change.
- Auth/config: Toggle to choose HTTPS/HTTP (default HTTPS). HTTP use prompts a confirmation dialog; cleartext traffic enabled for HTTP servers. Sign-out button clears server/API key.
- Network: All Kavita API calls use `x-api-key` header (Bearer/JWT flow removed). Download manager and page worker send the API key on requests/assets.
- About: Added links to Kavita website and Kavita Discord.
- Settings: Downloads screen is scrollable to fit on smaller displays.
- Updates/notifications: Startup update checker now shows a progress notification and, if a newer build exists, a tap-to-download notification. First app start prompts once to enable notifications; choice is remembered.

### Changed
- Auth: Removed login/refresh endpoints, `AuthManager`, and token/refresh storage. `KavitaApiFactory` injects `x-api-key` instead of Bearer.
- Storage: `AppConfig`/`AppPreferences` now store only server URL, API key, and userId; legacy token fields migrated/cleared.
- Downloads: WorkManager requests no longer use expedited mode (fix crash with battery constraints).
- Settings: Server input takes host only (scheme via HTTPS/HTTP toggle).

## v0.1.0-beta.3

### Added
- Reader: In-app PDF viewer (PdfRenderer) with progress sync; PDFs are downloaded directly via API.

## v0.1.0-beta.1

### Added
- Reader: Offline-first page loading, with "Prefer offline pages" toggle and offline indicator banner.
- Reader: Offline progress cache with timestamp; syncs newer progress between local and server when online.
- Reader: Background ProgressSyncWorker + service locator to sync local progress whenever network is available (enqueued on app start/resume and after saving progress).
- Downloads: Download manager + queue (pause/resume/retry/cancel), concurrency limit, settings for metered/low-battery, Downloaded tab, download actions in series detail menu.
- Downloads: Clear downloaded pages per type; queue cleanup for canceled tasks; show downloaded state for volumes/chapters.
- Settings: Download settings screen; button to open download queue from series detail.
- Downloads: Files now save exclusively to the app sandbox (`Android/data/.../Inkita/downloads/...`) instead of the public Downloads directory.
- Downloads: Configurable automatic retries with max attempt limit; clearing downloads/cache now runs off the main thread to keep UI responsive.
- Core: Unified AppNotificationManager wrapper with shared channels (Downloads/Prefetch/Sync/General), permission-aware progress/info helpers, and foreground notification builder for workers/services.
- Downloads: “Delete after reading” setting added (per-page sliding window and volume completion) with configurable depth; reader now deletes older downloaded pages as you advance.
- Startup: Centralized initialization via `StartupManager`, building shared prefs/DB/cache/repositories/auth and enqueuing progress sync; `MainActivity` now boots through it and passes components to `InkitaApp`.
- Auth: Refresh-token endpoint wired through `AuthManager`; tokens are refreshed and saved automatically via a shared helper in `AppPreferences`.
- Network: Shared `NetworkMonitor` with debounced connectivity status (wifi/cellular/ethernet), offline mode flag, and WorkManager constraint helper for UI/workers.
- Settings: Offline mode toggle to force the app to stay offline (network work skipped even if connectivity exists).
- Series detail: Specials tab shows extra/special volumes from the series detail API and caches their IDs for offline display.

### Changed
- Reader: Prevent navigation to next/prev page when offline and page not downloaded; swap download/read icons.
- Reader: Allow next-page navigation when page count is unknown (offline) instead of snapping back to first page.
- Cache: Remove broken downloaded entries when files are missing; revalidate on read.
- Downloads/Prefetch: Foreground notifications now built through the shared manager and respect POST_NOTIFICATIONS permission; small foreground text tweaks.
- Series detail: Volume cards reserve icon space (no shrink on long titles); removed expand arrows.
- Auth: Token handling now checks JWT expiry/early refresh, exposes `ensureValidToken` for 401 retries, and supports logout; network logging limited to debuggable + attached-debugger builds.
- Auth: Tokens/refresh/API key are now stored in EncryptedSharedPreferences with migration from plain DataStore; DataStore copies are cleared.
- Logging: Centralized `LoggingManager` with controllable debug/info and always-on (configurable) warn/error for consistent, safe logging across the app.

### Fixed
- Download queue cancel/clear handling; avoid duplicate page downloads; validate missing downloads on startup.
- Reader: Safe offline fallback when downloaded file is missing; timestamp comparison uses `lastModifiedUtcMillis`.
- Cache/download clearing from Advanced/Downloads/Detail menus no longer freezes the UI.
- Browse: after hitting offline error, the screen now retries and refreshes automatically once connectivity returns (via NetworkMonitor).

## v0.1.0-alpha.1

### Added
- Series detail: Refactored to MVVM (SeriesDetailViewModel) to keep the UI presentational and move data/actions to the ViewModel.
- Cache: CacheManager now handles caching (DB + thumbnails) with size display, stats export, and a confirmation dialog when clearing.
- Settings: New Advanced screen consolidates all cache settings (global/library/browse, TTL window, prefetch, clear cache, stats); General now only contains language selection.

- **Cache: Offline and metadata caching**
    - Cache series metadata (summary, writers, tags, publication status).
    - Cache pages and chapters for offline mode.
    - Offline fallback for Series Detail: instantly show cached content when offline.

- **Cache: Browse and collections caching**
    - Cache browse results (`cached_browse_refs` table + migration).
    - Cache collections in AppPreferences.

- **Cache: Thumbnail and cover handling**
    - Cache series thumbnails locally.
    - Validate cached cover images (detect corrupted/zero-byte files).
    - Re-download invalid covers.
    - Resize covers to max 512px before saving to reduce storage footprint.

- **Prefetching**
    - Prefetching moved to WorkManager (runs in background as a foreground worker).
    - Prefetch settings for:
        - In-progress series
        - Want-to-read series
        - Dynamic collections
        - Series details (volumes/chapters)
    - Allow selecting specific collections to prefetch.
    - Allow prefetch on metered networks and low battery.
    - Added notification permission request for prefetch progress.

- **UI / Settings**
    - Add clear cache functionality.
    - Clear cache dialog now supports per-type deletion (all / database / details / thumbnails).
    - Add cache statistics screen.
    - Add toast after clearing cache.
    - Display cache size in settings.
    - Add configurable cache refresh window (TTL, in minutes).

### Changed
- Centralized DTO→domain mapping in mapper files (libraries, reader entities, series volumes/TOC).
- Cache flow: Library/Browse/Detail now read/write through CacheManager, respecting TTL/refresh rules.
- Refactor caching logic into CacheManager and CacheManagerImpl.
- Move prefetch/caching code from LibraryViewModel into PrefetchWorker.
- Refactor launchPrefetchIfNeeded call.
- Reordered general settings and reorganized cache section.
- Refactor toggle button color scheme in Advanced settings.
- Load local thumbnails from new cache directory.
- Improved series detail loading state.

### Fixed
- Show cached series detail when offline (fallback without crash).
- Prevent bad continue states after marking read/unread.
- Various fixes in series detail loading and progress handling.

## v0.1.0-alpha

### Added
- Series detail: Swipe volumes/chapters to toggle read/unread.
- Series detail: Share button with link.
- Series detail: Continue reading uses Kavita continue-point.
- About screen with version, build time, GitHub, license.
- Browse filters persist across process death.
- Reader progress improvements.

### Changed
- Refactored series detail loading.
- Updated styling of swipe backgrounds and cards.
- README refreshed with logo and feature overview.

### Fixed
- Restored reading position handling on series detail.
- Prevented broken continue states after marking items read/unread.
