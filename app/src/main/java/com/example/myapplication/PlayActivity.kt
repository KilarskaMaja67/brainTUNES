// sa 4 przyciski akordow, kazdym z nich gra sie innym ruchem glowy:
//   glowa w lewo = C, glowa w prawo = G, glowa w gore = a-moll, glowa w dol = F
//   mocne mrugniecie = powrot


package com.example.myapplication

import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import android.widget.Toast

class PlayActivity : AppCompatActivity() {

    private lateinit var CdurBtn: Button
    private lateinit var GdurBtn: Button
    private lateinit var AmolBtn: Button
    private lateinit var FdurBtn: Button

    // SoundPool wczytuje dzwieki RAZ przy wejsciu na ekran i potem gra je bez opoznien
    private lateinit var soundPool: SoundPool
    private var soundC = 0
    private var soundG = 0
    private var soundAm = 0
    private var soundF = 0
    private val zaladowane = mutableSetOf<Int>()   // ktore dzwieki sa juz gotowe

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_play)

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(audioAttributes)
            .build()

        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) zaladowane.add(sampleId)
            else Log.e("PlayActivity", "Nie udalo sie zaladowac dzwieku $sampleId")
        }

        soundC = soundPool.load(this, R.raw.akord_c, 1)
        soundG = soundPool.load(this, R.raw.akord_g, 1)
        soundAm = soundPool.load(this, R.raw.akord_am, 1)
        soundF = soundPool.load(this, R.raw.akord_f, 1)

        CdurBtn = findViewById(R.id.CdurBtn)
        GdurBtn = findViewById(R.id.GdurBtn)
        AmolBtn = findViewById(R.id.AmolBtn)
        FdurBtn = findViewById(R.id.FdurBtn)
        val backBtn = findViewById<Button>(R.id.backBtn)

        // (toasty usuniete - przy szybkim graniu kolejkowaly sie i spowalnialy aplikacje)
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

    override fun onResume() {
        super.onResume()
        AppControler.ustawAkcje(
            lewo = { playLeft() },       // C
            prawo = { plsyRight() },     // G
            gora = { playUp() },         // a-moll
            dol = { playDown() },        // F
            mrugniecie = { finish() },   // mocne mrugniecie = powrot do menu
        )
    }

    private fun playSound(idDzwieku: Int) {
        if (idDzwieku !in zaladowane) {
            Log.w("PlayActivity", "Dzwiek $idDzwieku jeszcze sie laduje")
            return
        }
        // Parametry: ID, głośność lewy (1.0), głośność prawy (1.0), priorytet (1), pętla (0 = brak), tempo (1.0)
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

    fun playLeft() {
        CdurBtn.performClick()
    }

    fun plsyRight() {
        GdurBtn.performClick()
    }

    fun playUp() {
        AmolBtn.performClick()
    }

    fun playDown() {
        FdurBtn.performClick()
    }

    override fun onDestroy() {
        super.onDestroy()
        soundPool.release()
    }
}
