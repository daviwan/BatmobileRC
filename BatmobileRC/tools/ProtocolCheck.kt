import com.batmobilerc.Protocol

// Comprueba el generador de paquetes contra los ejemplos del controlador original en Python.
var failures = 0

fun check(name: String, actual: String, expected: String) {
    if (actual == expected) {
        println("OK    $name -> $actual")
    } else {
        println("FALLO $name\n   obtenido: $actual\n   esperado: $expected")
        failures++
    }
}

fun main() {
    // Reposo: idéntico a la constante IDLE del original
    check("reposo", Protocol.packet(0f, 0f, null, false), "41_80_31_80_50_F0_00_00_00")
    check("reposo == IDLE", Protocol.packet(0f, 0f, null, false), Protocol.IDLE)

    // set_throttle(150) -> 41_b2 ; set_steering(130) -> 31_9e (mismos valores, en mayúsculas)
    check("adelante 50% + derecha 30%", Protocol.packet(0.5f, 0.3f, null, false), "41_B2_31_9E_50_F0_00_00_00")

    // Atrás: bit 7 a cero; set_throttle(52) -> 41_30 (demo: "REVERSE")
    check("atrás 48%", Protocol.packet(-0.48f, 0f, null, false), "41_30_31_80_50_F0_00_00_00")

    // Izquierda: set_steering(60) -> 31_28
    check("izquierda 40%", Protocol.packet(0f, -0.4f, null, false), "41_80_31_28_50_F0_00_00_00")

    // Máximos
    check("máximo adelante/derecha", Protocol.axisByte(1f, Protocol.MAX_THROTTLE), "E4")
    check("máximo atrás", Protocol.axisByte(-1f, Protocol.MAX_THROTTLE), "64")

    // Un efecto ocupa el tercer hueco; no hay relleno (como "91_10_11_E4_41_98_50_F0_00" del demo)
    check("luces", Protocol.packet(0f, 0f, Protocol.LAMP_ON, false), "41_80_31_80_A1_10_50_F0_00")
    check("disparo + marcha", Protocol.packet(0.2f, 0f, Protocol.GUN_FIRE, false), "41_94_31_80_11_E4_50_F0_00")

    // Cola 01 cada 10 paquetes
    check("cola", Protocol.packet(0f, 0f, null, true), "41_80_31_80_50_F0_00_00_01")

    // Sonidos: índice 1-based -> hex(index-1)
    check("sonido 1", Protocol.soundCommand(1), "01_00")
    check("sonido 3", Protocol.soundCommand(3), "01_02")
    check("sonido 17", Protocol.soundCommand(17), "01_10")

    // Apagado
    check("apagado humo", Protocol.idleWith(Protocol.SMOKE_OFF), "41_80_31_80_91_01_50_F0_00")

    // Magnitud nunca supera 127 (7 bits)
    check("saturación", Protocol.axisByte(5f, 127), "FF")

    println(if (failures == 0) "\nTODO CORRECTO" else "\n$failures fallo(s)")
    if (failures != 0) System.exit(1)
}
