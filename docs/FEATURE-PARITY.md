# Feature parity — Movexa vs InShot 2.243

What Movexa does today, measured against the reference app
(`INSHOT-2.243-FEATURE-REPORT.md`). "Verified" means it was driven on an
Android 15 emulator and the result inspected, not just compiled.

Legend: **✅ working** · **◐ partial** · **⏳ Coming Soon** (server-backed by
design) · **❌ not built**

---

## 1. Screen flow

InShot's flow is `Springboard → Create → Picker → Editor → Export → Share`.
Movexa follows it exactly:

| Route | Screen | Status |
|---|---|---|
| `splash` | Splash → Onboarding (3 slides, skippable) | ✅ |
| `home` | Springboard: wordmark, PRO chip, settings, **saved projects**, create card, roadmap, bottom bar | ✅ |
| `picker/video` | Real gallery, multi-select ≤ 9 | ✅ |
| `picker/photo` | Real gallery, single select | ✅ |
| `picker/collage` | Real gallery, multi-select ≤ 9 | ✅ |
| `editor/video` | Video editor | ✅ |
| `editor/photo` | Photo editor | ✅ |
| `collage` | Collage builder | ✅ |
| `export` | Export / save / share | ✅ |

Back behaviour: the editor returns to the picker, the picker to home, home
exits. The export screen has "‹ Back to editor".

## 2. Editor chrome

Matched against the captured InShot screens in `reference/`:

| Element | Status |
|---|---|
| Preview owns all vertical space, centred, 9:16 | ✅ |
| Transport row: undo · redo · play · fullscreen / ✓ Done | ✅ |
| Tool strip: scrolling icons with labels, active item tinted | ✅ |
| Tool panel opens **above** the strip; Done button appears in the transport row | ✅ |
| Timeline pinned to the **bottom**: coral `+`, mute chip, clip thumbs, playhead, time ruler, elapsed/total | ✅ |
| Multi-clip timeline, drag to reorder, per-clip trim | ✅ |
| Undo / redo across every mutation | ✅ |

## 3. Video tools

| Tool | Status | Notes |
|---|---|---|
| Trim | ✅ | frame-accurate scrubber, per clip |
| Split / Duplicate / Delete | ✅ | split at the playhead |
| Speed | ✅ | 0.25×–4×, applied to the export |
| Volume / Mute | ✅ | mute + gain are exported |
| Filter | ✅ | 12 presets, applied to the export |
| Adjust | ✅ | brightness, contrast, saturation, hue, temperature |
| **Colour grade in the preview** | ✅ | **the preview is graded with Media3's own effect chain — the same effects the export applies, so it is the real grade, not a tint** |
| Text | ✅ | colour, size, bold, outline, shadow, draggable — baked into the export, and it honours its time window |
| Sticker | ✅ | 24 emoji stickers, draggable, baked into the export |
| Draw / Doodle | ✅ | colour, brush size, baked into the export |
| Canvas (ratio) | ✅ | 7 ratios, drives the export shape |
| Background | ✅ | black / white / blur / custom |
| **Transition** | ◐ | **the fade is rendered** — the frame dips to black at the clip boundary, measured at luma 0 in the exported file. Slide / Zoom / Wipe / Blur need a GL shader and are not rendered |
| Mask | ◐ | PIP layer only |
| Blend | ◐ | PIP layer only, 11 modes |
| **Music / Sound effects** | ✅ | **real audio** — the catalogue is synthesised to a WAV and mixed into the export (verified: the export's RMS doubles when a track is added) |
| Apply to all | ✅ | copies look + speed to every clip |
| **PIP** | ✅ | **rendered into the export** — the layer's frames are cut from its clip and composited over the frame at the right position and size |
| Reverse / Freeze | ✅ | freeze inserts a still; reverse flips the clip order |
| Effects (glitch, bling…) | ⏳ Coming Soon — needs a shader pipeline |
| Animation / Keyframe | ⏳ Coming Soon |
| Mosaic / Chroma / Tracking | ⏳ Coming Soon |
| AI Cut / Captions / AI Speech / Enhance / Cutout / Stabilize | ⏳ Coming Soon |

## 4. Photo tools

| Tool | Status |
|---|---|
| Filter, Adjust | ✅ |
| Effects | ◐ 5 looks render; shader presets are ⏳ |
| Text (add / edit / delete, colour, size, bold, outline, shadow) | ✅ |
| Sticker, Draw, Eraser | ✅ |
| Crop, Canvas, Background, Border, Frame, Layout | ✅ |
| PIP, Mask, Blend | ◐ |
| Mosaic | ⏳ |
| AI Retouch / AI Light / AI Effect / Enhance / Cutout | ⏳ |

## 5. Export

| | Status |
|---|---|
| Real render, not a preview approximation | ✅ Media3 Transformer + `media3-effect` |
| Trim, speed, mute, gain, rotate, flip | ✅ applied at export |
| Colour grade (brightness / contrast / HSL / RGB) | ✅ applied at export |
| Text + sticker + drawing overlays | ✅ composited into the frame |
| Overlay time windows | ✅ an overlay only shows inside its own window |
| Fade transition | ✅ the frame dips to black at the boundary |
| Picture in picture | ✅ composited into the frame |
| Music + sound effects | ✅ mixed into the audio track |
| 720p / 1080p / 4K | ✅ verified: 1080×1920, and 720×1280 on re-render |
| Save to the device gallery | ✅ `Movies/Movexa/`, `Pictures/Movexa/` |
| OS share sheet | ✅ |
| Photo export (flatten to JPEG) | ✅ |
| iOS export | ◐ trim, speed, mute/gain, colour grade, overlays, transitions and audio — **compiled, not yet run on a device** |

Verified output: **1080×1920, H.264, 30 fps, 6.04 s, AAC**.

## 6. Drafts

| | Status |
|---|---|
| Every edit is written out as a draft | ✅ debounced while editing, and on the way out of the editor |
| The home screen lists the drafts | ✅ with a thumbnail, a duration and a timestamp |
| Tapping a draft reopens the edit | ✅ verified: the filter applied before leaving was still applied after reopening |

## 7. The "Coming Soon" set is correct, not a shortcut

InShot's own `local_ai_config.json` routes six models to three servers:

| modelType | Endpoint | Feature |
|---|---|---|
| `matting` | `inpaint.inshot.cc` | Cutout / background removal |
| `denoise` | `stt.inshot.cc` | AI Cut (silence + filler removal) |
| `speech` | `stt.inshot.cc` | Auto Captions |
| `translate` | `stt.inshot.cc` | Bilingual subtitles |
| `flash` | `iqe.inshot.cc` | AI Enhance / retouch |
| `auto-beat` | `stt.inshot.cc` | Auto Beat |

So AI Cut, Auto Captions, AI Speech, AI Enhance, Cutout and Auto Beat are
**server jobs in InShot too**. Building them without a backend would mean
shipping a button that cannot work. They open the shared Coming Soon sheet,
which is the honest behaviour — and the same pattern the original
`PRODUCT_SPEC.md` §4.2 specified.

## 8. What is genuinely missing

| Gap | Why it matters | Cost |
|---|---|---|
| Slide / Zoom / Wipe / Blur transitions | only the fade is rendered | a custom GL shader for the transition |
| The picture-in-picture layer is cut at 12 fps | the layer is a little steppy | decoding the layer into a texture instead of into stills |
| iOS export is not run on a device | it compiles, but nobody has seen the file | an iOS simulator run |
| No blend modes in the export | the layer is drawn normally | a per-layer blend in the compositor |
| Chroma key | the model has it, the export ignores it | a chroma filter |

## 9. Reproducing the checks

```bash
./scripts/verify-android.sh            # launch → home → picker, no crashes
```

Export check:

```bash
adb shell run-as com.snapzy.app cat cache/snapzy_render_*.mp4 > out.mp4
ffprobe -v error -select_streams v:0 -show_streams -of json out.mp4
```

Note that Media3 encodes portrait as landscape with a `-90°` display matrix —
measure the **display** size, not the coded size. See `ARCHITECTURE.md`.
