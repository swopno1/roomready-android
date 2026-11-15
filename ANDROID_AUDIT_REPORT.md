# Android Audit Report — RoomReady

**Date:** November 15, 2026 (pass 2; supersedes the pre-P4 edition)
**App:** RoomReady – Resort Housekeeping (`com.vivescriptsolutions.roomready`)
**Version audited:** versionCode 1, versionName "1.0" (first release)
**Method:** source inspection + build/test/lint evidence. Every claim
below names the command, file, or test that proves it.

---

## 1. Project & Stack

- Single-module Compose/Material3 app (`app/`), MVVM + repository over
  Room (v8 schema, migrations 1→8 all migration-tested) and DataStore
  preferences. Navigation Compose; no DI framework (hand
  `AppContainer`); WorkManager (sync + reminders); CameraX (photos);
  OkHttp (PMS transport).
- Toolchain re-verified at latest compatible stable (Maven metadata,
  Nov 2026): AGP 9.5.1, Gradle 9.9.1, Kotlin 2.3.21, KSP 2.3.14, Java
  17, compileSdk 37, targetSdk 36, minSdk 24. Kotlin 2.4.21 exists but
  is blocked — no KSP 2.4 published, and KSP must match Kotlin
  major.minor. Evidence: `gradle/libs.versions.toml`,
  `gradle-wrapper.properties`.
- 119 unit tests (JUnit + Robolectric incl. Compose UI tests), 0
  `androidTest/` sources. 253 strings × 2 locales (en + bn).
- Branch `release-readiness`, clean tree; P2–P11 shipped on `main`,
  gate docs on this branch.

## 2. Permissions, Components, Attack Surface

- App manifest declares four permissions
  (`app/src/main/AndroidManifest.xml`): `ACCESS_NETWORK_STATE`
  (banner), `CAMERA` (evidence photos, runtime), `INTERNET` (Live
  sync only), `POST_NOTIFICATIONS` (local alerts, runtime on 33+).
- Merged **release** manifest adds only SDK entries: `WAKE_LOCK`,
  `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE` (all WorkManager),
  plus the app's own signature-level
  `…​.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` (androidx-core).
- Exported components: `MainActivity` (launcher, singleTop for
  notification deep links) + stock SDK receivers/services (WorkManager,
  ProfileInstaller). FileProvider non-exported, share-on-tap only. No
  intent-filter deep links, no WebView, no foreground services, no
  exact alarms, no push SDK.
- Backup: `allowBackup=true` with custom rules
  (`backup_rules.xml`/`data_extraction_rules.xml`); disclosed in
  PRIVACY_POLICY.md.

## 3. Data Flows & Secrets

- Demo mode: everything in app-sandbox Room + DataStore + private
  files; transport proven silent (`AirplaneModeTest`: `NotConfigured`
  on all six endpoints, no socket). Live mode: operational data +
  staff names to the user-configured HTTPS PMS origin only; photos
  never transmitted; API key Keystore-only, never logged.
- Secret sweep (P11, still valid — no main-source changes since except
  two string values): zero keys in source; demo13 URL only as the
  user-editable field's placeholder; AAB dex clean; zero keystores
  tracked (`.gitignore` covers `*.jks`/`*.keystore`/`*.p12`).
- Logging sweep (`Log\.|println(` in main): zero hits.

## 4. Dependencies

- Inventory verified minimal and current: Compose BOM, Activity,
  Lifecycle, Core, Navigation, Room, DataStore, Coroutines, WorkManager,
  security-crypto, CameraX, OkHttp, Robolectric, androidx.test. Each
  implementation dep is imported by main sources (surveyed).
- Release: R8 minify + resource shrink enabled; signed AAB built
  (6.9 MB) with a throwaway local key, then key shredded. Release
  without secrets fails fast with "Keystore file not set for signing
  config release" (operator key documented, never generated/committed).

## 5. UI/UX & Compatibility

- Forced light theme (single colorScheme, no `values-night`); Lora
  headings + Plus Jakarta Sans body; P1B brand kit wired (adaptive +
  monochrome icon, splash).
- Touch targets ≥48dp on interactive controls (surveyed, every
  screen); status always icon + word; zero hardcoded UI strings;
  TalkBack labels on all icon-buttons (traversal-tested on
  board/detail/run); primary actions in the bottom two-thirds
  (position-tested); 1.3× font-scale render tests green.
- 720dp tablet caps on content columns (8 sites); edge-to-edge with M3
  inset handling throughout.
- Not verifiable here (no device/emulator): physical small/large-screen
  rendering, landscape, OEM behavior, startup/memory/battery
  measurement. Robolectric composition tests are the substitute
  evidence.

## 6. Localization

- English (`values/`, 253 keys) + Bengali (`values-bn/`, 253 keys);
  `LanguageCompletenessTest` enforces key, blank, placeholder, and
  untranslated parity; BN runtime resource test green.
- In-app switch (Phone default / English / বাংলা) in Me, instant via
  recreate, persisted; Play language splits disabled so `values-bn`
  always ships. Bengali digits for counts/timers/room numbers;
  contract-step and demo-type display mapping; Bengali is LTR, no RTL
  work needed. Two imperative-mood fixes applied this pass
  (`step_pms_floors`, `step_pms_bathroom`).

## 7. Documentation State

- Refreshed this pass: root `roomready_PlayStore_Details.md` (listing
  rewritten for shipped v1, counts script-verified), CHANGELOG.md,
  PRIVACY_POLICY.md (alerts paragraph), TERMS_OF_SERVICE.md (§§2/7
  rewritten, email split), EU_TRADE_POLICY_COMPLIANCE.md (Live-mode
  roles), GOOGLE_PLAY_DECLARATIONS.md (reminder worker, 6.9 MB AAB),
  PLAY_STORE_DATA_SAFETY.md (CameraX row), PLAY_STORE_METADATA.md
  (253 strings, support@), release-notes-en/bn.txt (371/342 chars),
  ANDROID_AUDIT_REPORT.md (this file), PRODUCTION_READINESS.md.
- Unchanged and verified current: BRANDING.md + `brand/` kit (exports
  re-measured: 512, 1024×500, 1280×640, 1920×480, 1920×1080),
  DESIGN_ASSETS.md, VERIFICATION.md, RELEASE_GATE.md.

## 8. Verification Evidence

| Check | Command / source | Result |
|---|---|---|
| Unit tests | `:app:testDebugUnitTest` | 119/119 pass, 0 fail |
| Lint debug+release | `:app:lintDebug :app:lintRelease` | 0 errors both (29 pre-existing warnings) |
| Debug build | `:app:assembleDebug` | success, APK produced |
| Release bundle | `:app:bundleRelease` (+throwaway key, P11) | 6.9 MB AAB, R8; evidence stands (no main changes since) |
| Release fail-fast | `:app:bundleRelease` (no secrets, P11) | clean signing error |
| BN parity | `LanguageCompletenessTest` | 253/253, green |
| Listing counts | char-count script | all 8 blocks within limits |
| Merged release manifest | manifest dump (P11) | 4 app + 4 SDK/internal perms |
| Instrumentation tests | — | none exist (limitation) |
| On-device testing | — | no device/emulator (limitation) |
| Screenshots | — | none captured (limitation) |

## 9. Residual Risks

1. No physical-device validation (rendering, OEM quirks, performance).
2. No dependency-vulnerability scanner run (toolchain is latest
   stable; mitigates but does not replace a scan).
3. Live PMS (W2) unverified end to end — dynamic checks D1–D5 in
   VERIFICATION.md await the founder with the PMS admin.
4. Console-side items (rating, audience, trader, Data Safety filing,
   policy-page publishing, production signing, screenshots) are
   inherently founder-gated — enumerated in PRODUCTION_READINESS.md.
