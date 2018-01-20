package com.softartdev.tune.di.component

import android.app.Application
import android.content.Context
import com.softartdev.tune.di.ApplicationContext
import com.softartdev.tune.di.module.AppModule
import dagger.Component
import javax.inject.Singleton

@Singleton
@Component(modules = [AppModule::class])
interface AppComponent {

    @ApplicationContext
    fun context(): Context

    fun application(): Application

}
