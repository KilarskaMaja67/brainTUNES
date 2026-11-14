// sa 4 przyciski akordow, kazdym z nich gra sie innym ruchem glowy:
// glowa w lewo = C, glowa w prawo = G, glowa w gore = a-moll, glowa w dol = F
// mocne mrugniecia = powrot

package com.example.myapplication

import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

data class SongLine(
    val time_ms: Int,
    val lyrics: String,
    val chord_hint: String
)

class SongPlayActivity : AppCompatActivity() {

    private lateinit var CdurBtn: Button
    private lateinit var GdurBtn: Button
    private lateinit var AmolBtn: Button
    private lateinit var FdurBtn: Button

    private lateinit var chordHintText: TextView
    private lateinit var lyricsText: TextView
    private lateinit var exitHintText: TextView

    private lateinit var akordy: Akordy
    private var media_player: MediaPlayer? = null
    private var current_script: List<SongLine> = emptyList()

    private var blink_counter = 0
    private val handler = Handler(Looper.getMainLooper())

    private val reset_blinks_Runnable = Runnable {
        blink_counter = 0
        exitHintText.text = "blink twice to exit"
    }

    private val check_time_Runnable = object : Runnable {
        override fun run() {
            val mp = media_player
            if (mp != null && mp.isPlaying) {
                val current_time = mp.currentPosition
                val current_line = current_script.lastOrNull { current_time >= it.time_ms }
                if (current_line != null) {
                    lyricsText.text = current_line.lyrics
                    chordHintText.text = current_line.chord_hint
                }
            }
            handler.postDelayed(this, 200)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_song_play)

        akordy = Akordy(this)

        chordHintText = findViewById(R.id.chordHintText)
        lyricsText = findViewById(R.id.lyricsText)
        CdurBtn = findViewById(R.id.CdurBtn)
        GdurBtn = findViewById(R.id.GdurBtn)
        AmolBtn = findViewById(R.id.AmolBtn)
        FdurBtn = findViewById(R.id.FdurBtn)
        exitHintText = findViewById(R.id.exitHintText)



        val song_number = intent.getIntExtra("NUMER_PIOSENKI", 1)
        load_song(song_number)

        CdurBtn.setOnClickListener {
            CdurBtn.animation_click()
            akordy.play(Akordy.Akord.C)
        }

        GdurBtn.setOnClickListener {
            GdurBtn.animation_click()
            akordy.play(Akordy.Akord.G)
        }

        AmolBtn.setOnClickListener {
            AmolBtn.animation_click()
            akordy.play(Akordy.Akord.AM)
        }

        FdurBtn.setOnClickListener {
            FdurBtn.animation_click()
            akordy.play(Akordy.Akord.F)
        }
    }

    private fun load_song(song_number: Int) {
        val (audio_res_id, lrc_res_id) = if (song_number == 1) {
            R.raw.zawsze_tam_gdzie_ty_m to R.raw.zawsze_tam_gdzie_ty
        } else {
            R.raw.zawsze_tam_gdzie_ty_m to R.raw.zawsze_tam_gdzie_ty
        }

        current_script = load_lrc_file(lrc_res_id)

        media_player = MediaPlayer.create(this, audio_res_id)?.apply {
            setVolume(0.5f, 0.5f)
            start()
        }

        handler.post(check_time_Runnable)
    }

    private fun load_lrc_file(res_id: Int): List<SongLine> {
        val result = mutableListOf<SongLine>()
        val regex = Regex("""\[(\d+):(\d+)(?:\.(\d+))?\](.*)""")
        var last_chord = "Graj akordy!"

        resources.openRawResource(res_id).bufferedReader().useLines { lines ->
            lines.forEach { line ->
                val match = regex.find(line.trim())
                if (match != null) {
                    val minutes = match.groupValues[1].toIntOrNull() ?: 0
                    val seconds = match.groupValues[2].toIntOrNull() ?: 0
                    val fraction_text = match.groupValues[3]
                    val milliseconds = if (fraction_text.isNotEmpty()) {
                        fraction_text.padEnd(3, '0').take(3).toIntOrNull() ?: 0
                    } else {
                        0
                    }

                    val total_time_ms = (minutes * 60 + seconds) * 1000 + milliseconds
                    val content = match.groupValues[4].trim()

                    if (content.contains("|")) {
                        val parts = content.split("|", limit = 2)
                        last_chord = "Akord: ${parts[0].trim()}"
                        val song_lyrics = parts[1].trim()
                        result.add(SongLine(total_time_ms, song_lyrics, last_chord))
                    } else {
                        result.add(SongLine(total_time_ms, content, last_chord))
                    }
                }
            }
        }
        return result.sortedBy { it.time_ms }
    }

    override fun onResume() {
        super.onResume()
        blink_counter = 0
        exitHintText.text = "blink twice to exit"

        AppControler.ustawAkcje(
            left = { CdurBtn.performClick() },    // C
            right = { GdurBtn.performClick() },   // G
            up = { AmolBtn.performClick() },    // a-moll
            down = { FdurBtn.performClick() },     // F
            blink = { blink_to_exit() },     // mocne mrugniecie = powrot do listy
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

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(check_time_Runnable)
        handler.removeCallbacks(reset_blinks_Runnable)
        media_player?.stop()
        media_player?.release()
        media_player = null
        akordy.zwolnij()
    }
}