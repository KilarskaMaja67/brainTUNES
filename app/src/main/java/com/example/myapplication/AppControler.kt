package com.example.myapplication

import com.example.myapplication.eeg.EegCommand
import com.example.myapplication.eeg.EegEvent
import com.example.myapplication.eeg.EegReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Laczy opaske (komendy z Pythona) z aktualnym ekranem.
 * Kazdy ekran w onResume() woła ustawAkcje(...) - to, czego nie poda, nic nie robi.
 */
object AppControler {

    // akcje aktualnego ekranu (ustawiane TYLKO przez ustawAkcje)
    private var left: (() -> Unit)? = null
    private var right: (() -> Unit)? = null
    private var up: (() -> Unit)? = null
    private var down: (() -> Unit)? = null
    private var blink: (() -> Unit)? = null

    // opcjonalnie: ekran kalibracji moze pokazywac kroki ("BLINK1 1/5", "HEAD_L 2/3", "DONE"...)
    var naKrokKalibracji: ((String) -> Unit)? = null

    fun ustawAkcje(
        left: (() -> Unit)? = null,
        right: (() -> Unit)? = null,
        up: (() -> Unit)? = null,
        down: (() -> Unit)? = null,
        blink: (() -> Unit)? = null,
    ) {
        this.left = left
        this.right = right
        this.up = up
        this.down = down
        this.blink = blink
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
                        EegCommand.LEFT -> left?.invoke()
                        EegCommand.RIGHT -> right?.invoke()
                        EegCommand.UP -> up?.invoke()
                        EegCommand.DOWN -> down?.invoke()
                        EegCommand.DOUBLE_BLINK, EegCommand.BLINK -> blink?.invoke()
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
}