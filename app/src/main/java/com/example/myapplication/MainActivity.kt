package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity


class MainActivity : AppCompatActivity() {

    //ktory przycisk zaznaczony (0 = config, 1 = tutorial, 2 = play, 3 = song)
    private var selected_index = 0

    //lista z 4 przyciskami z XML
    private lateinit var list_buttons: List<Button>


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //POLACZENIE Z OPASKA - uruchamia sie raz na cala aplikacje
        // emulator: "10.0.2.2"; prawdziwy telefon: IP laptopa z `ipconfig`
        AppControler.start(laptopIp = "10.0.2.2")


        val configBtn = findViewById<Button>(R.id.configBtn)
        val tutorialBtn = findViewById<Button>(R.id.tutorialBtn)
        val playBtn = findViewById<Button>(R.id.playBtn)
        val songBtn = findViewById<Button>(R.id.songBtn)

        list_buttons = listOf(configBtn, tutorialBtn, playBtn, songBtn)

        // OBSŁUGA KLIKNIĘCIA PALCEM (działa teraz i jako plan awaryjny)
        configBtn.setOnClickListener {
            selected_index = 0
            updateLook()
            Toast.makeText(this, "selected: config", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, ConfigActivity::class.java))
        }

        tutorialBtn.setOnClickListener {
            selected_index = 1
            updateLook()
            Toast.makeText(this, "selected: tutorial", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, TutorialActivity::class.java))
        }

        playBtn.setOnClickListener {
            selected_index = 2
            updateLook()
            Toast.makeText(this, "selected: play", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, PlayActivity::class.java))
        }

        songBtn.setOnClickListener {
            selected_index = 3
            updateLook()
            startActivity(Intent(this, SongActivity::class.java))
        }

        updateLook()
    }

    // zeby appcontroler decydowalo co sie dzieje (gora/dol na tym ekranie nic nie robia)
    override fun onResume() {
        super.onResume()
        AppControler.ustawAkcje(
            gora = { upMove() },
            dol = { downMove() },
            prawo = { rightMove() },
            lewo = null,
            mrugniecie = null
        )
    }

    // powieksz przycisk
    private fun updateLook() {
        list_buttons.forEachIndexed { indeks, przycisk ->
            if (indeks == selected_index) {
                przycisk.scaleX = 1.15f // Powiększenie w poziomie o 15%
                przycisk.scaleY = 1.15f // Powiększenie w pionie o 15%
                przycisk.alpha = 1.0f   // Pełna widoczność
            } else {
                przycisk.scaleX = 1.0f  // Normalny rozmiar
                przycisk.scaleY = 1.0f
                przycisk.alpha = 0.6f   // Lekko przyciemniony
            }
        }
    }

    // pod opaske

    private fun upMove() {
        runOnUiThread {
            // Jeśli jesteś na samej górze (0), przeskakuje na ostatni przycisk na dole
            selected_index = if (selected_index - 1 < 0) list_buttons.size - 1 else selected_index - 1
            updateLook()
        }
    }

    private fun downMove() {
        runOnUiThread {
            // Przesuwa zaznaczenie w dół, a po ostatnim przycisku wraca na górę (0)
            selected_index = (selected_index + 1) % list_buttons.size
            updateLook()
        }
    }

    private fun rightMove() {
        runOnUiThread {
            list_buttons[selected_index].performClick()
        }
    }
}