package com.photoprint.pro

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photoprint.pro.domain.layout.LayoutEngine
import com.photoprint.pro.domain.project.IdGenerator
import com.photoprint.pro.domain.project.ProjectRepository
import com.photoprint.pro.domain.project.TemplateRepository
import com.photoprint.pro.domain.project.ProjectService
import com.photoprint.pro.domain.project.TimeSource
import com.photoprint.pro.platform.AndroidHost
import com.photoprint.pro.platform.AndroidImageSource
import com.photoprint.pro.platform.AndroidPlatformGateway
import com.photoprint.pro.presentation.app.AppController
import com.photoprint.pro.presentation.nav.NavStack
import com.photoprint.pro.presentation.session.PrintSession
import com.photoprint.pro.presentation.settings.AppSettings
import com.photoprint.pro.presentation.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject

/**
 * Holds the pure-Kotlin [AppController] across configuration changes (rotation). Projects and templates are
 * stored in Room, settings in DataStore; the work in progress is autosaved as a project.
 */
@HiltViewModel
class AppViewModel @Inject constructor(
    @ApplicationContext context: Context,
    engine: LayoutEngine,
    projects: ProjectRepository,
    templates: TemplateRepository,
    settings: SettingsRepository,
) : ViewModel() {

    /** Set by the Activity while it is in the foreground. */
    var host: AndroidHost? = null

    val imageSource = AndroidImageSource()

    val controller: AppController

    init {
        val ids = IdGenerator { UUID.randomUUID().toString() }
        val time = TimeSource { System.currentTimeMillis() }
        controller = AppController(
            nav = NavStack(),
            session = PrintSession(engine, ids, AppSettings()),
            projectRepository = projects,
            projectService = ProjectService(projects, ids, time),
            templateRepository = templates,
            settingsRepository = settings,
            gateway = AndroidPlatformGateway(context) { host },
            ids = ids,
            time = time,
            scope = viewModelScope,
        )
        controller.startAutosave()
    }
}
