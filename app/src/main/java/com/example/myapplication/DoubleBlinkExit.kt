package com.example.myapplication

import android.os.Handler
import android.widget.TextView

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
