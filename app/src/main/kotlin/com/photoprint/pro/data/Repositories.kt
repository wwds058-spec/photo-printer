package com.photoprint.pro.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.photoprint.pro.domain.model.PrintProject
import com.photoprint.pro.domain.model.PrintTemplate
import com.photoprint.pro.domain.project.ProjectRepository
import com.photoprint.pro.domain.project.TemplateRepository
import com.photoprint.pro.domain.storage.StorageCodec
import com.photoprint.pro.presentation.settings.AppSettings
import com.photoprint.pro.presentation.settings.SettingsCodec
import com.photoprint.pro.presentation.settings.SettingsRecord
import com.photoprint.pro.presentation.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/** Saved projects in Room. A row whose stored photos are unreadable is left out of the list, not fatal. */
class RoomProjectRepository(private val dao: ProjectDao) : ProjectRepository {
    override fun observeAll(): Flow<List<PrintProject>> =
        dao.observeAll().map { rows -> rows.mapNotNull { StorageCodec.toProject(it.toRecord()) } }

    override suspend fun get(id: String): PrintProject? = dao.get(id)?.let { StorageCodec.toProject(it.toRecord()) }

    override suspend fun upsert(project: PrintProject) = dao.upsert(StorageCodec.toRecord(project).toEntity())

    override suspend fun delete(id: String) = dao.delete(id)
}

class RoomTemplateRepository(private val dao: TemplateDao) : TemplateRepository {
    override fun observeUserTemplates(): Flow<List<PrintTemplate>> =
        dao.observeAll().map { rows -> rows.map { StorageCodec.toTemplate(it.toRecord()) } }

    override suspend fun upsert(template: PrintTemplate) = dao.upsert(StorageCodec.toRecord(template).toEntity())

    override suspend fun delete(id: String) = dao.delete(id)
}

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Settings in Preferences DataStore. Missing or unrecognised values resolve to defaults in [SettingsCodec]. */
class DataStoreSettingsRepository(private val context: Context) : SettingsRepository {
    private object Keys {
        val photoName = stringPreferencesKey("photo_name")
        val photoW = doublePreferencesKey("photo_w_mm")
        val photoH = doublePreferencesKey("photo_h_mm")
        val paperName = stringPreferencesKey("paper_name")
        val paperW = doublePreferencesKey("paper_w_mm")
        val paperH = doublePreferencesKey("paper_h_mm")
        val spacing = doublePreferencesKey("spacing_mm")
        val margin = doublePreferencesKey("margin_mm")
        val unit = stringPreferencesKey("unit")
        val quality = stringPreferencesKey("quality")
        val theme = stringPreferencesKey("theme")
        val language = stringPreferencesKey("language")
    }

    override val settings: Flow<AppSettings> = context.settingsStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { SettingsCodec.fromRecord(it.toRecord()) }

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.settingsStore.edit { prefs ->
            val next = transform(SettingsCodec.fromRecord(prefs.toRecord())).sanitized()
            prefs.write(SettingsCodec.toRecord(next))
        }
    }

    private fun Preferences.toRecord() = SettingsRecord(
        photoName = this[Keys.photoName], photoWidthMm = this[Keys.photoW], photoHeightMm = this[Keys.photoH],
        paperName = this[Keys.paperName], paperWidthMm = this[Keys.paperW], paperHeightMm = this[Keys.paperH],
        spacingMm = this[Keys.spacing], marginMm = this[Keys.margin],
        unit = this[Keys.unit], quality = this[Keys.quality], theme = this[Keys.theme], language = this[Keys.language],
    )

    private fun androidx.datastore.preferences.core.MutablePreferences.write(r: SettingsRecord) {
        fun <T> set(key: Preferences.Key<T>, value: T?) { if (value == null) remove(key) else this[key] = value }
        set(Keys.photoName, r.photoName); set(Keys.photoW, r.photoWidthMm); set(Keys.photoH, r.photoHeightMm)
        set(Keys.paperName, r.paperName); set(Keys.paperW, r.paperWidthMm); set(Keys.paperH, r.paperHeightMm)
        set(Keys.spacing, r.spacingMm); set(Keys.margin, r.marginMm)
        set(Keys.unit, r.unit); set(Keys.quality, r.quality); set(Keys.theme, r.theme); set(Keys.language, r.language)
    }
}
