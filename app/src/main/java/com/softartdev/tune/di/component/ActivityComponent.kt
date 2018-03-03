package com.softartdev.tune.di.component

import com.softartdev.tune.di.PerActivity
import com.softartdev.tune.di.module.ActivityModule
import com.softartdev.tune.ui.base.BaseActivity
import com.softartdev.tune.ui.main.music.MusicBrowserActivity
import dagger.Subcomponent

@PerActivity
@Subcomponent(modules = [ActivityModule::class])
interface ActivityComponent {
    fun inject(musicBrowserActivity: MusicBrowserActivity)
}
