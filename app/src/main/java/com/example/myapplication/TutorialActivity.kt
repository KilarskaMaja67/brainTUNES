package com.example.myapplication

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * SAMOUCZEK - uczy gestow opaski w 3 fazach:
 *   1. Poznaj ruchy      - "Przechyl glowe w PRAWO, aby zagrac G" (+ duza strzalka), 2 rundy
 *   2. Bez podpowiedzi   - "Zagraj a-moll" (kazdy akord 2x, losowo; po 2 pomylkach pojawia sie podpowiedz)
 *   3. Progresja         - C -> G -> a-moll -> F po kolei
 * Na koniec: wynik + przycisk "Powtorz samouczek" (klikany glowa w PRAWO albo palcem).
 * Dwa mocne mrugniecia w kazdej chwili = powrot do menu. (test bez opaski: symulator_komend.py)
 */
class TutorialActivity : AppCompatActivity() {

    /** Gesty glowy i ich akordy - tak samo jak w PlayActivity. */
    private enum class Gest(val akord: Akordy.Akord, val nazwa: String, val ruch: String, val strzalka: String) {
        LEWO(Akordy.Akord.C, "C", "Przechyl głowę w LEWO", "⬅"),
        PRAWO(Akordy.Akord.G, "G", "Przechyl głowę w PRAWO", "➡"),
        GORA(Akordy.Akord.AM, "a-moll", "Odchyl głowę do GÓRY", "⬆"),
        DOL(Akordy.Akord.F, "F", "Pochyl głowę w DÓŁ", "⬇"),
    }

    /** Jeden krok samouczka: faza, runda w fazie i jakiego ruchu glowy oczekujemy. */
    private data class Krok(val faza: Int, val gest: Gest, val runda: Int = 1)

    private lateinit var tvFaza: TextView
    private lateinit var pasek: ProgressBar
    private lateinit var tvPolecenie: TextView
    private lateinit var tvStrzalka: TextView
    private lateinit var tvPodpowiedz: TextView
    private lateinit var tvFeedback: TextView
    private lateinit var exitHintText: TextView
    private lateinit var btnPowtorz: Button

    private lateinit var akordy: Akordy
    private val handler = Handler(Looper.getMainLooper())

    private var kroki: List<Krok> = emptyList()
    private var indeks = 0
    private var czekam = false              // przerwa po dobrym ruchu / miedzy fazami
    private var koniec = false
    private var bledy = 0
    private var bledyWKroku = 0


    private val CZAS_NA_DRUGIE_MRUGNIECIE_MS = 4000L   // Python ignoruje mrugniecia przez ~1.2 s po komendzie,
    // wiec drugie mrugniecie musi miec czas: 1.2-4 s po pierwszym
    private var blink_counter = 0
    private val reset_blinks_Runnable = Runnable {
        blink_counter = 0
        exitHintText.text = "blink twice to exit"
    }

    private val ZIELONY = Color.rgb(46, 125, 50)
    private val CZERWONY = Color.rgb(198, 40, 40)
    private val POMARANCZOWY = Color.rgb(230, 120, 0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tutorial)

        tvFaza = findViewById(R.id.tvFaza)
        pasek = findViewById(R.id.pasekPostepu)
        tvPolecenie = findViewById(R.id.tvPolecenie)
        tvStrzalka = findViewById(R.id.tvStrzalka)
        tvPodpowiedz = findViewById(R.id.tvPodpowiedz)
        tvFeedback = findViewById(R.id.tvFeedback)
        exitHintText = findViewById(R.id.exitHintText)
        btnPowtorz = findViewById(R.id.btnPowtorz)
        btnPowtorz.setOnClickListener { start() }

        akordy = Akordy(this)
        start()
    }

    override fun onResume() {
        super.onResume()
        AppControler.ustawAkcje(
            lewo = { onGest(Gest.LEWO) },
            prawo = { onGest(Gest.PRAWO) },
            gora = { onGest(Gest.GORA) },
            dol = { onGest(Gest.DOL) },
            mrugniecie = { blink_to_exit() },            // mocne mrugniecie = powrot do menu (jak na innych ekranach)
        )
    }

    // (runOnUiThread niepotrzebne - AppControler wywoluje akcje juz na watku UI)
    private fun blink_to_exit() {
        blink_counter++
        handler.removeCallbacks(reset_blinks_Runnable)

        if (blink_counter == 1) {
            exitHintText.text = "to exit, blink once more"
            handler.postDelayed(reset_blinks_Runnable, CZAS_NA_DRUGIE_MRUGNIECIE_MS)
        } else if (blink_counter >= 2) {
            finish()
        }
    }
    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        akordy.zwolnij()
    }

    // ------------------------------------------------------------------ przebieg

    private fun zbudujKroki(): List<Krok> {
        // faza 1: dwie rundy z podpowiedziami - najpierw po kolei, potem w innej kolejnosci
        val runda1 = listOf(Gest.PRAWO, Gest.LEWO, Gest.GORA, Gest.DOL)
        var runda2 = runda1.shuffled()
        repeat(50) {
            if (runda2.first() != runda1.last() && runda2 != runda1) return@repeat
            runda2 = runda1.shuffled()
        }
        val faza1 = runda1.map { Krok(1, it, runda = 1) } + runda2.map { Krok(1, it, runda = 2) }

        // faza 2: kazdy akord 2x, losowo, bez dwoch takich samych pod rzad
        var losowe = (Gest.values().toList() + Gest.values().toList()).shuffled()
        repeat(50) {
            if (losowe.zipWithNext().none { (a, b) -> a == b }) return@repeat
            losowe = losowe.shuffled()
        }
        val faza2 = losowe.map { Krok(2, it) }

        val faza3 = listOf(Gest.LEWO, Gest.PRAWO, Gest.GORA, Gest.DOL).map { Krok(3, it) }   // C G a F
        return faza1 + faza2 + faza3
    }

    private fun start() {
        handler.removeCallbacksAndMessages(null)
        blink_counter = 0
        exitHintText.text = "blink twice to exit"
        kroki = zbudujKroki()
        indeks = 0
        bledy = 0
        koniec = false
        btnPowtorz.visibility = View.GONE
        pasek.max = kroki.size
        pokazWstepFazy(1)
    }

    private fun pokazWstepFazy(faza: Int) {
        czekam = true
        tvStrzalka.text = ""
        tvFeedback.text = ""
        tvFaza.text = nazwaFazy(kroki.first { it.faza == faza })
        tvPolecenie.text = when (faza) {
            1 -> "Poznaj ruchy"
            2 -> "Teraz bez podpowiedzi!"
            else -> "Zagraj progresję"
        }
        tvPodpowiedz.text = when (faza) {
            1 -> "Każdy ruch głowy gra inny akord.\nRób ruch i od razu wracaj do pozycji prostej.\n\nDwa mocne mrugnięcia = powrót do menu."
            2 -> "Pokażę tylko nazwę akordu.\nPamiętasz, który ruch go gra?"
            else -> "C → G → a-moll → F\nNajpopularniejsza progresja w muzyce pop."
        }
        handler.postDelayed({ pokazKrok() }, 3000)
    }

    /** Krotki ekran miedzy rundami tej samej fazy. */
    private fun pokazWstepRundy(krok: Krok) {
        czekam = true
        tvStrzalka.text = ""
        tvFeedback.text = ""
        tvFaza.text = nazwaFazy(krok)
        tvPolecenie.text = "Runda ${krok.runda}"
        tvPodpowiedz.text = "Jeszcze raz każdy ruch - tym razem w innej kolejności."
        handler.postDelayed({ pokazKrok() }, 2000)
    }

    private fun pokazKrok() {
        val krok = kroki[indeks]
        czekam = false
        bledyWKroku = 0
        tvFeedback.text = ""
        tvFaza.text = nazwaFazy(krok)
        pasek.progress = indeks

        val gest = krok.gest

        when (krok.faza) {
            1 -> {
                tvPolecenie.text = "${gest.ruch},\naby zagrać ${gest.nazwa}"
                tvStrzalka.text = gest.strzalka
                tvPodpowiedz.text = ""
            }
            2 -> {
                tvPolecenie.text = "Zagraj ${gest.nazwa}"
                tvStrzalka.text = ""
                tvPodpowiedz.text = ""
            }
            else -> {
                tvPolecenie.text = "Zagraj ${gest.nazwa}"
                tvStrzalka.text = ""
                tvPodpowiedz.text = postepProgresji()
            }
        }
    }

    /** np. "✓C → ✓G → [a-moll] → F" */
    private fun postepProgresji(): String {
        val start3 = kroki.indexOfFirst { it.faza == 3 }
        return kroki.drop(start3).mapIndexed { i, k ->
            val nazwa = k.gest.nazwa
            when {
                start3 + i < indeks -> "✓$nazwa"
                start3 + i == indeks -> "[$nazwa]"
                else -> nazwa
            }
        }.joinToString("  →  ")
    }

    private fun nazwaFazy(krok: Krok) = when (krok.faza) {
        1 -> "Faza 1/3 · Poznaj ruchy · runda ${krok.runda}/2"
        2 -> "Faza 2/3 · Bez podpowiedzi"
        else -> "Faza 3/3 · Progresja"
    }

    // ------------------------------------------------------------------ reakcje na gesty

    private fun onGest(gest: Gest) {
        // kazdy ruch zawsze gra swoj akord - uzytkownik slyszy, co zrobil
        akordy.play(gest.akord)

        if (koniec) {
            if (gest == Gest.PRAWO) {              // ekran koncowy: glowa w prawo = "klika" Powtorz samouczek
                btnPowtorz.isPressed = true
                handler.postDelayed({ btnPowtorz.isPressed = false; btnPowtorz.performClick() }, 250)
            }
            return
        }
        if (czekam) return
        val krok = kroki[indeks]
        val cel = krok.gest

        if (gest == cel) {
            sukces()
            return
        }

        bledy++
        bledyWKroku++
        if (krok.faza == 1) {
            feedback("To było ${gest.nazwa}. Spróbuj: ${cel.ruch.lowercase()}", CZERWONY)
        } else if (bledyWKroku >= 2) {
            feedback("Podpowiedź: ${cel.ruch.lowercase()}", POMARANCZOWY)
            tvStrzalka.text = cel.strzalka
        } else {
            feedback("To było ${gest.nazwa}. Spróbuj jeszcze raz!", CZERWONY)
        }
    }

    private fun sukces() {
        czekam = true
        feedback("Brawo! ✓  ${kroki[indeks].gest.nazwa}", ZIELONY)
        handler.postDelayed({
            indeks++
            when {
                indeks >= kroki.size -> zakoncz()
                kroki[indeks].faza != kroki[indeks - 1].faza -> pokazWstepFazy(kroki[indeks].faza)
                kroki[indeks].runda != kroki[indeks - 1].runda -> pokazWstepRundy(kroki[indeks])
                else -> pokazKrok()
            }
        }, 1200)
    }

    private fun zakoncz() {
        koniec = true
        pasek.progress = pasek.max
        val zadania = kroki.size
        val trafnosc = 100 * zadania / (zadania + bledy)
        tvFaza.text = "Gotowe!"
        tvPolecenie.text = "Samouczek ukończony 🎉"
        tvStrzalka.text = ""
        tvPodpowiedz.text = "Trafność: $trafnosc%  (pomyłki: $bledy)\n\nMrugnij mocno 2×, aby wrócić do menu"
        feedback("", ZIELONY)
        btnPowtorz.visibility = View.VISIBLE
    }

    // ------------------------------------------------------------------ wyglad

    private fun feedback(tekst: String, kolor: Int) {
        tvFeedback.text = tekst
        tvFeedback.setTextColor(kolor)
    }
}