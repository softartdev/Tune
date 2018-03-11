package com.softartdev.tune.ui.main

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.support.design.widget.NavigationView
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.view.GravityCompat
import android.support.v7.app.ActionBarDrawerToggle
import android.support.v7.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem
import com.softartdev.tune.R
import com.softartdev.tune.music.MediaPlaybackService
import com.softartdev.tune.music.MusicUtils
import com.softartdev.tune.ui.main.file.MainFileFragment
import com.softartdev.tune.ui.main.media.MainMediaFragment
import com.softartdev.tune.ui.main.music.MusicBrowserActivity
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.app_bar_main.*
import timber.log.Timber

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private var mediaBrowserCompat: MediaBrowserCompat? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        val serviceComponent = ComponentName(this, MediaPlaybackService::class.java)
        mediaBrowserCompat = MediaBrowserCompat(this, serviceComponent, connectionCallBack, null)

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

    override fun onStart() {
        super.onStart()
        mediaBrowserCompat?.connect()
    }

    override fun onStop() {
        super.onStop()
        mediaBrowserCompat?.disconnect()
    }

    private val mediaControllerCallback = object : MediaControllerCompat.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
            MusicUtils.updateNowPlaying(this@MainActivity)
        }
    }

    private val connectionCallBack = object : MediaBrowserCompat.ConnectionCallback() {
        override fun onConnected() {
            val sessionToken = mediaBrowserCompat?.sessionToken ?: throw IllegalArgumentException("No Session token")
            Timber.d("onConnected: session token %s", sessionToken)
            val mediaControllerCompat = MediaControllerCompat(this@MainActivity, sessionToken)
            mediaControllerCompat.registerCallback(mediaControllerCallback)
            MediaControllerCompat.setMediaController(this@MainActivity, mediaControllerCompat)

            if (mediaControllerCompat.metadata != null) {
                MusicUtils.updateNowPlaying(this@MainActivity)
            }
        }
        override fun onConnectionFailed() {
            Timber.d("onConnectionFailed")
        }
        override fun onConnectionSuspended() {
            Timber.d("onConnectionSuspended")
            MediaControllerCompat.setMediaController(this@MainActivity, null)
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
            R.id.nav_tracks -> showSelectedFragment(TRACKS_TAG)
            R.id.nav_playlists -> showSelectedFragment(PLAYLISTS_TAG)
            R.id.nav_artists -> showSelectedFragment(ARTISTS_TAG)
            R.id.nav_albums -> showSelectedFragment(ALBUMS_TAG)
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
            selectedFragment = when (tag) {
                SOUNDS_TAG, PODCASTS_TAG, DOWNLOADS_TAG -> MainFileFragment()
                else -> MainMediaFragment()
            }
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

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_music -> {
            startActivity(Intent(this, MusicBrowserActivity::class.java))
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    companion object {
        const val SOUNDS_TAG = "sounds_tag"
        const val PODCASTS_TAG = "podcasts_tag"
        const val DOWNLOADS_TAG = "downloads_tag"
        const val TRACKS_TAG = "tracks_tag"
        const val PLAYLISTS_TAG = "playlists_tag"
        const val ARTISTS_TAG = "artists_tag"
        const val ALBUMS_TAG = "albums_tag"
        private val TAGS = arrayOf(SOUNDS_TAG, PODCASTS_TAG, DOWNLOADS_TAG, TRACKS_TAG, PLAYLISTS_TAG, ARTISTS_TAG, ALBUMS_TAG)
    }
}
