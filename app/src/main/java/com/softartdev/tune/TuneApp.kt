package com.softartdev.tune

import android.app.Application
import android.content.Context
import com.softartdev.tune.di.component.AppComponent
import com.softartdev.tune.di.component.DaggerAppComponent
import com.softartdev.tune.di.module.AppModule
import com.softartdev.tune.di.module.MediaModule
import timber.log.Timber

class TuneApp : Application() {

    private var appComponent: AppComponent? = null

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }

    // Needed to replace the component with a test specific one
    var component: AppComponent
        get() {
            if (appComponent == null) {
                appComponent = DaggerAppComponent.builder()
                        .appModule(AppModule(this))
                        .mediaModule(MediaModule())
                        .build()
            }
            return appComponent as AppComponent
        }
        set(appComponent) {
            this.appComponent = appComponent
        }

    companion object {
        operator fun get(context: Context): TuneApp = context.applicationContext as TuneApp
    }

}