package com.example.myapplication

import android.animation.ValueAnimator
import android.graphics.Color
import android.graphics.Typeface
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

data class SongLine(
    val time_ms: Int,
    val lyrics: String,
    val chord_hint: String
)

private data class RibbonItem(
    val block: LinearLayout,
    val lyrics_tv: TextView,
    val chord_tv: TextView,
    var animator: ValueAnimator? = null
)

class SongPlayActivity : AppCompatActivity() {

    private lateinit var songTitleText: TextView

    private lateinit var CdurBtn: Button
    private lateinit var GdurBtn: Button
    private lateinit var AmolBtn: Button
    private lateinit var FdurBtn: Button

    private lateinit var lyricsScrollView: HorizontalScrollView
    private lateinit var lyricsRibbon: LinearLayout
    private lateinit var exitHintText: TextView

    private lateinit var akordy: Akordy
    private var media_player: MediaPlayer? = null
    private var current_script: List<SongLine> = emptyList()
    private val line_views = mutableListOf<RibbonItem>()
    private var active_line_index = -1

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
                update_ribbon_scroll(mp.currentPosition)
            }
            handler.postDelayed(this, 30L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_song_play)

        akordy = Akordy(this)

        songTitleText = findViewById(R.id.songTitleText)
        lyricsScrollView = findViewById(R.id.lyricsScrollView)
        lyricsRibbon = findViewById(R.id.lyricsRibbon)
        CdurBtn = findViewById(R.id.CdurBtn)
        GdurBtn = findViewById(R.id.GdurBtn)
        AmolBtn = findViewById(R.id.AmolBtn)
        FdurBtn = findViewById(R.id.FdurBtn)
        exitHintText = findViewById(R.id.exitHintText)

        // Blokuje reczne przesuwanie palcem, zeby nie zepsuc synchronizacji z muzyka
        lyricsScrollView.setOnTouchListener { _, _ -> true }

        val song_number = intent.getIntExtra("NUMBER_OF_SONG", 1)
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

    private fun get_chord_color(chord_text: String): Int {
        val clean = chord_text.trim()
        return when {
            clean.startsWith("C", ignoreCase = true) -> Color.parseColor("#FF3B30") // czerwony
            clean.startsWith("F", ignoreCase = true) -> Color.parseColor("#2196F3") // niebieski
            clean.startsWith("G", ignoreCase = true) -> Color.parseColor("#FF69B4") // rozowy
            clean.startsWith("a", ignoreCase = true) -> Color.parseColor("#FFD600") // zolty
            else -> Color.WHITE
        }
    }

    private fun build_song_ribbon() {
        lyricsRibbon.removeAllViews()
        line_views.clear()
        active_line_index = -1

        val screen_width = resources.displayMetrics.widthPixels
        val word_space = (6 * resources.displayMetrics.density).toInt()

        lyricsRibbon.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(screen_width, 1)
        })

        current_script.forEach { line ->
            val block = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(word_space, 0, word_space, 0)
                alpha = 0.6f
            }

            val lyrics_tv = TextView(this).apply {
                text = line.lyrics
                textSize = 18f
                setTypeface(null, Typeface.BOLD)
                maxLines = 1
            }

            val chord_tv = TextView(this).apply {
                text = line.chord_hint
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
                setTextColor(get_chord_color(line.chord_hint))
                maxLines = 1
            }

            block.addView(lyrics_tv)
            block.addView(chord_tv)
            lyricsRibbon.addView(block)
            line_views.add(RibbonItem(block, lyrics_tv, chord_tv))
        }

        lyricsRibbon.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(screen_width, 1)
        })
    }

    private fun animate_ribbon_item(item: RibbonItem, active: Boolean) {
        item.animator?.cancel()

        val start_lyrics = item.lyrics_tv.textSize / resources.displayMetrics.scaledDensity
        val start_chord = item.chord_tv.textSize / resources.displayMetrics.scaledDensity
        val start_alpha = item.block.alpha

        val target_lyrics = if (active) 23f else 18f
        val target_chord = if (active) 20f else 16f
        val target_alpha = if (active) 1.0f else 0.6f

        item.animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 250L
            addUpdateListener { anim ->
                val t = anim.animatedValue as Float
                item.lyrics_tv.textSize = start_lyrics + (target_lyrics - start_lyrics) * t
                item.chord_tv.textSize = start_chord + (target_chord - start_chord) * t
                item.block.alpha = start_alpha + (target_alpha - start_alpha) * t
            }
            start()
        }
    }

    private fun highlight_active_block(new_index: Int) {
        if (active_line_index in line_views.indices) {
            animate_ribbon_item(line_views[active_line_index], active = false)
        }

        if (new_index in line_views.indices) {
            animate_ribbon_item(line_views[new_index], active = true)
        }

        active_line_index = new_index
    }

    private fun update_ribbon_scroll(current_time: Int) {
        if (line_views.isEmpty() || lyricsScrollView.width == 0) return

        val half_screen = lyricsScrollView.width / 2f
        val current_index = current_script.indexOfLast { current_time >= it.time_ms }

        if (current_index != active_line_index) {
            highlight_active_block(current_index)
        }

        val target_center_x: Float = when {
            current_index == -1 -> {
                val first_view = line_views[0].block
                val first_center = first_view.left + first_view.width / 2f
                val t0 = current_script[0].time_ms
                val progress = if (t0 > 0) (current_time.toFloat() / t0).coerceIn(0f, 1f) else 1f
                (first_center - half_screen) + progress * half_screen
            }
            current_index in 0 until current_script.lastIndex -> {
                val v0 = line_views[current_index].block
                val v1 = line_views[current_index + 1].block
                val c0 = v0.left + v0.width / 2f
                val c1 = v1.left + v1.width / 2f
                val t0 = current_script[current_index].time_ms
                val t1 = current_script[current_index + 1].time_ms
                val progress = if (t1 > t0) ((current_time - t0).toFloat() / (t1 - t0)).coerceIn(0f, 1f) else 1f
                c0 + progress * (c1 - c0)
            }
            else -> {
                val last_view = line_views.last().block
                last_view.left + last_view.width / 2f
            }
        }

        lyricsScrollView.scrollTo((target_center_x - half_screen).toInt(), 0)
    }

    private fun load_song(song_number: Int) {
        val (title, audio_res_id, lrc_res_id) = if (song_number == 1) {
            Triple("Zawsze tam gdzie ty", R.raw.zawsze_tam_gdzie_ty_m, R.raw.zawsze_tam_gdzie_ty)
        } else {
            Triple("Piosenka 2", R.raw.zawsze_tam_gdzie_ty_m, R.raw.zawsze_tam_gdzie_ty)
        }

        songTitleText.text = title
        current_script = load_lrc_file(lrc_res_id)
        build_song_ribbon()

        media_player = MediaPlayer.create(this, audio_res_id)?.apply {
            setVolume(0.5f, 0.5f)
            start()
        }

        handler.post(check_time_Runnable)
    }

    private fun load_lrc_file(res_id: Int): List<SongLine> {
        val result = mutableListOf<SongLine>()
        val regex = Regex("""\[(\d+):(\d+)(?:\.(\d+))?\](.*)""")
        var last_chord = "Play chords"

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
                        last_chord = parts[0].trim()
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
            up = { AmolBtn.performClick() },      // a-moll
            down = { FdurBtn.performClick() },    // F
            blink = { blink_to_exit() },          // back
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
        line_views.forEach { it.animator?.cancel() }
        handler.removeCallbacks(check_time_Runnable)
        handler.removeCallbacks(reset_blinks_Runnable)
        media_player?.stop()
        media_player?.release()
        media_player = null
        akordy.free()
    }
}