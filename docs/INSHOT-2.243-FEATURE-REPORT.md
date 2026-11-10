# InShot 2.243.1555 — feature report

Source: `com.camerasideas.instashot` **2.243.1555** (versionCode 1555), minSdk 26 / targetSdk 36 / compileSdk 36.
Artifact: `base.apk` (72,653,360 bytes), SHA-256 `5c960da1c5a1f2b332c7bd69928c48480290a979367babc40df7140e11ca9dde`.

Supersedes [`INSHOT-FEATURE-REPORT.md`](./INSHOT-FEATURE-REPORT.md) (2.231.1551) — see [§7 Delta](#7-delta-vs-the-2231-report).

Method: **static inspection only. Nothing was executed.**

- REA CLI (`rea-agents@6.7.0`) → `decode-android-resources` (apktool 3.0.3): manifest, 2,000 string resources, **40 locales**, 6,296 decoded files.
- REA CLI → `inspect-android-package` (jadx-headless-mcp 0.7.1): **37,994 classes**, 6,126 resources, 30 permissions.
- REA CLI → `search-android-classes` (22 targeted queries) to confirm each feature at class level.
- Cross-check: standalone `jadx` CLI decompile → 25,521 classes, 37,824 `.java` files.

Everything marked **seen** is directly present in the shipped artifact. Everything marked **inferred** is a
reasoned conclusion from a name, config or bridge class. Nothing about runtime behaviour was observed.

> Scope note: InShot's filters (LUT tables), fonts, stickers, music, Lottie animations and transitions are
> their copyrighted assets. Build your own versions rather than copying them.

---

## 1. What the app is made of (engines)

### 1.1 Native layer — NOT in this artifact

**`base.apk` contains zero `.so` files.** It is the *base split* of an Android App Bundle; the native
libraries live in the per-ABI splits. So the native layer below is **inferred** from the Java bridge
classes and the 2.231 report, not observed here.

| Native library (from 2.231 report) | Role | Bridge class observed in 2.243 |
|---|---|---|
| `libisffmpeg`, `libisvideoengine`, `libisvideoutil`, `libisplayer` | Own FFmpeg build: decode, encode, preview player, timeline render | `com.camerasideas.instashot.videoengine.VideoEditor`, `MediaMuxer` |
| `libgpuimage-extension` + `GPU3DLookupFilter.glsl`, `GPUAdjustLookupFilter.glsl` | GPU filters: 3D LUT, adjust, HSL, tone curve | `com.camerasideas.instashot.graphicproc.*`, `com.inshot.graphics.engine` |
| `libanim_engine_lib` (~15 MB) | Animation / effect / transition engine (text, sticker, clip animation, keyframes) | `com.camerasideas.instashot.videoengine.EffectKeyframe`, `com.inshot.graphics.layer` |
| `libskia`, `libskigifcore`, `libgifimage` | 2D rendering, GIF / animated WebP stickers | `com.inshot.suit`, `com.android.skigifcore` |
| `libMNN`, `libYXAIEngine`, `libYXAImg` | On-device neural network runtime | `com.android.algo.*` |
| `libvid_seg`, `libportrait_matting_jni`, `libImageKit` | Video / portrait segmentation → cutout, background removal | `com.android.algo.portraitmatting`, `com.android.algo.videoSeg` |
| `libvid_sot` | Single-object tracking → **Tracking** feature | `com.android.algo.vidSot` |
| `libvideo_stabilization`, `libsmoothvideo` | **Video Stabilizer**, smooth slow motion | `com.android.videostabilize.VideoStabilize`, `com.camerasideas.smoothvideo` |
| `libauto_beat` | **Auto Beat** (music beat detection) | `com.android.autobeat.algo.AutoBeatAlgo` |
| `libaudio_silence_dt` | Silence / voice detection (AI Cut) | `com.android.audioaudilencedt` |
| `libfmod` | Audio effects: voice changer, equalizer | `com.android.autobeat`, `com.camerasideas.instashot.player.AudioEnhanceChannel` |
| `libai_retouch`, `libface_detect`, `libdouble_chin` | **AI Retouch** | `com.android.algo.aiRetouch`, `com.android.algo.doubleChin`, `com.android.algo.facedt` |
| `libinpaint` | **Eraser / object removal** (inpainting) | `com.android.inpaint`, `com.shantanu.aigc.remove` |

### 1.2 Java/Kotlin layer — seen

| Layer | Package | Scale | Role |
|---|---|---|---|
| UI | `com.camerasideas.instashot.fragment` | 1,475 classes | Every tool panel is a Fragment. `fragment.video` ≈120 screens, `fragment.image` ≈65, `fragment.common` ≈28 |
| Presentation | `com.camerasideas.mvp.presenter` | 474 classes | MVP contract layer |
| State | `com.camerasideas.instashot.store` | 120 classes | Remote store / marketplace / search |
| Graphics | `com.camerasideas.graphicproc` | ~120 classes | GPU graphics pipeline, `graphicsitems` layer tree |
| Filter framework | `com.inshot.graphics.{engine,layer,extension}` | ~350 filter classes | `IS*Filter` effect classes |
| Playback | `com.camerasideas.instashot.player` | 31 classes | `EditablePlayer`, `ICompositor`, `ISurfaceCreator`, `HWAudioDecoder/Encoder`, `AudioVolumeKeyframe` |
| Render/export | `com.camerasideas.instashot.videoengine` | `VideoEditor`, `MediaMuxer`, `EffectKeyframe` | Timeline render + mux |
| MVVM | `com.camerasideas.mvvm` | — | Used for the Stitch feature |
| Ad SDKs | `com.applovin`, `com.google.android.gms.ads`, `com.unity3d.services`, `com.moloco`, `com.inmobi`, `com.vungle`, `com.fyber` | 20+ manifest activities | AppLovin MAX is primary; 12-entry mediation waterfall in `local_ad_waterfall.json` |

### 1.3 AI: local vs cloud — the key architectural fact

`res/raw/local_ai_config.json` names six server-side model services. **AI features are remote jobs, not
on-device inference** (despite the MNN runtime in the 2.231 report, these particular pipelines are HTTP).

| `modelType` | Endpoint | Feature | Batch |
|---|---|---|---|
| `matting` | `https://inpaint.inshot.cc` | Cutout / background removal | no |
| `denoise` | `https://stt.inshot.cc` | AI Cut (silence + filler removal) | yes |
| `speech` | `https://stt.inshot.cc` | Auto Captions (speech-to-text) | yes |
| `translate` | `https://stt.inshot.cc` | Bilingual subtitles | yes |
| `flash` | `https://iqe.inshot.cc` | AI Enhance / AI Retouch | yes |
| `auto-beat` | `https://stt.inshot.cc` | Auto Beat | yes |

Model binaries are downloaded, not shipped: CDN `https://aicdn.inshot.cc/`, Firebase bucket `in2_ai_2025`,
upload via Firebase + service, download via CDN + Firebase (`local_ai_common_network_config.json`).

Job orchestration: `com.shantuanu.aiwork.startup.AiTaskInitializer`, `com.shantanu.aigc.remove`,
`com.shantanu.tts.service`, `com.android.algo.caption.Caption`.

Other hosts (`remote_config.json`): `https://inshot.cc`, `vip.inshot.cc` (billing),
`ad.myinstashot.com:8080` (ads), `https://www.tiktok.com/@inshot.app`.

### 1.4 Monetization

`com.android.billingclient` (permission `com.android.vending.BILLING`), `com.shantanu.iap` (with
`EmailSignInActivity`), `BindSubscribeFragment`, `SubscribeTableFragment`, `UpgradeFragment`,
`RemoveAdsFragment`, `FeatureSubscribeFragment`. Seasonal / festival / winback / regional-offer config
files drive pricing. `com.android.vending.CHECK_LICENSE` + `com.amazon.privacypass.ATTEST` for integrity.

---

## 2. Exact feature list (organised by editor screen)

Screen inventory is derived from the Fragment class names — these are the actual screens the user sees.

### 2.1 Video editor — `VideoEditActivity` (`com.camerasideas.instashot.fragment.video.*`)

**Clip tools**
- Trim (`VideoTrimFragment`) — frame-precise, multiple scales
- Split / cut sections (`VideoCutSectionFragment`, `VideoCutPickTimeFragment`, `VideoAudioCutFragment`)
- Replace / swap (`VideoSwapFragment`) — warns when the new clip is shorter
- Duplicate, Delete, Apply to all (`VideoApplyAllFragment`)
- Crop (`VideoCropFragment`), Rotate/flip, Position/zoom (`VideoPositionFragment`)
- Speed (`VideoSpeedFragment`) + **speed curve / ramping** (`VideoCurveSpeedFragment`, `CurveSpeedUtil`, `CurveSpeedView`)
- Normal speed (`VideoNormalSpeedFragment`), Freeze, Reverse (`ReverseFragment`)
- Alpha/opacity (`VideoAlphaFragment`), Mask (`VideoMaskFragment`), Blend (`VideoEffectScopeFragment`)

**Audio**
- Volume (`VideoVolumeFragment`), Fade in/out, Volume Balance, Denoise
- Voice Change (`VideoVoiceChangeFragment`), Voice Enhance (`VideoVoiceEnhanceFragment`)
- Equalizer (`AudioEqualizerFragment`, 11 presets), Rhythm / beat (`AudioRhythmFragment`)
- Record voice-over (`AudioRecordFragment`), Audio speed, Audio cut, Import/extract audio
- Music browser (`MusicBrowserFragment`, `MusicAlbumDetailFragment`, `FeaturedMusicListFragment`), search
- Sound effects (`SoundEffectListFragment`, `SoundEffectWallFragment`, `SoundEffectFavoriteFragment`, `SoundEffectDetailsFragment`)

**Look**
- Filters (`VideoFilterFragment`, `VideoFilterFragment2`) + Filter Manage (`FilterManageFragment`)
- Adjust, HSL (`VideoHslFragment`), Tone Curve (`VideoToneCurveFragment`, `ToneCurveView`)
- Effects (`VideoEffectFragment`) + Effect Scope (Main / PIP / All) + search + quick selection
- Background (`VideoBackgroundFragment`) — colour, pattern, blur, own photo
- Canvas / ratio — 13 ratios plus Instagram/TikTok/YouTube presets
- Mosaic (`MosaicEditFragment`) — pixelate, triangle, dot, blur, glass, magnifier
- Doodle (`VideoDoodleFragment`)

**Transitions & animation**
- Transitions (`VideoTransitionFragment`, `VideoTransitionLayout`) — with transition sound effects
- Clip animation (`VideoAnimationFragment`, `local_clip_animation.json` → 4 types) — in / out / loop
- Keyframe Ease — six independent tracks:
  `VideoKeyframeEaseFragment` (clip), `EffectKeyframeEaseFragment` (effect),
  `VideoStickerKeyframeEaseFragment` (material), `PipKeyframeEaseFragment` (PIP),
  `AudioVolumeKeyframe` (audio), filter keyframes (`clip_can_not_add_keyframe_desc` string)

**Layers**
- PIP (`VideoPiplineFragment`) — a **full parallel toolset**: trim, crop, speed, curve speed, reverse,
  volume, voice change, mask, chroma, blend, filter, HSL, tone curve, animation, keyframe ease, cutout, duration
- Text (`VideoTextFragment`) — font, colour, border/outline, shadow, glow, label, opacity, alignment,
  bend/curved text, width, batch edit (`VideoTextBatchEditFragment`)
- Text templates (`BaseTextTemplateFragment` + manage + collect)
- Stickers (`StickerFragment`, `HotStickerPanel`, `GifStickerFragment`, `BuddyStickerPanel`,
  `GoogleAnimatedEmojiStickerPanel`, `AnimationStickerPanel`) + cutout / eraser / outline / alpha / sound
- Audio stickers (`StickerSoundFragment`, `AudioStickerScrollView`)

**AI**
- AI Cut (`VideoAiCutFragment`, `VideoAiCutBatchEditFragment`) — remove silences & filler words, review segments
- Auto Captions (`VideoAutoCaptionFragment`) — STT, templates, bilingual, apply-to-all, PIP captions
- AI Speech / TTS (`TtsFilterVoicesFragment`, `TtsLanguagesFragment`)
- AI Enhance (`VideoEnhanceCutFragment`, `VideoEnhanceEditFragment`) — up to 10 min
- AI Effects (`VideoAIEffectFragment`) — face, mood, emotion, celebrate, flair
- Cutout (`VideoCutoutFragment`) — Basic + Precise
- Tracking (`VideoTrackingFragment`, `TrackingGuideFragment`) — sticker/text follows an object
- Video Stabilizer (`VideoStabilizeFragment`) — Low / Standard / Most Stable

**Project**
- Timeline (`VideoTimelineFragment`), Track view (`VideoTrackFragment`), Drafts (`VideoDraftFragment`,
  `DiscardDraftFragment`), Preview (`VideoPreviewFragment`), Help/QA, Export quality
  (`VideoChooseQualityFragment`), Save (`VideoSaveClientFragment`)

### 2.2 Photo editor — `ImageEditActivity` (`com.camerasideas.instashot.fragment.image.*`)

Filter, Adjust, HSL, Tone Curve, Ratio, Background (`ImageBackgroundFragment`) / Replace BG
(`ImageReplaceBgFragment`, `ReplaceBgBasicFragment`, `ReplaceBgGridFragment`), Border, Frame,
Layout templates (`ImageLayoutTemplateFragment`), Text (full styling set: font/colour/border/glow/
shadow/label/opacity), Stickers, Doodle, **Eraser** (`ImageEraserFragment`), **AI Retouch**
(`portrait_ai_retouch`), **AI Effect** (`ImageAIEffectFragment`), **AI Light**
(`ImageAiLightFragment` — backlight / hair glow / flash), **Enhance** (`ImageEnhanceFragment`),
**Cutout** (`ImageCutoutFragment`, `PhotoCutoutWrapperFragment`), **Edge Blend**
(`ImageEdgeBlendFragment`), PIP, **Collage** (`ImageCollageFragment`, `CollageTypeSelectFragment`)

### 2.3 Collage & Stitch
- Collage — 1–20 photos, layout templates, freestyle collage, AI Blend, swap photos by long-press
- Stitch (`StitchActivity`, `StitchTextFragment`) — photo stitching + captions collage

### 2.4 Other surfaces
- Templates (`VideoInMeloTemplateFragment`) — "2000+ Video Templates", inMelo SDK (`inshot_to_inmelo_config.json`)
- App wall / cross-promotion (`com.camerasideas.appwall`, `PeachyRecommendFragment`)
- Help & tutorials (`local_helptip_config.json`, 58 tips; `local_app_help_functions.json`),
  What's New (`local_app_new_features_v3.json`, 38 entries), Ideas (`ideas_info_config.json`, 61 tags /
  199 iframes), Feedback, Settings, Language picker
- Store: stickers, music, sound effects, font styles, templates (`store_config_android_4.json`)

---

## 3. Bundled asset catalogue (exact counts)

Read from `res/raw/*.json` — these are what ships *inside* the APK.

| Catalogue | Count | Detail |
|---|---|---|
| Filters | **122** in 26 packs | FEATURE, LUNAR, SKIN, JADE, FOODIE, CLEAR_LUT, BRIGHT, RETRO_LUT, MOSS, BARN, FESTIVE, MOODY, CREAM, MINT, AMBER, DREAMY, AURA, DUO, FRESH, FILM, PLUM, BREEZE, SPOT, COSY, NEON, CINEMA |
| Effects | **264** in 36 packs | basic, glitch, fade, film, blur, beats, shake, twist, analog, split, retro, lens, bling, glass, netting, weather, vibrate, stylize, celebrate, style, distort, line, clone, glow, flair, dizzy, doodle, magic, face, mood, emotion … |
| Transitions | **140** in 15 packs | basic, super1, film, glitch, shake, space3d, geometric, blur, fold, distort, paint, page, dissolve, slice, light |
| TTS voices | **245** | 17 languages, 40 tag groups, 7 emotions (NORMAL, HAPPY, SURPRISED, ANGRY, FEARFUL, SAD, WHISPER) |
| TTS languages | 17 | English, Português, Español, Français, Italiano, हिंदी, 日本語, 한국어, Russian, German, ไทย, Tiếng Việt, Arabic, Bahasa Indonesia, Urdu, Bengali, Chinese |
| Text templates | **111** in 5 groups | plus 11 caption templates |
| Fonts | 16 | Roboto (5 weights), PermanentMarker, BEBAS, Aileron, CaviarDreams, Aleo, AmaticSC, Daniel, Champagne & Limousines, AngelFace, PUSAB, Lobster, Blackout (2) |
| Music tracks | 75 | 4 styles, 12 posters, tags |
| Sound effects | 26 | 5 styles, 18 hot, trending list |
| Clip materials | **480** in 10 styles | intro, outro, transition, atmosphere, background, social, greenscreen, emptyShot, picture, festive |
| PIP blend modes | 11 | Normal, Lighten, Screen, Dodge, Darken, Multiply, Overlay, Hard, Isolate, Contrast, Divide |
| Masks | 11 shapes | feather + invert editing |
| Mosaic | 5 shapes × 6 types | square/round/heart/oval/drop × mosaic/triangle/dot/blur/glass/magnifier |
| Doodle | 7 packs | pen/marker/neon styles |
| Equalizer | 11 presets | |
| Voice change | 27 | 14 "character" + 13 "hot" |
| Image layouts | 20 (+9 freestyle, +11 blend) | |
| Pattern / colour / curve / HSL | 36 / 4 / 6 / 3 packs | gradient, modern, classic, morandi |
| Sticker shape packs | 36 | + 32 Buddy Emoji, 6 Google animated emoji, 13 aniemoji packs |
| Locales | **40** | |
| Pro features | 16 | premium feature list for the paywall |

---

## 4. Class-level confirmations (selected)

`search-android-classes` (JADX provider) — every feature above is backed by real classes, e.g.

- Chroma → `VideoChromaFragment`, `PipChromaFragment`
- Keyframe → `KeyframeEaseAdapter`, `EffectKeyframeEaseFragment`, `PipKeyframeEaseFragment`,
  `VideoKeyframeEaseFragment`, `VideoStickerKeyframeEaseFragment`, `AudioVolumeKeyframe`, `EffectKeyframe`
- Cutout → `CutoutTask`, `ImageCutoutFragment`, `VideoCutoutFragment`, `PipCutoutFragment`
- Stabilizer → `com.android.videostabilize.VideoStabilize`, `VideoStabilizeFragment`
- Auto Beat → `com.android.autobeat.algo.AutoBeatAlgo`
- Teleprompter → `TeleprompterAdjustFragment`, `TeleprompterHighlightTextView`
- Tracking → `VideoTrackingFragment`, `TrackingGuideFragment`
- Mosaic → `MosaicEditFragment`, `MosaicShapeAdapter`, `MosaicTypeAdapter`
- Stitch → `StitchActivity`, `StitchEditService`, `StitchTextFragment`

---

## 5. Permissions (30)

Media: `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, `READ_MEDIA_AUDIO`,
`READ_MEDIA_VISUAL_USER_SELECTED`, `READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE`,
`com.google.android.apps.photos.permission.GOOGLE_PHOTOS`
Network: `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `AD_ID`,
`ACCESS_ADSERVICES_ATTRIBUTION`, `ACCESS_ADSERVICES_ID`, `ACCESS_ADSERVICES_TOPICS`
Background: `WAKE_LOCK`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`,
`FOREGROUND_SERVICE_MEDIA_PROCESSING`, `POST_NOTIFICATIONS`, `VIBRATE`
Capture: `RECORD_AUDIO`
Billing/auth: `com.android.vending.BILLING`, `com.android.vending.CHECK_LICENSE`,
`com.google.android.c2dm.permission.RECEIVE`, `BIND_GET_INSTALL_REFERRER_SERVICE`,
`READ_GSERVICES`, `com.amazon.privacypass.ATTEST`, `USE_BIOMETRIC`, `USE_FINGERPRINT`
Own: `com.camerasideas.instashot.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`

---

## 6. Not determined by this analysis

- **Runtime behaviour** — nothing was executed.
- **Which features are Pro-gated** — the paywall strings reveal *some* gating (`caption_feature_description`,
  `exclusive_transitions`, `features_no_watermark`, `inmelo_templates_nums`) but the entitlement model
  itself is server-side.
- **Native library internals** — not in this artifact (§1.1).
- **Server endpoint shapes and API contracts** — hosts and model types are known; request/response
  schemas are not.
- **Quality/accuracy of the AI features** — the models live server-side.
- **APK signature** — not verified (`signature_verification: not_performed`).

---

## 7. Delta vs the 2.231 report

The in-app What's New feed (`local_app_new_features_v3.json`, 38 entries) for this build:

> Flair Effects · Video Stabilizer · Lens Effects · Transitions-Blur · Freestyle Collage · AI Speech ·
> Audio Sticker · Transitions-Shake · Photo Enhance · Photo Remove · AI Cut · 3D Space Transitions ·
> Captions Templates · Glass Effects · Dizzy Effects · Captions Stitch · Bling Effects · Blur Effects ·
> Bilingual · Photo AI Light · Transitions-Geometric · Teleprompter · Face Effects · New Filter Packs ·
> Text Template · Photo AI Retouch · Photo Face Editor

**Caveat:** this feed is a rolling promotion list, not a version diff — Teleprompter, Video Stabilizer and
AI Speech were already present in 2.231. The genuinely new-in-2.243 items, confirmed absent from the 2.231
report but present in this build's code and config:

- **Effect packs**: `flair`, `lens`, `glass`, `dizzy`, `twist`, `weather`, `vibrate`, `netting`
- **Transition packs**: `shake`, `paint`, `page`, `dissolve`, `slice`, `light`, `super1`, `glitch`
- **Photo AI Light** (backlight / hair glow / flash) and **Photo Face Editor**
- **Bilingual subtitles** — 2.231 described bilingual captions, but the 2.243 build exposes an explicit
  `translate` model type at `stt.inshot.cc` and a `Bilingual` What's New entry
- **Audio sticker** volume control, **Photo Remove** ("Basic Remove" / "Advanced Remove" magic brush)

Also newly observable in 2.243 that the 2.231 report did not cover: the **AI model-type split**
(§1.3), the **40-locale** string table, the **ad mediation waterfall**, and the **Pro feature list**
(`local_premium_features.json`, 16 entries).

---

## 8. Build order for a clone (value vs effort)

Reusable from this report, for a browser or native clone:

1. **Timeline + clip ops** (trim, split, reorder, speed, freeze, reverse, volume) — the foundation everything else sits on.
2. **Look pipeline** (filter LUT, adjust, HSL, tone curve, vignette/sharpen) — highest perceived polish per unit of work.
3. **Text + sticker layer** (outline/shadow/glow/label, animation in/out/loop, keyframe ease).
4. **Transitions** (140 InShot packs → your own set).
5. **Audio** (music, SFX, beat markers, EQ, voice change, voice-over recording, teleprompter).
6. **Mosaic/blur, mask, blend, chroma, PIP** — layer compositing.
7. **Collage / Stitch / Photo editor** — separate surfaces, do last.
8. **Server-backed AI** (captions, TTS, AI Cut, enhance, cutout) — only worth it with a backend.

**Do not copy**: InShot's LUT tables, fonts, stickers, music, Lottie assets or transition packs — they are
their copyrighted assets. Build your own.
