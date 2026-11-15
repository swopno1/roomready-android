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
| OkHttp (+ `INTERNET` permission) | PMS sync transport; used **only** in opt-in Live mode, HTTPS origins only, to the property server address the user typed |
| AndroidX WorkManager | Local background scheduler for the sync + reminder workers; no collection itself |
| AndroidX security-crypto | Keystore-backed encrypted storage for the PMS API key; no collection |
| CameraX (+ `CAMERA` permission) | In-app evidence photos to app-private storage; never transmitted; no collection |
| JUnit / Robolectric / Espresso (test only) | Not shipped in release |
| Advertising SDKs | **None** |
| Analytics / crash reporting / Firebase | **None** |

## 2. Data Stored On-Device

| Data | Store | Notes |
|---|---|---|
| Staff display name + role (+ custom roster names) | DataStore preferences | User-entered shift labels |
| PMS origin URL + connection mode | DataStore preferences | Only when the user configures Live mode |
| PMS property API key | Keystore-backed EncryptedSharedPreferences | Never logged; wiped on 401 / disconnect |
| Evidence photos (room after-photos, ticket photos) | App-private files (`files/photos/`) | Captured in-app via CameraX; referenced from Room; user-initiated FileProvider share only; **never transmitted by sync** |
| Room statuses, assignments, checklist runs + step notes | Room database | Operational data; in Live mode a synced PMS snapshot |
| Ticket titles + descriptions, status log (staff names + timestamps) | Room database | Operational data |
| Queued outbox writes, synced roster, sync header | Room database (`outbox_entries`, `staff_members`, `sync_state`) | Live-mode only; cleared on disconnect / demo reset |

In Demo mode (the default) all of the above stays in the app sandbox.
The only off-device path is Android's OS-level Auto Backup to the
*user's own* Google Drive (default rules, user-controlled), which is
platform behavior, not app collection. In opt-in Live mode, §3 applies.

## 3. Collection vs Sharing Assessment

- **Demo mode (default):** no transmission at all — the sync engine is
  inert without a configured Live connection, and no SDK transmits.
- **Live mode (opt-in, user-configured):** the app transmits operational
  data to the property PMS origin the user typed, over HTTPS only:
  room status actions with the acting staff name, inspection return
  reasons, and ticket titles/descriptions/status changes with staff
  names. It receives the PMS snapshot (room types, units, checklist
  steps, staff roster, property identity). Nothing is sent to
  ViveScript Solutions LLC or any third party — the destination is the
  user's own property system. Photos are never transmitted; checklist
  step notes stay on-device.
- **Shared with third parties:** None (the PMS destination is the
  user's own server, not a third-party recipient).
- **Ephemeral processing:** None.
- This matches PRIVACY_POLICY.md §§2–4 exactly.

## 4. Console Form Mapping (recommended answers)

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **Yes in Live mode only** — staff display names (Name) and housekeeping activity (room statuses, tickets) are transmitted to the user-configured property PMS over HTTPS; Demo mode transmits nothing. Photos stay on-device (a user-tapped system share sheet is user-initiated sharing, not app collection) |
| Is all collected data encrypted in transit? | **Yes** — Live sync accepts `https://` origins only |
| Do you provide a way for users to request data deletion? | In-app Disconnect (wipes key + reseeds demo + clears sync tables), Reset Demo Data, and uninstall remove all on-device data; the server-side copy lives in the user's own PMS, managed by the property |
| Account types /Deletion URL | No accounts — no URL required |

## 5. Security Practices

- App-sandbox storage; permissions are normal-level
  `ACCESS_NETWORK_STATE` (connectivity indicator), `INTERNET` (Live
  sync only, HTTPS-only), `CAMERA` (runtime, evidence photos), and
  `POST_NOTIFICATIONS` (runtime on API 33+, local alerts only);
  API key in Keystore-backed encrypted storage, never logged; no
  exported components beyond the launcher activity plus stock SDK
  receivers; no WebView, deep links, foreground services, or exact
  alarms (sync runs on a network-constrained WorkManager worker).
- No independent security review or certification is claimed.

## 6. Unknowns Requiring Confirmation

None on the engineering side — the inventory above is exhaustive for the
shipped dependency set. **[Human]** The founder files the form and must
re-verify this mapping if any future version adds accounts, analytics,
ads, or crash reporting (Live-mode PMS sync is now covered here).
