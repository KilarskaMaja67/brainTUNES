package com.example.myapplication

import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Choreographer
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

data class SongLine(
    val time_ms: Int,
    val lyrics: String,
    val chord_hint: String
)

/** One chord tile on the lane: when it should be played and which chord. */
private data class LaneTile(
    val time_ms: Int,
    val chord: Akordy.Akord,
    val view: TextView
)

/**
 * PLAY ALONG
 *  - chord LANE: tiles slide to the left; a chord should be played when its tile reaches the "now" zone.
 *    Moved every screen frame (Choreographer) with translationX only -> smooth, no relayout.
 *  - LYRICS: separate from the chords - the whole line changes with a short fade (no scrolling text).
 *  - COMBO: playing the right chord in time adds +1, a wrong chord resets it.
 *  - chord cross at the bottom: head left = C, right = G, up = a, down = F; played chord gets a ring.
 */
class SongPlayActivity : AppCompatActivity() {

    private companion object {
        const val LANE_SPEED_DP_PER_S = 110f   // how fast tiles move (bigger = more space between chords)
        const val TILE_DP = 52f
        const val EARLY_MS = 400               // a chord played up to 0.4 s before its tile arrives still counts
        const val FADE_MS = 140L

        /**
         * SYNC: the phone/emulator plays the sound a bit LATER than MediaPlayer reports
         * (audio buffer). We move the tiles + lyrics back by this many ms so they match what you HEAR.
         *   tiles reach the line BEFORE you hear the chord change -> make it BIGGER (e.g. 300)
         *   tiles reach the line AFTER you hear it                 -> make it SMALLER (e.g. 50)
         * Emulator usually needs ~200-350, a real phone ~50-150.
         */
        const val SYNC_OFFSET_MS = 250
    }

    private lateinit var songTitleText: TextView
    private lateinit var songProgress: ProgressBar
    private lateinit var chordLane: FrameLayout
    private lateinit var hitZone: View
    private lateinit var tvLyricsCurrent: TextView
    private lateinit var tvLyricsNext: TextView
    private lateinit var tvCombo: TextView

    private lateinit var CdurBtn: Button
    private lateinit var GdurBtn: Button
    private lateinit var AmolBtn: Button
    private lateinit var FdurBtn: Button
    private lateinit var rings: Map<Akordy.Akord, View>

    private lateinit var exitHintText: TextView

    private lateinit var akordy: Akordy
    private var media_player: MediaPlayer? = null
    private var paused_by_app = false          // true = we paused the song in onPause (e.g. Home button)
    private var current_script: List<SongLine> = emptyList()

    private val lane_tiles = mutableListOf<LaneTile>()
    private var px_per_ms = 0f
    private var active_line_index = -2         // -2 = nothing shown yet

    private var combo = 0
    private var last_scored_tile_ms = -1       // each tile can be scored only once

    // smooth clock: MediaPlayer.currentPosition jumps in steps on many phones, so we fill the gaps
    private var last_reported_ms = -1
    private var last_reported_at = 0L
    private var shown_ms = 0
    private var visual_ms = 0                  // song time that is shown on screen (= what you hear)

    private var blink_counter = 0
    private val handler = Handler(Looper.getMainLooper())

    private val reset_blinks_Runnable = Runnable {
        blink_counter = 0
        exitHintText.text = "blink twice to exit"
    }

    // called once per screen frame (~60x per second) while the screen is visible
    private var frames_running = false
    private val frame_callback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!frames_running) return
            on_frame()
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_song_play)

        akordy = Akordy(this)

        songTitleText = findViewById(R.id.songTitleText)
        songProgress = findViewById(R.id.songProgress)
        chordLane = findViewById(R.id.chordLane)
        hitZone = findViewById(R.id.hitZone)
        tvLyricsCurrent = findViewById(R.id.tvLyricsCurrent)
        tvLyricsNext = findViewById(R.id.tvLyricsNext)
        tvCombo = findViewById(R.id.tvCombo)
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

        px_per_ms = dp(LANE_SPEED_DP_PER_S) / 1000f

        val song_number = intent.getIntExtra("NUMBER_OF_SONG", 1)   // MUST be the same key as in SongActivity
        load_song(song_number)

        CdurBtn.setOnClickListener { play_chord(Akordy.Akord.C) }
        GdurBtn.setOnClickListener { play_chord(Akordy.Akord.G) }
        AmolBtn.setOnClickListener { play_chord(Akordy.Akord.AM) }
        FdurBtn.setOnClickListener { play_chord(Akordy.Akord.F) }
    }

    // ------------------------------------------------------------------ chords + combo

    private fun play_chord(chord: Akordy.Akord) {
        akordy.play(chord)
        rings.forEach { (c, ring) -> ring.setRing(if (c == chord) chordColor(c) else null) }
        score(chord)
    }

    /** Right chord for the current (or just arriving) tile = combo +1, wrong chord = combo 0. */
    private fun score(played: Akordy.Akord) {
        val expected = lane_tiles.lastOrNull { it.time_ms <= visual_ms + EARLY_MS } ?: return
        if (expected.chord == played) {
            if (expected.time_ms != last_scored_tile_ms) {
                last_scored_tile_ms = expected.time_ms
                combo++
                expected.view.animate().scaleX(1.25f).scaleY(1.25f).setDuration(90)
                    .withEndAction { expected.view.animate().scaleX(1f).scaleY(1f).setDuration(120).start() }
                    .start()
            }
        } else {
            combo = 0
        }
        tvCombo.text = if (combo >= 2) "🔥 Combo × $combo" else " "
    }

    // ------------------------------------------------------------------ building the lane

    /** One tile for every chord CHANGE in the .lrc file. */
    private fun build_lane() {
        lane_tiles.forEach { chordLane.removeView(it.view) }
        lane_tiles.clear()

        val size = dp(TILE_DP).toInt()
        var previous: Akordy.Akord? = null
        current_script.forEach { line ->
            val chord = chordFromText(line.chord_hint) ?: return@forEach
            if (chord == previous) return@forEach
            previous = chord

            val tile = TextView(this).apply {
                text = chord.label
                textSize = 22f
                setTextColor(android.graphics.Color.WHITE)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                gravity = Gravity.CENTER
                background = roundedBg(chordColor(chord), 16f)
                layoutParams = FrameLayout.LayoutParams(size, size, Gravity.CENTER_VERTICAL or Gravity.START)
                visibility = View.INVISIBLE
            }
            chordLane.addView(tile)
            lane_tiles.add(LaneTile(line.time_ms, chord, tile))
        }
    }

    // ------------------------------------------------------------------ every frame

    private fun on_frame() {
        val mp = media_player ?: return
        val now_ms = (smooth_position(mp) - SYNC_OFFSET_MS).coerceAtLeast(0)
        visual_ms = now_ms

        update_lane(now_ms)
        update_lyrics(now_ms)
        songProgress.progress = now_ms
    }

    /** Position of the song in ms that moves smoothly every frame and never jumps back. */
    private fun smooth_position(mp: MediaPlayer): Int {
        if (!mp.isPlaying) return shown_ms
        val now = SystemClock.uptimeMillis()
        val reported = mp.currentPosition
        if (reported != last_reported_ms) {
            last_reported_ms = reported
            last_reported_at = now
        }
        var estimate = last_reported_ms + (now - last_reported_at).toInt()
        estimate = estimate.coerceAtMost(reported + 250)   // don't run away if the player stalls
        if (estimate > shown_ms) shown_ms = estimate
        return shown_ms
    }

    /** Only translationX changes - this is what makes it smooth. */
    private fun update_lane(now_ms: Int) {
        val lane_width = chordLane.width
        if (lane_width == 0) return
        val hit_center = hitZone.left + hitZone.width / 2f
        val half = dp(TILE_DP) / 2f

        for (tile in lane_tiles) {
            val x = hit_center - half + (tile.time_ms - now_ms) * px_per_ms
            if (x < -2 * half || x > lane_width) {
                if (tile.view.visibility != View.INVISIBLE) tile.view.visibility = View.INVISIBLE
                continue
            }
            if (tile.view.visibility != View.VISIBLE) tile.view.visibility = View.VISIBLE
            tile.view.translationX = x
            tile.view.alpha = if (tile.time_ms < now_ms - 600) 0.35f else 1f   // already passed = faded
        }
    }

    /** Lyrics change only when the line changes: whole line fades out and the new one fades in. */
    private fun update_lyrics(now_ms: Int) {
        if (current_script.isEmpty()) return
        val index = current_script.indexOfLast { now_ms >= it.time_ms }
        if (index == active_line_index) return
        active_line_index = index
        if (index >= 0) {
            // Logcat (filter "Sync"): how late the line changed compared to the .lrc - should be 0-20 ms
            Log.d("Sync", "line ${index + 1} '${current_script[index].lyrics}' lrc=${current_script[index].time_ms} shown=$now_ms")
        }

        val current = if (index >= 0) current_script[index].lyrics.ifBlank { "♪" } else "♪"
        val next = current_script.getOrNull(index + 1)?.lyrics ?: ""

        swap_text(tvLyricsCurrent, current, 1f)
        swap_text(tvLyricsNext, next, 0.6f)
    }

    private fun swap_text(view: TextView, text: String, visible_alpha: Float) {
        view.animate().cancel()
        view.animate().alpha(0f).setDuration(FADE_MS).withEndAction {
            view.text = text
            view.animate().alpha(visible_alpha).setDuration(FADE_MS).start()
        }.start()
    }

    // ------------------------------------------------------------------ song loading (as before)

    private fun load_song(song_number: Int) {
        // title, backing track (.mp3), lyrics + chords (.lrc) - both files in res/raw
        val (title, audio_res_id, lrc_res_id) = when (song_number) {
            1 -> Triple("Zawsze tam gdzie ty", R.raw.zawsze_tam_gdzie_ty_m, R.raw.zawsze_tam_gdzie_ty)
            2 -> Triple("When I'm Gone", R.raw.cups_m, R.raw.cups)
            else -> Triple("Stay With Me", R.raw.stay_with_me_m, R.raw.stay_with_me)
        }

        songTitleText.text = title
        current_script = load_lrc_file(lrc_res_id)
        build_lane()

        media_player = MediaPlayer.create(this, audio_res_id)?.apply {
            setVolume(0.5f, 0.5f)
            songProgress.max = duration
            start()
        }
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

    // ------------------------------------------------------------------ lifecycle

    override fun onPause() {
        super.onPause()
        // stop the backing track when the screen disappears (Home button, other app) - otherwise it keeps playing
        if (media_player?.isPlaying == true) {
            media_player?.pause()
            paused_by_app = true
        }
        frames_running = false
        Choreographer.getInstance().removeFrameCallback(frame_callback)
    }

    override fun onResume() {
        super.onResume()
        // resume the backing track if we paused it in onPause
        if (paused_by_app) {
            media_player?.start()
            paused_by_app = false
        }
        last_reported_ms = -1   // restart the smooth clock from the real position
        if (!frames_running) {
            frames_running = true
            Choreographer.getInstance().postFrameCallback(frame_callback)
        }
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
        frames_running = false
        Choreographer.getInstance().removeFrameCallback(frame_callback)
        handler.removeCallbacks(reset_blinks_Runnable)
        media_player?.stop()
        media_player?.release()
        media_player = null
        akordy.free()
    }
}