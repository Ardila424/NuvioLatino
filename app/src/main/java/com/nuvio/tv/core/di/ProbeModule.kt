package com.nuvio.tv.core.di

import com.nuvio.tv.core.streams.AudioProbeCache
import com.nuvio.tv.core.streams.AudioProbeCacheDataStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ProbeModule {

    @Binds
    @Singleton
    abstract fun bindAudioProbeCache(impl: AudioProbeCacheDataStore): AudioProbeCache
}
