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

## Android module
- Compose + Material 3 + Hilt. `LayoutEngine` is plain Kotlin and provided by `di/DomainModule`.
- Design tokens: `core/ui/theme` (colours, type in `sp`, shapes, spacing, 48 dp touch targets).
- No `INTERNET` permission: photos never leave the device unless the user shares/prints them.
