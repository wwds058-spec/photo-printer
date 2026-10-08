package com.photoprint.pro.ui

import com.photoprint.pro.domain.layout.LayoutError
import com.photoprint.pro.domain.pdf.ImageQuality
import com.photoprint.pro.domain.printing.PrinterStatus
import com.photoprint.pro.domain.printing.ReadinessItem
import com.photoprint.pro.presentation.app.MessageKind
import com.photoprint.pro.presentation.format.Formatting
import com.photoprint.pro.presentation.layout.Suggestion

/**
 * Every user-facing string. English for now; this is the single seam to replace with localised
 * resources. Messages are written to say what happened and what to try next.
 */
object Strings {
    const val appName = "PHOTOPrint Pro"
    const val tagline = "Perfect Photos. Perfect Prints."

    // Navigation
    const val tabHome = "Home"
    const val tabProjects = "Projects"
    const val tabTemplates = "Templates"
    const val tabSettings = "Settings"
    const val back = "Back"
    const val close = "Close"
    const val cancel = "Cancel"
    const val ok = "OK"
    const val apply = "Apply"
    const val delete = "Delete"
    const val rename = "Rename"
    const val duplicate = "Duplicate"
    const val open = "Open"
    const val reprint = "Reprint"
    const val more = "More options"
    const val save = "Save"

    // Home
    const val newPrint = "New print"
    const val newPrintSubtitle = "Select photos, choose a size and print easily."
    const val quickPrint = "Quick print"
    const val recentProjects = "Recent projects"
    const val seeAll = "See all"
    const val noProjectsYet = "Your saved prints will appear here."
    const val settingsIcon = "Settings"

    fun quickPrintName(id: String) = when (id) {
        "passport" -> "Passport"
        "id" -> "ID photo"
        "2x2" -> "2 × 2 inch"
        else -> "4 × 6 photo"
    }

    // Steps
    fun stepOf(index: Int, total: Int) = "Step ${index + 1} of $total"

    // Photo selection
    const val selectPhotosTitle = "Select photos"
    const val fromGallery = "Gallery"
    const val fromCamera = "Camera"
    const val addMorePhotos = "Add photos"
    const val noPhotosHint = "Choose photos from your gallery or take a new one."
    const val removePhoto = "Remove photo"
    const val moveEarlier = "Move earlier"
    const val moveLater = "Move later"
    const val editPhoto = "Edit photo"
    const val next = "Next"
    const val photoOrder = "Photos print in this order."
    fun thumbDescription(position: Int, total: Int) = "Photo $position of $total"

    // Editor
    const val editorTitle = "Edit photo"
    const val rotate = "Rotate"
    const val reset = "Reset"
    const val replace = "Replace"
    const val zoom = "Zoom"
    const val brightness = "Brightness"
    const val contrast = "Contrast"
    const val adjust = "Adjust"
    const val aspect = "Crop shape"
    const val customAspect = "Custom"
    const val editorHint = "Pinch to zoom, drag to move."
    const val frameStaysFixed = "The frame keeps its physical size. You are only changing the picture inside it."
    fun frameFixed(size: String) = "Frame stays $size"
    const val width = "Width"
    const val height = "Height"
    const val editPlacementTitle = "Edit photo in sheet"

    // Sizes
    const val photoSizeTitle = "Photo size"
    const val paperSizeTitle = "Paper size"
    const val popular = "Popular"
    const val international = "International"
    const val custom = "Custom"
    const val photoPaper = "Photo paper"
    const val standardPaper = "Standard paper"
    const val customSize = "Custom size"
    const val unit = "Unit"
    const val useThisSize = "Use this size"
    const val sizePresetDisclaimer = "These are common starting sizes, not a guarantee that a document will accept them. Always check the requirements of the document you are applying for."
    const val mostCommon = "Most common"
    fun fieldError(e: com.photoprint.pro.presentation.input.SizeFieldError, min: String, max: String) = when (e) {
        com.photoprint.pro.presentation.input.SizeFieldError.EMPTY -> "Enter a number."
        com.photoprint.pro.presentation.input.SizeFieldError.NOT_A_NUMBER -> "That is not a number."
        com.photoprint.pro.presentation.input.SizeFieldError.TOO_SMALL -> "Too small. The minimum is $min."
        com.photoprint.pro.presentation.input.SizeFieldError.TOO_LARGE -> "Too large. The maximum is $max."
    }

    // Layout settings
    const val layoutTitle = "Layout"
    const val copies = "Copies"
    const val copiesPerPhoto = "Copies of each photo"
    const val autoFill = "Auto fill paper"
    const val autoFillHint = "Fill whole sheets with your photos."
    const val sheetsToFill = "Sheets to fill"
    const val spacing = "Spacing between photos"
    const val margins = "Paper margins"
    const val autoRotate = "Auto rotate for better fit"
    const val autoRotateHint = "Turns photos 90° when that fits more on a sheet."
    const val cutLines = "Show cutting lines"
    const val cutLinesHint = "Thin guides in the gaps and margins. They never cross a photo."
    const val preview = "Preview"
    const val increase = "Increase"
    const val decrease = "Decrease"
    const val estimate = "Estimated layout"
    fun perSheet(n: Int) = if (n == 1) "1 photo per sheet" else "$n photos per sheet"
    fun grid(columns: Int, rows: Int) = "$columns columns × $rows rows"
    fun totals(photos: Int, sheets: Int) = "${Formatting.photos(photos)} · ${Formatting.sheets(sheets)} required"
    const val photosTurned = "Photos are turned 90° to fit more on each sheet."
    fun lastSheet(n: Int) = "Last sheet holds $n."

    fun layoutProblem(e: LayoutError): String = when (e) {
        is LayoutError.InvalidPaper -> "The paper size is not valid. Enter a width and height greater than zero."
        is LayoutError.InvalidPhoto -> "The photo size is not valid. Photos must be at least 1 mm on each side."
        LayoutError.InvalidMargin -> "A margin is not valid. Margins cannot be negative."
        LayoutError.InvalidSpacing -> "The spacing is not valid. Spacing cannot be negative."
        LayoutError.NoPhotos -> "Add at least one photo to see the layout."
        LayoutError.InvalidCopies -> "Choose at least one copy."
        is LayoutError.TooManyPhotos -> "That is more than ${e.limit} photos. Try fewer copies."
        is LayoutError.MarginsConsumePaper -> "The margins leave no room to print. Try reducing the margins or choosing a larger paper."
        is LayoutError.PhotoDoesNotFit ->
            "These settings cannot fit even one photo on the selected paper. Try reducing the margin or choosing a larger paper."
    }

    fun suggestion(s: Suggestion) = when (s) {
        Suggestion.REDUCE_MARGIN -> "Reduce the margins"
        Suggestion.REDUCE_SPACING -> "Reduce the spacing"
        Suggestion.LARGER_PAPER -> "Choose a larger paper"
        Suggestion.SMALLER_PHOTO -> "Choose a smaller photo size"
        Suggestion.ALLOW_ROTATION -> "Turn on Auto rotate"
        Suggestion.CHECK_VALUES -> "Check the sizes you entered"
        Suggestion.ADD_PHOTOS -> "Add photos"
        Suggestion.FEWER_COPIES -> "Use fewer copies or fewer sheets"
    }

    // Preview
    const val previewTitle = "Print preview"
    const val fitToScreen = "Fit"
    const val zoomIn = "Zoom in"
    const val zoomOut = "Zoom out"
    const val previousSheet = "Previous sheet"
    const val nextSheet = "Next sheet"
    const val editLayout = "Layout"
    const val reorder = "Reorder"
    const val doneReordering = "Done"
    const val print = "Print"
    const val savePdf = "Save PDF"
    const val sharePdf = "Share PDF"
    const val saveImage = "Save image"
    const val shareImage = "Share image"
    const val exportMenu = "Save or share"
    const val tapPhotoHint = "Tap a photo to edit it. Pinch to look closer; this does not change the print size."
    const val sheetCanvas = "Print sheet preview"
    fun sheetDescription(sheet: String, paper: String, photos: Int) = "$sheet. Paper $paper. $photos photos on this sheet."
    fun viewZoomNote(percent: String) = "Viewing at $percent. Zoom only changes how large the sheet looks on screen."
    fun photoInfo(photo: String, paper: String) = "Photo $photo · Paper $paper"

    // Printers
    const val printersTitle = "Printer"
    const val refresh = "Refresh"
    const val addPrinter = "Add printer"
    const val continueLabel = "Continue"
    const val searching = "Looking for printers…"
    const val chooseInPrintDialog = "Choose your printer in the next step"
    const val systemDialogExplain = "Android shows the available printers, and their status, in its own print dialog when you print. This app can't list them in advance. If your printer is not there, use Add printer to set it up."
    const val noPrintService = "No print service found. Use Add printer to install or enable one for your printer."
    const val printerListFailed = "Couldn't look for printers."
    const val tryAgain = "Try again"
    const val selected = "Selected"
    fun printerStatus(s: PrinterStatus) = when (s) {
        PrinterStatus.READY -> "Ready"
        PrinterStatus.BUSY -> "Busy"
        PrinterStatus.OFFLINE -> "Offline"
        PrinterStatus.UNKNOWN -> "Status unknown"
    }

    // Print settings
    const val printSettingsTitle = "Print settings"
    const val paper = "Paper size"
    const val quality = "Print quality"
    const val color = "Color"
    const val colorMode = "Color"
    const val blackAndWhite = "Black & white"
    const val borderless = "Borderless"
    const val borderlessMayLeaveMargins = "Your selected printer may leave printable margins."
    const val borderlessUnsupported = "Your printer reports that it can't print borderless, so margins may remain."
    const val scaling = "Scaling"
    const val actualSize = "Actual size (100%)"
    const val fitToPage = "Fit to page"
    const val scalingWarning = "For accurate physical dimensions, avoid Fit to Page / Shrink to Fit if your printer dialog provides those options."
    const val fitToPageWarning = "Fit to page changes the size of everything on the sheet, so the photos will not print at their chosen size."
    const val orientation = "Orientation"
    const val orientationFollowsPaper = "Follows the paper"
    const val hiddenSettingsNote = "Android lets apps suggest these settings; the print dialog and your printer have the final say. Paper type, and how many copies of the whole job to print, are chosen in the print dialog."
    const val draft = "Draft"
    const val normal = "Normal"
    const val best = "Best"

    // Final check
    const val finalCheckTitle = "Final check"
    const val readyToPrint = "Ready to print"
    const val notReadyToPrint = "Not ready to print"
    const val readyWithWarnings = "Ready, with notes"
    const val testPrint = "Test print"
    const val testPrintHint = "Prints 1 sheet so you can measure it before printing the whole batch."
    const val printNow = "Print"
    const val calibrationLink = "Check printer scaling with a calibration page"
    fun readiness(item: ReadinessItem): String = when (item) {
        is ReadinessItem.LayoutFailed -> layoutProblem(item.error)
        is ReadinessItem.Photo -> "Photo: ${Formatting.size(item.size.widthMm, item.size.heightMm)}"
        is ReadinessItem.Paper -> "Paper: ${Formatting.size(item.size.widthMm, item.size.heightMm)}"
        is ReadinessItem.Copies -> "Copies: ${item.photos} · Sheets: ${item.sheets} (${item.photosPerSheet} per sheet)"
        ReadinessItem.NoPrinter -> "No printer selected."
        ReadinessItem.PrinterChosenInPrintDialog -> "Printer: you will choose it in the Android print dialog."
        is ReadinessItem.Printer -> "Printer: ${item.printer.name} · ${printerStatus(item.printer.status)}"
        is ReadinessItem.PaperNotAdvertised -> "This printer doesn't list ${item.paper.name} as available paper. You may need to select it in the print dialog."
        ReadinessItem.ScalingActualSize -> "Scaling: 100% / Actual size"
        ReadinessItem.ScalingUnverified -> "Scaling: set to Actual size, but this printer can't confirm it. Check that Fit to page is off."
        ReadinessItem.ScalingWillResize -> fitToPageWarning
        is ReadinessItem.BorderlessNotGuaranteed -> borderlessMayLeaveMargins
        is ReadinessItem.ImageQualityResult -> when (item.quality) {
            ImageQuality.GOOD -> "Image quality: good (${Math.round(item.lowestDpi)} dpi)"
            ImageQuality.ACCEPTABLE -> "Image quality: acceptable (${Math.round(item.lowestDpi)} dpi). Fine details may look soft."
            ImageQuality.LOW -> "Low resolution (${Math.round(item.lowestDpi)} dpi). The print may look blurry or pixelated."
        }
    }

    // Projects / templates / settings
    const val projectsTitle = "Projects"
    const val templatesTitle = "Templates"
    const val settingsTitle = "Settings"
    const val renameProject = "Rename project"
    const val projectName = "Project name"
    const val deleteProjectTitle = "Delete this project?"
    const val deleteProjectBody = "The saved layout will be removed. Your original photos are not touched."
    const val builtInTemplates = "Built in"
    const val myTemplates = "My templates"
    const val saveAsTemplate = "Save current settings as a template"
    const val templateName = "Template name"
    const val templateNote = "Templates save sizes, margins and spacing only, not your photos."
    const val useTemplate = "Use"
    const val noUserTemplates = "Templates you save will appear here."
    fun projectLine(paper: String, photo: String, count: Int) = "$paper · $photo · ${Formatting.photos(count)}"

    const val defaultPhotoSize = "Default photo size"
    const val defaultPaperSize = "Default paper size"
    const val defaultSpacing = "Default spacing"
    const val defaultMargins = "Default margins"
    const val units = "Units"
    const val theme = "Theme"
    const val themeSystem = "System"
    const val themeLight = "Light"
    const val themeDark = "Dark"
    const val language = "Language"
    const val languageSystem = "Device language"
    const val privacy = "Privacy"
    const val about = "About"
    const val calibration = "Printer calibration"
    const val choose = "Choose"
    const val privacyBody = "Your photos stay on this device. The app has no account, no servers and no internet permission, so it can't upload anything. A photo only leaves the device when you choose to share it, or send it to a printer or print service."
    const val aboutBody = "PHOTOPrint Pro – Perfect Photos. Perfect Prints. Version 0.1.0"

    // Calibration
    const val calibrationTitle = "Printer calibration"
    const val calibrationIntro = "Print a page with a 100 mm line, a 50 mm line and your photo frame at its true size. Measure them with a ruler to see whether your printer scales the page."
    const val calibrationSteps = "1. Print the page at Actual size / 100%.\n2. Measure the lines with a ruler.\n3. Enter the length you measured."
    const val printCalibration = "Print calibration page"
    const val measuredLength = "Measured length of the 100 mm line (mm)"
    const val calibrationAccurate = "Your printer is printing at the correct size."
    fun calibrationSmall(percent: Double) = "The page printed at ${Formatting.percent(percent)}, so it came out too small. Turn off Fit to page / Shrink to fit and print at Actual size."
    fun calibrationLarge(percent: Double) = "The page printed at ${Formatting.percent(percent)}, so it came out too large. Check that scaling is set to 100% / Actual size."
    const val calibrationInvalid = "Enter the length in millimetres, for example 98.5."

    // Quality chips
    const val goodQuality = "Good quality"
    const val lowResolution = "Low resolution"

    const val busyPreparing = "Preparing\u2026"
    const val busyPrinting = "Preparing to print\u2026"
    const val busyBuildingPdf = "Preparing your sheets\u2026"

    // Messages (snackbars). Each says what happened and, where useful, what to try.
    fun message(kind: MessageKind): String = when (kind) {
        MessageKind.NO_PHOTOS_YET -> "Add at least one photo first."
        MessageKind.LAYOUT_IMPOSSIBLE -> "These settings can't fit on the paper. Adjust the margins, paper or photo size."
        MessageKind.IMAGE_UNREADABLE -> "One of the photos couldn't be read. It may have been moved or deleted. Try replacing it."
        MessageKind.PDF_FAILED -> "The PDF couldn't be created. Please try again."
        MessageKind.PDF_SAVED -> "PDF saved."
        MessageKind.PDF_SHARED -> "PDF ready to share."
        MessageKind.IMAGE_SAVED -> "Image saved."
        MessageKind.IMAGE_SHARED -> "Image ready to share."
        MessageKind.PRINT_SENT -> "Sent to the print dialog."
        MessageKind.NO_PRINT_SERVICE -> "No print service is available. Install or enable one for your printer in Android settings."
        MessageKind.PRINTER_UNAVAILABLE -> "The printer isn't available right now. Check that it's on and connected."
        MessageKind.STORAGE_FAILED -> "Couldn't save the file. Check that there is free storage space."
        MessageKind.PERMISSION_DENIED -> "Permission was denied. You can allow it in Android settings."
        MessageKind.NOT_AVAILABLE_YET -> "This isn't available in this version yet."
        MessageKind.PROJECT_SAVED -> "Project saved."
        MessageKind.PROJECT_RENAMED -> "Project renamed."
        MessageKind.PROJECT_DUPLICATED -> "Project duplicated."
        MessageKind.PROJECT_DELETED -> "Project deleted."
        MessageKind.PROJECT_NOT_FOUND -> "That project no longer exists."
        MessageKind.NAME_INVALID -> "Enter a name of up to 80 characters."
        MessageKind.TEMPLATE_SAVED -> "Template saved."
        MessageKind.UNKNOWN -> "Something went wrong. Please try again."
    }

}
