package com.example.misal.di

import android.content.Context
import com.example.misal.domain.webrtc.WebRtcManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object WebRtcModule {

    @Provides
    @Singleton
    fun provideWebRtcManager(@ApplicationContext context: Context): WebRtcManager {
        return WebRtcManager(context)
    }
}
