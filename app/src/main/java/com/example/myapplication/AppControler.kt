package com.example.myapplication

import com.example.myapplication.eeg.EegCommand
import com.example.myapplication.eeg.EegEvent
import com.example.myapplication.eeg.EegReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object AppControler {

    var akcjaNaLewo: (() -> Unit)? = null
    var akcjaNaPrawo: (() -> Unit)? = null
    var akcjaNaGore: (() -> Unit)? = null
    var akcjaNaDol: (() -> Unit)? = null
    var akcjaNaMrugniecie: (() -> Unit)? = null
    var akcjaNaSzczeke: (() -> Unit)? = null

    // opcjonalnie: ekran kalibracji moze pokazywac kroki ("BLINK2 1/5", "HEAD_L 2/3", "DONE"...)
    var naKrokKalibracji: ((String) -> Unit)? = null

    /**
     * Ustawia WSZYSTKIE akcje naraz - to, czego nie podasz, bedzie wylaczone (null).
     * Wolaj w onResume() KAZDEGO ekranu, zeby nie zostaly akcje z poprzedniego ekranu.
     */
    fun ustawAkcje(
        lewo: (() -> Unit)? = null,
        prawo: (() -> Unit)? = null,
        gora: (() -> Unit)? = null,
        dol: (() -> Unit)? = null,
        mrugniecie: (() -> Unit)? = null,
        szczeka: (() -> Unit)? = null,
    ) {
        akcjaNaLewo = lewo
        akcjaNaPrawo = prawo
        akcjaNaGore = gora
        akcjaNaDol = dol
        akcjaNaMrugniecie = mrugniecie
        akcjaNaSzczeke = szczeka
    }

    // ---------- polaczenie z opaska (laptop z Pythonem) ----------
    private var eeg: EegReceiver? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)   // akcje wykonuja sie na watku UI

    /** Uruchom raz (w MainActivity.onCreate). Kolejne wywolania nic nie robia. */
    fun start(laptopIp: String) {
        if (eeg != null) return
        val receiver = EegReceiver(laptopIp = laptopIp)
        eeg = receiver
        receiver.start()
        scope.launch {
            receiver.events.collect { event ->
                when (event) {
                    is EegEvent.Command -> when (event.command) {
                        EegCommand.LEFT -> wykrytoRuchWLewo()
                        EegCommand.RIGHT -> wykrytoRuchWPrawo()
                        EegCommand.UP -> wykrytoRuchWGore()
                        EegCommand.DOWN -> wykrytoRuchWDol()
                        EegCommand.DOUBLE_BLINK, EegCommand.BLINK -> wykrytoMrugniecie()
                    }
                    is EegEvent.Calibration -> naKrokKalibracji?.invoke(event.step)
                    is EegEvent.Unknown -> { /* ignorujemy */ }
                }
            }
        }
    }

    /** Czy laptop z Pythonem odpowiada (np. do pokazania statusu na ekranie). */
    fun czyPolaczono(): Boolean = eeg?.connected?.value == true

    /** Popros skrypt Pythona o ponowna kalibracje (np. z ConfigActivity). */
    fun kalibruj() {
        eeg?.requestCalibration()
    }

    fun stop() {
        eeg?.stop()
        eeg = null
    }


    // jak wykryto ruch
    fun wykrytoRuchWLewo() {
        akcjaNaLewo?.invoke()
    }

    fun wykrytoRuchWPrawo() {
        akcjaNaPrawo?.invoke()
    }

    fun wykrytoRuchWGore() {
        akcjaNaGore?.invoke()
    }

    fun wykrytoRuchWDol() {
        akcjaNaDol?.invoke()
    }


    fun wykrytoMrugniecie() {
        akcjaNaMrugniecie?.invoke()
    }

    fun wykrytoSzczeke() {
        akcjaNaSzczeke?.invoke()
    }
}