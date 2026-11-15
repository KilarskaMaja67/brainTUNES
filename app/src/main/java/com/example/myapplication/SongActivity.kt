package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SongActivity : AppCompatActivity() {

    private var selected_index = 0
    private lateinit var list_buttons: List<Button>
    private lateinit var exitHintText: TextView

    private var blink_counter = 0
    private val handler = Handler(Looper.getMainLooper())
    private val reset_blinks_Runnable = Runnable {
        blink_counter = 0
        exitHintText.text = "blink twice to exit"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_song)

        val song1Btn = findViewById<Button>(R.id.song1Btn)
        val song2Btn = findViewById<Button>(R.id.song2Btn)
        exitHintText = findViewById(R.id.exitHintText)

        list_buttons = listOf(song1Btn, song2Btn)

        song1Btn.setOnClickListener {
            selected_index = 0
            update_look()
            open_song(1)
        }

        song2Btn.setOnClickListener {
            selected_index = 1
            update_look()
            open_song(2)
        }


        update_look()
    }

    private fun open_song(song_number: Int) {
        val intent = Intent(this, SongPlayActivity::class.java)
        intent.putExtra("NUMBER_OF_SONG", song_number) // musi być identycznie jak w SongPlayActivity
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        blink_counter = 0
        exitHintText.text = "blink twice to exit"

        AppControler.ustawAkcje(
            up = { move_up() },
            down = { move_down() },
            right = { select_button() },
            left = null,
            blink = { blink_to_exit() }
        )
    }

    private fun blink_to_exit() {
        runOnUiThread {
            blink_counter++
            handler.removeCallbacks(reset_blinks_Runnable)

            if (blink_counter == 1) {
                exitHintText.text = "to exit, blink once more"
                handler.postDelayed(reset_blinks_Runnable, 5000)
            } else if (blink_counter >= 2) {
                finish()
            }
        }
    }

    private fun update_look() {
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

    private fun move_up() {
        runOnUiThread {
            selected_index = if (selected_index - 1 < 0) list_buttons.size - 1 else selected_index - 1
            update_look()
        }
    }

    private fun move_down() {
        runOnUiThread {
            selected_index = (selected_index + 1) % list_buttons.size
            update_look()
        }
    }

    private fun select_button() {
        runOnUiThread {
            list_buttons[selected_index].performClick()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(reset_blinks_Runnable)
    }
}