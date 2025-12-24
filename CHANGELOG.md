# Changelog

All notable changes to this project will be documented here.

## Unreleased

### Added
- Series Detail V2: Clicking a collection opens Library V2 collections with that collection selected.
- Series Detail V2: Tapping a genre or tag opens Browse with the filter pre-applied.
- Downloads V2: PDF download requests now enqueue and appear in the Download Queue.
- Library/Browse: Download status badges now appear on series covers (complete/partial).
- Downloads V2: Download queue entries now show series/volume/chapter labels when available.

### Changed
- Reader: Split EpubReaderViewModel and PdfReaderViewModel into separate files.
- UI: Rounded corners aligned across Library V2, Series Detail V2, and Volume Detail V2 covers.
- Downloads V2: Downloaded PDF items now open with the correct MIME type.
- Downloads V2: Queue/Completed rows now wrap titles cleanly instead of truncating them.

### Fixed
- Navigation: Bottom bar now remains visible when opening Library/Browse with query params (collections/tags/genres).
- Cache: Series Detail V2 caching now defaults to enabled to prevent offline cache misses.
- History: Reading history list no longer crashes due to duplicate LazyColumn keys.

## v0.3.0-beta

### Added
- Library V2: Added a new navigation entry and sliding menu with sections (Home, Want To Read, Collections, Reading Lists, Browse People, libraries list) plus data loading from Kavita endpoints with pagination where applicable.
- Series Detail V2: New screen with summary expand/collapse, chips, metadata layout, action row (collections/want-to-read/web/share), and tabs for Books/Chapters/Specials/Related/Recommendations/Reviews.
- Volume Detail V2: Dedicated screen with volume metadata, summary expand/collapse, and chapters list; supports deep link from series detail volumes.
- Reader: Continue reading button now routes into the correct reader using progress data; volume/series continue labels reflect progress and volume/chapter info.
- UI: Progress overlays on volume/chapters (triangle for unread, progress bar for in-progress).
- Downloads V2: Per-volume and per-chapter download actions, download queue screen wiring, and download state icons for volumes/chapters/pages.
- Downloads V2: Page list supports swipe-to-download/remove with haptic feedback and adjustable swipe distance.
- Downloads V2: Download queue screen redesigned with cards, status chips, progress bars, and empty states.
- Reader: Prefer offline pages now loads downloaded HTML when available and shows an “Offline data” overlay flag.
- Series Detail V2: Specials now open into per-page lists (like Volume Detail) with download/reader actions and long-press download dialog.
- Settings: Added Kavita Images API key field for collection/person cover endpoints.
- Series Detail V2: Added menu actions to mark series read/unread.
- Series Detail V2: Volume long-press actions now include mark volume read/unread.
- Volume Detail V2: Chapter long-press actions now include mark read/unread (progress update).
- Volume Detail V2: Swipe right on a page updates progress (read/unread); swipe left still downloads/removes.
- Volume Detail V2 / Specials: Page titles now pull from EPUB TOC (getBookChapters) instead of generic labels.
- UI: Important update modal now shows once per versionCode.

### Changed
- DTOs: Completed several Kavita DTOs (SeriesMetadataDto, PersonDto, GenreTagDto, VolumeDto, and related detail DTOs) for Detail V2 data aggregation.
- Detail data: Added InkitaDetailV2 aggregator and wiring to fetch full series detail payloads and related data.
- Reader: Remaining time in the overlay updates on page/chapter changes and is rounded to one decimal.
- Downloads V2: Download worker now respects max concurrent downloads and automatic retries/max retry attempts from settings.
- Downloads V2: Download-all now includes volumes, chapters, specials, and storyline chapters.
- Settings: Prefetch switches in Advanced are temporarily disabled until the new prefetch pipeline is implemented.
- Logging: Added verbose logs for Library V2 cache decisions, Series/Volume Detail V2 flows, and Download V2 enqueue/worker events.
- Downloads: Download settings now use dedicated metered/low-battery preferences; download workers respect those constraints.
- Cache: "Cache stale after" now supports minutes or hours with a 15-minute default.
- Network: Increased global HTTP timeouts to 30 seconds.

### Fixed
- Detail V2: Progress and continue point refresh after returning from the reader to avoid stale data.
- Volume detail: Prevent volume name from being overwritten by API responses that return only the numeric label.
- Reader: Remaining time refreshes on page changes.
- Downloads V2: Deduplicate page downloads and avoid re-queuing already downloaded pages.
- Downloads V2: Queue processing continues beyond the first batch of pending jobs.
- Reader: Offline overlays now show page/title data derived from downloaded files when online data is unavailable.
- Reader: Progress sync now respects Kavita timestamps without timezone and avoids overwriting newer server progress.
- Downloads V2: Clearing downloaded pages now removes items from the Downloaded tab.
- Browse: Thumbnail shimmer stays active per-item until the image finishes loading.

## v0.2.0-beta.2

### Added
- Localization: Declared supported locales (EN, CS) via `locale-config` and AppCompat auto locale storage so per-app language works from system settings and the in-app picker.
- Logging: Added rotating on-device log files with export/save/clear controls, a verbose logging toggle, sanitization of hosts/IPs/API keys, config snapshot in exported zips, and automatic pruning (12 days, 5 files).
- Browse: Added shimmer placeholders for grid tiles and thumbnails while covers load.
- Settings: Added “Max thumbnails in parallel” in Advanced to limit concurrent thumbnail downloads.
- Settings: Added option to disable thumbnails in Browse for text-only results.

### Changed
- UI: Switched theme base to `Theme.AppCompat.DayNight.NoActionBar` (required for per-app language support with Compose).
- Localization: Language picker now applies the chosen locale through `AppCompatDelegate` and aligns with the system “App language” setting; Czech strings completed and cleaned up.
- Browse: Progressive grid loading with placeholders sized to the browse page size; images load on-demand through Coil.
- Browse: Enabled Coil memory/disk cache for thumbnails to reduce re-downloads when returning or scrolling.
- Network: Custom Coil `ImageLoader` now respects the max-parallel thumbnails setting via OkHttp dispatcher limits.
- Browse: Guarded paging requests to avoid duplicate page loads during fast scrolling.
- Browse: Search field now includes a clear (X) button that resets the query and reloads results.
- Browse: Fresh cache no longer blocks loading additional pages; pagination continues beyond the first cached page.
- Reader: Refactored to use a shared BaseReader interface with separate EPUB/PDF renderers.
- Reader: Split into BaseReaderScreen with dedicated EPUB/PDF screens for easier per-format customization.
- Reader: Added domain BaseReader + EPUB/PDF reader implementations and per-format ViewModels.

## v0.2.0-beta.1

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
