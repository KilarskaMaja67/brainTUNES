package com.example.myapplication

import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

data class LinijkaPiosenki(
    val czasMs: Int,
    val tekst: String,
    val akordPodpowiedz: String
)

// sterowanie jak w PlayActivity:
//   glowa w lewo = C, glowa w prawo = G, glowa w gore = a-moll, glowa w dol = F, mocne mrugniecie = powrot

class SongPlayActivity : AppCompatActivity() {

    private lateinit var CdurBtn: Button
    private lateinit var GdurBtn: Button
    private lateinit var AmolBtn: Button
    private lateinit var FdurBtn: Button
    private lateinit var chordHintText: TextView
    private lateinit var lyricsText: TextView

    private lateinit var akordy: Akordy

    private var mediaPlayer: MediaPlayer? = null
    private var wstrzymanaPrzezPauze = false
    private val handler = Handler(Looper.getMainLooper())
    private var aktualnyScenariusz: List<LinijkaPiosenki> = emptyList()

    private val sprawdzCzasRunnable = object : Runnable {
        override fun run() {
            val mp = mediaPlayer
            if (mp != null && mp.isPlaying) {
                val aktualnyCzas = mp.currentPosition
                val aktualnaLinijka = aktualnyScenariusz.lastOrNull { aktualnyCzas >= it.czasMs }
                if (aktualnaLinijka != null) {
                    lyricsText.text = aktualnaLinijka.tekst
                    chordHintText.text = aktualnaLinijka.akordPodpowiedz
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
        val backBtn = findViewById<Button>(R.id.backBtn)

        val numerPiosenki = intent.getIntExtra("NUMER_PIOSENKI", 1)
        zaladujPiosenke(numerPiosenki)

        CdurBtn.setOnClickListener {
            CdurBtn.animujKlik()
            akordy.graj(Akordy.Akord.C)
        }

        GdurBtn.setOnClickListener {
            GdurBtn.animujKlik()
            akordy.graj(Akordy.Akord.G)
        }

        AmolBtn.setOnClickListener {
            AmolBtn.animujKlik()
            akordy.graj(Akordy.Akord.AM)
        }

        FdurBtn.setOnClickListener {
            FdurBtn.animujKlik()
            akordy.graj(Akordy.Akord.F)
        }

        backBtn.setOnClickListener {
            finish()
        }
    }

    private fun zaladujPiosenke(numer: Int) {
        // TODO: inne piosenki - na razie kazdy numer gra te sama
        val (plikAudioId, plikLrcId) = when (numer) {
            1 -> R.raw.zawsze_tam_gdzie_ty_m to R.raw.zawsze_tam_gdzie_ty
            else -> R.raw.zawsze_tam_gdzie_ty_m to R.raw.zawsze_tam_gdzie_ty
        }

        aktualnyScenariusz = wczytajPlikLrc(plikLrcId)

        mediaPlayer = MediaPlayer.create(this, plikAudioId)?.apply {
            setVolume(0.5f, 0.5f)
            start()
        }

        handler.post(sprawdzCzasRunnable)
    }

    private fun wczytajPlikLrc(resId: Int): List<LinijkaPiosenki> {
        val wynik = mutableListOf<LinijkaPiosenki>()
        val regex = Regex("""\[(\d+):(\d+)(?:\.(\d+))?\](.*)""")
        var ostatniAkord = "Graj akordy!"

        resources.openRawResource(resId).bufferedReader().useLines { linie ->
            linie.forEach { linia ->
                val dopasowanie = regex.find(linia.trim())
                if (dopasowanie != null) {
                    val minuty = dopasowanie.groupValues[1].toIntOrNull() ?: 0
                    val sekundy = dopasowanie.groupValues[2].toIntOrNull() ?: 0
                    val ulamekTekst = dopasowanie.groupValues[3]
                    val milisekundy = if (ulamekTekst.isNotEmpty()) {
                        ulamekTekst.padEnd(3, '0').take(3).toIntOrNull() ?: 0
                    } else {
                        0
                    }

                    val calkowityCzasMs = (minuty * 60 + sekundy) * 1000 + milisekundy
                    val tresc = dopasowanie.groupValues[4].trim()

                    if (tresc.contains("|")) {
                        val czesci = tresc.split("|", limit = 2)
                        ostatniAkord = "Akord: ${czesci[0].trim()}"
                        val tekstPiosenki = czesci[1].trim()
                        wynik.add(LinijkaPiosenki(calkowityCzasMs, tekstPiosenki, ostatniAkord))
                    } else {
                        wynik.add(LinijkaPiosenki(calkowityCzasMs, tresc, ostatniAkord))
                    }
                }
            }
        }
        return wynik.sortedBy { it.czasMs }
    }

    override fun onResume() {
        super.onResume()
        AppControler.ustawAkcje(
            lewo = { CdurBtn.performClick() },    // C
            prawo = { GdurBtn.performClick() },   // G
            gora = { AmolBtn.performClick() },    // a-moll
            dol = { FdurBtn.performClick() },     // F
            mrugniecie = { finish() },            // mocne mrugniecie = powrot
        )
        // wznow muzyke, jesli zatrzymalismy ja przy wyjsciu z ekranu
        if (wstrzymanaPrzezPauze) {
            mediaPlayer?.start()
            wstrzymanaPrzezPauze = false
        }
    }

    override fun onPause() {
        super.onPause()
        // zatrzymaj muzyke, gdy ekran znika (np. przycisk Home) - inaczej grala by w tle
        if (mediaPlayer?.isPlaying == true) {
            mediaPlayer?.pause()
            wstrzymanaPrzezPauze = true
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(sprawdzCzasRunnable)
        mediaPlayer?.release()
        mediaPlayer = null
        akordy.zwolnij()
    }
}