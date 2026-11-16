# Privacy Policy for RoomReady – Resort Housekeeping

**Last Updated:** November 15, 2026
**Effective Date:** November 15, 2026
**Publisher:** ViveScript Solutions LLC
**Website:** [https://www.vivereply.com/roomready](https://www.vivereply.com/roomready)
**Live version of this policy:** [https://www.vivereply.com/roomready/privacy](https://www.vivereply.com/roomready/privacy)
**Contact:** [privacy@vivescriptsolutions.com](mailto:privacy@vivescriptsolutions.com)

> Status note: the website pages above are the ViveReply canonical tree.
> The founder must confirm the URLs are live and publish this policy
> there before release. This document is not a substitute for
> qualified legal advice.

---

## 1. Introduction

ViveScript Solutions LLC ("we," "our," or "us") provides **RoomReady –
Resort Housekeeping** ("RoomReady" or the "Application"), an offline-first
utility for hotel and resort housekeeping teams to track room readiness,
turnover checklists, and maintenance tickets.

On first launch RoomReady asks you to connect your property's
management system (§3), then works fully offline from the synced
snapshot. You never register an account, provide an email, or log
into any service. Nothing is ever sent to ViveScript Solutions LLC.

---

## 2. Information We Do NOT Collect

We practice strict data minimization:

- **No Personal Identifiers transmitted to us:** We do not receive names,
  email addresses, phone numbers, postal addresses, or government
  identification. Staff names travel only with your actions to your
  own property system (see §3).
- **No Account System:** The Application has no sign-in or registration.
- **Minimal Permissions:** We request no Microphone, Location,
  Contacts, SMS, Phone State, Storage, Calendar, or Nearby Devices
  permission. Four permissions are declared: the normal-level
  `ACCESS_NETWORK_STATE` (dashboard connectivity indicator only),
  `CAMERA`, requested at first photo use with a plain-language
  explanation, solely for optional in-app evidence photos,
  `INTERNET`, used only for PMS sync (§3) with the server address
  you configure, over HTTPS only, and
  `POST_NOTIFICATIONS` (runtime on Android 13+, asked once after
  your first finished clean), used only for on-device cleaning
  alerts — no push service, no data leaves the phone for alerts.
  Photos stay in
  app-private storage and are shared only if you tap share (system
  share sheet via FileProvider); sync never transmits photos. The
  merged release manifest otherwise contains only the app's own
  signature-level internal permission (verified).
- **No Advertising, Analytics, or Crash SDKs:** No AdMob, Firebase,
  analytics, or crash-reporting SDK is integrated. There is nothing in
  the app that phones home to us.

---

## 3. Property PMS Sync

On first launch you enter your property system's server address plus
its property API key. The key is stored only in Android
Keystore-backed encrypted storage, is never logged, and is wiped if
the server rejects it or when you disconnect.

The Application exchanges operational data with that server address —
and only that address — over HTTPS (plain `http://` addresses are
refused):

- **Sent:** room status actions with the acting staff name, inspection
  return reasons, and ticket titles, descriptions, and status changes
  with staff names.
- **Received:** the property snapshot (room types, units, checklist
  steps, staff roster, property identity).

Nothing is ever sent to ViveScript Solutions LLC or any third party;
the destination is your own property's system. Photos are never
transmitted, and checklist step notes stay on-device. Disconnecting
wipes the key and clears the synced snapshot and any queued writes
(the app asks you to confirm first when unsynced work is queued),
returning to the setup screen.

---

## 4. Information Stored Locally on Your Device

Everything you enter while working — staff display name and role, room
statuses and assignments, checklist steps and step notes, ticket titles
and descriptions, and status-change logs — is stored **100% on your
device** in a local Room database and DataStore preferences. This data is
never transmitted to ViveScript Solutions LLC or any third-party server
by the Application (the §3 operational subset is additionally synced
with your own property system).

Evidence photos you take (room after-photos, ticket photos) are stored
in the app's private files directory, referenced from the local
database, and never transmitted by the app. They leave the device only
if you explicitly tap a photo's share button, which opens the system
share sheet. Skipping a run photo records only your one-line reason
(no photo is taken). Deleting data (§6) removes photo references;
photo files live and die with the app's private storage.

Cleaning alerts (returned rooms, urgent tickets, long-clean reminders)
are generated and shown entirely on-device from the data above. There
is no push service: turning alerts off in system Settings stops them
completely, with nothing transmitted at any point.

---

## 5. Android System Backup

Android's default Auto Backup may copy the Application's on-device data
to your own Google Drive account as part of your device backup, under
your Google account settings. This is an operating-system behavior, not
something the Application controls or can read back from our side — we
have no access to your backups. You can disable backup for the app via
Android Settings, and uninstalling the app removes its local data from
the device.

---

## 6. Data Retention and Deletion

- **In-app controls:** Me → PMS Connection → Disconnect wipes the API
  key and clears the synced snapshot and queued writes (with explicit
  confirmation when unsynced work is queued); uninstalling the app
  deletes all locally stored data from the device.
- **No ViveScript server-side data exists**, so there is nothing for us
  to delete on request. If you contact us about data, we will confirm
  this in writing. Data synced to your own property system under §3 is
  managed by the property, not by us.
- Because there are no accounts, Google Play's account-deletion
  declaration does not apply; the in-app controls plus uninstall cover
  complete removal of app data from the device.

---

## 7. Children's Privacy

RoomReady is a workplace utility intended for adult hospitality staff. It
is not directed at children, and we do not knowingly process children's
data. The app syncs only workplace operational data with the
property's own system (§3).

---

## 8. Security Measures

Local data is protected by Android's app sandbox: other apps cannot read
RoomReady's database or preferences without root access. The app
contains no WebView, no deep links, and no exported components other
than the launcher activity. The PMS API key lives only in
Keystore-backed encrypted storage and is sent exclusively to the
configured server over HTTPS. No security practice can guarantee
absolute safety on a compromised or rooted device.

---

## 9. Your Rights and Contact

Depending on your jurisdiction (including GDPR rights for EU users, such
as access, rectification, erasure, and portability), you may contact us
at [privacy@vivescriptsolutions.com](mailto:privacy@vivescriptsolutions.com).
Because we hold no personal data about you on any server, most requests
will be answered by confirming that and explaining the on-device
controls in §6.

---

## 10. Changes to This Policy

If a future version of RoomReady adds accounts, analytics, advertising,
or any new data transmission beyond the §3 property sync, we will
update this policy, revise the Effective Date, and update the Google
Play Data Safety declaration before that version ships.
