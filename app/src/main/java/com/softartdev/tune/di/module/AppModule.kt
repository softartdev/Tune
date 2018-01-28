package com.softartdev.tune.di.module

import android.app.Application
import android.content.Context
import com.softartdev.tune.di.ApplicationContext
import dagger.Module
import dagger.Provides

@Module
class AppModule(private val application: Application) {

    @Provides
    internal fun provideApplication(): Application {
        return application
    }

    @Provides
    @ApplicationContext
    internal fun provideContext(): Context {
        return application
    }
}