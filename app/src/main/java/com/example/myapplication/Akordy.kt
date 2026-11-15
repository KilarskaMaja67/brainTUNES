package com.example.myapplication

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button

/**
 * Wspolny odtwarzacz akordow (Play, SongPlay, Tutorial).
 *
 * - Dzwieki wczytuja sie RAZ na cala aplikacje (przy pierwszym uzyciu) i zostaja w pamieci,
 *   wiec po wejsciu na kolejny ekran graja od razu - bez "jeszcze sie laduje".
 * - Nowy akord plynnie wycisza poprzedni (jak na gitarze), zamiast nakladac sie na niego.
 *   Dzieki temu nie przekraczamy limitu strumieni i SoundPool nie ucina dzwiekow w pol.
 *
 * Uzycie bez zmian: Akordy(this), akordy.play(Akordy.Akord.C), akordy.zwolnij().
 */
class Akordy(context: Context) {

    enum class Akord { C, G, AM, F }

    init {
        Wspolne.zaladuj(context.applicationContext)
    }

    fun play(akord: Akord) = Wspolne.play(akord)

    /** Nic nie robi - dzwieki zostaja w pamieci na cala aplikacje (zeby nie ladowac ich od nowa). */
    fun zwolnij() {}

    // ---------- jeden SoundPool na cala aplikacje ----------
    private object Wspolne {
        const val ZATRZYMUJ_POPRZEDNI = true   // false = akordy nakladaja sie na siebie
        const val FADE_MS = 120L               // jak szybko wycisza sie poprzedni akord

        private var soundPool: SoundPool? = null
        private val dzwieki = mutableMapOf<Akord, Int>()
        private val zaladowane = mutableSetOf<Int>()
        private val handler = Handler(Looper.getMainLooper())
        private var ostatniStream = 0

        fun zaladuj(context: Context) {
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
                if (status == 0) zaladowane.add(sampleId)
                else Log.e("Akordy", "Nie udalo sie zaladowac dzwieku $sampleId")
            }
            dzwieki[Akord.C] = sp.load(context, R.raw.akord_c, 1)
            dzwieki[Akord.G] = sp.load(context, R.raw.akord_g, 1)
            dzwieki[Akord.AM] = sp.load(context, R.raw.akord_am, 1)
            dzwieki[Akord.F] = sp.load(context, R.raw.akord_f, 1)
            soundPool = sp
        }

        fun play(akord: Akord) {
            val sp = soundPool ?: return
            val id = dzwieki[akord] ?: return
            if (id !in zaladowane) {
                Log.w("Akordy", "$akord jeszcze sie laduje")
                return
            }
            if (ZATRZYMUJ_POPRZEDNI) wycisz(sp, ostatniStream)
            // ID, glosnosc lewy, glosnosc prawy, priorytet, petla (0 = brak), tempo
            ostatniStream = sp.play(id, 1.0f, 1.0f, 1, 0, 1.0f)
        }

        /** Plynne wyciszenie (bez "klikniecia", ktore daje nagle stop). */
        private fun wycisz(sp: SoundPool, streamId: Int) {
            if (streamId == 0) return
            val kroki = 6
            var krok = 0
            handler.post(object : Runnable {
                override fun run() {
                    krok++
                    val glosnosc = 1f - krok.toFloat() / kroki
                    if (glosnosc <= 0f) {
                        sp.stop(streamId)
                    } else {
                        sp.setVolume(streamId, glosnosc, glosnosc)
                        handler.postDelayed(this, FADE_MS / kroki)
                    }
                }
            })
        }
    }
}

