package com.photoprint.pro.di

import com.photoprint.pro.domain.layout.LayoutEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** The domain modules are plain Kotlin, so they are wired into Hilt here rather than annotated themselves. */
@Module
@InstallIn(SingletonComponent::class)
object DomainModule {
    @Provides
    @Singleton
    fun provideLayoutEngine(): LayoutEngine = LayoutEngine()
}
