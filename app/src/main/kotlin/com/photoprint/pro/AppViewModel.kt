package com.photoprint.pro

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photoprint.pro.domain.layout.LayoutEngine
import com.photoprint.pro.domain.project.IdGenerator
import com.photoprint.pro.domain.project.InMemoryProjectRepository
import com.photoprint.pro.domain.project.InMemoryTemplateRepository
import com.photoprint.pro.domain.project.ProjectService
import com.photoprint.pro.domain.project.TimeSource
import com.photoprint.pro.platform.AndroidHost
import com.photoprint.pro.platform.AndroidImageSource
import com.photoprint.pro.platform.AndroidPlatformGateway
import com.photoprint.pro.presentation.app.AppController
import com.photoprint.pro.presentation.nav.NavStack
import com.photoprint.pro.presentation.session.PrintSession
import com.photoprint.pro.presentation.settings.AppSettings
import com.photoprint.pro.presentation.settings.InMemorySettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject

/**
 * Holds the pure-Kotlin [AppController] across configuration changes (rotation).
 *
 * TODO(persistence): projects, templates and settings use in-memory stores for now, so they are lost when
 * the process ends. Room (projects/templates) and DataStore (settings) implement the same interfaces.
 */
@HiltViewModel
class AppViewModel @Inject constructor(
    @ApplicationContext context: Context,
    engine: LayoutEngine,
) : ViewModel() {

    /** Set by the Activity while it is in the foreground. */
    var host: AndroidHost? = null

    val imageSource = AndroidImageSource()

    val controller: AppController

    init {
        val ids = IdGenerator { UUID.randomUUID().toString() }
        val time = TimeSource { System.currentTimeMillis() }
        val projects = InMemoryProjectRepository()
        controller = AppController(
            nav = NavStack(),
            session = PrintSession(engine, ids, AppSettings()),
            projectRepository = projects,
            projectService = ProjectService(projects, ids, time),
            templateRepository = InMemoryTemplateRepository(),
            settingsRepository = InMemorySettingsRepository(),
            gateway = AndroidPlatformGateway(context) { host },
            ids = ids,
            time = time,
            scope = viewModelScope,
        )
        controller.startAutosave()
    }
}
