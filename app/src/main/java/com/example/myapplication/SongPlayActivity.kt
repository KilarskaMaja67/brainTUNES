package com.example.myapplication

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

data class LinijkaPiosenki(
    val czasMs: Int,
    val tekst: String,
    val akordPodpowiedz: String
)

class SongPlayActivity : AppCompatActivity() {

    private lateinit var CdurBtn: Button
    private lateinit var GdurBtn: Button
    private lateinit var AmolBtn: Button
    private lateinit var FdurBtn: Button
    private lateinit var chordHintText: TextView
    private lateinit var lyricsText: TextView

    private lateinit var soundPool: SoundPool
    private var soundC = 0
    private var soundG = 0
    private var soundAm = 0
    private var soundF = 0

    private var mediaPlayer: MediaPlayer? = null
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
        enableEdgeToEdge()
        setContentView(R.layout.activity_song_play)
        // UWAGA: glowny element w activity_song_play.xml musi miec android:id="@+id/main"
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(audioAttributes)
            .build()

        soundC = soundPool.load(this, R.raw.akord_c, 1)
        soundG = soundPool.load(this, R.raw.akord_g, 1)
        soundAm = soundPool.load(this, R.raw.akord_am, 1)
        soundF = soundPool.load(this, R.raw.akord_f, 1)

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
            changeLooks(CdurBtn)
            playSound(soundC)
        }

        GdurBtn.setOnClickListener {
            changeLooks(GdurBtn)
            playSound(soundG)
        }

        AmolBtn.setOnClickListener {
            changeLooks(AmolBtn)
            playSound(soundAm)
        }

        FdurBtn.setOnClickListener {
            changeLooks(FdurBtn)
            playSound(soundF)
        }

        backBtn.setOnClickListener {
            finish()
        }
    }

    private fun zaladujPiosenke(numer: Int) {
        val (plikAudioId, plikLrcId) = if (numer == 1) {
            R.raw.zawsze_tam_gdzie_ty_m to R.raw.zawsze_tam_gdzie_ty
        } else {
            R.raw.zawsze_tam_gdzie_ty_m to R.raw.zawsze_tam_gdzie_ty
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
            gora = { finish() },          // glowa w gore = powrot; reszta do dopisania
        )
        AppControler.akcjaNaLewo = { playLeft() }
        AppControler.akcjaNaPrawo = { playRight() }
        AppControler.akcjaNaMrugniecie = { playBlink() }
        AppControler.akcjaNaSzczeke = { playClutch() }
    }

    private fun playSound(idDzwieku: Int) {
        soundPool.play(idDzwieku, 1.0f, 1.0f, 1, 0, 1.0f)
    }

    private fun changeLooks(przycisk: Button) {
        przycisk.animate()
            .scaleX(1.08f)
            .scaleY(1.08f)
            .setDuration(100)
            .withEndAction {
                przycisk.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(100)
                    .start()
            }
            .start()
    }

    fun playLeft() = runOnUiThread { CdurBtn.performClick() }
    fun playRight() = runOnUiThread { GdurBtn.performClick() }
    fun playBlink() = runOnUiThread { AmolBtn.performClick() }
    fun playClutch() = runOnUiThread { FdurBtn.performClick() }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(sprawdzCzasRunnable)
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        soundPool.release()
    }
}