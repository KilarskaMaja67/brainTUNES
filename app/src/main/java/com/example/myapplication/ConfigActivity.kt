package com.example.myapplication

import android.graphics.Color
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * EKRAN KALIBRACJI.
 * Po wejsciu kalibracja startuje SAMA (przed kalibracja gesty jeszcze nie dzialaja).
 * Python mierzy i liczy, a ten ekran pokazuje polecenia, piszczy w momencie akcji,
 * ocenia kontakt opaski i pokazuje wynik. Nowa kalibracja nadpisuje zapisana.
 *
 * Po zakonczeniu:  ⬅ glowa w lewo = Powtorz,  ➡ glowa w prawo = Gotowe (albo palcem).
 * Udana kalibracja sama wraca do menu po 5 s.
 */
class ConfigActivity : AppCompatActivity() {

    private lateinit var tvPolaczenie: TextView
    private lateinit var pasek: ProgressBar
    private lateinit var tvPolecenie: TextView
    private lateinit var tvKrok: TextView
    private lateinit var tvKontakt: TextView
    private lateinit var tvWynik: TextView
    private lateinit var btnPowtorz: Button
    private lateinit var btnGotowe: Button

    private val handler = Handler(Looper.getMainLooper())
    private var ton: ToneGenerator? = null

    private var trwa = false            // kalibracja w toku
    private var zakonczona = false      // pokazujemy wynik
    private var ostatniKrokMs = 0L      // kiedy przyszla ostatnia wiadomosc od Pythona

    private val ZIELONY = Color.rgb(46, 125, 50)
    private val POMARANCZOWY = Color.rgb(230, 120, 0)
    private val CZERWONY = Color.rgb(198, 40, 40)

    // liczba krokow z akcja: spokoj + 5 mrugniec + 4 kierunki x 3
    private val KROKI_RAZEM = 1 + 5 + 12

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_config)

        tvPolaczenie = findViewById(R.id.tvPolaczenie)
        pasek = findViewById(R.id.pasekKalibracji)
        tvPolecenie = findViewById(R.id.tvPolecenie)
        tvKrok = findViewById(R.id.tvKrok)
        tvKontakt = findViewById(R.id.tvKontakt)
        tvWynik = findViewById(R.id.tvWynik)
        btnPowtorz = findViewById(R.id.btnPowtorz)
        btnGotowe = findViewById(R.id.btnGotowe)

        pasek.max = KROKI_RAZEM
        btnPowtorz.setOnClickListener { zacznij() }
        btnGotowe.setOnClickListener { finish() }

        ton = try { ToneGenerator(AudioManager.STREAM_MUSIC, 80) } catch (e: Exception) { null }
    }

    override fun onResume() {
        super.onResume()
        AppControler.naKrokKalibracji = { onKrok(it) }
        // w trakcie kalibracji Python nie wysyla komend; po niej: lewo = Powtorz, prawo = Gotowe
        AppControler.ustawAkcje(
            lewo = { if (zakonczona) kliknij(btnPowtorz) },
            prawo = { if (zakonczona) kliknij(btnGotowe) },
        )
        if (!trwa && !zakonczona) zacznij()
    }

    override fun onPause() {
        super.onPause()
        AppControler.naKrokKalibracji = null
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        ton?.release()
    }

    // ------------------------------------------------------------------ przebieg

    private fun zacznij() {
        handler.removeCallbacksAndMessages(null)
        trwa = true
        zakonczona = false
        ostatniKrokMs = 0L
        btnPowtorz.visibility = View.GONE
        btnGotowe.visibility = View.GONE
        pasek.progress = 0
        tvKontakt.text = ""
        tvWynik.text = ""
        tvPolaczenie.text = ""
        tvPolecenie.text = "Łączenie z opaską…"
        tvKrok.text = "Załóż opaskę i usiądź wygodnie"
        AppControler.kalibruj()
        handler.postDelayed(sprawdzCzyRuszylo, 5000)
    }

    /** Jesli Python nie odpowiedzial w 5 s - ponow prosbe i pokaz komunikat. */
    private val sprawdzCzyRuszylo = object : Runnable {
        override fun run() {
            if (trwa && ostatniKrokMs == 0L) {
                tvPolaczenie.text = "Brak odpowiedzi z laptopa – czy skrypt Pythona działa?"
                tvPolaczenie.setTextColor(CZERWONY)
                AppControler.kalibruj()
                handler.postDelayed(this, 5000)
            }
        }
    }

    private fun onKrok(krok: String) {
        if (!trwa) return
        ostatniKrokMs = System.currentTimeMillis()
        tvPolaczenie.text = ""

        val nazwa = krok.substringBefore(" ")
        val reszta = krok.substringAfter(" ", "")
        val nr = reszta.substringBefore("/").toIntOrNull() ?: 0      // np. "3/5" -> 3

        when (nazwa) {
            "REST" -> {
                polecenie("Siedź spokojnie", "Patrz przed siebie, nie ruszaj się (5 s)")
                pasek.progress = 1
            }
            "CONTACT" -> {
                val dobry = reszta.startsWith("GOOD")
                tvKontakt.text = if (dobry) "✅ Opaska dobrze przylega"
                else "⚠️ Słaby kontakt opaski – dociśnij ją na czole i odgarnij włosy"
                tvKontakt.setTextColor(if (dobry) ZIELONY else POMARANCZOWY)
            }
            "GET_READY" -> polecenie("Przygotuj się…", "Za chwilę kolejne zadanie")
            "WAIT" -> tvPolecenie.text = "…odpoczynek"
            "BLINK1" -> akcja("Mrugnij MOCNO", "Mrugnięcie $reszta", 1 + nr)
            "BLINK2" -> akcja("Mrugnij 2× szybko", "Mrugnięcie $reszta", 1 + nr)
            "HEAD_L" -> akcja("⬅ Głowa w LEWO\ni wróć", "Ruch $reszta", 6 + nr)
            "HEAD_P" -> akcja("➡ Głowa w PRAWO\ni wróć", "Ruch $reszta", 9 + nr)
            "HEAD_GORA" -> akcja("⬆ Głowa do GÓRY\ni wróć", "Ruch $reszta", 12 + nr)
            "HEAD_DOL" -> akcja("⬇ Głowa w DÓŁ\ni wróć", "Ruch $reszta", 15 + nr)
            "RESULT" -> pokazWynik(reszta)
            "DONE" -> zakoncz(udana = true)
            "FAIL" -> zakoncz(udana = false)
        }
    }

    private fun pokazWynik(wynik: String) {
        // np. "BLINK=OK HEAD=WEAK"
        val mrug = if (wynik.contains("BLINK=OK")) "✅ Mrugnięcie: OK" else "⚠️ Mrugnięcie: za słabe – mrugaj mocniej"
        val glowa = if (wynik.contains("HEAD=OK")) "✅ Ruchy głowy: OK" else "⚠️ Ruchy głowy: niewyraźne – rób większe ruchy"
        tvWynik.text = "$mrug\n$glowa"
    }

    private fun zakoncz(udana: Boolean) {
        trwa = false
        zakonczona = true
        pasek.progress = KROKI_RAZEM
        btnPowtorz.visibility = View.VISIBLE
        btnGotowe.visibility = View.VISIBLE
        if (udana) {
            polecenie("Kalibracja gotowa! ✅", "Za chwilę wrócisz do menu…")
            handler.postDelayed({ if (zakonczona) finish() }, 5000)
        } else {
            polecenie("Kalibracja nieudana ⚠️", "Popraw opaskę i powtórz")
        }
    }

    // ------------------------------------------------------------------ wyglad

    private fun polecenie(glowne: String, pod: String) {
        tvPolecenie.text = glowne
        tvKrok.text = pod
    }

    private fun akcja(glowne: String, pod: String, postep: Int) {
        polecenie(glowne, pod)
        pasek.progress = postep
        try { ton?.startTone(ToneGenerator.TONE_PROP_BEEP, 150) } catch (_: Exception) { }
    }

    private fun kliknij(przycisk: Button) {
        przycisk.isPressed = true
        handler.postDelayed({ przycisk.isPressed = false; przycisk.performClick() }, 250)
    }
}