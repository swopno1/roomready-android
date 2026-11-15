# Android Audit Report — RoomReady

**Date:** November 15, 2026
**App:** RoomReady – Resort Housekeeping (`com.vivescriptsolutions.roomready`)
**Version audited:** versionCode 1, versionName "1.0" (first release)
**Method:** source inspection + build/test/lint evidence. Every claim
below names the command, file, or test that proves it.

---

## 1. Project & Stack

- Single-module Compose/Material3 app (`app/`), MVVM + repository over
  Room (v5 schema, migrations 1→2→3→4→5 all migration-tested) and
  DataStore preferences. Navigation Compose; no DI framework (hand
  `AppContainer`).
- Toolchain after modernization: AGP 9.5.1, Gradle 9.9.1, Kotlin
  2.3.21, KSP 2.3.14 (aligned), Java 17, compileSdk 37, targetSdk 36,
  minSdk 24, Compose BOM 2026.09.00, Room 2.8.5, Navigation 2.10.2.
  Evidence: `gradle/libs.versions.toml`, `gradle-wrapper.properties`.
- 36 unit tests (JUnit + Robolectric incl. Compose UI tests), 0
  `androidTest/` sources. 155 strings × 2 locales (en + bn).
- Working tree was clean at audit start (`1da698d`); all changes in
  this pass are additive modernization + docs.

## 2. Permissions, Components, Attack Surface

- App manifest declares **zero** permissions
  (`app/src/main/AndroidManifest.xml`).
- Merged **release** manifest contains exactly one permission: the app's
  own signature-level
  `com.vivescriptsolutions.roomready.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`
  (androidx-core internal); `INTERNET: False` (release manifest dump).
- Exported components in release: `MainActivity` (launcher, required)
  and `ProfileInstallReceiver` (standard baseline-profile installer).
  No deep links, no WebView, no background/foreground services, no
  WorkManager/alarms/push (source + manifest grep).
- Backup: `allowBackup=true` with template-default rules → default
  Android Auto Backup to the user's own Google Drive; disclosed in
  PRIVACY_POLICY.md. No custom backup content.

## 3. Data Flows & Secrets

- All data (staff names/roles, rooms, checklist runs + notes, tickets,
  status logs) lives in app-sandbox Room + DataStore; no transmission
  path exists (no permission, no HTTP/WebSocket/client code — grep).
- Secret sweep (`AIza|api[_-]?key|password|secret|bearer|private[_-]?key`
  over `app/src`, `gradle.properties`): zero hits. No `BuildConfig`
  secrets usage; secrets + google-services plugins removed as dead.
- Logging sweep (`Log\.|println(` in main): zero hits.
- No vulnerabilities scanner available in this environment (recorded
  limitation); mitigated by: latest-stable dependency set, zero
  permissions, zero network, zero deserialization of remote input.

## 4. Dependencies

- Inventory verified minimal and current (all latest stable per
  Google Maven / Maven Central metadata, Nov 2026): Compose BOM,
  Activity, Lifecycle, Core, Navigation, Room, DataStore, Coroutines,
  Robolectric, androidx.test.
- Removed: Firebase/camera/Retrofit/Moshi/OkHttp/location/credentials/
  Coil/Accompanist catalog entries + commented deps (~20), secrets and
  google-services plugins, stale passthrough flag.
- Release: R8 minify + resource shrink enabled; signed AAB built
  (4.3 MB, mapping emitted) with a throwaway local key, then key
  deleted. Release without secrets fails fast with "Keystore file not
  set for signing config release" (operator key documented, never
  generated/committed).

## 5. UI/UX & Compatibility

- Forced light theme (documented choice); Lora headings + Plus Jakarta
  Sans body; P1B brand kit wired (adaptive + monochrome icon, splash).
- Touch targets ≥48dp on interactive controls (surveyed); decorative
  icons only carry null descriptions; zero hardcoded UI strings.
- Fixes in this pass: `adjustResize` IME behavior, board loading
  indicator, 720dp tablet caps on detail/run/ticket screens,
  configuration-safe snackbar strings (fixed 2 lint errors surfaced by
  the new toolchain).
- Not verifiable here (no device/emulator): physical small/large-screen
  rendering, landscape, OEM behavior, startup/memory/battery
  measurement. Robolectric composition tests (incl. BN locale) are the
  substitute evidence.

## 6. Localization

- English (`values/`, 155 keys) + Bengali (`values-bn/`, 155 keys);
  script-verified key + placeholder parity, zero MissingTranslation
  lint findings, BN runtime resource test green.
- Dates/times use device-locale formatting; Bengali is LTR; no
  in-app language switcher (system locale; recorded recommendation).

## 7. Documentation State

- Created this pass: root `roomready_PlayStore_Details.md` (EN+BN,
  counts script-verified), CHANGELOG.md, DESIGN_ASSETS.md,
  EU_TRADE_POLICY_COMPLIANCE.md, GOOGLE_PLAY_DECLARATIONS.md,
  PLAY_STORE_DATA_SAFETY.md, PLAY_STORE_METADATA.md, PRIVACY_POLICY.md,
  TERMS_OF_SERVICE.md, release-notes-en/bn.txt (251/234 chars),
  ANDROID_AUDIT_REPORT.md (this file), PRODUCTION_READINESS.md.
- Pre-existing: BRANDING.md + `brand/` kit (referenced, not duplicated).

## 8. Verification Evidence

| Check | Command / source | Result |
|---|---|---|
| Unit tests | `:app:testDebugUnitTest` | 36/36 pass, 0 fail |
| Lint debug+release | `:app:lintDebug :app:lintRelease` | 0 errors both |
| Debug build | `:app:assembleDebug` | success, APK produced |
| Release bundle | `:app:bundleRelease` (+throwaway key) | 4.3 MB AAB, R8 mapping |
| Release fail-fast | `:app:bundleRelease` (no secrets) | clean signing error |
| BN parity | key/placeholder script + locale test | 155/155, green |
| Listing counts | char-count script | all within limits |
| Merged release manifest | manifest dump | 1 internal perm, 0 dangerous |
| Instrumentation tests | — | none exist (limitation) |
| On-device testing | — | no device/emulator (limitation) |
| Screenshots | — | none captured (limitation) |

## 9. Residual Risks

1. No physical-device validation (rendering, OEM quirks, performance).
2. No dependency-vulnerability scanner run (mitigated, see §3).
3. Console-side items (rating, audience, trader, Data Safety filing,
   policy-page publishing, production signing, screenshots) are
   inherently founder-gated — enumerated in PRODUCTION_READINESS.md.
