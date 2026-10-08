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

The Android print job will print the *same PDF* produced for "Save PDF" (via a
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
- `PdfDocument.PageInfo` takes whole points, so the PDF page box can differ from the paper by up
  to ~0.18 mm (`PdfPageSpec.pageWidthRoundingErrorMm`). Frames are drawn with float precision.
  The phase-11 calibration page will measure the real end-to-end effect per printer.
- Actual-size printing depends on the printer service. The print dialog may offer "fit to page";
  the app can warn but cannot force it off. Android's public API exposes limited settings, so
  unsupported options are hidden/disabled in the UI rather than faked.

## Android module
- Compose + Material 3 + Hilt. `LayoutEngine` is plain Kotlin and provided by `di/DomainModule`.
- Design tokens: `core/ui/theme` (colours, type in `sp`, shapes, spacing, 48 dp touch targets).
- No `INTERNET` permission: photos never leave the device unless the user shares/prints them.
