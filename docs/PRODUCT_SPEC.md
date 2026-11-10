# Snapzy — Product Specification

> Original app, original screen design. Feature *categories* are modeled on the mobile video/photo editor category (trim, crop, filters, collage, chroma key, keyframes, AI tools, Pro subscription, etc.) — the IA, screens, navigation, color system and copy below are designed fresh for Snapzy, not copied from any existing app.

## 1. App overview

| | |
|---|---|
| Brand name | **Snapzy** |
| Tagline | "Edit fast. Post faster." |
| Package ID | `com.snapzy.app` |
| Platforms | Android + iOS, one shared codebase (Kotlin Multiplatform) |
| Category | Video & photo editor, short-form content creation |
| Monetization (future) | Freemium — local editing free, Pro tier unlocks premium filters/templates + removes watermark (billing is a server-dependent feature — see §4, marked Coming Soon for MVP) |

## 2. Design system

### 2.1 Color palette (dark-first, original)

Dark UI is the right call for an editor — it keeps focus on the media preview and reduces eye strain during long edit sessions.

| Token | Hex | Usage |
|---|---|---|
| `bg.base` | `#0E0F13` | App background, editor canvas surround |
| `bg.surface` | `#1A1C22` | Cards, sheets, toolbars |
| `bg.surfaceElevated` | `#24262E` | Modals, active tool panel |
| `brand.primary` | `#14E8C4` (Snapzy Teal) | Primary actions, active tool highlight, progress |
| `brand.secondary` | `#7C4DFF` (Snapzy Violet) | Secondary accents, gradients, selection highlight |
| `brand.gradient` | `linear(#14E8C4 → #7C4DFF)` | Logo, splash, Pro badge, export button |
| `accent.pro` | `#FFC247` | "PRO" / paywall badges, premium content tag |
| `accent.success` | `#3DDC84` | Export success, save confirmation |
| `accent.danger` | `#FF5C5C` | Destructive actions (delete clip, discard) |
| `text.primary` | `#F5F6FA` | Primary text/icons on dark surfaces |
| `text.secondary` | `#9A9DAC` | Secondary/disabled text, hints |
| `divider` | `#2E313B` | Hairlines, separators |
| `overlay.scrim` | `#000000` @ 60% | Modal/sheet scrim |

Light theme uses the same tokens inverted (`bg.base` → `#F7F8FA`, `text.primary` → `#14151A`, etc.) — supported but dark is default.

### 2.2 Typography

- **Headings / brand moments:** Manrope (geometric, distinct from typical system fonts) — weights 600/700
- **Body / UI labels:** Inter — weights 400/500/600
- **Type scale:** `display` 28/36, `title` 20/28, `body` 15/22, `label` 13/18, `caption` 11/16

### 2.3 Spacing & shape

- 8pt grid (4/8/12/16/24/32/48)
- Corner radius: `sm` 8dp (chips, thumbnails), `md` 14dp (cards, sheets), `lg` 24dp (bottom sheets top corners), `pill` 999dp (buttons, tags)
- Icon grid: 24dp, stroke-style line icons (not filled) for toolbar, filled for primary actions

### 2.4 Core components

- **Bottom tool sheet** — slides up from editor toolbar, 40–55% screen height, drag handle, horizontally scrollable option row inside
- **Segmented pill tabs** — Home top-level navigation (Projects / Discover / Templates)
- **Floating gradient FAB** — the "+ Create" button, uses `brand.gradient`
- **Pro badge chip** — small rounded `accent.pro` chip with lock icon, shown on gated content/tools
- **"Coming Soon" sheet** — standardized bottom sheet (see §5) shown for every server/API-dependent feature

## 3. Information architecture & navigation

```
Splash
  └─> Onboarding (first launch only, 3 slides, skippable)
       └─> Home
            ├─ Tab: Projects (default)      — recent/drafts grid, resume editing
            ├─ Tab: Create (center FAB)     — New Video | New Photo | Collage | Templates*
            ├─ Tab: Discover*                — effects/music/sticker marketplace (placeholder feed)
            └─ Tab: Settings                — account*, appearance, Go Pro*, about, legal

Create → New Video  → Media Picker (multi-select) → Video Editor → Export → Share Sheet
Create → New Photo  → Media Picker (single-select) → Photo Editor → Export → Share Sheet
Create → Collage    → Media Picker (multi, up to 9) → Collage Builder → Photo Editor (optional) → Export
Create → Templates* → Coming Soon sheet

Projects → tap project → resumes directly into Video/Photo Editor at last saved state
Discover*, Settings → Go Pro*, Settings → Account* → Coming Soon sheet

(* = server/API-dependent entry point; MVP shows the screen's shell/entry icon, tapping the
   gated action opens the Coming Soon sheet instead of performing it)
```

Navigation pattern: single-activity, Compose Multiplatform navigation graph, bottom tab bar on Home only; Editor and Export screens are full-screen stacks pushed on top (no bottom bar) to maximize canvas space.

## 4. Full feature list

### 4.1 Local / on-device — fully functional, no network required

**Capture & import**
- Import photo/video from device gallery (multi-select for collage)
- Basic permission flow (storage/media access)

**Video Editor — core toolbar**
- Trim / split / delete clip
- Reorder clips on timeline
- Speed control (0.25x–4x) with ramp curve (constant, ease-in, ease-out)
- Volume control per clip + mute
- Rotate / flip / crop / aspect ratio presets (1:1, 9:16, 16:9, 4:5, free)
- Filters (bundled LUT-style filter pack, intensity slider)
- Adjust (brightness, contrast, saturation, sharpness, vignette, temperature)
- Text overlay (fonts bundled, color, outline, animation: none/fade/pop)
- Sticker/emoji overlay (bundled sticker pack shipped in app, not downloaded)
- Draw/doodle overlay (freehand, brush size/color)
- Transitions between clips (bundled set: fade, slide, zoom, wipe)
- Picture-in-picture (PIP) overlay clip
- Chroma key (green-screen background removal using a bundled/solid background)
- Keyframe animation (position/scale/rotation/opacity over time) for clip/text/sticker/PIP layers
- Collage layout composition (grid templates, swap/reorder photos)
- Blur effect (background blur, mosaic/pixelate censor blur)
- Undo/redo, project autosave (local draft)

**Photo Editor — core toolbar**
- Crop / rotate / flip / aspect presets
- Filters + adjust (same engine as video where applicable)
- Text, stickers, draw overlay
- Blur / mosaic

**Export**
- Local render to device (resolution choice: 480p/720p/1080p, frame rate match source)
- Save to device gallery
- Share via OS native share sheet (hands off to installed apps — not a Snapzy server call)

### 4.2 Server/API-dependent — **Coming Soon in MVP**

Every item below gets a real icon/entry point in the UI; tapping it opens the standardized Coming Soon sheet (§5) instead of performing the action.

- AI Cut (auto-remove silences/filler words — needs speech-recognition backend)
- AI Enhance (cloud image/video quality upscaling)
- AI Eraser / Object & background removal (cloud inpainting)
- Auto Captions (speech-to-text captioning)
- Text-to-Speech / AI voiceover
- Sticker / music / effects **search & marketplace download** (bundled starter pack is local; the expandable marketplace is not)
- Templates browsing (server-curated template feed)
- Discover feed (trending edits/effects)
- Go Pro subscription + purchase restore + cross-device account binding
- Sign-in / account system
- Push notifications
- Remote-config–driven promos/seasonal banners
- Cloud project backup/sync

## 5. The "Coming Soon" pattern (standardized, reused everywhere)

Every gated feature uses the **same** bottom sheet component so the app feels intentional, not broken:

```
┌─────────────────────────────┐
│   (feature icon, grayscale)  │
│                               │
│   "AI Enhance is coming      │
│    soon"                     │
│                               │
│   One line describing what   │
│   it will do when it ships.  │
│                               │
│   [ Notify me ]  (secondary) │
│   [ Got it ]      (primary)  │
└─────────────────────────────┘
```
- "Notify me" stores a local boolean/flag only in MVP (no real backend to notify from yet) — or is simply omitted if you don't want to promise it
- Gated toolbar icons additionally show a small `accent.pro`-colored "soon" tag/dot so users don't have to tap to find out

## 6. Tech stack

| Layer | Choice |
|---|---|
| Shared logic + UI | **Kotlin Multiplatform** + **Compose Multiplatform** (Android + iOS from one codebase) |
| Architecture | MVI-ish: `ViewModel`-equivalent per screen exposing `StateFlow<UiState>`, Kotlin Coroutines for async |
| DI | Koin (KMP-friendly, no codegen required) |
| Navigation | Compose Multiplatform Navigation (or Decompose if deeper back-stack control is needed later) |
| Local persistence | SQLDelight (KMP SQL) for project/draft metadata; platform file storage for media |
| Video/photo pipeline | Platform `expect/actual`: Android → **Media3 Transformer/Effects** (trim, speed, crop, overlay, basic color/LUT filters built-in); iOS → **AVFoundation + Core Image** (`CIFilter` for color/LUT, `AVMutableComposition` for trim/overlay/speed) |
| Custom GPU effects (chroma key, custom filters, keyframe compositing) | Android: OpenGL ES shader pipeline; iOS: Metal/Core Image custom kernel — isolated behind one shared Kotlin interface (`expect class GpuEffectEngine`) so UI code never touches platform GPU code directly |
| Image loading | Coil 3 (Compose Multiplatform–compatible) |
| Build | Gradle (Kotlin DSL), Android target via standard AGP, iOS via Kotlin/Native + Compose Multiplatform's iOS framework export |

## 7. Out of scope for v1 (explicitly)

- Anything in §4.2 — ships as UI shell + Coming Soon only
- Real billing/IAP integration (Pro screen is visual/marketing only until a backend exists)
- Multi-device sync, user accounts, cloud anything
