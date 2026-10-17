# Batmobile RC

App Android nativa para el **Mattel Justice League Ultimate Batmobile R/C (FKM40)**, pensada para consolas
como la **Anbernic RG556**: gatillos para acelerar y frenar, stick para girar, botones para los efectos y
joysticks táctiles en pantalla como la app original (retirada de Google Play en marzo de 2024).

El protocolo (UDP de texto a 192.168.201.1:1051, 10 Hz) viene de la ingeniería inversa publicada en
<https://github.com/salient-concepts/batmobile-fkm40-controller> (licencia MIT). No funcionan la cámara ni la telemetría.

## Controles

| Mando | Acción |
|---|---|
| RT | Acelerar (proporcional si el gatillo es analógico) |
| LT | Marcha atrás / freno |
| Stick izquierdo (eje X) | Dirección (el stick derecho también vale) |
| A | Luces |
| B | Humo |
| X | Armadura (abrir/cerrar) |
| Y | Cañón (subir/bajar) |
| R1 | Disparar |
| Cruceta | Sonidos 1 a 4 |
| L3 / R3 | Sonidos 5 y 6 |

En pantalla: barra vertical a la izquierda (gas), barra horizontal a la derecha (dirección), botones de efectos y
sonidos 1-8. Si tu consola tiene A/B/X/Y intercambiados, cambia las constantes en `MainActivity.dispatchKeyEvent`.

## Compilar (Android Studio)

1. Instala Android Studio y abre la carpeta `BatmobileRC`. Deja que sincronice Gradle (si te ofrece actualizar
   el plugin de Android o Gradle, acepta).
2. Menú **Build > Build Bundle(s) / APK(s) > Build APK(s)**. El APK queda en `app/build/outputs/apk/debug/`.
3. Cópialo a la RG556 e instálalo (activa "instalar apps de orígenes desconocidos") o usa `adb install`.

Por línea de comandos: `./gradlew assembleDebug` (necesita el SDK de Android instalado y `ANDROID_HOME`).

## Uso

1. Deja el coche con su **clave WiFi por defecto `BATMOBILE1`**. Si la cambiaste, reset de fábrica con el
   agujero de la parte inferior izquierda de la batería (mantener 10 s), o escribe tu clave en la app.
2. Enciende el coche. Su WiFi se llama `XXXX-BATMOBILE`. Solo puede haber un dispositivo conectado a la vez.
3. En la app, pulsa **Conectar** (Android pedirá confirmación) o, si ya te conectaste desde Ajustes,
   **Ya estoy conectado a su WiFi**. La app ata el tráfico a esa red aunque no tenga internet.
4. Cuando ponga "Armado", conduce.
5. Termina siempre con **Parar y desconectar**: manda unos 7 segundos de paquetes de parada antes de soltar la
   WiFi. Si cortas la WiFi sin parar, los motores pueden quedarse en marcha.

## Seguridad

- Si la app pierde el foco o pasa a segundo plano, acelerador y dirección vuelven a cero.
- Si se pierde la WiFi, se cierra el enlace.
- Haz las primeras pruebas con las ruedas en el aire o en un espacio libre.

## Estructura

- `Protocol.kt`: formato de paquetes (sin dependencias de Android, probado con `tools/ProtocolCheck.kt`).
- `BatmobileLink.kt`: bucle de envío a 10 Hz, efectos y apagado seguro.
- `WifiConnector.kt`: conexión a la WiFi del coche y detección de la red correcta.
- `MainActivity.kt`: lectura del mando (ejes, gatillos, botones).
- `Ui.kt`: pantalla y joysticks táctiles.

## Pendiente / ideas

- Etiquetar los 256 sonidos (hay que probarlos uno a uno).
- Decodificar la telemetría de batería.
- La cámara RTSP no emite imagen con el firmware actual.
