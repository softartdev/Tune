/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.softartdev.tune.music

import android.annotation.SuppressLint
import android.content.Context
import android.os.SystemClock
import android.support.v7.widget.AppCompatImageButton
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View

/**
 * A button that will repeatedly call a 'listener' method
 * as long as the button is pressed.
 */
class RepeatingImageButton : AppCompatImageButton {
    private var mStartTime: Long = 0
    private var mRepeatCount: Int = 0
    private var mListener: RepeatListener? = null
    private var mInterval: Long = 500

    constructor(context: Context) : this(context, null) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, android.R.attr.imageButtonStyle) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        init()
    }

    private fun init() {
        isFocusable = true
        isLongClickable = true
    }

    /**
     * Sets the listener to be called while the button is pressed and
     * the interval in milliseconds with which it will be called.
     * @param listener The listener that will be called
     * @param interval The interval in milliseconds for calls
     */
    fun setRepeatListener(listener: RepeatListener, interval: Long) {
        mListener = listener
        mInterval = interval
    }

    private val mRepeater = object : Runnable {
        override fun run() {
            doRepeat(false)
            if (isPressed) {
                postDelayed(this, mInterval)
            }
        }
    }

    override fun performLongClick(): Boolean {
        mStartTime = SystemClock.elapsedRealtime()
        mRepeatCount = 0
        post(mRepeater)
        return true
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            // remove the repeater, but call the hook one more time
            removeCallbacks(mRepeater)
            if (mStartTime != 0L) {
                doRepeat(true)
                mStartTime = 0
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                // need to call super to make long press work, but return
                // true so that the application doesn't get the down event.
                super.onKeyDown(keyCode, event)
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                // remove the repeater, but call the hook one more time
                removeCallbacks(mRepeater)
                if (mStartTime != 0L) {
                    doRepeat(true)
                    mStartTime = 0
                }
            }
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun doRepeat(last: Boolean) {
        val now = SystemClock.elapsedRealtime()
        mListener?.onRepeat(this, now - mStartTime, if (last) -1 else mRepeatCount++)
    }

    interface RepeatListener {
        /**
         * This method will be called repeatedly at roughly the interval
         * specified in setRepeatListener(), for as long as the button
         * is pressed.
         * @param v The button as a View.
         * @param duration The number of milliseconds the button has been pressed so far.
         * @param repeatcount The number of previous calls in this sequence.
         * If this is going to be the last call in this sequence (i.e. the user
         * just stopped pressing the button), the value will be -1.
         */
        fun onRepeat(v: View, duration: Long, repeatcount: Int)
    }
}
