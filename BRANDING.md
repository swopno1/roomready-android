# RoomReady Brand Kit (P1B)

RoomReady is a ViveReply product. Its identity derives from the official
ViveReply brand: deep ink + vivid mint, flat geometric shapes, generous
spacing. The RoomReady app icon is **fresh, original artwork created for
this pass** — no Yard/PWA art files were copied or traced.

## 1. Palette

Measured from the official parent assets (`brand/vendor/`, sampled by
pixel, not eyeballed):

| Swatch | Hex | Source / role |
|---|---|---|
| ViveReply Ink | `#0F172A` | Exact darkest-common colour of the lockup wordmark. Icon field, splash, graphic backgrounds. |
| RoomReady Mint | `#00F1CE` | Midpoint of the mark's mint gradient family (`#00FF94` → `#00EEDA`; sampled mean `#00F4C7`). The icon's single accent. |
| White | `#FFFFFF` | Glyph + type on ink. |

Rules:

- The app icon uses **at most ink + one mint + white**, flat. No gradients
  inside the glyph, ever.
- The parent mark also carries a magenta/pink stroke. That colour is **not**
  in the RoomReady palette — RoomReady speaks ink/mint/white only, so it
  reads as the housekeeping member next to Suite and Concierge without
  clashing with them.
- Decorative flat shapes (ring, dot) on marketing graphics use mint on ink.
  No gradients anywhere in the kit.

## 2. The icon — "The Ready Door"

A white door slab with a mint check resolving across it, keylined in ink,
on a solid ink field. "Room, ready" in one glance.

- Sources: `brand/source/icon-foreground.svg` (glyph),
  `brand/source/icon-background.svg` (ink field),
  `brand/source/icon-mono.svg` (Android 13 themed silhouette).
- The glyph lives inside the 72dp adaptive safe zone (foreground art spans
  roughly x 29–88, y 26–82 of the 108dp viewport).
- Legibility proof at 48dp and 32dp: `brand/proof/legibility-proof.png`
  (1x + 4x enlargements, mono on grey, circle mask). Door and check stay
  separable at both sizes thanks to the ink keyline.

## 3. Typography direction

- **In-app headings:** Lora SemiBold (already wired in
  `ui/theme/Type.kt`, `res/font/lora.ttf`). Keep — it gives the calm,
  hospitality feel.
- **In-app body/UI:** Plus Jakarta Sans (`res/font/plus_jakarta_sans.ttf`).
- **Brand lockups (store + repo graphics):** Plus Jakarta Sans, Bold weight
  for "RoomReady", Regular for the tagline. The regen script renders text
  with the same TTF the app ships, so marketing and product type match.
- Never set the RoomReady wordmark in Lora, and never letterspace,
  condense, or recolour the ViveReply parent lockup.

## 4. Logo / icon usage rules

- **"By ViveReply" lockups** use the parent files in `brand/vendor/`
  **unmodified** — no recolouring, no redrawing, no cropping into the art.
  On ink backgrounds the opaque-white lockup is presented as a white chip
  (padding + rounded corners only; pixels untouched).
- **Clear space:** at least the height of the icon's check on all sides.
- **Minimum sizes:** app icon 48dp in product UI; ViveReply chip 120px wide
  in graphics; never render the door glyph below 32dp except OS-masked
  launcher contexts.
- **Never:** stretch or rotate the icon; add shadows, gradients, or
  outlines; place the glyph on unapproved backgrounds (anything other than
  ViveReply Ink, white, or the official mint); recolour the parent logo;
  use the Yard/PWA-era artwork.

## 5. Status-colour guardrail (hard rule)

Housekeeping status colours are **semantic** and branding never recolours
them. Current values (`ui/theme/Color.kt`, untouched by this pass):

- Dirty `#8C3B2B` · Cleaning `#A65E0A` · Cleaned `#24639E` ·
  Ready `#276E40` · Out of Service `#635E58`

Mint `#00F1CE` must never be used as a status fill — Ready already owns
green. The icon/splash use mint only as brand accent, never adjacent to
status chips in a way that implies meaning.

## 6. Asset inventory

All rasters regenerate from SVG via `brand/generate_icons.py`
(`pip install pillow cairosvg`). Delete any PNG and re-run to reproduce it.

| File | Size | Notes |
|---|---|---|
| `brand/export/play-icon-512.png` | 512×512 | Solid (no alpha), Play high-res icon |
| `brand/export/feature-graphic-1024x500.png` | 1024×500 | Icon + "RoomReady / Every room ready." + ViveReply chip |
| `brand/export/social-preview-1280x640.png` | 1280×640 | Centred stack + chip |
| `brand/export/readme-banner-1920x480.png` | 1920×480 | Wide banner, same kit |
| `brand/export/end-card-1920x1080.png` | 1920×1080 | Ink bg, icon, tagline, chip, vivereply.com |
| `brand/proof/legibility-proof.png` | 880×300 | 192px + 48/32px proofs, mono, round mask |
| `res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher.png` | 48–192 | Legacy icons |
| `res/mipmap-*/ic_launcher_round.png` | 48–192 | Circle-masked |
| `res/drawable/ic_launcher_foreground.xml` | vector | Glyph VectorDrawable (mirrors the SVG) |
| `res/drawable/ic_launcher_background.xml` | vector | Solid ink |
| `res/drawable/ic_launcher_monochrome.xml` | vector | Themed-icon silhouette |
| `res/mipmap-anydpi-v26/ic_launcher[_round].xml` | xml | Adaptive icon + monochrome |

Splash: `Theme.RoomReady` points at `Theme.RoomReady.Splash` via
`android:splashScreenTheme` (ink background, centred glyph); Android 12+
shows it, then continues with the app theme — no code changes, no extra
dependency. The splash attributes live in `values-v31/themes.xml`; the base
`values/themes.xml` carries a plain placeholder so pre-31 is unaffected
(and lint stays `NewApi`-clean).
