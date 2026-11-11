# Movexa — MVP Build Plan (Kotlin Multiplatform)

Companion to [`PRODUCT_SPEC.md`](./PRODUCT_SPEC.md). This file is the engineering plan: module layout, screen-by-screen build order, and phase-by-phase acceptance criteria so the app can be built incrementally without ever being in a broken state.

> **Status note (rebuild).** The plan below describes the original phased build.
> The app has since been rebuilt against the real InShot 2.243 UI — see
> [`UI-REFERENCE.md`](./UI-REFERENCE.md) for the captured reference screens and
> [`ARCHITECTURE.md`](./ARCHITECTURE.md) for how the code is actually organised
> today. Where the two differ, `ARCHITECTURE.md` wins: the original plan
> called for 13 Gradle modules and a `SQLDelight` draft store, and the shipped
> code uses a single module with packages and an in-memory session instead.
> [`INSHOT-2.243-FEATURE-REPORT.md`](./INSHOT-2.243-FEATURE-REPORT.md) is the
> feature inventory of the reference app.

## 1. Repo / module layout

```
snapzy/
├── androidApp/                      # Android entry point (Activity, manifest, Play-specific glue)
├── iosApp/                          # Xcode project wrapping the shared Compose UI framework
├── shared/
│   ├── core-designsystem/           # Color/type/shape tokens, shared composables (buttons, sheets, chips)
│   ├── core-navigation/             # Nav graph, screen route definitions
│   ├── core-data/                   # SQLDelight schema, project/draft repository, file storage helpers
│   ├── core-media-engine/           # expect/actual: trim, crop, speed, filters, export (Media3 / AVFoundation)
│   ├── core-gpu-effects/            # expect/actual: chroma key, custom shader filters, keyframe compositor
│   ├── feature-onboarding/
│   ├── feature-home/                # Projects / Create / Discover(stub) / Settings tabs
│   ├── feature-picker/              # Media picker (gallery grid, permissions)
│   ├── feature-editor-video/        # Video editor screen + all tool sub-panels
│   ├── feature-editor-photo/        # Photo editor screen + tool sub-panels
│   ├── feature-collage/             # Collage layout builder
│   ├── feature-export/              # Export/render screen + share sheet hookup
│   ├── feature-comingsoon/          # The reusable "Coming Soon" sheet + gated-feature registry
│   └── feature-settings/            # Settings, About, Legal (static), Go Pro (visual-only)
└── docs/
    ├── PRODUCT_SPEC.md
    └── MVP_PLAN.md
```

**Rule:** no feature module depends on another feature module directly — they only depend on `core-*`. Cross-feature navigation goes through `core-navigation` route definitions, not direct references. This keeps each screen buildable/testable in isolation.

## 2. The gated-feature registry (how "Coming Soon" stays consistent)

One small shared piece of infra used everywhere in §4.2 of the spec, instead of hand-rolling a dialog per screen:

```kotlin
// core: feature-comingsoon
enum class GatedFeature(val title: String, val description: String, val icon: String) {
    AI_CUT("AI Cut", "Auto-remove silences and filler words from your videos.", "ic_ai_cut"),
    AI_ENHANCE("AI Enhance", "One-tap cloud upscaling for low-quality clips.", "ic_ai_enhance"),
    AI_ERASER("AI Eraser", "Remove unwanted objects or backgrounds automatically.", "ic_ai_eraser"),
    AUTO_CAPTIONS("Auto Captions", "Automatic speech-to-text captions.", "ic_captions"),
    VOICEOVER("AI Voiceover", "Turn text into natural speech narration.", "ic_voice"),
    MARKETPLACE("Marketplace", "Browse and download more stickers, music, and effects.", "ic_store"),
    TEMPLATES("Templates", "Trending edit templates, ready to remix.", "ic_template"),
    DISCOVER("Discover", "See what the community is creating.", "ic_discover"),
    GO_PRO("Movexa Pro", "Unlock premium filters, templates, and remove the watermark.", "ic_pro"),
    ACCOUNT("Account", "Sign in to back up projects and sync across devices.", "ic_account"),
}

// Usage from any screen:
onClick = { showComingSoon(GatedFeature.AI_ENHANCE) }
```

Adding a new gated feature later = one enum entry, zero new UI code. When a feature is actually built, delete its enum entry and wire the real screen — the call site doesn't change shape.

## 3. Build phases

Each phase ends with a fully runnable, crash-free app — later phases add capability, they never leave an earlier phase half-wired.

### Phase 0 — Skeleton (foundation)
- Gradle KMP project scaffold (`androidApp`, `iosApp`, `shared/*` modules), Compose Multiplatform wired on both targets
- `core-designsystem`: color tokens, typography, spacing, base components (button, chip, bottom sheet, segmented tabs) from §2 of the spec
- `core-navigation`: nav graph skeleton with every route stubbed as an empty screen (so the full app shell is clickable end-to-end on day one)
- Splash → Onboarding → Home shell (3 tabs + FAB) navigable
- **Acceptance:** app installs on Android + iOS, launches to Home, all 4 tabs switch, FAB opens the Create sheet (routes still stub screens)

### Phase 1 — Photo editor vertical slice
- `feature-picker`: gallery grid + permission request (single-select)
- `feature-editor-photo`: crop, rotate, filters (bundled pack), adjust sliders, text overlay, sticker overlay, draw overlay, undo/redo
- `core-media-engine` photo path (Android: `BitmapFactory`/`Canvas`/`ColorMatrix`; iOS: `CIFilter` pipeline)
- `feature-export`: render flattened image, save to gallery, OS share sheet
- **Acceptance:** user can pick a photo, apply crop+filter+text+sticker, export, and it saves correctly on both platforms

### Phase 2 — Video editor core
- `feature-picker` multi-select for video
- `core-media-engine` video path: trim/split, speed (with ramp), volume/mute, crop/aspect, filters/adjust reusing Phase 1's filter engine where possible
- `feature-editor-video`: timeline UI (clip thumbnails, drag to reorder, trim handles), bottom toolbar + sub-panels per tool
- Export: Android Media3 Transformer render pipeline; iOS AVFoundation `AVAssetExportSession`/`AVMutableComposition`
- **Acceptance:** multi-clip trim + reorder + speed + filter + export produces a correct playable file on both platforms

### Phase 3 — Advanced local video
- Text/sticker/draw overlays on video (reuse Phase 1 overlay components, now time-bound to a clip range)
- Transitions (bundled set) between clips
- PIP overlay clip
- Keyframe animation (position/scale/rotation/opacity) — `core-gpu-effects` compositor
- Chroma key — `core-gpu-effects` (OpenGL shader on Android, Core Image/Metal on iOS)
- `feature-collage`: layout templates, photo slot swap/reorder, hand-off into photo editor for final touches
- Blur/mosaic effect
- Local autosave/draft system (`core-data` + SQLDelight), Projects tab lists real drafts
- **Acceptance:** every local feature from PRODUCT_SPEC §4.1 is functional; a project can be closed mid-edit and resumed exactly where it left off

### Phase 4 — Coming Soon integration, Settings, polish
- `feature-comingsoon`: the registry + standardized sheet from §2
- Wire every §4.2 entry point (AI tools in video/photo toolbars, Discover tab, Templates, Marketplace search, Go Pro screen, Account in Settings) to open the sheet
- `feature-settings`: appearance (light/dark), language picker (static list, no backend), about/legal (static text), cache clear
- Go Pro screen: visual paywall mockup (plan cards, feature comparison) — "Subscribe" button opens Coming Soon sheet instead of real billing
- App icon, splash animation, empty-state illustrations, error states, loading skeletons
- **Acceptance:** no dead taps anywhere in the app — every icon either works (local feature) or opens the Coming Soon sheet; no crashes, no unstyled/default-Compose-theme screens left

## 4. What ships in v1 vs. what's a visible promise

| | v1 behavior |
|---|---|
| Local editing features (§4.1) | Fully working |
| AI features, Marketplace, Discover, Templates | Visible in UI, open Coming Soon sheet |
| Go Pro | Visual paywall, no real purchase |
| Accounts/sync | Not present beyond a Settings entry point → Coming Soon |

### 4.1 As built

| Area | Shipped |
|---|---|
| Media | Real device gallery (MediaStore / PhotoKit), multi-select up to 9, runtime permission flow with OS-picker fallback |
| Video editor | Multi-clip timeline pinned to the bottom of the screen, trim scrubber, split / duplicate / delete, speed, volume + mute, filter, adjust, text, sticker, draw, canvas ratio, background, transition, mask, blend, music, sound effects, apply-to-all, undo/redo |
| Photo editor | Filter, adjust, text, sticker, draw, eraser, canvas ratio, background, border / frame / layout, cutout placeholder, PIP placeholder |
| Export | Real Media3 render: trim, speed, mute, gain, rotate/flip, brightness/contrast/HSL/RGB grading, text + sticker overlays, 720p / 1080p / 4K. Photo export flattens the composition to JPEG. |
| Gated | Every server-backed feature opens the shared Coming Soon sheet from one `GatedFeature` enum |

### 4.2 Known gaps

- **Video colour grading is not shown in the preview.** `VideoPlayerSurface` is an `AndroidView` (ExoPlayer), so a Compose `ColorFilter` cannot tint video pixels. The export *does* apply the grade.
- **PIP layers are not exported** — a PIP overlay needs a second decoder in the Media3 graph.
- **Video transitions are stored but not rendered** — Media3 1.5 has no transition primitive.
- **iOS export applies trim and mute only** — speed and overlay compositing are Android-only so far.
- **No draft persistence.** Projects are in-memory for the session; the Projects tab shows an empty state.


## 5. Suggested build order inside each phase

1. Data/state layer first (models, repository, fake/in-memory impl if the real one isn't ready)
2. UI shell with hardcoded/sample data
3. Wire real data layer in
4. Platform-specific `actual` implementations (Android first, then iOS, or in parallel if two people)
5. Manual pass on both platforms before moving to the next phase
