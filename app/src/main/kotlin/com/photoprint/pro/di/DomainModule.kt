package com.photoprint.pro.di

import android.content.Context
import androidx.room.Room
import com.photoprint.pro.data.AppDatabase
import com.photoprint.pro.data.DataStoreSettingsRepository
import com.photoprint.pro.data.RoomProjectRepository
import com.photoprint.pro.data.RoomTemplateRepository
import com.photoprint.pro.domain.layout.LayoutEngine
import com.photoprint.pro.domain.project.ProjectRepository
import com.photoprint.pro.domain.project.TemplateRepository
import com.photoprint.pro.presentation.settings.SettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** The domain modules are plain Kotlin, so they are wired into Hilt here rather than annotated themselves. */
@Module
@InstallIn(SingletonComponent::class)
object DomainModule {
    @Provides
    @Singleton
    fun provideLayoutEngine(): LayoutEngine = LayoutEngine()

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "photoprint.db").build()

    @Provides
    @Singleton
    fun provideProjectRepository(db: AppDatabase): ProjectRepository = RoomProjectRepository(db.projectDao())

    @Provides
    @Singleton
    fun provideTemplateRepository(db: AppDatabase): TemplateRepository = RoomTemplateRepository(db.templateDao())

    @Provides
    @Singleton
    fun provideSettingsRepository(@ApplicationContext context: Context): SettingsRepository = DataStoreSettingsRepository(context)
}
