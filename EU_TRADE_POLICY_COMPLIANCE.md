# EU Trade Policy & Consumer Compliance Assessment — RoomReady

**Date:** November 15, 2026
**App:** RoomReady – Resort Housekeeping (`com.vivescriptsolutions.roomready`), v1 (1.0)
**Basis:** inspection of the actual implementation (offline-first Room +
DataStore app syncing with the user's own property PMS over HTTPS;
four permissions; no accounts, ads, analytics, payments, or push
service).

> This is an engineering assessment, not legal advice. Items marked
> **[Human]** require founder confirmation or qualified counsel before
> claiming compliance anywhere.

---

## 1. GDPR

**Applies to the developer in a minimal way; the property running the
PMS carries the controller duties for synced data.**

- The Application transmits nothing to the developer or any third
  party (verified: no analytics/crash/advertising SDKs, no push
  service, no ViveScript servers). There is therefore no
  developer-side processing of user data to register, and no data
  breach notification surface on our servers — because there are none.
- Once connected, the app transmits operational data (room statuses,
  tickets, acting staff display names) to the property PMS origin the
  user configured, over HTTPS only. Under GDPR roles, the
  *property/employer* operating that system is the controller of the
  synced copy; ViveScript Solutions LLC never receives it.
- Personal data entered by the user (staff display names, free-text
  notes/tickets) is stored on-device, plus the user's own Google
  system backup if they leave Android Auto Backup enabled.
- Data-subject rights are served through on-device controls (in-app
  Disconnect, uninstall) plus the contact in PRIVACY_POLICY.md §9.
  Rights over the property-PMS copy are exercised against the
  property, not against us.
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
- Descriptions must still be accurate: the listing must describe PMS
  sync and on-device alerts exactly as shipped — property-configured
  sync over HTTPS, alerts generated on-device, no push service, no
  ViveScript servers. The drafted listing in
  `roomready_PlayStore_Details.md` matches v1 behavior.

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
