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

    private val loaded = mutableSetOf<Int>()

    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        )
        .build()
        .apply {
            setOnLoadCompleteListener { _, sampleId, status ->
                if (status == 0) loaded.add(sampleId)
                else Log.e("Chords", "Failed to load audio $sampleId")
            }
            dzwieki[Akord.C] = sp.load(context, R.raw.akord_c, 1)
            dzwieki[Akord.G] = sp.load(context, R.raw.akord_g, 1)
            dzwieki[Akord.AM] = sp.load(context, R.raw.akord_am, 1)
            dzwieki[Akord.F] = sp.load(context, R.raw.akord_f, 1)
            soundPool = sp
        }

    private val sounds: Map<Akord, Int> = mapOf(
        Akord.C to soundPool.load(context, R.raw.akord_c, 1),
        Akord.G to soundPool.load(context, R.raw.akord_g, 1),
        Akord.AM to soundPool.load(context, R.raw.akord_am, 1),
        Akord.F to soundPool.load(context, R.raw.akord_f, 1),
    )

    fun play(akord: Akord) {
        val id = sounds.getValue(akord)
        if (id !in loaded) {
            Log.w("Chords", "$akord are still loading")
            return
        }
        soundPool.play(id, 1.0f, 1.0f, 1, 0, 1.0f)
    }

    fun free() {
        soundPool.release()
    }
}

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

