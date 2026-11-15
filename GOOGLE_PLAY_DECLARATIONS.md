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
- **Evidence:** No `play-services-ads` dependency, no AdMob App ID, no
  `INTERNET` permission (`app/build.gradle.kts`, merged release manifest).
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

- **Status:** Nothing to declare.
- **Evidence:** App manifest declares zero `uses-permission`. Merged
  release manifest contains only the app's own signature-level
  `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` (androidx-core internal)
  and no `INTERNET`, location, camera, microphone, storage, contacts,
  calendar, or nearby-devices entries (verified via release manifest
  dump).
- **Recommended answer:** No sensitive-permission declarations required.
- **Human confirmation:** No.

## 6. Background / Foreground Services

- **Status:** None.
- **Evidence:** No `Service` subclasses, no `FOREGROUND_SERVICE*`
  permissions, no WorkManager, no alarms, no push. All work is
  foreground UI + synchronous Room/DataStore I/O.
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
  local shift label). Complete data removal = in-app Reset Demo Data +
  uninstall; no server-side data exists.
- **Recommended answer:** Declare no account creation; no deletion URL
  required.
- **Human confirmation:** No (verify Console still asks nothing once
  the no-accounts path is selected).

## 10. Data Safety

- **Status:** Drafted in `PLAY_STORE_DATA_SAFETY.md`, not yet filed.
- **Evidence:** Zero off-device collection or sharing by the app; no
  SDKs that collect; OS-level backup disclosed.
- **Recommended answer:** No data collected; no data shared (per the
  drafted form mapping).
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
  (4.3 MB, mapping emitted, then key deleted). No keystore is committed
  or stored in-repo.
- **Recommended answer:** Founder signs the upload AAB with the
  production upload key enrolled in Play App Signing.
- **Human confirmation:** **[Human]** — founder only.
