# Release Gate — RoomReady v1 (1.0)

**Date:** November 15, 2026
**Branch:** `release-readiness` (unpushed, unmerged — this gate only)
**Method:** each check executed here unless marked founder-only.

---

## 1. Bundle

| Item | Value |
|---|---|
| Command | `:app:bundleRelease` (R8 + `shrinkResources`, verified enabled in `app/build.gradle.kts`) |
| Result | BUILD SUCCESSFUL, signed with a throwaway local key (created, used, shredded — never in the repo) |
| AAB size | 7,234,235 bytes (**6.9 MB**) — target <25 MB: PASS |
| versionCode / versionName | 1 / "1.0" |
| Toolchain | AGP 9.5.1, Gradle 9.9.1, Kotlin 2.3.21, KSP 2.3.14, compileSdk 37, targetSdk 36, minSdk 24 |
| lintRelease | 0 errors, 29 pre-existing warnings |

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
| `INTERNET` | app | Opt-in Live PMS sync only, HTTPS-only |
| `POST_NOTIFICATIONS` | app | Local cleaning alerts (runtime on 33+, ask-once) |
| `WAKE_LOCK` | SDK (WorkManager) | Background sync/reminder workers |
| `RECEIVE_BOOT_COMPLETED` | SDK (WorkManager) | Worker reschedule after reboot |
| `FOREGROUND_SERVICE` | SDK (WorkManager) | Declared by the library; the app runs no foreground service |
| `…​.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | app signature-level (androidx-core internal) | Stock core receiver guard |

Nothing else. Components: MainActivity (launcher) + FileProvider
(non-exported) + stock CameraX/WorkManager/Room/ProfileInstaller
services/receivers. No FCM, no ads, no analytics receivers.

## 4. Secret sweep (source + AAB)

- Source: zero API keys in `app/src/main` (the only `rk.*` string is
  the `rk.key1.secret1` fake in `SyncEngineTest`). Zero hardcoded PMS
  URLs in code.
- `https://demo13.vivereply.com` appears exactly twice in source
  (en + bn `connection_url_hint`) and twice in the compiled AAB
  resources — as the placeholder text of the user-editable URL field
  only. It is never read as a value (`PmsApi` uses the stored
  preference, empty by default). Matches the allowed demo preset.
- AAB dex: zero `demo13` hits; zero key-pattern hits (`rk.*` matches
  are WorkManager drawable filenames); URLs found are SDK/doc links
  only (schemas.android.com, developer.android.com, issue trackers).
- Native libs are stock AndroidX (CameraX, graphics, DataStore).

## 5. Data Safety draft

Demo mode (default): transmits nothing. Live mode (opt-in): transmits
operational data to the property PMS origin the user typed, over
HTTPS only — room status actions, ticket titles/descriptions/status,
inspection return reasons, each tagged with the acting staff's
display name; receives the property snapshot. Photos are never
transmitted; the property API key lives only in Keystore-backed
encrypted storage.

- Collect/share: **Yes in Live mode only** — Name (staff display
  names) + App activity (room/ticket operations). Demo mode: none.
- Encrypted in transit: **Yes** (HTTPS-only enforced).
- Deletion: in-app Disconnect (wipes key, clears sync tables, reseeds
  demo) + Reset Demo Data + uninstall clear the device; the server
  copy lives in the user's own PMS, managed by the property.
- Accounts: none — no deletion URL required.

> **D1 — founder decision:** this prompt's brief says "no personal
> data", but staff display names (often real names) travel to the
> user's own PMS in Live mode, so this draft declares Name. If the
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

**READY** — all six engineering gates pass (bundle 6.9 MB, loud
signing failure, exact permission set, clean secret sweep,
1/"1.0", lintRelease 0 errors). Remaining work is founder-only (§6
+ decision D1). No engineering blockers.
