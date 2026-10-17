package com.batmobilerc

import android.net.Network
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.abs

/**
 * Enlace con el coche: mantiene el bucle de envío a 10 Hz y la secuencia de apagado segura.
 *
 * Fuentes de control: el mando físico (pad*) y los joysticks táctiles (touch*). Gana la de mayor magnitud.
 */
class BatmobileLink {
    var status by mutableStateOf("Desconectado")
        private set
    var armed by mutableStateOf(false)
        private set
    var busy by mutableStateOf(false)
        private set
    var lamps by mutableStateOf(false)
        private set
    var smoke by mutableStateOf(false)
        private set
    var armor by mutableStateOf(false)
        private set
    var gunUp by mutableStateOf(false)
        private set
    var lastPacket by mutableStateOf("")
        private set

    @Volatile var padThrottle = 0f
    @Volatile var padSteer = 0f
    @Volatile var touchThrottle = 0f
    @Volatile var touchSteer = 0f

    private val throttle: Float get() = pick(padThrottle, touchThrottle)
    private val steering: Float get() = pick(padSteer, touchSteer)

    private val effects = ConcurrentLinkedQueue<String>()
    @Volatile private var running = false
    private var socket: DatagramSocket? = null
    private var sender: Thread? = null
    private var tick = 0
    private val address: InetAddress by lazy { InetAddress.getByName(Protocol.IP) }

    private fun pick(a: Float, b: Float) = if (abs(a) >= abs(b)) a else b

    fun updateStatus(text: String) {
        status = text
    }

    /** Suelta todos los controles: el coche se para y la dirección se centra, pero el enlace sigue armado. */
    fun releaseInputs() {
        padThrottle = 0f
        padSteer = 0f
        touchThrottle = 0f
        touchSteer = 0f
    }

    // ---------------------------------------------------------------- ciclo de vida

    @Synchronized
    fun start(network: Network) {
        if (running || busy) return
        try {
            val s = DatagramSocket()
            network.bindSocket(s)
            socket = s
        } catch (e: Exception) {
            status = "No se pudo abrir el socket: ${e.message}"
            return
        }
        effects.clear()
        resetEffectState()
        releaseInputs()
        tick = 0
        running = true
        armed = true
        status = "Armado: listo para conducir"
        sender = Thread({ loop() }, "batmobile-tx").also {
            it.isDaemon = true
            it.start()
        }
    }

    private fun loop() {
        var next = System.nanoTime()
        while (running) {
            tick = (tick + 1) % 10
            val pkt = Protocol.packet(throttle, steering, effects.poll(), tick == 0)
            send(pkt)
            if (tick % 5 == 0) lastPacket = pkt
            next += Protocol.INTERVAL_MS * 1_000_000L
            val waitMs = (next - System.nanoTime()) / 1_000_000L
            try {
                if (waitMs > 0) Thread.sleep(waitMs) else next = System.nanoTime()
            } catch (_: InterruptedException) {
                break
            }
        }
    }

    private fun send(pkt: String) {
        try {
            val bytes = pkt.toByteArray(Charsets.US_ASCII)
            socket?.send(DatagramPacket(bytes, bytes.size, address, Protocol.PORT))
        } catch (_: Exception) {
            // Red no disponible momentáneamente; el siguiente paquete lo reintenta.
        }
    }

    /**
     * Apagado seguro. Hay que llamarlo ANTES de soltar la WiFi: si se corta sin parar, los motores pueden
     * quedarse en marcha. Apaga humo y luces, y manda ~7 s de paquetes de reposo. [onDone] se ejecuta al terminar.
     */
    fun shutdown(onDone: () -> Unit = {}) {
        synchronized(this) {
            if (busy) return
            if (!running) {
                onDone()
                return
            }
            busy = true
        }
        releaseInputs()
        status = "Apagando de forma segura…"
        Thread({
            running = false
            sender?.interrupt()
            try {
                sender?.join(500)
            } catch (_: InterruptedException) {
            }
            try {
                send(Protocol.idleWith(Protocol.SMOKE_OFF))
                Thread.sleep(200)
                send(Protocol.idleWith(Protocol.LAMP_OFF))
                Thread.sleep(200)
                repeat(Protocol.SHUTDOWN_IDLE_PACKETS) {
                    send(Protocol.IDLE)
                    Thread.sleep(Protocol.INTERVAL_MS)
                }
            } catch (_: InterruptedException) {
            }
            closeSocket()
            finishDisconnect("Desconectado")
            onDone()
        }, "batmobile-shutdown").start()
    }

    /** Corte inmediato cuando ya se ha perdido la WiFi (no hay nada a lo que mandar el apagado). */
    @Synchronized
    fun abort(reason: String) {
        running = false
        sender?.interrupt()
        closeSocket()
        finishDisconnect(reason)
    }

    private fun closeSocket() {
        try {
            socket?.close()
        } catch (_: Exception) {
        }
        socket = null
    }

    private fun finishDisconnect(text: String) {
        resetEffectState()
        releaseInputs()
        armed = false
        busy = false
        status = text
    }

    private fun resetEffectState() {
        lamps = false
        smoke = false
        armor = false
        gunUp = false
    }

    // ---------------------------------------------------------------- efectos

    private fun ready() = armed && !busy

    fun toggleLamps() {
        if (!ready()) return
        lamps = !lamps
        effects.add(if (lamps) Protocol.LAMP_ON else Protocol.LAMP_OFF)
    }

    fun toggleSmoke() {
        if (!ready()) return
        smoke = !smoke
        effects.add(if (smoke) Protocol.SMOKE_ON else Protocol.SMOKE_OFF)
    }

    fun toggleArmor() {
        if (!ready()) return
        armor = !armor
        effects.add(Protocol.ARMOR_TOGGLE)
    }

    fun toggleGun() {
        if (!ready()) return
        gunUp = !gunUp
        effects.add(Protocol.GUN_ELEVATION_TOGGLE)
    }

    fun fire() {
        if (!ready()) return
        effects.add(Protocol.GUN_FIRE)
    }

    fun sound(index: Int) {
        if (!ready()) return
        effects.add(Protocol.soundCommand(index))
    }
}
