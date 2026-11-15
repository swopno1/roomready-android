# Play Store Data Safety — RoomReady

**Date:** November 15, 2026
**App:** RoomReady – Resort Housekeeping (`com.vivescriptsolutions.roomready`), v1 (1.0)
**Method:** inspected `app/build.gradle.kts` + `libs.versions.toml`
(dependency inventory), `AndroidManifest.xml` + merged release manifest
(permission inventory), all Room entities / DataStore keys (stored-data
inventory), and searched sources for networking, advertising, analytics,
and crash-reporting integrations. Findings below, then the Console form
mapping.

---

## 1. SDK & Integration Inventory

| SDK / library | Data behavior |
|---|---|
| AndroidX (Compose, Material3, Navigation, Room, DataStore, Lifecycle, Activity, Core) | UI + local storage only; no collection |
| Kotlin coroutines | In-process concurrency; no collection |
| JUnit / Robolectric / Espresso (test only) | Not shipped in release |
| Advertising SDKs | **None** |
| Analytics / crash reporting / Firebase | **None** |
| Networking (OkHttp/Retrofit/WebView) | **None** — no `INTERNET` permission |

## 2. Data Stored On-Device (never transmitted by the app)

| Data | Store | Notes |
|---|---|---|
| Staff display name + role (+ custom roster names) | DataStore preferences | User-entered shift labels |
| Room statuses, assignments, checklist runs + step notes | Room database | Operational data |
| Ticket titles + descriptions, status log (staff names + timestamps) | Room database | Operational data |

All of the above stays in the app sandbox. The only off-device path is
Android's OS-level Auto Backup to the *user's own* Google Drive (default
rules, user-controlled), which is platform behavior, not app collection.

## 3. Collection vs Sharing Assessment

- **Collected (off-device by the app/developer):** None — the app cannot
  transmit (no permission, no code path) and no SDK transmits.
- **Shared with third parties:** None.
- **Ephemeral processing:** None.
- This matches PRIVACY_POLICY.md §§2–4 exactly.

## 4. Console Form Mapping (recommended answers)

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **No** |
| Is all collected data encrypted in transit? | N/A — no transmission (answer Yes/exempt path per Console; nothing leaves the device) |
| Do you provide a way for users to request data deletion? | In-app Reset Demo Data + uninstall removes all on-device data; no server data exists |
| Account types /Deletion URL | No accounts — no URL required |

## 5. Security Practices

- Local-only storage in the Android app sandbox; the sole permission is
  normal-level `ACCESS_NETWORK_STATE` (connectivity indicator only, no
  data access); no exported components beyond the launcher activity
  plus stock SDK receivers; no WebView, deep links, or background
  services.
- No independent security review or certification is claimed.

## 6. Unknowns Requiring Confirmation

None on the engineering side — the inventory above is exhaustive for the
shipped dependency set. **[Human]** The founder files the form and must
re-verify this mapping if any future version adds networking, accounts,
analytics, ads, or crash reporting.
