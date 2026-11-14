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
 *   1. Poznaj ruchy      - "Przechyl glowe w PRAWO, aby zagrac G" (+ duza strzalka),
 *                          na koncu "Mrugnij mocno" (= powrot)
 *   2. Bez podpowiedzi   - "Zagraj a-moll" (kazdy akord 2x, losowo; po 2 pomylkach pojawia sie podpowiedz)
 *   3. Progresja         - C -> G -> a-moll -> F po kolei
 * Na koniec: wynik + "Jeszcze raz" / mocne mrugniecie = powrot do menu.
 * Bez przyciskow akordow - tylko gesty (test bez opaski: symulator_komend.py).
 */
class TutorialActivity : AppCompatActivity() {

    /** Gesty glowy i ich akordy - tak samo jak w PlayActivity. */
    private enum class Gest(val akord: Akordy.Akord, val nazwa: String, val ruch: String, val strzalka: String) {
        LEWO(Akordy.Akord.C, "C", "Przechyl głowę w LEWO", "⬅"),
        PRAWO(Akordy.Akord.G, "G", "Przechyl głowę w PRAWO", "➡"),
        GORA(Akordy.Akord.AM, "a-moll", "Odchyl głowę do GÓRY", "⬆"),
        DOL(Akordy.Akord.F, "F", "Pochyl głowę w DÓŁ", "⬇"),
    }

    /** Jeden krok samouczka. gest == null oznacza krok "mocne mrugniecie". */
    private data class Krok(val faza: Int, val gest: Gest?)

    private lateinit var tvFaza: TextView
    private lateinit var pasek: ProgressBar
    private lateinit var tvPolecenie: TextView
    private lateinit var tvStrzalka: TextView
    private lateinit var tvPodpowiedz: TextView
    private lateinit var tvFeedback: TextView
    private lateinit var btnJeszczeRaz: Button

    private lateinit var akordy: Akordy
    private val handler = Handler(Looper.getMainLooper())

    private var kroki: List<Krok> = emptyList()
    private var indeks = 0
    private var czekam = false              // przerwa po dobrym ruchu / miedzy fazami
    private var koniec = false
    private var bledy = 0
    private var bledyWKroku = 0
    private var ostatnieMrugniecieMs = 0L

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
        btnJeszczeRaz = findViewById(R.id.btnJeszczeRaz)

        findViewById<Button>(R.id.backBtn).setOnClickListener { finish() }
        btnJeszczeRaz.setOnClickListener { start() }

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
            mrugniecie = { onMrugniecie() },
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        akordy.zwolnij()
    }

    // ------------------------------------------------------------------ przebieg

    private fun zbudujKroki(): List<Krok> {
        val faza1 = listOf(Gest.PRAWO, Gest.LEWO, Gest.GORA, Gest.DOL).map { Krok(1, it) } + Krok(1, null)

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
        kroki = zbudujKroki()
        indeks = 0
        bledy = 0
        koniec = false
        btnJeszczeRaz.visibility = View.GONE
        pasek.max = kroki.size
        pokazWstepFazy(1)
    }

    private fun pokazWstepFazy(faza: Int) {
        czekam = true
        tvStrzalka.text = ""
        tvFeedback.text = ""
        tvFaza.text = nazwaFazy(faza)
        tvPolecenie.text = when (faza) {
            1 -> "Poznaj ruchy"
            2 -> "Teraz bez podpowiedzi!"
            else -> "Zagraj progresję"
        }
        tvPodpowiedz.text = when (faza) {
            1 -> "Każdy ruch głowy gra inny akord.\nRób ruch i od razu wracaj do pozycji prostej."
            2 -> "Pokażę tylko nazwę akordu.\nPamiętasz, który ruch go gra?"
            else -> "C → G → a-moll → F\nNajpopularniejsza progresja w muzyce pop."
        }
        handler.postDelayed({ pokazKrok() }, 3000)
    }

    private fun pokazKrok() {
        val krok = kroki[indeks]
        czekam = false
        bledyWKroku = 0
        tvFeedback.text = ""
        tvFaza.text = nazwaFazy(krok.faza)
        pasek.progress = indeks

        val gest = krok.gest
        if (gest == null) {                         // krok z mrugnieciem
            tvPolecenie.text = "Mrugnij MOCNO"
            tvStrzalka.text = "👁"
            tvPodpowiedz.text = "Mocne mrugnięcie = powrót.\nZwykłe mrugnięcia są ignorowane."
            return
        }

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
            val nazwa = k.gest?.nazwa ?: ""
            when {
                start3 + i < indeks -> "✓$nazwa"
                start3 + i == indeks -> "[$nazwa]"
                else -> nazwa
            }
        }.joinToString("  →  ")
    }

    private fun nazwaFazy(faza: Int) = when (faza) {
        1 -> "Faza 1/3 · Poznaj ruchy"
        2 -> "Faza 2/3 · Bez podpowiedzi"
        else -> "Faza 3/3 · Progresja"
    }

    // ------------------------------------------------------------------ reakcje na gesty

    private fun onGest(gest: Gest) {
        // kazdy ruch zawsze gra swoj akord - uzytkownik slyszy, co zrobil
        akordy.play(gest.akord)

        if (koniec || czekam) return
        val krok = kroki[indeks]
        val cel = krok.gest

        if (cel == null) {
            feedback("To był ruch głową. Teraz mrugnij mocno!", POMARANCZOWY)
            return
        }
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

    private fun onMrugniecie() {
        if (koniec) {
            finish()
            return
        }
        val krok = kroki[indeks]
        if (!czekam && krok.gest == null) {         // to byl krok z mrugnieciem
            sukces()
            return
        }
        // w trakcie samouczka mrugniecie nie wyrzuca od razu - trzeba potwierdzic
        val teraz = System.currentTimeMillis()
        if (teraz - ostatnieMrugniecieMs < 3000) {
            finish()
            return
        }
        ostatnieMrugniecieMs = teraz
        feedback("Mrugnięcie = wyjście. Mrugnij jeszcze raz w ciągu 3 s, aby wyjść.", POMARANCZOWY)
    }

    private fun sukces() {
        czekam = true
        feedback("Brawo! ✓  ${kroki[indeks].gest?.nazwa ?: ""}", ZIELONY)
        handler.postDelayed({
            indeks++
            when {
                indeks >= kroki.size -> zakoncz()
                kroki[indeks].faza != kroki[indeks - 1].faza -> pokazWstepFazy(kroki[indeks].faza)
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
        tvPodpowiedz.text = "Trafność: $trafnosc%  (pomyłki: $bledy)\n\nMrugnij mocno, aby wrócić do menu."
        feedback("", ZIELONY)
        btnJeszczeRaz.visibility = View.VISIBLE
    }

    // ------------------------------------------------------------------ wyglad

    private fun feedback(tekst: String, kolor: Int) {
        tvFeedback.text = tekst
        tvFeedback.setTextColor(kolor)
    }
}