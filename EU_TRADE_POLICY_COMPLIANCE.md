# EU Trade Policy & Consumer Compliance Assessment — RoomReady

**Date:** November 15, 2026
**App:** RoomReady – Resort Housekeeping (`com.vivescriptsolutions.roomready`), v1 (1.0)
**Basis:** inspection of the actual implementation (offline Room + DataStore
app; zero permissions; no network, accounts, ads, analytics, or payments).

> This is an engineering assessment, not legal advice. Items marked
> **[Human]** require founder confirmation or qualified counsel before
> claiming compliance anywhere.

---

## 1. GDPR

**Applies to the developer in a minimal way; barely touches the app.**

- The Application transmits no personal data to the developer or any
  third party (verified: no `INTERNET` permission, no networking code,
  no analytics/crash/advertising SDKs). There is therefore no
  developer-side processing of user data to register, and no data
  breach notification surface on our servers — because there are none.
- Personal data entered by the user (staff display names, free-text
  notes/tickets) is stored only on-device, plus the user's own Google
  system backup if they leave Android Auto Backup enabled. Under GDPR
  roles, the *user/employer* operating the device is the controller of
  that on-device data; ViveScript Solutions LLC never receives it.
- Data-subject rights are served through on-device controls (in-app
  reset, uninstall) plus the contact in PRIVACY_POLICY.md §8.
- **[Human]** Confirm with counsel that no Data Processing Agreement or
  records-of-processing entry is required given zero developer-side
  processing. Do not publish a "GDPR certified/compliant" badge — no
  such certification is claimed here.

## 2. ePrivacy

**Not applicable in any material sense.** The app sets no cookies, uses
no trackers, sends no electronic communications, and accesses no
device-stored information beyond its own sandbox. No consent banner is
required inside the app.

## 3. EU Consumer Protection (including Omnibus / Digital Content rules)

- The app is a free utility with no purchases, subscriptions, ads, or
  paid tiers, so price-transparency, withdrawal, and digital-content
  conformity rules for paid content do not attach.
- Descriptions must still be accurate: the listing must not promise
  cloud sync, multi-device sharing, PMS integration, or notifications
  (none exist in v1). The drafted listing in
  `roomready_PlayStore_Details.md` describes offline-only behavior.

## 4. Digital Services Act (DSA)

RoomReady is not a hosting service, marketplace, or online platform: it
stores no user content on any server and moderates nothing. No DSA
transparency, notice-and-action, or recommender obligations apply to the
app. Google Play's own DSA trader requirements are a Console matter
(see §5).

## 5. EU Trader Transparency (Play Console)

- ViveScript Solutions LLC distributes as a **trader**. **[Human]** The
  founder must complete Google Play Console trader verification
  (legal name, address, D-U-N-S / contact details) before the listing
  can go live in the EU/EEA. This is a Console step, not a code step.
- The in-app and listing publisher name must match the verified trader
  name exactly.

## 6. Developer Verification & App Signing

- Play Console developer identity verification and Play App Signing
  enrollment are **[Human]** Console steps. The release pipeline here is
  configured fail-fast: release signing requires the operator's own
  keystore (`KEYSTORE_PATH`/`STORE_PASSWORD`/`KEY_PASSWORD`); no
  keystore is generated, stored, or committed by engineering.

## 7. Summary of Human-Gated Items

| # | Item | Owner |
|---|------|-------|
| 1 | Confirm GDPR posture with counsel (no developer-side processing) | Founder/counsel |
| 2 | Complete Play Console trader verification for ViveScript Solutions LLC | Founder |
| 3 | Complete developer identity verification + Play App Signing | Founder |
| 4 | Publish privacy policy + terms at the canonical URLs | Founder |
| 5 | Complete IARC rating, target-audience (adult staff), and Data Safety forms | Founder |
