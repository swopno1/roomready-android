# Changelog — RoomReady

All notable changes to the RoomReady Android application.

## [Unreleased] — P4 dashboard + offline-proof

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
