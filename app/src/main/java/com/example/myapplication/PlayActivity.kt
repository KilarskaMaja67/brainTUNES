//  sa 4 przyciski akordow, kazdym z nich gra sie innym ruchem glowy:
//  glowa w lewo = C, glowa w prawo = G, glowa w gore = a-moll, glowa w dol = F
//  mocne mrugniecia = powrot

package com.example.myapplication

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class PlayActivity : AppCompatActivity() {

    private lateinit var CdurBtn: Button
    private lateinit var GdurBtn: Button
    private lateinit var AmolBtn: Button
    private lateinit var FdurBtn: Button

    private lateinit var exitHintText: TextView

    // frames around the tiles - the ring of the played chord is drawn on them
    private lateinit var rings: Map<Akordy.Akord, View>

    private lateinit var akordy: Akordy

    private var blink_counter = 0
    private val handler = Handler(Looper.getMainLooper())
    private val reset_blinks_Runnable = Runnable {
        blink_counter = 0
        exitHintText.text = "blink twice to exit"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_play)

        akordy = Akordy(this)

        CdurBtn = findViewById(R.id.CdurBtn)
        GdurBtn = findViewById(R.id.GdurBtn)
        AmolBtn = findViewById(R.id.AmolBtn)
        FdurBtn = findViewById(R.id.FdurBtn)
        exitHintText = findViewById(R.id.exitHintText)

        rings = mapOf(
            Akordy.Akord.C to findViewById<View>(R.id.ringC),
            Akordy.Akord.G to findViewById<View>(R.id.ringG),
            Akordy.Akord.AM to findViewById<View>(R.id.ringAm),
            Akordy.Akord.F to findViewById<View>(R.id.ringF),
        )

        // big chord letter + small gesture underneath
        CdurBtn.setChordLabel("C", "⬅ left")
        GdurBtn.setChordLabel("G", "right ➡")
        AmolBtn.setChordLabel("a", "⬆ up")
        FdurBtn.setChordLabel("F", "⬇ down")

        CdurBtn.setOnClickListener { play_chord(Akordy.Akord.C) }
        GdurBtn.setOnClickListener { play_chord(Akordy.Akord.G) }
        AmolBtn.setOnClickListener { play_chord(Akordy.Akord.AM) }
        FdurBtn.setOnClickListener { play_chord(Akordy.Akord.F) }

    }

    override fun onResume() {
        super.onResume()
        AppControler.ustawAkcje(
            left = { CdurBtn.performClick() },    // C
            right = { GdurBtn.performClick() },   // G
            up = { AmolBtn.performClick() },    // a-moll
            down = { FdurBtn.performClick() },     // F
            blink = { blink_to_exit() },     // mocne mrugniecie = powrot do menu
        )
    }

    /** Plays the chord and puts a ring in its colour around its tile (the others lose theirs). */
    private fun play_chord(chord: Akordy.Akord) {
        akordy.play(chord)
        rings.forEach { (c, ring) -> ring.setRing(if (c == chord) chordColor(c) else null) }
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

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(reset_blinks_Runnable)
        akordy.free()
    }
}