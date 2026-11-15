# Changelog — RoomReady

All notable changes to the RoomReady Android application.

## [Unreleased]

### P9 — Local notifications (stretch)

- Local-only alerts, no FCM or server push: inspection return
  ("Room X needs re-cleaning: \<reason\>"), urgent ticket created,
  and one reminder per clean stuck over 60 minutes (half-hourly
  WorkManager sweep, no constraints).
- `POST_NOTIFICATIONS` asked once after the first finished clean
  with an in-app rationale; denied or dismissed means silently off,
  never re-asked. Tapping opens the relevant room or ticket
  (singleTop + parsed deep-link extras).
- Two channels (Needs attention / Cleaning reminders); key/staff
  data never leaves the device for alerts. Legal, declaration, and
  data-safety docs updated for the new permission.

### P8 — Bengali + localisation + low-literacy UX

- Full Bengali (বাংলা) second language: every screen, status, step,
  priority, sync state, rationale, and empty state translated; demo
  room-type names and the 8 fixed PMS contract steps resolve to
  localized display strings (unknown server text still renders raw).
- Me → Language switch (Phone default / English / বাংলা) applies
  instantly via activity recreate; choice persists in DataStore and
  applies at startup. Play language splits disabled so values-bn
  always ships on-device.
- Bengali digits (০–৯) for counts, timers, and room numbers in
  Bengali UI via a digit-aware string helper (%d args also
  auto-localize through the wrapped configuration locale).
- Copy hardened for low literacy: stale "coming later" notes removed,
  dev jargon (Room/DataStore, "contract v", "zero network") replaced
  with plain words; demo card hidden in Live mode.
- TalkBack labels on all icon-buttons; decorative icons verified
  paired with words; status always icon + word; primary actions in
  the bottom two-thirds (proven by test for room detail).
- Tests: language-completeness gate (keys/blanks/placeholders/
  untranslated), locale + digit + PMS-step tests, 1.3× font-scale
  render tests, clickables-labeled traversal tests.

### P7 — PMS sync layer (pairs with server W2)

- Me → PMS Connection screen: Demo (bundled snapshot, zero network)
  or Live (PMS origin + property API key) with last-sync time, honest
  status line, Sync now, and Disconnect. HTTPS origins only.
- Live mode replaces local inventory with the PMS bootstrap snapshot
  (room types, units, checklist steps, staff roster, property identity)
  in one Room transaction; Demo data is reseeded on disconnect.
- Outbox + WorkManager worker (network-constrained, exponential
  backoff) pushes status actions and ticket writes in order, then pulls
  and merges; offline is silent and the banner shows pending count.
- Contract v1 enforced (mismatch = honest update-needed state, nothing
  touched); malformed payloads rejected whole; 401 wipes the key and
  returns to Connection; 409 surfaces the conflict, never forces.
- Out-of-service is PMS-driven and read-only in Live mode (Demo keeps
  local marking); staff picker uses the synced roster in Live mode.
- New normal-level `INTERNET` permission (Live sync only, HTTPS-only);
  API key in Keystore-backed encrypted storage, never logged.
  Legal/declaration/data-safety docs updated for Live-mode sync.
- Schema v8 + migration 7→8; fake-server (MockWebServer) suite covers
  push order, retry, 401 wipe, 409 conflict, merge matrix, bootstrap
  replace, malformed rejection, and outbox drain.

### P6 — Photo evidence (CameraX)

- Finishing a checklist requires an after photo (full-resolution
  CameraX capture with preview + retake) or a visible Skip escape with
  a mandatory one-line reason; tickets accept an optional photo.
- Photos in app-private `files/photos/`, referenced from runs/tickets,
  shown as thumbnails (room detail, inspection, ticket detail), shared
  only via FileProvider on explicit tap. Nothing leaves the device
  otherwise.
- CAMERA requested on first use with a plain-language rationale;
  denied/absent camera degrades to skip-with-reason (runs) or plain
  back navigation (tickets). Camera hardware `required="false"`.
- Schema v7 + migration 6→7; legal/declaration/data-safety docs
  updated for camera + photos.

### P5 — Supervisor inspection loop

- Inspection screen for CLEANED rooms: read-only completed checklist
  with per-step notes + elapsed clean time, big Pass (→ READY, logged)
  and Return actions.
- Return requires a reason chip (Bathroom/Beds/Dusting/Amenities/Other)
  + optional note; the run reopens with exactly the failed steps
  unchecked and the reason bannered atop the resumed checklist.
- Supervisor "To inspect" queue: dashboard card (count + first rooms +
  view-all into filtered board); existing Rooms CLEANED filter covers
  the board side.
- Returned rooms flagged in the housekeeper's room list; return reason
  also shown on status-log rows.
- Soft role gating: both roles can act everywhere (trust-based tool);
  supervisor-wait gate removed, acting name logged on every transition.
- Local transitions shaped as (room, from, to, actorName, reason?) with
  reason persisted on runs + logs, ready for P7 PMS mirroring
  (INSPECT_PASS / INSPECT_FAIL + note). Schema v6 + migration 5→6.

### P4 — Dashboard + offline-proof (unreleased)

- Dashboard: per-status tappable tiles (deep-link into filtered Rooms
  board), "% rooms Ready" headline, cleans-completed-today count with
  per-person list, open tickets by priority, and CLEANING > 60 min
  stuck-room highlights — all computed from Room queries, no caches.
- Signature offline-proof banner with live connectivity state via
  `NetworkCallback` (no polling); adds the normal-level
  `ACCESS_NETWORK_STATE` permission (indicator only, zero network
  traffic — legal/declaration docs updated accordingly).

## [1.0] — v1, unreleased (first Play release in preparation)

First public release candidate. Ships the complete P1–P3 feature set
plus the P1B brand kit on a modernized toolchain.

### Technical upgrades

- Android Gradle Plugin 9.1.1 → 9.5.1; Gradle wrapper 9.3.1 → 9.9.1.
- Kotlin 2.2.10 → 2.3.21 with KSP aligned 2.3.5 → 2.3.14 (major.minor
  now match, as KSP requires).
- Java source/target 11 → 17.
- Compose BOM 2024.09.00 → 2026.09.00; compileSdk 36.1 → 37 (required
  by current AndroidX AAR metadata); targetSdk stays 36, minSdk 24.
- AndroidX: activity-compose 1.13.0, lifecycle 2.11.0, core-ktx 1.19.1,
  navigation-compose 2.10.2, Room 2.8.5, DataStore 1.3.0,
  coroutines 1.11.0, Robolectric 4.18, androidx.test core/runner 1.7.0.
- Removed dead weight: `secrets` + `google-services` Gradle plugins (no
  google-services.json, no Firebase usage, no BuildConfig secrets),
  ~20 commented-out dependencies, unused catalog entries (camera,
  Retrofit/Moshi/OkHttp, Firebase, location, credentials, Coil,
  Accompanist), and the stale `googleServices.missing.passthrough` flag.
- Release: R8 minification + resource shrinking enabled
  (`isMinifyEnabled`, `isShrinkResources`); release AAB verified at
  ~4.3 MB with mapping output. Release signing stays operator-owned and
  fails fast without secrets.

### Features (shipped over P1–P3, included in v1)

- Rooms board with Dirty/Cleaning/Cleaned/Ready/Out-of-Service statuses,
  filters, search, and urgent-ticket badges.
- Turnover checklist runs (8-step full + 4-step stayover for occupied
  rooms) with progress, elapsed timer, per-step notes, pause/resume, and
  a no-override 8/8 finish gate into inspection.
- Maintenance tickets: create (room/title/description/priority/reporter),
  Open/Done filter, detail screen with who/when resolution, reopen, nav
  + card urgent badges, urgent banner atop active runs.
- Supervisor approval flow (Cleaned → Ready), status-activity log,
  shift profile with role/staff switching, demo resort seed + reset.

### Improvements

- Brand kit: original "Ready Door" icon (adaptive + monochrome themed +
  legacy mipmaps), Android 12+ splash, Play/store graphic set with a
  `brand/generate_icons.py` regen script, `BRANDING.md` usage rules.
- Full Bengali (`values-bn`) translation of all 155 strings; system
  locale switching.
- Tablet: 720dp max content width on detail, run, and ticket screens.
- Board loading indicator; `adjustResize` keyboard behavior.
- Configuration-safe snackbar strings (`stringResource`, satisfies
  `LocalContextGetResourceValueCall`).

### Bug fixes

- Fixed KSP/Kotlin version mismatch (2.3.5 plugin on Kotlin 2.2.10).
- Fixed 2 lint errors (`LocalContextGetResourceValueCall`) surfaced by
  the upgraded toolchain.

### Security fixes

- None required: zero permissions, no network, no secrets, no logging
  of sensitive data (verified by sweep; see ANDROID_AUDIT_REPORT.md).

### Localization changes

- Added `values-bn/` (Bengali): 155/155 keys, placeholder parity
  verified by script, runtime locale test added.

### Known issues

- No UI screenshots captured yet (no device/emulator in this
  environment); required before submission — see DESIGN_ASSETS.md.
- No `androidTest/` instrumentation suite; Robolectric composition
  tests cover screen rendering instead.
- Privacy/terms website pages and all Play Console forms await the
  founder (see PRODUCTION_READINESS.md required human decisions).
