# Android Audit Report — RoomReady

**Date:** November 15, 2026 (refreshed for the production shape)
**App:** RoomReady – Resort Housekeeping (`com.vivescriptsolutions.roomready`)
**Version audited:** versionCode 1, versionName "1.0" (first release)
**Method:** source inspection + build/test/lint evidence. Every claim
below names the command, file, or test that proves it.

---

## 1. Project & Stack

- Single-module Compose/Material3 app (`app/`), MVVM + repository over
  Room (v9 schema, migrations 1→9 all migration-tested) and DataStore
  preferences. Navigation Compose; no DI framework (hand
  `AppContainer`); WorkManager (sync + reminders); CameraX (photos);
  OkHttp (PMS transport).
- Toolchain (founder-pinned stable set): AGP 9.1.1, Gradle wrapper 9.9.1,
  Kotlin 2.3.21, KSP 2.3.14, Java 17, compileSdk 36.1, targetSdk 36,
  minSdk 24. Evidence: `gradle/libs.versions.toml`,
  `gradle-wrapper.properties`, `app/build.gradle.kts`.
- 125 unit tests (JUnit + Robolectric incl. Compose UI tests), 0
  `androidTest/` sources. 240 strings × 2 locales (en + bn).
- No demo mode, no seeded content, no sample data anywhere in shipped
  source (verified by grep; fixtures live in `app/src/test` only).

## 2. Permissions, Components, Attack Surface

- App manifest declares four permissions
  (`app/src/main/AndroidManifest.xml`): `ACCESS_NETWORK_STATE`
  (banner), `CAMERA` (evidence photos, runtime), `INTERNET` (PMS sync
  only), `POST_NOTIFICATIONS` (local alerts, runtime on 33+).
- Merged **release** manifest adds only SDK entries: `WAKE_LOCK`,
  `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE` (all WorkManager),
  plus the app's own signature-level
  `…​.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` (androidx-core).
- Exported components: `MainActivity` (launcher, singleTop for
  notification deep links) + stock SDK receivers/services. FileProvider
  non-exported, share-on-tap only. No intent-filter deep links, no
  WebView, no foreground services, no exact alarms, no push SDK.
- Backup: `allowBackup=true` with custom rules
  (`backup_rules.xml`/`data_extraction_rules.xml`); disclosed in
  PRIVACY_POLICY.md.

## 3. Data Flows & Secrets

- Before configuration: empty tables, honest empty screens, no
  transmission (`AirplaneModeTest` + `ProductionShapeTest`). Once
  connected: operational data + staff names to the user-configured
  HTTPS PMS origin only; photos never transmitted; API key
  Keystore-only, never logged.
- Secret sweep (source): zero keys in `app/src/main` (test fakes in
  `app/src/test` only); zero property URLs (`demo13`/`vivereply` grep
  returns nothing); zero keystores tracked (`.gitignore` covers
  `*.jks`/`*.keystore`/`*.p12`).
- Logging sweep (`Log\.|println(` in main): zero hits.
- No vulnerabilities scanner available in this environment (recorded
  limitation); mitigated by: pinned stable dependency set, Keystore
  key storage, defensive payload validation (unknown fields ignored,
  malformed payloads rejected whole).

## 4. Dependencies

- Inventory verified minimal: Compose BOM, Activity, Lifecycle, Core,
  Navigation, Room, DataStore, Coroutines, WorkManager,
  security-crypto, CameraX, OkHttp, Robolectric, androidx.test. Each
  implementation dep is imported by main sources (surveyed).
- Release: R8 minify + resource shrink enabled; signed AAB built
  (~16.25 MiB (exact bytes: RELEASE_GATE.md)) with a throwaway local key, then key
  shredded. Release
  without secrets fails fast with "Keystore file not set for signing
  config release" (operator key documented, never generated/committed).

## 5. UI/UX & Compatibility

- Forced light theme (single colorScheme, no `values-night`); Lora
  headings + Plus Jakarta Sans body; P1B brand kit wired (adaptive +
  monochrome icon, splash). Setup-first flow: unconfigured installs
  see the honest setup screen; onboarding follows the first sync.
- Touch targets ≥48dp on interactive controls (surveyed); status
  always icon + word; zero hardcoded UI strings; TalkBack labels on
  all icon-buttons (traversal-tested); primary actions in the bottom
  two-thirds (position-tested); 1.3× font-scale render tests green.
- 720dp tablet caps on content columns; edge-to-edge with M3 inset
  handling throughout.
- Not verifiable here (no device/emulator): physical small/large-screen
  rendering, landscape, OEM behavior, startup/memory/battery
  measurement. Robolectric composition tests are the substitute
  evidence.

## 6. Localization

- English (`values/`, 240 keys) + Bengali (`values-bn/`, 240 keys);
  `LanguageCompletenessTest` enforces key, blank, placeholder, and
  untranslated parity; BN runtime resource test green.
- In-app switch (Phone default / English / বাংলা) in Me, instant via
  recreate, persisted; Play language splits disabled so `values-bn`
  always ships. Bengali digits for counts/timers/room numbers;
  contract-step display mapping; Bengali is LTR, no RTL work needed.

## 7. Documentation State

- Current: root `roomready_PlayStore_Details.md` (EN+BN, counts
  script-verified), CHANGELOG.md, DESIGN_ASSETS.md,
  EU_TRADE_POLICY_COMPLIANCE.md, GOOGLE_PLAY_DECLARATIONS.md,
  PLAY_STORE_DATA_SAFETY.md, PLAY_STORE_METADATA.md, PRIVACY_POLICY.md,
  TERMS_OF_SERVICE.md, release-notes-en/bn.txt (353/333 chars),
  ANDROID_AUDIT_REPORT.md (this file), PRODUCTION_READINESS.md,
  VERIFICATION.md, RELEASE_GATE.md — all rewritten or verified for
  the production shape (zero demo/sample claims).
- Pre-existing: BRANDING.md + `brand/` kit (referenced, not duplicated).

## 8. Verification Evidence

| Check | Command / source | Result |
|---|---|---|
| Unit tests | `:app:testDebugUnitTest` | 145/145 pass across 22 suites, 0 fail |
| Lint debug+release | `:app:lintDebug :app:lintRelease` | 0 errors both |
| Debug build | `:app:assembleDebug` | success, APK produced |
| Release bundle | `:app:bundleRelease` (+throwaway key) | ~16.25 MiB AAB (exact bytes: RELEASE_GATE.md), R8 (rebuild with operator key at submission) |
| Release fail-fast | `:app:bundleRelease` (no secrets) | clean signing error |
| BN parity | `LanguageCompletenessTest` | 251/251, green |
| Listing counts | char-count script | all within limits |
| Merged release manifest | manifest dump | 4 app + 4 SDK/internal perms |
| No-demo grep | dataset/names/URL grep over `app/src/main` | zero hits |
| Instrumentation tests | — | none exist (limitation) |
| On-device testing | — | no device/emulator (limitation) |
| Screenshots | — | none captured (limitation) |

## 9. Residual Risks

1. No physical-device validation (rendering, OEM quirks, performance).
2. No dependency-vulnerability scanner run (mitigated, see §3).
3. Live PMS end-to-end unverified here — dynamic checks D1–D5 in
   VERIFICATION.md await the founder with the PMS admin.
4. Console-side items (rating, audience, trader, Data Safety filing,
   policy-page publishing, production signing, screenshots) are
   inherently founder-gated — enumerated in PRODUCTION_READINESS.md.
