package com.batmobilerc

import android.content.SharedPreferences
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BatmobileApp(
    link: BatmobileLink,
    prefs: SharedPreferences,
    onConnectAuto: (ssid: String, pass: String) -> Unit,
    onConnectManual: () -> Unit,
    onDisconnect: () -> Unit,
) {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(Modifier.fillMaxSize()) {
            var ssid by remember { mutableStateOf(prefs.getString("ssid", "") ?: "") }
            var pass by remember { mutableStateOf(prefs.getString("pass", "BATMOBILE1") ?: "BATMOBILE1") }
            val enabled = link.armed && !link.busy

            Row(
                Modifier.fillMaxSize().padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Acelerador táctil (vertical): arriba adelante, abajo marcha atrás.
                AxisPad(Modifier.width(90.dp).fillMaxHeight(), vertical = true, label = "GAS") {
                    link.touchThrottle = it
                }

                Column(
                    Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(link.status, fontSize = 14.sp)

                    if (!link.armed) {
                        OutlinedTextField(
                            value = ssid,
                            onValueChange = { ssid = it },
                            label = { Text("WiFi del coche (vacío = buscar *-BATMOBILE)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = pass,
                            onValueChange = { pass = it },
                            label = { Text("Clave") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(
                            onClick = {
                                prefs.edit().putString("ssid", ssid).putString("pass", pass).apply()
                                onConnectAuto(ssid, pass)
                            },
                            enabled = !link.busy,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Conectar") }
                        OutlinedButton(
                            onClick = onConnectManual,
                            enabled = !link.busy,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Ya estoy conectado a su WiFi") }
                    } else {
                        Button(
                            onClick = onDisconnect,
                            enabled = !link.busy,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Parar y desconectar") }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ToggleBtn("Luces", link.lamps, enabled, Modifier.weight(1f)) { link.toggleLamps() }
                        ToggleBtn("Humo", link.smoke, enabled, Modifier.weight(1f)) { link.toggleSmoke() }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ToggleBtn("Armadura", link.armor, enabled, Modifier.weight(1f)) { link.toggleArmor() }
                        ToggleBtn("Cañón", link.gunUp, enabled, Modifier.weight(1f)) { link.toggleGun() }
                    }
                    Button(
                        onClick = { link.fire() },
                        enabled = enabled,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("FUEGO") }

                    Text("Sonidos", fontSize = 12.sp)
                    for (row in 0..1) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            for (col in 0..3) {
                                val n = row * 4 + col + 1
                                OutlinedButton(
                                    onClick = { link.sound(n) },
                                    enabled = enabled,
                                    contentPadding = PaddingValues(0.dp),
                                    modifier = Modifier.weight(1f),
                                ) { Text("$n") }
                            }
                        }
                    }

                    Text(
                        "Mando: RT acelera, LT marcha atrás/freno, stick izq. gira, A luces, B humo, X armadura, " +
                            "Y cañón, R1 fuego, cruceta sonidos 1-4, L3/R3 sonidos 5-6.",
                        fontSize = 10.sp,
                    )
                    if (link.lastPacket.isNotEmpty()) Text(link.lastPacket, fontSize = 10.sp)
                }

                // Dirección táctil (horizontal).
                AxisPad(Modifier.width(150.dp).fillMaxHeight(), vertical = false, label = "DIRECCIÓN") {
                    link.touchSteer = it
                }
            }
        }
    }
}

@Composable
private fun ToggleBtn(text: String, on: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    if (on) {
        Button(onClick = onClick, enabled = enabled, modifier = modifier) { Text(text) }
    } else {
        OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier) { Text(text) }
    }
}

/**
 * Joystick de un solo eje con retorno al centro. Vertical: arriba = +1. Horizontal: derecha = +1.
 */
@Composable
fun AxisPad(modifier: Modifier, vertical: Boolean, label: String, onValue: (Float) -> Unit) {
    var value by remember { mutableFloatStateOf(0f) }
    val accent = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceVariant

    Box(
        modifier
            .background(track.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .pointerInput(vertical) {
                val w = size.width.toFloat()
                val h = size.height.toFloat()
                fun toValue(p: Offset): Float {
                    val v = if (vertical) (h / 2f - p.y) / (h / 2f) else (p.x - w / 2f) / (w / 2f)
                    return v.coerceIn(-1f, 1f)
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    value = toValue(down.position)
                    onValue(value)
                    drag(down.id) { change ->
                        value = toValue(change.position)
                        onValue(value)
                        change.consume()
                    }
                    value = 0f
                    onValue(0f)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            if (vertical) {
                drawLine(accent.copy(alpha = 0.4f), Offset(cx, 24f), Offset(cx, size.height - 24f), strokeWidth = 6f)
            } else {
                drawLine(accent.copy(alpha = 0.4f), Offset(24f, cy), Offset(size.width - 24f, cy), strokeWidth = 6f)
            }
            val knob = if (vertical) {
                Offset(cx, cy - value * (size.height / 2f - 32f))
            } else {
                Offset(cx + value * (size.width / 2f - 32f), cy)
            }
            drawCircle(accent, radius = 28f, center = knob)
        }
        Text(label, fontSize = 10.sp)
    }
}
