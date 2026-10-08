package com.photoprint.pro.domain.project

import com.photoprint.pro.domain.model.PrintProject
import com.photoprint.pro.domain.model.PrintTemplate
import com.photoprint.pro.domain.model.ProjectPhoto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

fun interface IdGenerator {
    fun next(): String
}

fun interface TimeSource {
    fun nowMillis(): Long
}

/** Implemented with Room in the app module. Metadata only; image files are stored separately. */
interface ProjectRepository {
    /** Most recently modified first. */
    fun observeAll(): Flow<List<PrintProject>>

    suspend fun get(id: String): PrintProject?

    suspend fun upsert(project: PrintProject)

    suspend fun delete(id: String)
}

/** User templates (built-ins come from [com.photoprint.pro.domain.model.BuiltInTemplates]). */
interface TemplateRepository {
    fun observeUserTemplates(): Flow<List<PrintTemplate>>

    suspend fun upsert(template: PrintTemplate)

    suspend fun delete(id: String)
}

enum class ProjectError { NOT_FOUND, BLANK_NAME, NAME_TOO_LONG, NO_PHOTOS }

sealed interface ProjectOutcome<out T> {
    data class Success<T>(val value: T) : ProjectOutcome<T>
    data class Failure(val error: ProjectError) : ProjectOutcome<Nothing>
}

/**
 * Project lifecycle rules: create, rename, duplicate, autosave-style updates, delete.
 * IDs and time are injected so behaviour is deterministic in tests.
 */
class ProjectService(
    private val repository: ProjectRepository,
    private val ids: IdGenerator,
    private val time: TimeSource,
) {
    suspend fun createFromTemplate(
        template: PrintTemplate,
        name: String,
        photos: List<ProjectPhoto>,
    ): ProjectOutcome<PrintProject> {
        val clean = validName(name) ?: return invalidName(name)
        if (photos.isEmpty()) return ProjectOutcome.Failure(ProjectError.NO_PHOTOS)
        val project = template.toProject(ids.next(), clean, photos, time.nowMillis())
        repository.upsert(project)
        return ProjectOutcome.Success(project)
    }

    suspend fun rename(id: String, newName: String): ProjectOutcome<PrintProject> {
        val clean = validName(newName) ?: return invalidName(newName)
        return update(id) { it.copy(name = clean) }
    }

    /** Autosave hook: applies [change], bumps the modified time and saves. */
    suspend fun update(id: String, change: (PrintProject) -> PrintProject): ProjectOutcome<PrintProject> {
        val current = repository.get(id) ?: return ProjectOutcome.Failure(ProjectError.NOT_FOUND)
        // Identity and creation time are not editable through a change lambda.
        val changed = change(current).copy(id = current.id, createdAtMillis = current.createdAtMillis, modifiedAtMillis = time.nowMillis())
        repository.upsert(changed)
        return ProjectOutcome.Success(changed)
    }

    /** New id and timestamps; photo references and settings are copied, image files are shared. */
    suspend fun duplicate(id: String): ProjectOutcome<PrintProject> {
        val source = repository.get(id) ?: return ProjectOutcome.Failure(ProjectError.NOT_FOUND)
        val now = time.nowMillis()
        val copy = source.copy(
            id = ids.next(),
            name = copyName(source.name),
            createdAtMillis = now,
            modifiedAtMillis = now,
        )
        repository.upsert(copy)
        return ProjectOutcome.Success(copy)
    }

    suspend fun delete(id: String): ProjectOutcome<Unit> {
        repository.get(id) ?: return ProjectOutcome.Failure(ProjectError.NOT_FOUND)
        repository.delete(id)
        return ProjectOutcome.Success(Unit)
    }

    private fun validName(name: String): String? = name.trim().takeIf { it.isNotEmpty() && it.length <= MAX_NAME }

    private fun invalidName(name: String) =
        ProjectOutcome.Failure(if (name.isBlank()) ProjectError.BLANK_NAME else ProjectError.NAME_TOO_LONG)

    private fun copyName(name: String): String = "$name (copy)".let { if (it.length <= MAX_NAME) it else name.take(MAX_NAME - 7) + " (copy)" }

    companion object {
        const val MAX_NAME = 80
    }
}

/** Simple in-memory repository for tests and Compose previews. */
class InMemoryProjectRepository : ProjectRepository {
    private val state = MutableStateFlow<Map<String, PrintProject>>(emptyMap())

    override fun observeAll(): Flow<List<PrintProject>> =
        state.map { it.values.sortedByDescending { p -> p.modifiedAtMillis } }

    override suspend fun get(id: String): PrintProject? = state.value[id]

    override suspend fun upsert(project: PrintProject) {
        state.value = state.value + (project.id to project)
    }

    override suspend fun delete(id: String) {
        state.value = state.value - id
    }
}

/** In-memory template store for tests and previews. */
class InMemoryTemplateRepository : TemplateRepository {
    private val state = MutableStateFlow<Map<String, com.photoprint.pro.domain.model.PrintTemplate>>(emptyMap())

    override fun observeUserTemplates(): Flow<List<com.photoprint.pro.domain.model.PrintTemplate>> = state.map { it.values.toList() }

    override suspend fun upsert(template: com.photoprint.pro.domain.model.PrintTemplate) {
        state.value = state.value + (template.id to template)
    }

    override suspend fun delete(id: String) {
        state.value = state.value - id
    }
}
