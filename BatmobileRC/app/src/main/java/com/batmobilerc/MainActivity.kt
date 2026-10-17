package com.batmobilerc

import android.Manifest
import android.content.Context
import android.net.Network
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sign

class MainActivity : ComponentActivity() {

    private val link = BatmobileLink()
    private lateinit var wifi: WifiConnector

    // Estado del mando
    private var rtAxis = 0f
    private var ltAxis = 0f
    private var rtKey = false
    private var ltKey = false
    private var hatX = 0f
    private var hatY = 0f
    private var lastSoundAt = 0L

    private var pendingConnect: (() -> Unit)? = null
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            // Se intenta conectar aunque se deniegue: el modo manual no necesita permisos de ubicación.
            pendingConnect?.invoke()
            pendingConnect = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wifi = WifiConnector(applicationContext)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val prefs = getSharedPreferences("batmobile", Context.MODE_PRIVATE)
        setContent {
            BatmobileApp(
                link = link,
                prefs = prefs,
                onConnectAuto = { ssid, pass -> connect(false, ssid, pass) },
                onConnectManual = { connect(true, "", "") },
                onDisconnect = { disconnect() },
            )
        }
    }

    // ------------------------------------------------------------------ conexión

    private fun connect(manual: Boolean, ssid: String, pass: String) {
        val go = {
            link.setStatus(if (manual) "Buscando la WiFi del Batmobile…" else "Conectando con el Batmobile…")
            wifi.connect(manual, ssid, pass, object : WifiConnector.Listener {
                override fun onReady(network: Network) = link.start(network)
                override fun onLost() = link.abort("Se perdió la conexión con el Batmobile")
                override fun onFailed(message: String) = link.setStatus(message)
            })
        }
        val perms = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) perms.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        pendingConnect = go
        permissionLauncher.launch(perms.toTypedArray())
    }

    private fun disconnect() {
        link.shutdown { wifi.release() }
    }

    override fun onPause() {
        super.onPause()
        link.releaseInputs() // coche parado si la app pasa a segundo plano
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) link.releaseInputs()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) link.shutdown { wifi.release() }
    }

    // ------------------------------------------------------------------ mando

    private fun deadzone(v: Float, dz: Float = 0.12f): Float =
        if (abs(v) < dz) 0f else (v - sign(v) * dz) / (1f - dz)

    private fun updateTriggers() {
        val rt = max(rtAxis, if (rtKey) 1f else 0f)
        val lt = max(ltAxis, if (ltKey) 1f else 0f)
        link.padThrottle = deadzone(rt - lt, 0.05f)
    }

    private fun playSound(index: Int) {
        val now = SystemClock.uptimeMillis()
        if (now - lastSoundAt < 250) return // evita doble disparo si el mando manda cruceta como tecla y como eje
        lastSoundAt = now
        link.sound(index)
    }

    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
        val fromPad = (ev.source and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK ||
            (ev.source and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
        if (fromPad && ev.action == MotionEvent.ACTION_MOVE) {
            rtAxis = max(ev.getAxisValue(MotionEvent.AXIS_RTRIGGER), ev.getAxisValue(MotionEvent.AXIS_GAS))
            ltAxis = max(ev.getAxisValue(MotionEvent.AXIS_LTRIGGER), ev.getAxisValue(MotionEvent.AXIS_BRAKE))
            updateTriggers()

            val lx = ev.getAxisValue(MotionEvent.AXIS_X)
            val rx = ev.getAxisValue(MotionEvent.AXIS_Z)
            link.padSteer = deadzone(if (abs(lx) >= abs(rx)) lx else rx)

            // Cruceta como eje (HAT): sonidos 1-4 al pulsar.
            val hx = ev.getAxisValue(MotionEvent.AXIS_HAT_X)
            val hy = ev.getAxisValue(MotionEvent.AXIS_HAT_Y)
            if (hy < -0.5f && hatY >= -0.5f) playSound(1)
            if (hx > 0.5f && hatX <= 0.5f) playSound(2)
            if (hy > 0.5f && hatY <= 0.5f) playSound(3)
            if (hx < -0.5f && hatX >= -0.5f) playSound(4)
            hatX = hx
            hatY = hy
            return true
        }
        return super.dispatchGenericMotionEvent(ev)
    }

    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        val down = e.action == KeyEvent.ACTION_DOWN
        val fresh = down && e.repeatCount == 0
        when (e.keyCode) {
            // Gatillos que algunas consolas mandan como botones digitales.
            KeyEvent.KEYCODE_BUTTON_R2 -> { rtKey = down; updateTriggers(); return true }
            KeyEvent.KEYCODE_BUTTON_L2 -> { ltKey = down; updateTriggers(); return true }

            KeyEvent.KEYCODE_BUTTON_A -> { if (fresh) link.toggleLamps(); return true }
            KeyEvent.KEYCODE_BUTTON_B -> { if (fresh) link.toggleSmoke(); return true }
            KeyEvent.KEYCODE_BUTTON_X -> { if (fresh) link.toggleArmor(); return true }
            KeyEvent.KEYCODE_BUTTON_Y -> { if (fresh) link.toggleGun(); return true }
            KeyEvent.KEYCODE_BUTTON_R1 -> { if (fresh) link.fire(); return true }

            KeyEvent.KEYCODE_DPAD_UP -> { if (fresh) playSound(1); return true }
            KeyEvent.KEYCODE_DPAD_RIGHT -> { if (fresh) playSound(2); return true }
            KeyEvent.KEYCODE_DPAD_DOWN -> { if (fresh) playSound(3); return true }
            KeyEvent.KEYCODE_DPAD_LEFT -> { if (fresh) playSound(4); return true }
            KeyEvent.KEYCODE_BUTTON_THUMBL -> { if (fresh) playSound(5); return true }
            KeyEvent.KEYCODE_BUTTON_THUMBR -> { if (fresh) playSound(6); return true }
        }
        return super.dispatchKeyEvent(e)
    }
}
