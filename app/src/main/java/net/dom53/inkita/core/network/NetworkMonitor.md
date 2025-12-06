# NetworkMonitor

### What it is
`NetworkMonitor` is a shared connectivity helper that exposes the current network status as a `StateFlow<NetworkStatus>` for both UI and background work. It:
- Listens to `ConnectivityManager` callbacks (INTERNET + VALIDATED) and classifies transport (wifi/cellular/ethernet/other).
- Tracks metered/roaming flags.
- Supports a manual `offline mode` flag (from `AppPreferences.offlineModeFlow`) that forces the app to behave as offline even when connectivity exists.
- Provides `isOnline()`/`shouldDeferNetworkWork()` helpers and a `buildConstraints(...)` helper for WorkManager requests that respect offline mode and metered/battery prefs.
- Debounces rapid connectivity changes by 300 ms to avoid UI/work flapping.

### Data model
`NetworkStatus` contains:
- `isOnline`: physical/validated connectivity.
- `connectionType`: Wifi/Cellular/Ethernet/Other/None.
- `isMetered`, `isRoaming`: from `NetworkCapabilities`.
- `offlineMode`: user-driven soft offline.
- `isOnlineAllowed`: derived `isOnline && !offlineMode`; use this for gating network work.

### Lifecycle
- `NetworkMonitor.getInstance(context, prefs?)` returns a process-wide singleton. If `prefs` is omitted, it creates its own `AppPreferences`.
- Internally, it registers a `NetworkCallback` on construction and exposes a debounced `status` flow seeded with the current snapshot + offline mode.
- Call `cancel()` only if you need to tear it down manually (normally not required).

### Usage
- UI: collect `NetworkMonitor.getInstance(context).status` for banners or to disable actions; check `isOnlineAllowed`.
- WorkManager: use `buildConstraints(allowMetered, requireBatteryNotLow)` and also skip enqueueing if `status.value.offlineMode` is true.
- Guard network calls: check `isOnlineAllowed` before issuing (or rely on higher-level retry logic).
- Offline mode toggle: call `AppPreferences.setOfflineMode(true/false)`; the status flow reflects it and network work will defer.

### Notes
- Logging of status is enabled only in debuggable builds when a debugger is attached (see `StartupManager`).
- Debounce is fixed at 300 ms; adjust in `NetworkMonitor` if you see flapping on specific devices.
