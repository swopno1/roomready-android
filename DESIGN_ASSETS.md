# Design Assets — RoomReady

**Date:** November 15, 2026
**App:** RoomReady – Resort Housekeeping (`com.vivescriptsolutions.roomready`), v1 (1.0)

Inventory of brand and store artwork. Full usage rules live in
`BRANDING.md`. All rasters regenerate from vector sources via
`brand/generate_icons.py` (`pip install pillow cairosvg`) — no
hand-exported one-offs.

---

## 1. App Icon ("The Ready Door")

Original flat artwork: white door slab + mint `#00F1CE` check with an
ink `#0F172A` keyline on a solid ink field. Glyph inside the 72dp
adaptive safe zone; legibility proven at 48dp and 32dp
(`brand/proof/legibility-proof.png`, inspected at 1x and 4x).

| Asset | Spec |
|---|---|
| `brand/source/icon-foreground.svg` | 108×108 glyph source |
| `brand/source/icon-background.svg` | 108×108 solid ink source |
| `brand/source/icon-mono.svg` | 108×108 themed-icon silhouette source |
| `res/drawable/ic_launcher_foreground.xml` | VectorDrawable, mirrors SVG geometry |
| `res/drawable/ic_launcher_background.xml` | Solid ink vector |
| `res/drawable/ic_launcher_monochrome.xml` | Android 13 themed icon |
| `res/mipmap-anydpi-v26/ic_launcher[_round].xml` | Adaptive icon + monochrome |
| `res/mipmap-{mdpi…xxxhdpi}/ic_launcher[.png,_round.png]` | 48/72/96/144/192 legacy rasters |

Splash: `Theme.RoomReady.Splash` (ink background, centred glyph) via
`values-v31/themes.xml`; verified present in the release AAB resources.

## 2. Store & Repo Graphics (all in `brand/export/`)

| File | Dimensions | Content |
|---|---|---|
| `play-icon-512.png` | 512×512, solid, no alpha | Full icon artwork |
| `feature-graphic-1024x500.png` | 1024×500 | Icon + "RoomReady / Every room ready." + ViveReply chip |
| `social-preview-1280x640.png` | 1280×640 | Centred stack + chip |
| `readme-banner-1920x480.png` | 1920×480 | Wide banner, same kit |
| `end-card-1920x1080.png` | 1920×1080 | Icon, tagline, chip, vivereply.com |

Parent ViveReply lockups in `brand/vendor/` are used **unmodified**, as a
white chip on ink backgrounds. Marketing type uses the same Plus Jakarta
Sans TTF the app ships.

## 3. In-App Design System

- Forced light theme, calm hotel palette; Lora SemiBold headings, Plus
  Jakarta Sans body (`res/font/`).
- Housekeeping status colors are semantic and fixed: Dirty `#8C3B2B`,
  Cleaning `#A65E0A`, Cleaned `#24639E`, Ready `#276E40`, Out of Service
  `#635E58` — branding never recolours them.
- 48dp minimum touch targets on interactive controls; 720dp max content
  width caps for tablets; edge-to-edge with M3 inset handling.

## 4. Screenshots — Missing, Honestly Reported

No UI screenshots exist yet: capture was impossible without a device or
emulator in this environment, and this document will not fabricate them.
Required before submission (Play needs ≥2 phone screenshots):

1. Rooms board (status chips + urgent badge visible).
2. Checklist run screen (progress + timer + steps).
3. Recommended extras: ticket detail, room detail, Bengali locale board.

Capture on a mid-range phone at 1080×2400 (9:16), English + Bengali,
against the connected property. File them under `brand/export/screenshots/`
and record them here when done.
