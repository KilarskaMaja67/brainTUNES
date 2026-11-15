package com.example.myapplication

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button

class Akordy(context: Context) {

    enum class Akord { C, G, AM, F }

    init {
        Shared.load(context.applicationContext)
    }

    fun play(akord: Akord) = Shared.play(akord)


    fun free() {}

    fun zwolnij() = free()

    //one spindpool for app
    private object Shared {
        const val STOP_PREVIOUS = true      // false = chords overlap
        const val FADE_MS = 120L            // how fast the previous chord fades out

        private var soundPool: SoundPool? = null
        private val sounds = mutableMapOf<Akord, Int>()
        private val loaded = mutableSetOf<Int>()
        private val handler = Handler(Looper.getMainLooper())
        private var lastStream = 0

        fun load(context: Context) {
            if (soundPool != null) return
            val sp = SoundPool.Builder()
                .setMaxStreams(8)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .build()
            sp.setOnLoadCompleteListener { _, sampleId, status ->
                if (status == 0) loaded.add(sampleId)
                else Log.e("Chords", "Failed to load audio $sampleId")
            }
            sounds[Akord.C] = sp.load(context, R.raw.akord_c, 1)
            sounds[Akord.G] = sp.load(context, R.raw.akord_g, 1)
            sounds[Akord.AM] = sp.load(context, R.raw.akord_am, 1)
            sounds[Akord.F] = sp.load(context, R.raw.akord_f, 1)
            soundPool = sp
        }

        fun play(akord: Akord) {
            val sp = soundPool ?: return
            val id = sounds[akord] ?: return
            if (id !in loaded) {
                Log.w("Chords", "$akord is still loading")
                return
            }
            if (STOP_PREVIOUS) fadeOut(sp, lastStream)
            // ID, left volume, right volume, priority, loop (0 = none), rate
            lastStream = sp.play(id, 1.0f, 1.0f, 1, 0, 1.0f)
        }

        private fun fadeOut(sp: SoundPool, streamId: Int) {
            if (streamId == 0) return
            val steps = 6
            var step = 0
            handler.post(object : Runnable {
                override fun run() {
                    step++
                    val volume = 1f - step.toFloat() / steps
                    if (volume <= 0f) {
                        sp.stop(streamId)
                    } else {
                        sp.setVolume(streamId, volume, volume)
                        handler.postDelayed(this, FADE_MS / steps)
                    }
                }
            })
        }
    }
}

// animation for buton
fun Button.animation_click() {
    animate()
        .scaleX(1.08f)
        .scaleY(1.08f)
        .setDuration(100)
        .withEndAction {
            animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()
        }
        .start()
}
