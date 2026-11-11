# InShot UI reference

Ground truth for the layout, captured from the **real InShot app** running on an
Android 15 emulator (Pixel 9 Pro, 1280×2856). These are the reference images
every screen in this project is checked against.

| File | Screen |
|---|---|
| `reference/inshot-home.png` | Springboard |
| `reference/inshot-picker.png` | Media picker (single-select) |
| `reference/inshot-picker-multiselect.png` | Media picker (multi-select) |
| `reference/inshot-video-editor.png` | Video editor, no panel open |
| `reference/inshot-filter-panel.png` | Video editor with the Filter panel open |
| `reference/inshot-export.png` | Export screen |

How these were captured: the `.apkm` bundle in `~/Downloads` was unzipped and
installed with `adb install-multiple` (base + `arm64_v8a` + `xxhdpi` splits),
then the flow was driven with `adb shell input tap` and captured with
`adb shell screencap`.

> These images are reference material for layout only. InShot's fonts, icons,
> illustrations, LUT tables, stickers and music are their assets — do not copy
> them. Movexa's own visual identity is the dark teal/violet system in
> `core/designsystem`.

---

## 1. Springboard

```
┌────────────────────────────┐
│ InShot            ♛    ⚙    │  wordmark left, pro + settings right
│                            │
│                            │  recent projects (empty on a fresh install)
│                            │
│ CREATE NEW                 │  section label, uppercase
│ ┌────────────────────────┐ │
│ │  ⬤ Video  ⬤ Photo  ⬤ Collage │ │  ONE elevated card,
│ │                        │ │  three large circular buttons,
│ └────────────────────────┘ │  label under each
│ WHAT'S NEW           ALL › │  section label + link
│ [card][card][card]         │  horizontal carousel
└────────────────────────────┘
```

**Movexa equivalent:** `feature/home/HomeScreen.kt` keeps the same skeleton —
wordmark + PRO chip + settings at the top, a `Create new` section holding one
elevated card with three gradient circular buttons (Video / Photo / Collage),
then a compact row of secondary pills (Templates, AI Tools, Store), then the
roadmap list, then a rounded bottom bar with Home / Discover / Settings and a
gradient create FAB.

The one deliberate difference is the palette: InShot uses a warm orange→pink
gradient on a light theme; Movexa keeps the dark theme from
`PRODUCT_SPEC.md` §2.

## 2. Media picker

```
┌────────────────────────────┐
│ ←   RECENTS ⌄   MATERIALS ⋮│  back, album dropdown, overflow
├────────────────────────────┤
│              VIDEO PHOTO ALL│  right-aligned tab strip, underline on active
├────────────────────────────┤
│ ▢ ▢ ▢ ▢                    │  4-column grid of square thumbnails
│ ▢ ▢ ▢ ▢                    │  video cells carry a duration badge
│ ▢ ▢ ▢ ▢                    │  bottom-left
├────────────────────────────┤
│ 🗑 Selected items ⌃      ✓  │  trash + count left, circular check right
└────────────────────────────┘
```

Multi-select mode replaces the tab strip with a multi-select icon at the top
left, and the bottom bar becomes `🗑  N selected ⌃` with the check button
turning brand-coloured. A strip of the chosen items appears below.

## 3. Video editor

The important structural point: **the timeline is the last element in the
column, pinned to the bottom of the screen.**

```
┌────────────────────────────┐
│ ←   (?)            Export  │
│                            │
│      ┌────────────┐        │
│      │            │        │  preview owns all the vertical space
│      │   9:16     │        │
│      │            │        │
│      └────────────┘        │
│                            │
│  ↶  ↷       ▶         ⛶    │  transport row
│                            │
│ ┌────────────────────────┐ │
│ │  (open panel content)  │ │  only while a tool is open
│ └────────────────────────┘ │
│                            │
│ Canvas Audio Sticker …     │  scrolling tool strip: icon + label
│                            │
│ (+) Mute Clip ┃▓▓▓▓┃ 00:00 │  timeline, always last
│              0:00.0  0:05.0│
└────────────────────────────┘
```

Details worth copying:

- The **tool strip** is a single horizontally scrolling row of icons with their
  labels underneath. The active tool is tinted, not underlined.
- **Opening a tool replaces the tool strip's neighbours, not the strip.** The
  panel's content appears above the strip, and the transport row's right-hand
  button becomes a filled brand-coloured **✓** that closes it. While a panel
  is open, InShot shows a *panel-specific* sub-strip instead of the main tool
  strip (e.g. `Effect ✦ / AI Effect / Filter / Adjust` for the look panel).
- The **timeline row** is: a coral circular **+** on the left, a small
  **"Mute Clip"** chip with a speaker glyph, then the scrolling clip
  thumbnails with a thin white playhead drawn over them, a time ruler
  (`00:00 · 00:02 · 00:04`) and the elapsed / total times underneath.
- The header has a back arrow, a help icon, and **Export** on the right.

## 4. Export screen

```
┌────────────────────────────┐
│ ←   (?)            Export  │
│                            │
│      Resolution   1080p ›  │  label left, value + chevron right
│      Frame Rate    30fps › │
│      Format          MP4 › │
│                            │
│        [ EXPORT ]          │
└────────────────────────────┘
```

`feature/export/ExportScreen.kt` uses the same "label left, value right"
pattern for resolution, plus a render progress indicator while Media3 is
encoding.

---

## 5. How a change gets checked

1. Build and install: `./gradlew :composeApp:assembleDebug && adb install -r composeApp/build/outputs/apk/debug/composeApp-debug.apk`
2. Drive the flow: `adb shell input tap <x> <y>` (screen is 1280×2856).
3. Capture: `adb shell screencap -p /sdcard/s.png && adb pull /sdcard/s.png out.png`
4. Compare against the reference images in this folder.

`scripts/verify-android.sh` wraps steps 1–3 for the launch → home path.


## Home screen

The springboard follows the reference app's layout, on Movexa's own gradient:

```
Movexa                                  ♔  ⚙
        (the open space holds RECENT — the saved drafts)

CREATE NEW
┌──────────────────────────────────────────────┐
│   ( video )      ( photo )      ( collage )  │
└──────────────────────────────────────────────┘

WHAT'S NEW                              ALL ›
┌────────┐ ┌────────┐ ┌────────┐
│Templates│ │AI Tools│ │ Store  │
└────────┘ └────────┘ └────────┘
```

- The wallpaper is Movexa's aqua → blue → violet gradient
  (`MovexaColors.homeGradient`); the reference app uses a warm one.
- The create buttons sit on a frosted card; the reference app uses a white one.
- The crown opens the Pro sheet and the gear opens Settings — both are
  server-backed, so both open the shared Coming Soon sheet.
- `ALL ›` opens Discover, which is server-backed too.
- There is no bottom navigation; the reference app has none either.
- The saved drafts fill the open space above `CREATE NEW` under a `RECENT`
  label, which is where the reference app puts its promotional carousel.
