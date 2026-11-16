# Play Store Metadata — RoomReady

**Date:** November 15, 2026
**App:** RoomReady – Resort Housekeeping (`com.vivescriptsolutions.roomready`), v1 (1.0)

Operational tracking for the listing. The actual listing text lives in
the root primary source `roomready_PlayStore_Details.md` — this file
tracks configuration, status, and what remains human-gated. Nothing here
has been submitted to Play Console.

---

## 1. Listing Configuration

| Item | Value / status |
|---|---|
| Package ID | `com.vivescriptsolutions.roomready` |
| Version | versionCode 1, versionName "1.0" (first upload; no prior version) |
| Category | Business (recommended; founder confirms in Console) |
| App type | Free, no in-app purchases, no ads, no subscriptions |
| Content rating | Expected Everyone — **[Human]** complete IARC questionnaire |
| Target audience | Recommended 18+, staff use, not directed at children — **[Human]** confirm |
| Store presence | Unpublished; first release pending |

## 2. Localization Status

| Locale | Listing text | App UI | Status |
|---|---|---|---|
| en-US (default) | Final, counts verified | `values/` (240 strings) | Ready |
| bn-BD | Final, counts verified | `values-bn/` (240 strings, parity-tested) | Ready |

No other listing locales planned for v1. Listing translations describe
the same shipped functionality; product names (RoomReady, ViveReply)
preserved in both.

## 3. Contact & Policy URLs (all require founder publish)

| Item | Value |
|---|---|
| Website | https://www.vivereply.com/roomready |
| Privacy policy | https://www.vivereply.com/roomready/privacy |
| Terms | https://www.vivereply.com/roomready/terms |
| Support email | support@vivescriptsolutions.com |
| Privacy contact | privacy@vivescriptsolutions.com |
| Developer | ViveScript Solutions LLC |

## 4. Graphics Status

| Asset | Spec | Status |
|---|---|---|
| App icon | 512×512 PNG | Ready (`brand/export/play-icon-512.png`) |
| Feature graphic | 1024×500 | Ready (`brand/export/feature-graphic-1024x500.png`) |
| Phone screenshots | ≥2 required, 16:9 or 9:16 | **Missing** — needs on-device captures (see DESIGN_ASSETS.md) |
| Tablet / TV / Wear | Optional | Out of scope for v1 |

## 5. Release Notes Status

`release-notes-en.txt` + `release-notes-bn.txt` drafted (see CHANGELOG
phase). Both under the 500-char Play limit; verified by script at
authoring time.

## 6. Pre-Submission Checklist

- [ ] Privacy + terms pages published and URLs verified live
- [ ] IARC questionnaire completed
- [ ] Target audience + Data Safety forms filed
- [ ] Trader + developer verification completed
- [ ] Screenshots captured on-device and uploaded
- [ ] Release AAB signed with the production upload key and uploaded to a closed track first
