package com.example.myapplication

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
import android.widget.Button

/**
 * Wspolny odtwarzacz akordow dla PlayActivity i SongPlayActivity.
 * Dzwieki wczytuja sie raz (przy tworzeniu), potem graja bez opoznien.
 */
class Akordy(context: Context) {

    enum class Akord { C, G, AM, F }

    private val zaladowane = mutableSetOf<Int>()

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
                if (status == 0) zaladowane.add(sampleId)
                else Log.e("Akordy", "Nie udalo sie zaladowac dzwieku $sampleId")
            }
        }

    private val dzwieki: Map<Akord, Int> = mapOf(
        Akord.C to soundPool.load(context, R.raw.akord_c, 1),
        Akord.G to soundPool.load(context, R.raw.akord_g, 1),
        Akord.AM to soundPool.load(context, R.raw.akord_am, 1),
        Akord.F to soundPool.load(context, R.raw.akord_f, 1),
    )

    fun graj(akord: Akord) {
        val id = dzwieki.getValue(akord)
        if (id !in zaladowane) {
            Log.w("Akordy", "$akord jeszcze sie laduje")
            return
        }
        // ID, glosnosc lewy, glosnosc prawy, priorytet, petla (0 = brak), tempo
        soundPool.play(id, 1.0f, 1.0f, 1, 0, 1.0f)
    }

    fun zwolnij() {
        soundPool.release()
    }
}

/** Krotka animacja "wcisniecia" przycisku. */
fun Button.animujKlik() {
    animate()
        .scaleX(1.08f)
        .scaleY(1.08f)
        .setDuration(100)
        .withEndAction {
            animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()
        }
        .start()
}