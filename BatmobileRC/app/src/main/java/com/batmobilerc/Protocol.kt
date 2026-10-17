package com.batmobilerc

/**
 * Protocolo del Mattel Justice League Ultimate Batmobile R/C (FKM40).
 *
 * UDP con texto ASCII plano hacia 192.168.201.1:1051 a 10 Hz. El coche hace de punto de acceso WiFi.
 * Formato: de uno a tres pares CMD_VAL separados por guion bajo, luego 50_F0, relleno 00_00 hasta
 * completar 4 "huecos" y por último la cola XX.
 *   - ejemplo: 41_80_31_80_50_F0_00_00_00
 *   - XX = 01 cada 10 paquetes, 00 en el resto
 *
 * Este fichero no depende de Android para poder probarse en cualquier JVM.
 */
object Protocol {
    const val IP = "192.168.201.1"
    const val PORT = 1051
    const val INTERVAL_MS = 100L

    /** Paquete de reposo: motor parado, dirección centrada. */
    const val IDLE = "41_80_31_80_50_F0_00_00_00"

    /** Magnitud máxima (0..127) que se envía para acelerador y dirección. Sube STEER hasta 127 para giros más cerrados. */
    const val MAX_THROTTLE = 100
    const val MAX_STEER = 100

    const val LAMP_ON = "A1_10"
    const val LAMP_OFF = "A1_01"
    const val SMOKE_ON = "91_10"
    const val SMOKE_OFF = "91_01"
    const val ARMOR_TOGGLE = "21_64"
    const val GUN_ELEVATION_TOGGLE = "21_E4"
    const val GUN_FIRE = "11_E4"

    /** Nº de paquetes de reposo tras apagar efectos (5 s de parada + 2 s de margen a 10 Hz). */
    const val SHUTDOWN_IDLE_PACKETS = 70

    /**
     * Byte de acelerador/dirección. Signo-magnitud: bit 7 = positivo (adelante / derecha),
     * 0x80 = parado/centrado, 0x81..0xFF = positivo, 0x01..0x7F = negativo (atrás / izquierda).
     * [value] va de -1 a 1.
     */
    fun axisByte(value: Float, max: Int): String {
        val magnitude = Math.round(Math.abs(value).coerceIn(0f, 1f) * max).coerceIn(0, 127)
        val b = when {
            magnitude == 0 -> 0x80
            value > 0f -> 0x80 + magnitude
            else -> magnitude
        }
        return Integer.toHexString(b).uppercase().padStart(2, '0')
    }

    /** Comando de sonido; [index] es 1-based. */
    fun soundCommand(index: Int): String =
        "01_" + Integer.toHexString((index - 1).coerceIn(0, 255)).uppercase().padStart(2, '0')

    /**
     * Paquete completo: acelerador + dirección + (opcional) un efecto de un solo disparo.
     * [tail] debe ser true cada 10º paquete.
     */
    fun packet(throttle: Float, steering: Float, effect: String?, tail: Boolean): String {
        val cmds = ArrayList<String>(3)
        cmds.add("41_" + axisByte(throttle, MAX_THROTTLE))
        cmds.add("31_" + axisByte(steering, MAX_STEER))
        if (effect != null) cmds.add(effect)
        val sb = StringBuilder(cmds.joinToString("_")).append("_50_F0")
        repeat(4 - (cmds.size + 1)) { sb.append("_00_00") }
        sb.append(if (tail) "_01" else "_00")
        return sb.toString()
    }

    /** Paquete de reposo con un efecto, usado en la secuencia de apagado. */
    fun idleWith(effect: String): String = "41_80_31_80_${effect}_50_F0_00"
}
