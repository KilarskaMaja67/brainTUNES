package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import android.widget.Toast
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import com.example.myapplication.eeg.EegCommand
import com.example.myapplication.eeg.EegEvent
import com.example.myapplication.eeg.EegReceiver



class MainActivity : AppCompatActivity() {


    //ktory przycisk zaznaczony (0 = config, 1 = tutorial, 2 = play)
    private var selected_index = 0

    //lista z 3 przyciskami z XML
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

        list_buttons = listOf(configBtn, tutorialBtn, playBtn)

        // 3. OBSŁUGA KLIKNIĘCIA PALCEM (działa teraz i jako plan awaryjny)
        configBtn.setOnClickListener {
            selected_index = 0
            update_look()
            Toast.makeText(this, "selected: config", Toast.LENGTH_SHORT).show()
            val intent = Intent(this, ConfigActivity::class.java)
            startActivity(intent)
        }

        tutorialBtn.setOnClickListener {
            selected_index = 1
            update_look()
            Toast.makeText(this, "selected: tutorial", Toast.LENGTH_SHORT).show()
            val intent = Intent(this, TutorialActivity::class.java)
            startActivity(intent)
        }

        playBtn.setOnClickListener {
            selected_index = 2
            update_look()
            Toast.makeText(this, "selected: play", Toast.LENGTH_SHORT).show()
            val intent = Intent(this, PlayActivity::class.java)
            startActivity(intent)
        }

        songBtn.setOnClickListener{
            selected_index = 3
            val intent = Intent(this, SongActivity::class.java)
            startActivity(intent)
        }

        update_look()
    }

    // zeby appcontroler decydowalo co sie dzieje
    override fun onResume() {
        super.onResume()
        AppControler.akcjaNaLewo = { left() }
        AppControler.akcjaNaPrawo = { right() }
        AppControler.akcjaNaMrugniecie = { choose() }
        AppControler.akcjaNaGore = null      // na tym ekranie gora/dol nic nie robia
        AppControler.akcjaNaDol = null
        AppControler.akcjaNaSzczeke = null
    }

// powieksz przycisc
    private fun update_look() {
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

    fun right() {
        selected_index = (selected_index + 1) % list_buttons.size
        update_look()
    }

    fun left() {
        selected_index = if (selected_index - 1 < 0) {
            list_buttons.size - 1
        } else {
            selected_index - 1
        }
        update_look()
    }

    fun choose() {
        // Udaje kliknięcie palcem w ten przycisk, który jest teraz powiększony
        list_buttons[selected_index].performClick()
    }
}
