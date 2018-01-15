package com.softartdev.tune.ui

import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.softartdev.tune.MainActivity.Companion.DOWNLOADS_TAG
import com.softartdev.tune.MainActivity.Companion.PODCASTS_TAG
import com.softartdev.tune.MainActivity.Companion.SOUNDS_TAG

import com.softartdev.tune.R
import kotlinx.android.synthetic.main.fragment_main.*

class MainFragment : Fragment() {

    private var mTag: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (arguments != null) {
            mTag = arguments!!.getString(ARG_TAG)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_main, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        when (tag) {
            SOUNDS_TAG -> main_text_view.setText(R.string.sounds)
            PODCASTS_TAG -> main_text_view.setText(R.string.podcasts)
            DOWNLOADS_TAG -> main_text_view.setText(R.string.downloads)
        }
    }

    companion object {
        private val ARG_TAG = "arg_tag"

        fun newInstance(tag: String): MainFragment {
            val fragment = MainFragment()
            val args = Bundle()
            args.putString(ARG_TAG, tag)
            fragment.arguments = args
            return fragment
        }
    }

}
