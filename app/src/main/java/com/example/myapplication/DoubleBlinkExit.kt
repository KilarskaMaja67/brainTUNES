package com.example.myapplication

import android.os.Handler
import android.widget.TextView

/**
 * "Blink twice to exit" - reusable on every screen.
 *
 *   private val blinkExit by lazy { DoubleBlinkExit(handler, exitHintText) { finish() } }
 *   ...
 *   AppControler.ustawAkcje(..., blink = { blinkExit.onBlink() })
 *
 * First blink shows "to exit, blink once more"; a second blink within [timeoutMs] calls [onExit].
 * Python ignores blinks for ~1.2 s after a command, so the timeout must be clearly longer than that.
 */
class DoubleBlinkExit(
    private val handler: Handler,
    private val hintView: TextView,
    private val timeoutMs: Long = 4000L,
    private val onExit: () -> Unit,
) {
    private var count = 0
    private val resetRunnable = Runnable { reset() }

    init {
        hintView.text = IDLE_TEXT
    }

    fun onBlink() {
        count++
        handler.removeCallbacks(resetRunnable)
        if (count == 1) {
            hintView.text = CONFIRM_TEXT
            handler.postDelayed(resetRunnable, timeoutMs)
        } else {
            onExit()
        }
    }

    fun reset() {
        handler.removeCallbacks(resetRunnable)
        count = 0
        hintView.text = IDLE_TEXT
    }

    companion object {
        const val IDLE_TEXT = "blink twice to exit"
        const val CONFIRM_TEXT = "to exit, blink once more"
    }
}