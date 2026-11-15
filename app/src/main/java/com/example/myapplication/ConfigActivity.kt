package com.example.myapplication

import android.graphics.Color
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * CALIBRATION SCREEN.
 * Calibration starts AUTOMATICALLY when the screen opens (gestures don't work before calibration).
 * Python measures and computes; this screen shows the instructions, beeps on each action,
 * rates the headband contact and shows the result. A new calibration overwrites the saved one.
 *
 * When finished:  ⬅ head left = Repeat,  ➡ head right = Done (or tap).
 * A successful calibration returns to the menu automatically after 5 s.
 */
class ConfigActivity : AppCompatActivity() {

    private lateinit var tvConnection: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvInstruction: TextView
    private lateinit var tvStep: TextView
    private lateinit var tvContact: TextView
    private lateinit var tvResult: TextView
    private lateinit var btnRepeat: Button
    private lateinit var btnDone: Button

    private val handler = Handler(Looper.getMainLooper())
    private var tone: ToneGenerator? = null

    private var running = false         // calibration in progress
    private var finished = false        // showing the result
    private var lastStepMs = 0L         // when the last message from Python arrived

    private val GREEN = Color.rgb(46, 125, 50)
    private val ORANGE = Color.rgb(230, 120, 0)
    private val RED = Color.rgb(198, 40, 40)

    // number of action steps: rest + 5 blinks + 4 directions x 3
    private val TOTAL_STEPS = 1 + 5 + 12

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_config)

        tvConnection = findViewById(R.id.tvConnection)
        progressBar = findViewById(R.id.calibrationProgress)
        tvInstruction = findViewById(R.id.tvInstruction)
        tvStep = findViewById(R.id.tvStep)
        tvContact = findViewById(R.id.tvContact)
        tvResult = findViewById(R.id.tvResult)
        btnRepeat = findViewById(R.id.btnRepeat)
        btnDone = findViewById(R.id.btnDone)

        progressBar.max = TOTAL_STEPS
        btnRepeat.setOnClickListener { start() }
        btnDone.setOnClickListener { finish() }

        tone = try { ToneGenerator(AudioManager.STREAM_MUSIC, 80) } catch (e: Exception) { null }
    }

    override fun onResume() {
        super.onResume()
        AppControler.naKrokKalibracji = { onStep(it) }
        // Python sends no commands during calibration; afterwards: left = Repeat, right = Done
        AppControler.ustawAkcje(
            up = { finish() },          // head up = back
            left = { if (finished) click(btnRepeat) },
            right = { if (finished) click(btnDone) },
        )
        if (!running && !finished) start()
    }

    override fun onPause() {
        super.onPause()
        AppControler.naKrokKalibracji = null
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        tone?.release()
    }

    // ------------------------------------------------------------------ flow

    private fun start() {
        handler.removeCallbacksAndMessages(null)
        running = true
        finished = false
        lastStepMs = 0L
        btnRepeat.visibility = View.GONE
        btnDone.visibility = View.GONE
        progressBar.progress = 0
        tvContact.text = ""
        tvResult.text = ""
        tvConnection.text = ""
        tvInstruction.text = "Connecting to the headband…"
        tvStep.text = "Put on the headband and sit comfortably"
        AppControler.kalibruj()
        handler.postDelayed(checkStarted, 5000)
    }

    /** If Python hasn't answered within 5 s - ask again and show a message. */
    private val checkStarted = object : Runnable {
        override fun run() {
            if (running && lastStepMs == 0L) {
                tvConnection.text = "No response from the laptop – is the Python script running?"
                tvConnection.setTextColor(RED)
                AppControler.kalibruj()
                handler.postDelayed(this, 5000)
            }
        }
    }

    /** Messages from Python (protocol names stay as they are: HEAD_GORA = up, HEAD_DOL = down). */
    private fun onStep(step: String) {
        if (!running) return
        lastStepMs = System.currentTimeMillis()
        tvConnection.text = ""

        val name = step.substringBefore(" ")
        val rest = step.substringAfter(" ", "")
        val nr = rest.substringBefore("/").toIntOrNull() ?: 0      // e.g. "3/5" -> 3

        when (name) {
            "REST" -> {
                instruction("Sit still", "Look straight ahead, don't move (5 s)")
                progressBar.progress = 1
            }
            "CONTACT" -> {
                val good = rest.startsWith("GOOD")
                tvContact.text = if (good) "✅ Good headband contact"
                else "⚠️ Weak headband contact – press it to your forehead and move your hair aside"
                tvContact.setTextColor(if (good) GREEN else ORANGE)
            }
            "GET_READY" -> instruction("Get ready…", "Next task in a moment")
            "WAIT" -> tvInstruction.text = "…rest"
            "BLINK1" -> action("Blink HARD", "Blink $rest", 1 + nr)
            "BLINK2" -> action("Blink twice quickly", "Blink $rest", 1 + nr)
            "HEAD_L" -> action("⬅ Head LEFT\nand back", "Move $rest", 6 + nr)
            "HEAD_P" -> action("➡ Head RIGHT\nand back", "Move $rest", 9 + nr)
            "HEAD_GORA" -> action("⬆ Head UP\nand back", "Move $rest", 12 + nr)
            "HEAD_DOL" -> action("⬇ Head DOWN\nand back", "Move $rest", 15 + nr)
            "RESULT" -> showResult(rest)
            "DONE" -> end(success = true)
            "FAIL" -> end(success = false)
        }
    }

    private fun showResult(result: String) {
        // e.g. "BLINK=OK HEAD=WEAK"
        val blink = if (result.contains("BLINK=OK")) "✅ Blink: OK" else "⚠️ Blink: too weak – blink harder"
        val head = if (result.contains("HEAD=OK")) "✅ Head moves: OK" else "⚠️ Head moves: unclear – make bigger moves"
        tvResult.text = "$blink\n$head"
    }

    private fun end(success: Boolean) {
        running = false
        finished = true
        progressBar.progress = TOTAL_STEPS
        btnRepeat.visibility = View.VISIBLE
        btnDone.visibility = View.VISIBLE
        if (success) {
            instruction("Calibration done! ✅", "Returning to the menu in a moment…")
            handler.postDelayed({ if (finished) finish() }, 5000)
        } else {
            instruction("Calibration failed ⚠️", "Adjust the headband and repeat")
        }
    }

    // ------------------------------------------------------------------ look

    private fun instruction(main: String, sub: String) {
        tvInstruction.text = main
        tvStep.text = sub
    }

    private fun action(main: String, sub: String, progress: Int) {
        instruction(main, sub)
        progressBar.progress = progress
        try { tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 150) } catch (_: Exception) { }
    }

    private fun click(button: Button) {
        button.isPressed = true
        handler.postDelayed({ button.isPressed = false; button.performClick() }, 250)
    }
}