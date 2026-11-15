package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity


class MainActivity : AppCompatActivity() {

    //which button is selected (0 = config, 1 = tutorial, 2 = play, 3 = song)
    private var selected_index = 0

    //lista with 4 buttons from XML
    private lateinit var list_buttons: List<Button>

    private lateinit var tvConnection: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val check_connection_Runnable = object : Runnable {
        override fun run() {
            update_connection_pill()
            handler.postDelayed(this, 1000L)
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //CONNECTION TO THE WEARABLE - initialized once for the entire application
        // emulator: "10.0.2.2"; real phone: laptop's IP address (from `ipconfig`)
        AppControler.start(laptopIp = "10.183.103.159")


        val configBtn = findViewById<Button>(R.id.configBtn)
        val tutorialBtn = findViewById<Button>(R.id.tutorialBtn)
        val playBtn = findViewById<Button>(R.id.playBtn)
        val songBtn = findViewById<Button>(R.id.songBtn)

        list_buttons = listOf(configBtn, tutorialBtn, playBtn, songBtn)
        tvConnection = findViewById(R.id.tvConnection)


        configBtn.setOnClickListener {
            selected_index = 0
            updateLook()
            startActivity(Intent(this, ConfigActivity::class.java))
        }

        tutorialBtn.setOnClickListener {
            selected_index = 1
            updateLook()
            startActivity(Intent(this, TutorialActivity::class.java))
        }

        playBtn.setOnClickListener {
            selected_index = 2
            updateLook()
            startActivity(Intent(this, PlayActivity::class.java))
        }

        songBtn.setOnClickListener {
            selected_index = 3
            updateLook()
            startActivity(Intent(this, SongActivity::class.java))
        }

        updateLook()
    }

    private var openingCalibration = false

    private fun openCalibrationIfNeeded() {
        if (openingCalibration) return
        openingCalibration = true
        startActivity(Intent(this, ConfigActivity::class.java))
    }

    override fun onPause() {
        super.onPause()
        AppControler.naBrakKalibracji = null
        handler.removeCallbacks(check_connection_Runnable)
    }

    private fun update_connection_pill() {
        if (AppControler.czyPolaczono()) {
            tvConnection.text = "● Headband connected"
            tvConnection.setTextColor(brainColor(R.color.brain_green))
            tvConnection.setBackgroundResource(R.drawable.bg_pill_ok)
        } else {
            tvConnection.text = "● Waiting for the headband…"
            tvConnection.setTextColor(brainColor(R.color.brain_orange))
            tvConnection.setBackgroundResource(R.drawable.bg_pill_warn)
        }
    }

    // so that the app controller decides what happens
    override fun onResume() {
        super.onResume()
        openingCalibration = false
        AppControler.naBrakKalibracji = { openCalibrationIfNeeded() }
        handler.removeCallbacks(check_connection_Runnable)
        handler.post(check_connection_Runnable)
        if (AppControler.czySkalibrowano() == false) openCalibrationIfNeeded()
        AppControler.ustawAkcje(
            up = { upMove() },
            down = { downMove() },
            right = { rightMove() },
            left = null,
            blink = null
        )
    }

    // change how picked button looks
    private fun updateLook() {
        list_buttons.forEachIndexed { index, button ->
            button.setSelectedLook(index == selected_index)  
        }
    }


    private fun upMove() {
        runOnUiThread {
            selected_index = if (selected_index - 1 < 0) list_buttons.size - 1 else selected_index - 1
            updateLook()
        }
    }

    private fun downMove() {
        runOnUiThread {
            selected_index = (selected_index + 1) % list_buttons.size
            updateLook()
        }
    }

    private fun rightMove() {
        runOnUiThread {
            list_buttons[selected_index].performClick()
        }
    }
}
