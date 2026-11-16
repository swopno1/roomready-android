# Release Gate — RoomReady v1 (1.0)

**Date:** November 15, 2026 (refreshed for the production shape)
**Branch:** `main`
**Method:** each check executed here unless marked founder-only.

---

## 1. Bundle

| Item | Value |
|---|---|
| Command | `:app:bundleRelease` (R8 + `shrinkResources`, verified enabled in `app/build.gradle.kts`) |
| Result | BUILD SUCCESSFUL, signed with a throwaway local key (created, used, shredded — never in the repo) |
| AAB size | 17,044,129 bytes (**~16.25 MiB**) — target <25 MB: PASS (ephemeral-key measurement; rebuild with the operator key at submission). Delta vs pre-QR baseline (6,576,673 bytes, same method): +10,467,456 bytes from bundled ML Kit barcode scanning |
| versionCode / versionName | 1 / "1.0" |
| Toolchain | AGP 9.1.1, Gradle wrapper 9.9.1, Kotlin 2.3.21, KSP 2.3.14, compileSdk 36.1, targetSdk 36, minSdk 24 (founder-pinned stable set) |
| lintRelease | 0 errors, 34 warnings |

### Dependency delta (QR feature)

- Added: `com.google.mlkit:barcode-scanning:17.3.0` (**bundled**
  model — scanning works fully offline, no downloads) and
  `com.android.tools:desugar_jdk_libs:2.1.5` (core library
  desugaring, required for `java.time` on API 24+).
- Toolchain pins untouched: AGP 9.1.1, Kotlin 2.3.21, KSP 2.3.14,
  compileSdk 36.1, targetSdk 36, Gradle wrapper 9.9.1.
- Permissions unchanged: no manifest changes (verified: the QR
  commit touches no manifest; CAMERA is reused for the scanner).

## 2. Signing

- Release signing reads `KEYSTORE_PATH` / `STORE_PASSWORD` /
  `KEY_PASSWORD` from the environment, falling back to the local path
  `my-upload-key.jks` (never the debug keystore).
- Absent key proven to fail loudly: `validateSigningRelease FAILED >
  Keystore file not set for signing config release` (observed, no env,
  no file).
- `.gitignore` now covers `/my-upload-key.jks`, `*.jks`,
  `*.keystore`, `*.p12`, `*.keystore.properties` (plus existing
  `local.properties`, `.env`, `debug.keystore`). Verified: zero
  keystores tracked in git.

## 3. Permissions (merged release manifest)

| Permission | Source | Justification |
|---|---|---|
| `ACCESS_NETWORK_STATE` | app | Offline/online banner indicator |
| `CAMERA` | app | Optional evidence photos (runtime, first-use rationale) |
| `INTERNET` | app | PMS sync only, HTTPS-only |
| `POST_NOTIFICATIONS` | app | Local cleaning alerts (runtime on 33+, ask-once) |
| `WAKE_LOCK` | SDK (WorkManager) | Background sync/reminder workers |
| `RECEIVE_BOOT_COMPLETED` | SDK (WorkManager) | Worker reschedule after reboot |
| `FOREGROUND_SERVICE` | SDK (WorkManager) | Declared by the library; the app runs no foreground service |
| `…​.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | app signature-level (androidx-core internal) | Stock core receiver guard |

Nothing else. Components: MainActivity (launcher) + FileProvider
(non-exported) + stock CameraX/WorkManager/Room/ProfileInstaller
services/receivers. No FCM, no ads, no analytics receivers.

## 4. Secret sweep (source + AAB)

- Source: zero API keys in `app/src/main` (the only `rk.*` strings
  are test fakes in `app/src/test`). Zero property (PMS) URLs anywhere:
  `demo13` grep over shipped source returns nothing (the old URL-hint
  placeholder is deleted; the field ships empty). The only first-party
  URLs in shipped source are the three static About links in
  `ui/about/AboutLinks.kt` (vivereply.com/roomready/**), opened in the
  system browser via ACTION_VIEW — no WebView, no user data attached.
- AAB dex (P11 build): zero `demo13` hits; zero key-pattern hits
  (`rk.*` matches are WorkManager drawable filenames); URLs found
  are SDK/doc links only (schemas.android.com, developer.android.com,
  issue trackers).
- Native libs are stock AndroidX (CameraX, graphics, DataStore).

## 5. Data Safety draft

Before configuration: transmits nothing. Once connected: transmits
operational data to the property PMS origin the user typed, over
HTTPS only — room status actions, ticket titles/descriptions/status,
inspection return reasons, each tagged with the acting staff's
display name; receives the property snapshot. Photos are never
transmitted; the property API key lives only in Keystore-backed
encrypted storage.

- Collect/share: **Yes once connected** — Name (staff display names)
  + App activity (room/ticket operations).
- Encrypted in transit: **Yes** (HTTPS-only enforced).
- Deletion: in-app Disconnect (wipes key + clears snapshot and queued
  writes, with confirmation when work is queued) + uninstall clear
  the device; the server copy lives in the user's own PMS, managed
  by the property.
- Accounts: none — no deletion URL required.

> **D1 — founder decision:** an earlier brief said "no personal
> data", but staff display names (often real names) travel to the
> user's own PMS once connected, so this draft declares Name. If the
> founder's position is that shift labels are not personal data,
> adjust the filing — the engineering fact is recorded above.

## 6. Founder-only list

1. **Production key:** sign the upload AAB with the production upload
   key enrolled in Play App Signing. No keystore lives in this repo.
2. **Play listing:** file the Console forms (IARC, target audience,
   Data Safety per §5/D1, privacy URL after publishing
   `PRIVACY_POLICY.md`, trader/identity verification).
3. **Real-property rollout:** run dynamic checks D1–D5 in
   `VERIFICATION.md` against the live W2 PMS before inviting staff.

## Verdict

**READY** — all six engineering gates pass (bundle ~16.25 MiB, loud
signing failure, exact permission set, clean secret sweep,
1/"1.0", lintRelease 0 errors). Remaining work is founder-only (§6
+ decision D1). No engineering blockers.
