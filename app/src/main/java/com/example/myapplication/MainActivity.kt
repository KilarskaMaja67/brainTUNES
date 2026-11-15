package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity


class MainActivity : AppCompatActivity() {

    //which button is selected (0 = config, 1 = tutorial, 2 = play, 3 = song)
    private var selected_index = 0

    //lista with 4 buttons from XML
    private lateinit var list_buttons: List<Button>


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //CONNECTION TO THE WEARABLE - initialized once for the entire application
        // emulator: "10.0.2.2"; real phone: laptop's IP address (from `ipconfig`)
        AppControler.start(laptopIp = "10.0.2.2")


        val configBtn = findViewById<Button>(R.id.configBtn)
        val tutorialBtn = findViewById<Button>(R.id.tutorialBtn)
        val playBtn = findViewById<Button>(R.id.playBtn)
        val songBtn = findViewById<Button>(R.id.songBtn)

        list_buttons = listOf(configBtn, tutorialBtn, playBtn, songBtn)

        // FINGER-TAP HANDLING (works now and also serves as a fallback)
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

    // first launch (no calibration saved on the laptop) -> open the calibration screen automatically
    private var openingCalibration = false

    private fun openCalibrationIfNeeded() {
        if (openingCalibration) return
        openingCalibration = true
        startActivity(Intent(this, ConfigActivity::class.java))
    }

    override fun onPause() {
        super.onPause()
        AppControler.naBrakKalibracji = null
    }

    // so that the app controller decides what happens
    override fun onResume() {
        super.onResume()
        openingCalibration = false
        AppControler.naBrakKalibracji = { openCalibrationIfNeeded() }
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
            if (index == selected_index) {
                button.scaleX = 1.15f
                button.scaleY = 1.15f
                button.alpha = 1.0f
            } else {
                button.scaleX = 1.0f
                button.scaleY = 1.0f
                button.alpha = 0.6f
            }
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