# Feature parity — Snapzy vs InShot 2.243

What Snapzy does today, measured against the reference app
(`INSHOT-2.243-FEATURE-REPORT.md`). "Verified" means it was driven on an
Android 15 emulator and the result inspected, not just compiled.

Legend: **✅ working** · **◐ partial** · **⏳ Coming Soon** (server-backed by
design) · **❌ not built**

---

## 1. Screen flow

InShot's flow is `Springboard → Create → Picker → Editor → Export → Share`.
Snapzy follows it exactly:

| Route | Screen | Status |
|---|---|---|
| `splash` | Splash → Onboarding (3 slides, skippable) | ✅ |
| `home` | Springboard: wordmark, PRO chip, settings, projects, create card, roadmap, bottom bar | ✅ |
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
| Text | ✅ | colour, size, bold, outline, shadow, draggable — **baked into the export** |
| Sticker | ✅ | 24 emoji stickers, draggable, baked into the export |
| Draw / Doodle | ✅ | colour, brush size, baked into the export |
| Canvas (ratio) | ✅ | 7 ratios, drives the export shape |
| Background | ✅ | black / white / blur / custom |
| Transition | ◐ | stored per project, **not rendered in the export** |
| Mask | ◐ | PIP layer only |
| Blend | ◐ | PIP layer only, 11 modes |
| Music / Sound effects | ◐ | added to the project, **no audio file is attached** |
| Apply to all | ✅ | copies look + speed to every clip |
| PIP | ◐ | model + preview, **not exported** (needs a second decoder) |
| Reverse / Freeze | ⏳ | Coming Soon |
| Effects (glitch, bling…) | ⏳ | Coming Soon — needs a shader pipeline |
| Animation / Keyframe | ⏳ | Coming Soon |
| Mosaic / Chroma / Tracking | ⏳ | Coming Soon |
| AI Cut / Captions / AI Speech / Enhance / Cutout / Stabilize | ⏳ | Coming Soon |

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
| Text + sticker overlays | ✅ composited into the frame |
| 720p / 1080p / 4K | ✅ verified: 1080×1920, and 720×1280 on re-render |
| Save to the device gallery | ✅ `Movies/Snapzy/`, `Pictures/Snapzy/` |
| OS share sheet | ✅ |
| Photo export (flatten to JPEG) | ✅ |
| iOS export | ◐ trim + mute only |

Verified output: **1080×1920, H.264, 30 fps, 6.04 s, AAC**.

## 6. The "Coming Soon" set is correct, not a shortcut

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

## 7. What is genuinely missing

| Gap | Why it matters | Cost |
|---|---|---|
| PIP not exported | a composited second video layer | a second decoder in the Media3 graph |
| Transitions not rendered | clips cut hard against each other | Media3 1.5 has no transition primitive |
| Music / SFX have no audio | audio tracks are metadata only | needs a bundled audio pipeline |
| Video colour grade invisible in the preview | you cannot see what you are grading | render the preview through a tintable surface |
| No draft persistence | projects do not survive a restart | a real store (the plan called for SQLDelight) |
| Blur background missing from exported photos | `Modifier.blur` is not captured by the layer export | blur the bitmap before compositing |
| iOS export is trim + mute only | parity gap | AVFoundation composition with `AVVideoComposition` + Core Image |

## 8. Reproducing the checks

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
