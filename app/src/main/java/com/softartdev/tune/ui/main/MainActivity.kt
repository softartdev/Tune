package com.softartdev.tune.ui.main

import android.os.Bundle
import android.support.design.widget.NavigationView
import android.support.v4.view.GravityCompat
import android.support.v7.app.ActionBarDrawerToggle
import android.support.v7.app.AppCompatActivity
import android.view.MenuItem
import com.softartdev.tune.R
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.app_bar_main.*

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        val toggle = ActionBarDrawerToggle(this, drawer_layout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close)
        drawer_layout.addDrawerListener(toggle)
        toggle.syncState()

        nav_view.setNavigationItemSelectedListener(this)
        if (savedInstanceState == null) {
            nav_view.menu.getItem(0).let {
                it.isChecked = true
                onNavigationItemSelected(it)
            }
        }
    }

    override fun onBackPressed() {
        if (drawer_layout.isDrawerOpen(GravityCompat.START)) {
            drawer_layout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_sounds -> showSelectedFragment(SOUNDS_TAG)
            R.id.nav_podcasts -> showSelectedFragment(PODCASTS_TAG)
            R.id.nav_downloads -> showSelectedFragment(DOWNLOADS_TAG)
        }
        drawer_layout.closeDrawer(GravityCompat.START)
        return true
    }

    private fun showSelectedFragment(tag: String) {
        var selectedFragment = supportFragmentManager.findFragmentByTag(tag)
        if (selectedFragment != null) {
            //if the fragment exists, show it.
            supportFragmentManager.beginTransaction().show(selectedFragment).commit()
        } else {
            //if the fragment does not exist, add it to fragment manager.
            selectedFragment = MainFileFragment()
            supportFragmentManager.beginTransaction().add(R.id.main_frame_layout, selectedFragment, tag).commit()
        }
        hideOthers(tag)
    }

    private fun hideOthers(tagSelected: String) {
        //if the other fragments is visible, hide it.
        TAGS.asSequence()
                .filter { it != tagSelected }
                .forEach { hideUnselectedFragment(it) }
    }

    private fun hideUnselectedFragment(tag: String) {
        val unselectedFragment = supportFragmentManager.findFragmentByTag(tag)
        if (unselectedFragment != null) {
            supportFragmentManager.beginTransaction().hide(unselectedFragment).commit()
        }
    }

    companion object {
        const val SOUNDS_TAG = "sounds_tag"
        const val PODCASTS_TAG = "podcasts_tag"
        const val DOWNLOADS_TAG = "downloads_tag"
        private val TAGS = arrayOf(SOUNDS_TAG, PODCASTS_TAG, DOWNLOADS_TAG)
    }
}
