package com.photoprint.pro.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.photoprint.pro.core.ui.theme.PhotoPrintTheme
import com.photoprint.pro.core.ui.theme.Spacing
import com.photoprint.pro.domain.printing.PrinterCapabilities
import com.photoprint.pro.domain.printing.PrintReadiness
import com.photoprint.pro.presentation.app.AppController
import com.photoprint.pro.presentation.app.BusyKind
import com.photoprint.pro.presentation.app.PrinterListState
import com.photoprint.pro.presentation.editor.AspectOption
import com.photoprint.pro.presentation.format.Formatting
import com.photoprint.pro.presentation.nav.NavDirection
import com.photoprint.pro.presentation.nav.Route
import com.photoprint.pro.presentation.nav.Workflow
import com.photoprint.pro.ui.screens.CalibrationScreen
import com.photoprint.pro.ui.screens.FinalCheckScreen
import com.photoprint.pro.ui.screens.HomeScreen
import com.photoprint.pro.ui.screens.LayoutSettingsScreen
import com.photoprint.pro.ui.screens.PaperSizeScreen
import com.photoprint.pro.ui.screens.PhotoEditorScreen
import com.photoprint.pro.ui.screens.PhotoSizeScreen
import com.photoprint.pro.ui.screens.PrintPreviewScreen
import com.photoprint.pro.ui.screens.PrintSettingsScreen
import com.photoprint.pro.ui.screens.PrintersScreen
import com.photoprint.pro.ui.screens.ProjectsScreen
import com.photoprint.pro.ui.screens.SelectPhotosScreen
import com.photoprint.pro.ui.screens.SettingsScreen
import com.photoprint.pro.ui.screens.TemplatesScreen

private fun stepFor(route: Route): Pair<Int, Int>? = Workflow.stepIndex(route)?.let { it to Workflow.steps.size }

/**
 * The whole app UI. Reads everything from [controller]; the host (Android Activity) supplies the image
 * source and forwards system Back to `controller.nav.pop()`.
 */
@Composable
fun PhotoPrintRoot(
    controller: AppController,
    imageSource: PhotoImageSource,
    nowMillis: () -> Long = { System.currentTimeMillis() },
) {
    val settings by controller.settings.collectAsState()
    CompositionLocalProvider(LocalImageSource provides imageSource) {
        PhotoPrintTheme(settings.theme) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                AppContent(controller, nowMillis)
            }
        }
    }
}

@Composable
private fun AppContent(controller: AppController, nowMillis: () -> Long) {
    val navState by controller.nav.state.collectAsState()
    val state by controller.session.state.collectAsState()
    val settings by controller.settings.collectAsState()
    val projects by controller.projects.collectAsState()
    val userTemplates by controller.userTemplates.collectAsState()
    val printers by controller.printers.collectAsState()
    val busy by controller.busy.collectAsState()
    val message by controller.message.collectAsState()

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(Strings.message(it.kind))
            controller.consumeMessage(it.id)
        }
    }

    val nav = controller.nav
    val route = navState.current
    val isTab = route in Route.tabs

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = { if (isTab) BottomTabs(route) { nav.switchTab(it) } },
        ) { rootInner ->
            AnimatedContent(
                targetState = route,
                modifier = Modifier.fillMaxSize().padding(bottom = rootInner.calculateBottomPadding()),
                transitionSpec = {
                    when (navState.direction) {
                        NavDirection.FORWARD -> (slideInHorizontally { it / 4 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 8 } + fadeOut())
                        NavDirection.BACK -> (slideInHorizontally { -it / 4 } + fadeIn()) togetherWith (slideOutHorizontally { it / 8 } + fadeOut())
                        NavDirection.NONE -> fadeIn() togetherWith fadeOut()
                    }
                },
                label = "route",
            ) { r ->
                val step = stepFor(r)
                val back: () -> Unit = { nav.pop() }
                when (r) {
                    Route.Home -> Scaffold(containerColor = MaterialTheme.colorScheme.background) { inner ->
                        HomeScreen(
                            projects = projects,
                            nowMillis = nowMillis(),
                            unit = settings.unit,
                            onNewPrint = controller::newPrint,
                            onQuickPrint = controller::quickPrint,
                            onOpenProject = controller::openProject,
                            onSeeAll = { nav.switchTab(Route.Projects) },
                            onSettings = { nav.switchTab(Route.Settings) },
                            contentPadding = inner,
                        )
                    }

                    Route.SelectPhotos -> SelectPhotosScreen(
                        photos = state.photos,
                        step = step!!,
                        onAddGallery = controller::addFromGallery,
                        onAddCamera = controller::addFromCamera,
                        onRemove = controller.session::removePhoto,
                        onMove = controller.session::movePhoto,
                        onEdit = { nav.push(Route.EditPhoto(it)) },
                        onNext = { nav.push(Route.PhotoSize) },
                        onBack = back,
                    )

                    is Route.EditPhoto -> {
                        val photo = state.photo(r.photoId)
                        if (photo == null) LaunchedEffect(Unit) { nav.pop() } else PhotoEditorScreen(
                            title = Strings.editorTitle,
                            photo = photo,
                            aspect = state.editorAspect,
                            aspectLocked = false,
                            frameCaption = null,
                            step = step,
                            onCropChange = { controller.session.setCrop(r.photoId, it) },
                            onAspectChange = controller.session::setEditorAspect,
                            onReplace = { controller.replacePhoto(r.photoId) },
                            onApply = back,
                            onBack = back,
                        )
                    }

                    Route.PhotoSize -> PhotoSizeScreen(
                        selected = state.photoSize,
                        unit = settings.unit,
                        step = step!!,
                        onSelect = controller.session::setPhotoSize,
                        onNext = { nav.push(Route.PaperSize) },
                        onBack = back,
                    )

                    Route.PaperSize -> PaperSizeScreen(
                        selected = state.paperSize,
                        unit = settings.unit,
                        step = step!!,
                        onSelect = controller.session::setPaperSize,
                        onNext = { nav.push(Route.LayoutSettings) },
                        onBack = back,
                    )

                    Route.LayoutSettings -> LayoutSettingsScreen(
                        state = state,
                        unit = settings.unit,
                        step = step!!,
                        onCopies = controller.session::setCopies,
                        onAutoFill = controller.session::setAutoFill,
                        onFillSheets = controller.session::setFillSheets,
                        onSpacing = controller.session::setSpacingAll,
                        onMargin = controller.session::setMarginAll,
                        onRotation = controller.session::setAllowRotation,
                        onCutLines = controller.session::setShowCutLines,
                        onPreview = { nav.push(Route.Preview) },
                        onBack = back,
                    )

                    Route.Preview -> {
                        val plan = state.plan
                        if (plan == null) LaunchedEffect(Unit) { nav.pop() } else {
                            val quality = remember(state.photos, state.photoSize) { PrintReadiness.imageQuality(state.toProject("q", 0)) }
                            PrintPreviewScreen(
                                state = state,
                                plan = plan,
                                unit = settings.unit,
                                actualPxPerMm = controller.actualPxPerMm,
                                quality = quality,
                                step = step!!,
                                onSheetChange = controller.session::setSheet,
                                onEditPhoto = { nav.push(Route.EditPlacement(it)) },
                                onReplace = controller::replacePhoto,
                                onMovePhoto = controller.session::movePhoto,
                                onEditLayout = back,
                                onSavePdf = controller::savePdf,
                                onSharePdf = controller::sharePdf,
                                onSaveImage = controller::saveImage,
                                onShareImage = controller::shareImage,
                                onPrint = {
                                    controller.refreshPrinters()
                                    nav.push(Route.Printers)
                                },
                                onBack = back,
                            )
                        }
                    }

                    is Route.EditPlacement -> {
                        val photo = state.photo(r.photoId)
                        if (photo == null) LaunchedEffect(Unit) { nav.pop() } else PhotoEditorScreen(
                            title = Strings.editPlacementTitle,
                            photo = photo,
                            aspect = AspectOption.of(state.photoSize.widthMm, state.photoSize.heightMm),
                            aspectLocked = true,
                            frameCaption = Strings.frameFixed(Formatting.size(state.photoSize.widthMm, state.photoSize.heightMm, settings.unit)),
                            step = step,
                            onCropChange = { controller.session.setCrop(r.photoId, it) },
                            onAspectChange = {},
                            onReplace = { controller.replacePhoto(r.photoId) },
                            onApply = back,
                            onBack = back,
                        )
                    }

                    Route.Printers -> {
                        LaunchedEffect(Unit) { if (printers is PrinterListState.Idle) controller.refreshPrinters() }
                        PrintersScreen(
                            printers = printers,
                            selectedId = state.printerId,
                            step = step!!,
                            onSelect = controller::selectPrinter,
                            onRefresh = controller::refreshPrinters,
                            onAddPrinter = controller::openAddPrinter,
                            onContinue = { nav.push(Route.PrintSettings) },
                            onBack = back,
                        )
                    }

                    Route.PrintSettings -> {
                        val listed = (printers as? PrinterListState.Loaded)?.printers?.firstOrNull { it.info.id == state.printerId }
                        PrintSettingsScreen(
                            state = state,
                            capabilities = listed?.capabilities ?: PrinterCapabilities(),
                            printerName = listed?.info?.name,
                            unit = settings.unit,
                            step = step!!,
                            onSettings = controller.session::setPrintSettings,
                            onNext = { nav.push(Route.FinalCheck) },
                            onBack = back,
                        )
                    }

                    Route.FinalCheck -> {
                        val report = remember(state, printers) { controller.readiness() }
                        FinalCheckScreen(
                            report = report,
                            step = step!!,
                            onTestPrint = { controller.print(testOnly = true) },
                            onPrint = { controller.print(testOnly = false) },
                            onCalibration = { nav.push(Route.Calibration) },
                            onBack = back,
                        )
                    }

                    Route.Projects -> ProjectsScreen(
                        projects = projects,
                        nowMillis = nowMillis(),
                        unit = settings.unit,
                        onOpen = controller::openProject,
                        onRename = controller::renameProject,
                        onDuplicate = controller::duplicateProject,
                        onDelete = controller::deleteProject,
                        onReprint = controller::reprint,
                    )

                    Route.Templates -> TemplatesScreen(
                        builtIn = controller.builtInTemplates,
                        mine = userTemplates,
                        unit = settings.unit,
                        canSaveCurrent = state.hasPhotos || state.projectId != null,
                        onUse = controller::useTemplate,
                        onDeleteMine = controller::deleteUserTemplate,
                        onSaveCurrent = controller::saveCurrentAsTemplate,
                    )

                    Route.Settings -> SettingsScreen(
                        settings = settings,
                        onChange = controller::updateSettings,
                        onCalibration = { nav.push(Route.Calibration) },
                    )

                    Route.Calibration -> CalibrationScreen(onPrint = controller::printCalibration, onBack = back)
                }
            }
        }

        busy?.let { BusyOverlay(it) }
    }
}

@Composable
private fun BottomTabs(current: Route, onSelect: (Route) -> Unit) {
    NavigationBar {
        listOf(
            Triple(Route.Home, Icons.Filled.Home, Strings.tabHome),
            Triple(Route.Projects, Icons.Filled.Folder, Strings.tabProjects),
            Triple(Route.Templates, Icons.Filled.Dashboard, Strings.tabTemplates),
            Triple(Route.Settings, Icons.Filled.Settings, Strings.tabSettings),
        ).forEach { (route, icon, label) ->
            NavigationBarItem(
                selected = current == route,
                onClick = { onSelect(route) },
                icon = { Icon(icon, contentDescription = null) },
                label = { Text(label) },
            )
        }
    }
}

/** Blocks taps while a PDF is built or a print job is prepared, and says what is happening. */
@Composable
private fun BusyOverlay(kind: BusyKind) {
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)).pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center,
    ) {
        Surface(shape = MaterialTheme.shapes.large, tonalElevation = 6.dp) {
            Column(Modifier.padding(Spacing.lg), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                CircularProgressIndicator()
                Text(
                    when (kind) {
                        BusyKind.BUILDING_PDF -> "Preparing your sheets…"
                        BusyKind.PRINTING -> "Preparing to print…"
                        BusyKind.SAVING -> "Preparing…"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}
