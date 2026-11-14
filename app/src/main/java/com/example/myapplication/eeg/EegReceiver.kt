package com.example.myapplication.eeg

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketException

/** Komendy wysylane przez skrypt sterowanie_final.py (nazwy jak w COMMANDS w Pythonie). */
enum class EegCommand { LEFT, RIGHT, UP, DOWN, DOUBLE_BLINK, BLINK }

sealed interface EegEvent {
    /** Wykryty kontroler: ruch glowy albo mrugniecie. */
    data class Command(val command: EegCommand) : EegEvent

    /**
     * Krok kalibracji - co uzytkownik ma teraz zrobic. Przyklady wartosci `step`:
     * "REST", "GET_READY", "WAIT", "BLINK2 1/5", "BLINK1 3/5", "HEAD_L 1/3", "HEAD_P 2/3",
     * "HEAD_GORA 1/3", "HEAD_DOL 3/3", "DONE", "FAIL"
     */
    data class Calibration(val step: String) : EegEvent

    /** Cokolwiek innego (np. po zmianie nazw komend w Pythonie). */
    data class Unknown(val raw: String) : EegEvent
}

/**
 * Odbiera komendy z laptopa (skrypt Pythona) po UDP.
 *
 * - nasluchuje na porcie [listenPort] (domyslnie 5005),
 * - co 2 s wysyla "HELLO" do laptopa (port 5006), zeby Python wiedzial, gdzie wysylac komendy,
 * - [requestCalibration] prosi Pythona o ponowna kalibracje.
 *
 * Zdarzenia zbierasz z [events] (Flow). [connected] = czy laptop odpowiada (w ciagu ostatnich 5 s cos przyszlo).
 */
class EegReceiver(
    private val laptopIp: String,
    private val listenPort: Int = 5005,
    private val laptopPort: Int = 5006,
) {
    private val _events = MutableSharedFlow<EegEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<EegEvent> = _events

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected

    private var scope: CoroutineScope? = null
    private var socket: DatagramSocket? = null
    @Volatile private var lastPacketAt = 0L

    fun start() {
        if (scope != null) return
        val s = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = s
        s.launch {
            val sock = DatagramSocket(null).apply {
                reuseAddress = true
                bind(InetSocketAddress(listenPort))
            }
            socket = sock
            launch { helloLoop() }
            val buf = ByteArray(512)
            while (isActive) {
                val packet = DatagramPacket(buf, buf.size)
                try {
                    sock.receive(packet)          // czeka na wiadomosc z laptopa
                } catch (e: SocketException) {
                    break                          // gniazdo zamkniete przez stop()
                }
                lastPacketAt = System.currentTimeMillis()
                _connected.value = true
                val msg = String(packet.data, 0, packet.length).trim()
                if (msg == "HI") continue          // odpowiedz laptopa na HELLO - tylko "jestem"
                _events.tryEmit(parse(msg))
            }
        }
    }

    fun stop() {
        scope?.cancel()
        socket?.close()
        scope = null
        socket = null
        _connected.value = false
    }

    /** Poproś skrypt Pythona o ponowna kalibracje. */
    fun requestCalibration(): Job? = scope?.launch { send("CALIBRATE") }

    private suspend fun helloLoop() {
        val s = scope ?: return
        while (s.isActive) {
            send("HELLO")
            if (System.currentTimeMillis() - lastPacketAt > 5000) _connected.value = false
            delay(2000)
        }
    }

    private fun send(msg: String) {
        try {
            val data = msg.toByteArray()
            socket?.send(DatagramPacket(data, data.size, InetAddress.getByName(laptopIp), laptopPort))
        } catch (_: Exception) {
            // brak sieci / zly adres - sprobujemy przy nastepnym HELLO
        }
    }

    private fun parse(msg: String): EegEvent = when {
        msg.startsWith("CAL:") -> EegEvent.Calibration(msg.removePrefix("CAL:"))
        else -> EegCommand.values().firstOrNull { it.name == msg }
            ?.let { EegEvent.Command(it) }
            ?: EegEvent.Unknown(msg)
    }
}
