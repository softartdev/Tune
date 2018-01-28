package com.softartdev.tune.di.component

import com.softartdev.tune.di.PerFragment
import com.softartdev.tune.di.module.FragmentModule
import com.softartdev.tune.ui.main.MainFileFragment
import dagger.Subcomponent

/**
 * This component inject dependencies to all Fragments across the application
 */
@PerFragment
@Subcomponent(modules = [FragmentModule::class])
interface FragmentComponent {
    fun inject(mainFileFragment: MainFileFragment)
}