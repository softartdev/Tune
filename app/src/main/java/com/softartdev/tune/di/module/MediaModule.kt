package com.softartdev.tune.di.module

import android.content.Context
import com.softartdev.tune.music.MusicProvider
import dagger.Module
import dagger.Provides

@Module
class MediaModule(private val context: Context) {

    @Provides
    internal fun provideMusicProvider(): MusicProvider = MusicProvider(context)

}