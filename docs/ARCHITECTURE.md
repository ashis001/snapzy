# Movexa — architecture

How the app is actually built, as opposed to how `MVP_PLAN.md` originally
laid it out. This file describes the code that exists today.

## Module layout

Single Gradle module (`composeApp`) with three source sets. The original plan
called for 13 modules (`core-designsystem`, `feature-*`, …); a single module
with packages is what we ship — the boundaries are still drawn, just with
directories instead of Gradle projects.

```
composeApp/src/
├── commonMain/kotlin/com/snapzy/app/
│   ├── App.kt                      nav graph
│   ├── core/
│   │   ├── designsystem/           colour, type, shape tokens + editor chrome + icons
│   │   ├── editing/                the edit model, controller, tool registry, catalogues
│   │   ├── navigation/             route constants
│   │   ├── comingsoon/             the gated-feature registry + sheet
│   │   └── media/                  expect/actual media engine, gallery, export
│   └── feature/
│       ├── home/                   springboard
│       ├── onboarding/
│       ├── picker/                 device gallery
│       ├── editor/
│       │   ├── common/             shared tool panels + overlay renderer
│       │   ├── photo/              photo editor
│       │   └── video/              video editor + timeline
│       ├── collage/
│       └── export/
├── androidMain/                    Media3 / MediaStore implementations
└── iosMain/                        AVFoundation / PhotoKit implementations
```

## The edit model

`core/editing/EditModels.kt` is the single source of truth. Everything is
plain data so the same model drives the preview, the timeline, the export
planner and (later) draft persistence.

```kotlin
EditProject
├── clips: List<VideoClip>        trim range, speed, volume, mute, rotation, flip, filter, adjust
├── overlays: List<Overlay>       Text | Sticker | Drawing | Pip
├── audioTracks: List<AudioTrack> music / SFX / voice-over
├── canvas: CanvasSpec            aspect, background style
└── durationMs
```

`EditorController` holds the project plus a 50-step undo/redo history.
Mutations go through `update { }` (records undo) or `updateLive { }` (does
not — used during slider drags so one gesture is one undo step).

## Preview vs export

The preview and the export must agree, so the colour maths lives in exactly
one place per layer:

| Layer | Shared maths | Preview | Export |
|---|---|---|---|
| Colour | `core/editing/LookMatrix.kt` → `buildLookMatrix(filter, adjust)` | Compose `ColorMatrix` on the image | Media3 `Brightness` / `Contrast` / `HslAdjustment` / `RgbAdjustment` |
| Overlays | `feature/editor/common/OverlayPreview.kt` | Compose draw layer | Media3 `TextOverlay` / `BitmapOverlay` + `OverlayEffect` |

`buildLookMatrix` and the export's `colorEffects` implement the same filter +
adjust maths independently but from the same spec, so they cannot drift in
intent — and a mismatch is visible immediately in the exported file.

### Known preview/export gaps

- **Video colour grading is not shown in the preview.** `VideoPlayerSurface`
  is an `AndroidView`, so a Compose `ColorFilter` does not tint video pixels.
  The preview shows the active filter/adjust values in a badge; the exported
  file *does* have the grade applied. Fixing this means rendering the preview
  through a `TextureView` we can tint.
- **The preview surface must be a `TextureView`, not Media3's `PlayerView`.**
  `PlayerView` uses a `SurfaceView`, which lives in its own hardware layer
  behind the window — Compose paints over it and the video is invisible.
  `VideoPlayer.android.kt` drives a plain `TextureView` through
  `player.setVideoTextureView(...)` instead.
- **PIP clips are not exported.** A PIP overlay needs a second decoder in the
  Media3 graph; the model supports it, the render does not yet.
- **Video transitions are stored but not rendered.** Media3 1.5 has no
  transition primitive for arbitrary clips.
- **Per-clip volume is exported** through a small 16-bit PCM gain stage
  (`GainAudioProcessor`), because Media3 has no built-in gain processor and
  `ChannelMixingAudioProcessor` needs the source channel count up front.

## Export pipeline

`core/media/VideoRender.kt` (expect) → `VideoRender.android.kt` / `.ios.kt`.

**Android** — Media3 Transformer 1.5.0 + `media3-effect`:

```
EditProject
  → per clip: EditedMediaItem
      ClippingConfiguration (trim)
      setRemoveAudio (mute)
      setEffects(Effects(audioProcessors, videoEffects))
        video: ScaleAndRotateTransformation, colour effects, SpeedChangeEffect,
               OverlayEffect (text / sticker / drawing / PIP),
               OverlayEffect (the transition fade)
        audio: ToInt16PcmAudioProcessor, SonicAudioProcessor (speed),
               GainAudioProcessor (volume)
  → EditedMediaItemSequence
  → Composition
      setEffects(Effects(emptyList(), [Presentation]))
  → Transformer → mp4 in the cache dir
```

**iOS** — `AVMutableComposition` + `AVAssetReader` / `AVAssetWriter` with a
Core Image pipeline: trim, speed, mute and gain, rotation, the colour grade,
the overlays, the transition fade and the audio bed. See `Ios*.kt` in
`iosMain/core/media`.

### Overlays hang off the clip, not the composition

Media3 applies an item's own effects on the **composition's** timeline, and
its composition-level effects are not applied consistently when a sequence
holds more than one item. So every overlay is attached to the clips it falls
inside and its window is expressed in composition time. The same is true of
the transition: the outgoing clip carries a black layer that ramps up over
its last 200 ms and the incoming one carries a black layer that ramps down
over its first 200 ms, which puts a dip to black exactly on the boundary.

Media3's overlay anchors use OpenGL's convention, where `+1` is the **top**,
so a normalised `y` of `0.25` becomes an anchor of `1 - 2y`.

### The preview needs a frame processor

A paused ExoPlayer with an *empty* effect list does not draw its first frame,
so the preview would stay black until playback started. The chain therefore
always holds an identity `ScaleAndRotateTransformation`, and the colour
effects are appended to it.

Swapping the effects rebuilds the video renderer, which detaches the surface —
so the `TextureView` is handed back to the player afterwards.

### The colour grade is one piece of maths

`core/media/ColorEffects.android.kt` maps a filter and an adjustment onto
Media3's colour effects, and the **preview uses the same function** through
`ExoPlayer.setVideoEffects`. What the user sees is what the export writes.

### Music and sound effects are synthesised

`core/media/AudioSynth.kt` is a small software synthesiser: every catalogue
entry is rendered to a 16-bit PCM WAV the first time it is used, so the
catalogue is real audio with no bundled assets. `AudioBed` mixes the music
(looped) and the sound effects (placed at their own start times) into a single
bed of exactly the timeline's length, which is added to the export as one
extra audio source. Media3 needs every audio source to agree on a channel
count, so the bed is widened to match the clips.

### Transitions

Media3 1.5 has no transition primitive and its video compositor does not run in
the export path, so a transition is drawn by hand: the incoming clip's opening
is cut into stills with `MediaMetadataRetriever` and laid over the outgoing
clip's tail with a per-frame overlay, while the incoming clip's own playback
starts after those moments. The two clips therefore overlap, and the timeline
shortens by the transition's length — the same as InShot.

The treatment is decided per frame by the overlay's settings: the alpha ramps
for a dissolve, the anchor moves for a slide, the scale ramps for a zoom, and
a wipe bakes a moving edge into the stills themselves.

### Picture in picture

Media3 1.5 cannot composite a second *video* into an export — the video
compositor does not run in the transformer's export path. The layer is
therefore drawn from stills: the frames of the PIP clip are cut out ahead of
the render (`MediaMetadataRetriever`), scaled to the size the layer occupies
inside its own clip, and handed to Media3 one per frame.

`ExportSettings` carries output width/height, frame rate and bitrate, with
`P720` / `P1080` / `P4K` presets.

### Output size

The output size is derived from the project's canvas, not from the source:
the short side of the chosen preset is kept and the long side follows the
canvas ratio, so a 9:16 canvas at 1080p renders 1080×1920 and a 1:1 canvas
renders 1080×1080.

Media3's `Presentation.createForAspectRatio` fits the ratio inside the source
and never upscales, so it is not used — `createForWidthAndHeight` with
`LAYOUT_SCALE_TO_FIT` is.

### Media3 portrait encoding — a measurement gotcha

Media3 encodes portrait video in landscape and writes a compensating display
matrix, so a 1080×1920 file reports as `1920x1080` with
`displaymatrix: rotation of -90.00 degrees`. **The file is correct** — players
rotate it back and show it upright.

Verify a render with the *display* size, not the coded size:

```bash
python3 - <<'PY'
import subprocess, json
s = json.loads(subprocess.run(
    ['ffprobe','-v','error','-select_streams','v:0','-show_streams','-of','json','out.mp4'],
    capture_output=True, text=True).stdout)['streams'][0]
w, h, rot = s['width'], s['height'], 0
for sd in s.get('side_data_list', []):
    if 'rotation' in sd: rot = int(sd['rotation'])
if abs(rot) % 180 == 90: w, h = h, w
print('display', w, 'x', h)
PY
```

`ffmpeg` applies the matrix when decoding, so an extracted PNG is already the
upright frame while `ffprobe` still reports the landscape coded size.

### Verified

A 6-second 640×1138 portrait source exported through the app produces a file
that reports **1080×1920, H.264, 30 fps, 6.04 s, AAC**, and the 720p chip
re-renders to 720×1280. Saving writes it to `Movies/Movexa/` on the device.

Measured on the exported file:

| Check | Result |
|---|---|
| Fade transition | the frame's mean luma falls 129 → **0** across the clip boundary and back |
| Music bed | the export's RMS doubles once a track is added |
| Picture in picture | a rectangle of the layer's own colour bars appears at 75 % across, 25 % down |
| Warm filter | the blue channel of the exported frame drops (a warmer image) |

## Drafts

`core/storage/` holds the draft store. `EditProject` is not serialised
directly — `DraftSnapshot` is a `@Serializable` mirror made only of
primitives (a colour becomes an ARGB long, an `Offset` becomes two floats,
image bytes become base64), and `DraftMapper` converts in both directions.
Enums are stored by name and fall back to their first constant if the name
is unknown, so an old draft can never crash the app.

`DraftAutosave` writes the edit out while the user works: a debounced save
after every change, and a final save when the editor is left or exported.
The home screen reloads its list every time it comes back into view.

## Device gallery

`core/media/DeviceMedia.kt` (expect) → platform implementations.

- **Android** — `MediaStore` query (images + video), `MediaMetadataRetriever`
  for video thumbnails, `BitmapFactory` for images.
- **iOS** — PhotoKit `PHAsset` fetch, `PHImageManager` for image data.

`rememberMediaPermission` handles the runtime grant on Android
(`READ_MEDIA_IMAGES` / `READ_MEDIA_VIDEO` on API 33+, `READ_EXTERNAL_STORAGE`
below). Where permission is missing the picker falls back to the OS system
picker, which needs no permission.

## Design system

`core/designsystem/` holds the tokens from `PRODUCT_SPEC.md` §2 plus the
editor chrome.

### Colour

The app is a video editor, so the chrome is deliberately quiet: **the user's
footage is always the most colourful thing on screen.** Every colour in the app
comes from `MovexaColors` — there is not a single hex literal outside
`core/designsystem/`.

**Surfaces — a five-step cool near-black elevation ladder**

| Token | Value | Used for |
|---|---|---|
| `bgCanvas` | `#070809` | letterbox bars, the deepest well |
| `bgBase` | `#0B0D12` | app background |
| `bgSurface` | `#13161E` | cards, tool bars, the timeline |
| `bgSurfaceElevated` | `#1B1F29` | tool panels, bottom sheets |
| `bgOverlay` | `#232836` | modals, menus |

**Borders** — white at low alpha (`borderSubtle` 6 %, `divider` 12 %,
`borderStrong` 16 %) reads as a lit edge rather than a drawn line, and it
stays visible on every surface.

**Text** — `textPrimary #EEF1F6`, `textSecondary #9AA3B2`, `textTertiary
#6B7484` (decorative only), `textOnBrand #04211C`.

**Brand** — a calm aqua `#3DDCC0` as the single accent, with deep indigo
`#6E6AF0` as the gradient partner (never a standalone accent) and a four-step
`brandRamp` for gradients and pressed states. Amber is reserved for Pro
badges; red for destructive; green for success.

Contrast against `bgBase`, checked with the WCAG formula:

| Pair | Ratio | Grade |
|---|---|---|
| `textPrimary` / `bgBase` | 17.2 | AAA |
| `textSecondary` / `bgBase` | 7.6 | AAA |
| `textSecondary` / `bgSurface` | 7.1 | AAA |
| `brandPrimary` / `bgBase` | 11.3 | AAA |
| `accentDanger` / `bgBase` | 5.8 | AA |

The earlier palette (`#0E0F13` / `#14E8C4` / `#7C4DFF`) passed contrast too —
the problem was not legibility but *character*: an electric cyan and a
Material-purple read as a template. The new palette is desaturated, warmer in
its neutrals, and uses the accent far more sparingly, so the chrome recedes.

### Icons

`EditorIcons.kt` — 47 hand-drawn line icons drawn with Compose primitives
(`drawToolIcon`), so the toolbar needs no icon font and no new dependency.
Every icon shares a 24×24 grid, a 2 px stroke and round caps.

### Chrome

`EditorComponents.kt` — `EditorToolBar`, `ToolPanel`, `OptionChip`,
`ColorSwatch`, `SliderRow`, `SegmentedTabs`, `TabStrip`, `GradientButton`,
`ToolIconView`, `SurfaceCard`.

### Layout rules

The editor follows InShot's structure exactly (see `UI-REFERENCE.md`):

```
header → preview (owns all vertical space) → transport row
       → open tool's panel → tool strip → timeline (always last)
```

## Gated features

`core/comingsoon/GatedFeature.kt` is a single enum of every feature that
needs a backend we do not have (AI Cut, Auto Captions, AI Speech, AI Enhance,
Cutout, Stabilizer, Tracking, Templates, Marketplace, Go Pro, Account). Every
entry point calls `showComingSoon(GatedFeature.X)` instead of hand-rolling a
dialog.

The InShot reference confirms these are genuinely server-side: its own
`local_ai_config.json` routes `matting`, `denoise`, `speech`, `translate`,
`flash` and `auto-beat` to `inpaint.inshot.cc`, `stt.inshot.cc` and
`iqe.inshot.cc` — see `docs/INSHOT-2.243-FEATURE-REPORT.md` §1.3.

## Tool registry

`core/editing/EditorTool.kt` declares every tool once, plus `VIDEO_TOOLS`
(36, in InShot's order) and `PHOTO_TOOLS` (23). Each editor renders its own
subset from the shared list, so adding a tool is one enum entry plus one
`when` branch in the panel router.

## Build

```bash
./gradlew :composeApp:assembleDebug            # APK
./gradlew :composeApp:compileKotlinIosSimulatorArm64   # iOS
```

Requires JDK 17+, Android SDK 36, and the Xcode toolchain for iOS.
