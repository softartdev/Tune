package com.softartdev.tune.di.component

import com.softartdev.tune.di.PerFragment
import com.softartdev.tune.di.module.FragmentModule
import com.softartdev.tune.ui.main.file.MainFileFragment
import com.softartdev.tune.ui.main.media.MainMediaFragment
import dagger.Subcomponent

/**
 * This component inject dependencies to all Fragments across the application
 */
@PerFragment
@Subcomponent(modules = [FragmentModule::class])
interface FragmentComponent {
    fun inject(mainFileFragment: MainFileFragment)
    fun inject(mainFileFragment: MainMediaFragment)
}