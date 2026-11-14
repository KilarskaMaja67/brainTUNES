package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class SongActivity : AppCompatActivity() {

    private var zaznaczonyIndeks = 0
    private lateinit var listaPiosenek: List<Button>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_song)

        val song1Btn = findViewById<Button>(R.id.song1Btn)
        val song2Btn = findViewById<Button>(R.id.song2Btn)
        val song3Btn = findViewById<Button>(R.id.song3Btn)
        val backBtn = findViewById<Button>(R.id.backBtn)

        listaPiosenek = listOf(song1Btn, song2Btn, song3Btn)

        // Wejście w konkretną piosenkę
        song1Btn.setOnClickListener {
            zaznaczonyIndeks = 0
            odswiezWyglad()
            otworzPiosenke(1)
        }

        song2Btn.setOnClickListener {
            zaznaczonyIndeks = 1
            odswiezWyglad()
            otworzPiosenke(2)
        }

        song3Btn.setOnClickListener {
            zaznaczonyIndeks = 2
            odswiezWyglad()
            otworzPiosenke(3)
        }

        // Powrót do MainActivity
        backBtn.setOnClickListener {
            finish()
        }

        odswiezWyglad()
    }

    // Przechodzi do ekranu grania i przekazuje, która piosenka została kliknięta
    private fun otworzPiosenke(numerPiosenki: Int) {
        val intent = Intent(this, SongPlayActivity::class.java)
        intent.putExtra("NUMER_PIOSENKI", numerPiosenki)
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        AppControler.akcjaNaLewo = { ruchWLewo() }
        AppControler.akcjaNaPrawo = { ruchWPrawo() }
        AppControler.akcjaNaMrugniecie = { zatwierdzWybor() }
        AppControler.akcjaNaSzczeke = null
    }

    private fun odswiezWyglad() {
        listaPiosenek.forEachIndexed { indeks, przycisk ->
            if (indeks == zaznaczonyIndeks) {
                przycisk.scaleX = 1.15f
                przycisk.scaleY = 1.15f
                przycisk.alpha = 1.0f
            } else {
                przycisk.scaleX = 1.0f
                przycisk.scaleY = 1.0f
                przycisk.alpha = 0.6f
            }
        }
    }

    fun ruchWPrawo() {
        runOnUiThread {
            zaznaczonyIndeks = (zaznaczonyIndeks + 1) % listaPiosenek.size
            odswiezWyglad()
        }
    }

    fun ruchWLewo() {
        runOnUiThread {
            zaznaczonyIndeks = if (zaznaczonyIndeks - 1 < 0) listaPiosenek.size - 1 else zaznaczonyIndeks - 1
            odswiezWyglad()
        }
    }

    fun zatwierdzWybor() {
        runOnUiThread {
            listaPiosenek[zaznaczonyIndeks].performClick()
        }
    }
}