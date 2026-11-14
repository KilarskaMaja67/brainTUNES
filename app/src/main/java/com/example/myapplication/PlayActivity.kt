// dodac te pliki z muzyka i usuanc toast przy funkcjach klikania !!


// sa 4 przyciski akordow, kazdym z nich gra sie innym ruchem


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

    private lateinit var soundPool: SoundPool
    private var soundC = 0
    private var soundG = 0
    private var soundAm = 0
    private var soundF = 0

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

        soundC = soundPool.load(this, R.raw.akord_c, 1)
        soundG = soundPool.load(this, R.raw.akord_g, 1)
        soundAm = soundPool.load(this, R.raw.akord_am, 1)
        soundF = soundPool.load(this, R.raw.akord_f, 1)

        CdurBtn = findViewById(R.id.CdurBtn)
        GdurBtn = findViewById(R.id.GdurBtn)
        AmolBtn = findViewById(R.id.AmolBtn)
        FdurBtn = findViewById(R.id.FdurBtn)
        val backBtn = findViewById<Button>(R.id.backBtn)

        CdurBtn.setOnClickListener {
            changeLooks(CdurBtn)
            Toast.makeText(this, "Gra akord: C", Toast.LENGTH_SHORT).show()
            playSound(soundC)
        }

        GdurBtn.setOnClickListener {
            changeLooks(GdurBtn)
            Toast.makeText(this, "Gra akord: G", Toast.LENGTH_SHORT).show()
            playSound(soundG)
        }

        AmolBtn.setOnClickListener {
            changeLooks(AmolBtn)
            Toast.makeText(this, "Gra akord: a-moll", Toast.LENGTH_SHORT).show()
            playSound(soundAm)
        }

        FdurBtn.setOnClickListener {
            changeLooks(FdurBtn)
            Toast.makeText(this, "Gra akord: F", Toast.LENGTH_SHORT).show()
            playSound(soundF)
        }

        backBtn.setOnClickListener {
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        AppControler.akcjaNaLewo = { playLeft() }
        AppControler.akcjaNaPrawo = { plsyRight() }
        AppControler.akcjaNaMrugniecie = { playBlink() }
        AppControler.akcjaNaSzczeke = { playClutch() }
    }

    private fun playSound(idDzwieku: Int) {
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
        runOnUiThread {
            CdurBtn.performClick()
        }
    }

    fun plsyRight() {
        runOnUiThread {
            GdurBtn.performClick()
        }
    }

    fun playBlink() {
        runOnUiThread {
            AmolBtn.performClick()
        }
    }

    fun playClutch() {
        runOnUiThread {
            FdurBtn.performClick()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        soundPool.release()
    }
}
