# Verifying secure storage migration (debug build)

Steps to confirm the API key lives only in encrypted prefs:

1) Install and run the app (debuggable build) so migration runs in `StartupManager`.
2) Check that encrypted prefs exist:
   - `adb shell run-as net.dom53.inkita ls shared_prefs` → should list `secure_prefs.xml`.
   - `adb shell run-as net.dom53.inkita cat shared_prefs/secure_prefs.xml` → shows encrypted blobs (expected).
3) Ensure plain DataStore no longer holds sensitive keys:
   - `adb shell run-as net.dom53.inkita strings files/datastore/inkita_prefs.preferences_pb | Select-String "api_key"` → should be empty output.
4) If you need logging for migration, temporarily add a `Log.d("SecureStorage", "...")` inside `migrateSensitiveIfNeeded()`/setters and filter Logcat with `tag:SecureStorage` (remove afterwards).

Notes:
- Migration wipes legacy keys even if they were blank, then writes the API key into `secure_prefs`.
- Secure values are encrypted; seeing base64-like blobs in `secure_prefs.xml` is expected.
