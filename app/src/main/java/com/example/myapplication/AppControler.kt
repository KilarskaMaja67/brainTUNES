package com.example.myapplication

import com.example.myapplication.eeg.EegCommand
import com.example.myapplication.eeg.EegEvent
import com.example.myapplication.eeg.EegReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch


object AppControler {

    private var left: (() -> Unit)? = null
    private var right: (() -> Unit)? = null
    private var up: (() -> Unit)? = null
    private var down: (() -> Unit)? = null
    private var blink: (() -> Unit)? = null


    var naKrokKalibracji: ((String) -> Unit)? = null

    var naBrakKalibracji: (() -> Unit)? = null

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

    private var eeg: EegReceiver? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)  

    fun start(laptopIp: String) {
        if (eeg != null) return
        val receiver = EegReceiver(laptopIp = laptopIp)
        eeg = receiver
        receiver.start()
        scope.launch {
            receiver.calibrated.collect { stan -> if (stan == false) naBrakKalibracji?.invoke() }
        }
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
                    is EegEvent.Unknown -> {  }
                }
            }
        }
    }

    fun czyPolaczono(): Boolean = eeg?.connected?.value == true


    fun czySkalibrowano(): Boolean? = eeg?.calibrated?.value


    fun kalibruj() {
        eeg?.requestCalibration()
    }

    fun stop() {
        eeg?.stop()
        eeg = null
    }
}
