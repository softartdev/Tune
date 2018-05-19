package com.softartdev.tune.di.component

import android.app.Application
import android.content.Context
import com.softartdev.tune.data.DataManager
import com.softartdev.tune.di.ApplicationContext
import com.softartdev.tune.di.module.AppModule
import com.softartdev.tune.music.MediaPlaybackService
import dagger.Component
import javax.inject.Singleton

@Singleton
@Component(modules = [AppModule::class])
interface AppComponent {

    @ApplicationContext
    fun context(): Context

    fun application(): Application

    fun dataManager(): DataManager

    fun inject(mediaPlaybackService: MediaPlaybackService)

}
