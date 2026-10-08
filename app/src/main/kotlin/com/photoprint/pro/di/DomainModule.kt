package com.photoprint.pro.di

import com.photoprint.pro.domain.layout.LayoutEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** The domain module is plain Kotlin, so it is wired into Hilt here rather than annotated itself. */
@Module
@InstallIn(SingletonComponent::class)
object DomainModule {
    @Provides
    @Singleton
    fun provideLayoutEngine(): LayoutEngine = LayoutEngine()
}
