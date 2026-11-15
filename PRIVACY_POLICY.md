# Privacy Policy for RoomReady – Resort Housekeeping

**Last Updated:** November 15, 2026
**Effective Date:** November 15, 2026
**Publisher:** ViveScript Solutions LLC
**Website:** [https://www.vivescriptsolutions.com/en/apps/roomready](https://www.vivescriptsolutions.com/en/apps/roomready)
**Live version of this policy:** [https://www.vivescriptsolutions.com/en/apps/roomready/privacy](https://www.vivescriptsolutions.com/en/apps/roomready/privacy)
**Contact:** [privacy@vivescriptsolutions.com](mailto:privacy@vivescriptsolutions.com)

> Status note: the website pages above follow the ViveScript Solutions
> app-site convention. The founder must confirm the URLs and publish this
> policy there before release. This document is not a substitute for
> qualified legal advice.

---

## 1. Introduction

ViveScript Solutions LLC ("we," "our," or "us") provides **RoomReady –
Resort Housekeeping** ("RoomReady" or the "Application"), an offline-first
utility for hotel and resort housekeeping teams to track room readiness,
turnover checklists, and maintenance tickets.

RoomReady is designed to work **entirely on your device**. You can install
it and start a shift immediately without registering an account, providing
an email, logging into any service, or connecting to the internet.

---

## 2. Information We Do NOT Collect

We practice strict data minimization:

- **No Personal Identifiers transmitted to us:** We do not receive names,
  email addresses, phone numbers, postal addresses, or government
  identification. Staff names you enter stay on your device (see §3).
- **No Account System:** The Application has no sign-in or registration.
- **No Sensitive Permissions:** We request no Camera, Microphone,
  Location, Contacts, SMS, Phone State, Storage, Calendar, or Nearby
  Devices permission. The single declared permission is the normal-level
  `ACCESS_NETWORK_STATE`, used only to show the dashboard's live
  online/offline indicator — it reveals nothing about you and the app
  still makes zero network requests. The merged release manifest
  otherwise contains only the app's own signature-level internal
  permission (verified).
- **No Network Activity:** The app declares no `INTERNET` permission and
  contains no networking code, so it cannot transmit anything.
- **No Advertising, Analytics, or Crash SDKs:** No AdMob, Firebase,
  analytics, or crash-reporting SDK is integrated. There is nothing in
  the app that phones home.

---

## 3. Information Stored Locally on Your Device

Everything you enter while working — staff display name and role, room
statuses and assignments, checklist steps and step notes, ticket titles
and descriptions, and status-change logs — is stored **100% on your
device** in a local Room database and DataStore preferences. This data is
never transmitted to ViveScript Solutions LLC or any third-party server
by the Application.

Demo content shipped with the app (sample rooms, staff roster names, and
example tickets) is fictional seed data, also stored only on-device.

---

## 4. Android System Backup

Android's default Auto Backup may copy the Application's on-device data
to your own Google Drive account as part of your device backup, under
your Google account settings. This is an operating-system behavior, not
something the Application controls or can read back from our side — we
have no access to your backups. You can disable backup for the app via
Android Settings, and uninstalling the app removes its local data from
the device.

---

## 5. Data Retention and Deletion

- **In-app reset:** Me → Reset Demo Data restores the demo dataset;
  uninstalling the app deletes all locally stored data from the device.
- **No server-side data exists**, so there is nothing for us to delete on
  request. If you contact us about data, we will confirm this in writing.
- Because there are no accounts, Google Play's account-deletion
  declaration does not apply; the in-app reset plus uninstall covers
  complete removal of app data from the device.

---

## 6. Children's Privacy

RoomReady is a workplace utility intended for adult hospitality staff. It
is not directed at children, and we do not knowingly process children's
data. Since the app transmits nothing off-device, there is no collection
path in any case.

---

## 7. Security Measures

Local data is protected by Android's app sandbox: other apps cannot read
RoomReady's database or preferences without root access. The app requests
no permissions that could widen its attack surface, contains no WebView,
no deep links, and no exported components other than the launcher
activity. No security practice can guarantee absolute safety on a
compromised or rooted device.

---

## 8. Your Rights and Contact

Depending on your jurisdiction (including GDPR rights for EU users, such
as access, rectification, erasure, and portability), you may contact us
at [privacy@vivescriptsolutions.com](mailto:privacy@vivescriptsolutions.com).
Because we hold no personal data about you on any server, most requests
will be answered by confirming that and explaining the on-device
controls in §5.

---

## 9. Changes to This Policy

If a future version of RoomReady adds networking, accounts, analytics,
or any data transmission, we will update this policy, revise the
Effective Date, and update the Google Play Data Safety declaration
before that version ships.
