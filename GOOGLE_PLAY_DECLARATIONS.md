# Google Play Console Declarations — RoomReady

**Date:** November 15, 2026
**App:** RoomReady – Resort Housekeeping (`com.vivescriptsolutions.roomready`), v1 (1.0)

Each declaration records evidence from the verified implementation and a
recommended Console answer. Nothing here has been submitted — the founder
completes every form in Play Console. Items marked **[Human]** need a
human decision or Console-only action.

---

## 1. App Access

- **Status:** No restrictions.
- **Evidence:** No login, onboarding gate is a local role/name pick
  (`OnboardingScreen.kt`); full functionality available immediately.
- **Recommended answer:** "All functionality is available without special
  access." No demo credentials needed.
- **Human confirmation:** No.

## 2. Advertising

- **Status:** App contains **no ads**.
- **Evidence:** No `play-services-ads` dependency, no AdMob App ID
  (`app/build.gradle.kts`, merged release manifest). The `INTERNET`
  permission exists solely for opt-in Live-mode PMS sync to a
  user-configured HTTPS origin — no ad traffic.
- **Recommended answer:** "No, my app does not contain ads."
- **UMPSDK compatibility:** N/A — no ads (per standing rule, this
  subsection stops here).
- **Human confirmation:** No.

## 3. Content Rating (IARC)

- **Status:** Questionnaire not yet completed.
- **Evidence:** Utility app; no violence, sexual content, profanity,
  gambling, or user communication. Expected outcome: **Everyone**.
- **Recommended answer:** Complete the questionnaire; expected Everyone.
- **Human confirmation:** **[Human]** — founder completes the IARC form.

## 4. Target Audience & Children's Content

- **Status:** Not yet declared.
- **Evidence:** Workplace housekeeping tool designed for adult resort
  staff; not directed at children; no child-attractive content.
- **Recommended answer:** Target age 18+; "not primarily directed at
  children." No Designed-for-Families participation.
- **Human confirmation:** **[Human]** — founder sets target audience.

## 5. Permissions & Sensitive Permissions

- **Status:** Two normal + two runtime permissions; no Play
  permission declaration required for any of them.
- **Evidence:** App manifest declares `ACCESS_NETWORK_STATE` (normal,
  connectivity indicator only), `INTERNET` (normal, used only for
  opt-in Live-mode PMS sync to a user-configured `https://` origin;
  refused otherwise), `CAMERA` (dangerous, requested at first
  photo use with an in-app plain-language rationale; declining
  degrades to skip-with-reason, and camera hardware is
  `required="false"` so camera-less devices can install), and
  `POST_NOTIFICATIONS` (runtime on API 33+, asked once after the
  first finished clean with an in-app rationale; declining leaves
  alerts silently off). Alerts are local only — no FCM, no push.
  Photos stay in app-private storage and are never transmitted by
  sync; FileProvider sharing fires only on explicit user tap. Merged
  manifest confirms no location, microphone, storage, contacts,
  calendar, or nearby-devices entries — only the app's own
  signature-level `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`
  (androidx-core internal) alongside.
- **Recommended answer:** No sensitive-permission declarations required
  (`INTERNET` is normal-level; CAMERA and POST_NOTIFICATIONS need no
  special Play declaration; runtime prompts handled in-app).
- **Human confirmation:** No.

## 6. Background / Foreground Services

- **Status:** Background sync via WorkManager only; no services.
- **Evidence:** No `Service` subclasses, no `FOREGROUND_SERVICE*`
  permissions, no exact alarms, no push. Outbox sync runs on a
  network-constrained one-shot WorkManager worker with exponential
  backoff (`PmsSyncWorker`), enqueued only after Live-mode mutations.
  Stuck-clean reminders run on a half-hourly unconstrained periodic
  worker (`StuckReminderWorker`); both are local-only.
- **Recommended answer:** No declarations.
- **Human confirmation:** No.

## 7. Sensitive Categories (Health / Financial / Other)

- **Status:** Not applicable.
- **Evidence:** Housekeeping operations utility; no health, financial,
  VPN, or other sensitive-category features.
- **Human confirmation:** No.

## 8. Privacy Policy URL

- **Status:** URL drafted, page not yet published.
- **Evidence:** `PRIVACY_POLICY.md` written from verified behavior.
- **Recommended answer:**
  `https://www.vivescriptsolutions.com/en/apps/roomready/privacy`
- **Human confirmation:** **[Human]** — founder must publish the page,
  then paste the URL.

## 9. Account Deletion

- **Status:** Not applicable (no accounts).
- **Evidence:** No sign-in/registration anywhere (`UserSession` is a
  local shift label). Complete data removal = in-app Disconnect /
  Reset Demo Data + uninstall; no ViveScript server-side data exists
  (a Live-mode property PMS holds its own copy, managed by the
  property).
- **Recommended answer:** Declare no account creation; no deletion URL
  required.
- **Human confirmation:** No (verify Console still asks nothing once
  the no-accounts path is selected).

## 10. Data Safety

- **Status:** Drafted in `PLAY_STORE_DATA_SAFETY.md`, not yet filed.
- **Evidence:** Demo mode transmits nothing; Live mode transmits staff
  names + housekeeping activity to the user-configured property PMS
  over HTTPS; no SDKs that collect; OS-level backup disclosed.
- **Recommended answer:** Data collected/shared in Live mode only, per
  the drafted form mapping (names + app activity, encrypted in
  transit).
- **Human confirmation:** **[Human]** — founder files the form.

## 11. Developer & Trader Information

- **Status:** Pending founder Console steps.
- **Evidence:** N/A (organizational).
- **Recommended answer:** Verify ViveScript Solutions LLC identity +
  EU trader details; enroll in Play App Signing.
- **Human confirmation:** **[Human]** — founder only.

## 12. App Signing & Release Integrity

- **Status:** Pipeline ready, operator key required.
- **Evidence:** Release build fails fast without `KEYSTORE_PATH` /
  `STORE_PASSWORD` / `KEY_PASSWORD` ("Keystore file not set for signing
  config release"); R8 release AAB verified with a throwaway local key
  (6.9 MB, then key shredded). No keystore is committed
  or stored in-repo.
- **Recommended answer:** Founder signs the upload AAB with the
  production upload key enrolled in Play App Signing.
- **Human confirmation:** **[Human]** — founder only.
