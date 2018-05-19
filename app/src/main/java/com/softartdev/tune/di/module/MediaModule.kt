package com.softartdev.tune.di.module

import android.content.Context
import com.softartdev.tune.di.ApplicationContext
import com.softartdev.tune.music.MusicProvider
import dagger.Module
import dagger.Provides

@Module
class MediaModule {

    @Provides
    internal fun provideMusicProvider(@ApplicationContext context: Context): MusicProvider = MusicProvider(context)

}