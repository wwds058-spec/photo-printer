# Architecture

## The one rule

**Preview, PDF and print are all derived from the same `SheetLayout`** (millimetres, origin
top-left). Nothing downstream recomputes positions.

```
LayoutRequest ──LayoutEngine──▶ LayoutPlan ─▶ SheetLayout (mm) ─┬─▶ PreviewTransform  (px)   on-screen
                                                                 ├─▶ PdfPageSpec       (pt)   PDF file
                                                                 └─▶ PrintMediaSpec    (mils) Android print job
```

The three conversions are linear scalings, and `SheetGeometryConsistencyTest` converts each back to
millimetres and requires the originals. View zoom changes only `PreviewTransform.pxPerMm`, never the
layout.

The PDF is written by `PdfSheetWriter` (pure Kotlin, in `:domain`), not by Android's `PdfDocument`, because
`PdfDocument.PageInfo` only accepts whole points (up to ~0.18 mm page error) while a PDF `MediaBox` takes real
numbers. The Android print job will print the *same PDF* produced for "Save PDF" (via a
`PrintDocumentAdapter`), so print cannot diverge from the PDF either.

## Layout Engine (`domain/layout`)

- Input: `LayoutRequest` (paper, photo, margins, spacing, rotation allowed, fill strategy, cut lines).
- For each allowed orientation: `columns = floor((usable + gap) / (size + gap))`, same for rows.
  Keeps the orientation with more photos; ties go to upright. A 1e-6 mm tolerance absorbs float
  error (e.g. `6 × 25.4`).
- The grid is centred in the area inside the margins. All sheets share one grid, so slot positions
  match across sheets and the last sheet fills slots in reading order.
- Output: `LayoutPlan` — grid (orientation, columns × rows, origin, unused space), and one
  `SheetLayout` per sheet with exact mm coordinates.
- Failures are typed (`LayoutError`), never exceptions; the UI maps them to friendly text.
- Auto Fill Paper = `FillStrategy.FillSheets`.
- No counts are hard-coded: e.g. 35×45 on 102×152 gives 6 upright or 8 rotated, by calculation.

### Cutting guides
Never cross a photo. With spacing > 0: a full line down the middle of each gap. Always: short
ticks *outside* the grid at every photo edge. If the grid fills the paper there is no room for
ticks and none are drawn.

### Known limits (deliberate, documented)
- One orientation per sheet (no mixed packing of leftover strips).
- Actual-size printing depends on the printer service. The print dialog may offer "fit to page";
  the app can warn but cannot force it off. Android's public API exposes limited settings, so
  unsupported options are hidden/disabled in the UI rather than faked.

## PDF export (`domain/pdf`)

- One page per sheet; `MediaBox` = exact paper size (e.g. 102 mm = 289.1339 pt, unrounded).
- JPEGs are embedded byte-for-byte (`DCTDecode`), shared across frames; no recompression.
- Each frame clips its image: crop/zoom/pan/rotate move the *image*, never the frame. The image
  always covers the frame (zoom < 1 and over-panning are clamped, so no blank edges).
- Placement rotation (engine turned the photo 90° to fit more) and user rotation compose; both are clockwise.
- `/ViewerPreferences /PrintScaling /None` asks viewers to print at 100 %.
- Output is deterministic (no timestamps). A missing image throws `MissingImageException` before any byte is written.
- `ImageFit.effectiveDpi` gives the real source-pixels-per-inch at the chosen crop/zoom
  (≥ 300 good, ≥ 200 acceptable, else low) for the pre-print quality warning.
- The app passes the editor's output as a JPEG per photo. Brightness/contrast are baked into that
  JPEG by the Android side (re-encode at high quality) — the writer itself does not alter pixels.
- Tests render the PDF with PDFBox at 254 dpi (10 px = 1 mm) and measure where colours land.

## Projects and templates (`domain/model/Project.kt`, `domain/project`)

- `PrintProject` = settings + photo *references* (`ProjectPhoto.sourceRef`), never pixels. Image files
  live in app-private storage; Room (app module, later) stores only this metadata.
- `PrintProject.toLayoutRequest()` is the only place a project becomes a layout, including each photo's
  own crop/zoom/pan, so editing one photo in the preview carries through to the PDF.
- `PrintTemplate` stores layout settings only. Built-ins: Passport 35×45→4×6, ID 25×35→4×6, 2×2→4×6,
  Passport→A4. User templates ("My Studio Template") use `TemplateRepository`.
- `ProjectService`: create-from-template, rename (trimmed, 1–80 chars), duplicate, autosave-style
  `update` (cannot change id/created time; bumps modified time), delete. IDs and clock are injected.
- Repositories are interfaces with `Flow`; `InMemoryProjectRepository` serves tests/previews.

## Printing rules (`domain/printing`)

- `PrinterCapabilities` distinguishes `SUPPORTED` / `UNSUPPORTED` / `UNKNOWN`; the UI hides or disables
  settings accordingly instead of pretending every printer supports everything.
- `PrintReadiness.evaluate` builds the READY TO PRINT report as *structured* items (no English):
  only verified facts are `OK`. Unconfirmed actual-size scaling, unconfirmed borderless, a busy or
  unknown-state printer, paper not in the printer's reported list, and low-resolution photos are
  `WARNING`; no printer, an offline printer, or an impossible layout are `BLOCKER`.
  "Fit to page" is a warning that sizes will change, not a silent pass.
- `PrintReadiness.testSheet` = the first sheet of the real layout ("Print 1 test copy").
- `CalibrationPage` builds a PDF page with 100 mm and 50 mm rulers and the selected photo frame at true
  size (omitted, never scaled, if it does not fit beside the rulers). `ScaleDiagnosis` turns a
  measured length into a scale percentage and a verdict (within 0.5 % counts as accurate).

## Presentation logic (`:presentation`, pure Kotlin)

- `PrintSession` holds everything the user has chosen and recomputes the layout on every change, so the preview,
  PDF and print read one plan. Screens never keep their own copy, so going back a step loses nothing.
- `AppController` is the app's ViewModel in pure Kotlin: navigation, saving, export, printing, projects, templates,
  settings. It talks to the OS only through the small `PlatformGateway` interface and is tested with a fake one.
  Unexpected platform exceptions become a friendly message; the busy indicator is always cleared.
- Gesture maths (`CropEditing`, `SheetViewportMath`) is plain functions, tested against the same code the PDF uses:
  after a pinch the image point under the fingers has not moved in the printed frame; the editor's pan limits are
  `ImageFit.panLimits`, the same function `ImageFit.draw` clamps with.
- `NavStack` is a simple back stack rather than Jetpack Navigation Compose: Navigation Compose cannot be built or
  tested in the authoring environment, and because the work lives in `PrintSession` the screens are stateless, so
  nothing needs the library's saved-state handling. It can be swapped later; routes are already a sealed type.

## Compose UI (`:ui`)

- Stateless screens: values and callbacks in, no logic. `PhotoPrintRoot` maps routes to screens, animates
  transitions by direction, shows bottom tabs on top-level destinations only, turns controller messages into
  snackbars and blocks input while a PDF is built.
- The preview draws with `ImageFit.draw`, the same function the PDF writer uses, and scales positions from the
  `SheetLayout` in millimetres, so the screen shows what will print. Zoom changes only the view scale.
- Status is never colour alone: every status has an icon and a word; controls have 48 dp targets and content
  descriptions; text is in `sp`. Gestures have non-gesture alternatives (zoom slider, rotate, reorder buttons).
- Compiled here with Compose Desktop, which has the same `androidx.compose.*` API; `:app` compiles the same files
  against androidx Compose (BOM 2024.12.01 ≈ Compose Multiplatform 1.7.3).

## Android module (`:app`)

- Hilt `AppViewModel` holds the `AppController`; `MainActivity` registers the system pickers and forwards Back.
- No `INTERNET`, storage or camera permission. Photos are copied into app-private storage on import.
- `AndroidPlatformGateway`: photo picker, camera (TakePicture), export (create-document, share sheet), PDF to PNG
  (PdfRenderer), and printing through `PrintManager` with a `PrintDocumentAdapter` that streams the same PDF.
- An untouched RGB/grey JPEG is embedded byte for byte; anything else (EXIF rotation, brightness/contrast, PNG,
  CMYK) is decoded at full resolution and re-encoded at quality 95.

### What Android's public print API does not allow (so the UI does not pretend)

- **No printer list.** Apps cannot enumerate printers; the system print dialog shows them. The printer screen says
  so, offers *Add printer* (system print settings), and the final check shows "you will choose it in the print
  dialog" as neutral information, never as a green check.
- **Copies, quality, borderless, actual size are hints at best.** The print dialog and the print service have the
  last word, so the app shows the warning "avoid Fit to Page" and offers the calibration page to measure the result.
- A *Copies of the whole job* control is not shown, because it could not be applied.

## Not yet done

Room/DataStore persistence, the physical-measurement pass on real printers, language switching, a sharpness
adjustment, background options, screenshot tests (need the Compose runtime, whose desktop artifacts depend on Google
Maven), and a review of the Android code on a real build.
