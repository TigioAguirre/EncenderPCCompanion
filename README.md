# EncenderPC companion

App de Android que escucha el botón de reproducción de música de una
Mi Band (u otra pulsera/reloj con control de medios Bluetooth) y, al
presionarlo, llama a un webhook (VirtualSmartHome, Home Assistant,
IFTTT, etc.) para encender tu PC de forma remota.

> Estado: proyecto personal / hobby, en desarrollo activo. Las
> contribuciones y reportes de errores son bienvenidos — ver
> [CONTRIBUTING.md](CONTRIBUTING.md).

## ¿Cómo funciona?

1. La app registra una sesión de medios (`MediaSession`) "fantasma": un
   audio silencioso que le hace creer al sistema (y a tu pulsera) que
   hay música sonando, para que el botón de play/pause de la pulsera
   tenga un destino al que enviar la pulsación.
2. Esa sesión **solo se activa si el interruptor principal de la app
   está encendido** y si ninguna otra app de música real está sonando
   en ese momento (para no interferir con Spotify, YouTube Music, etc.).
3. Al presionar play/pause en la pulsera, la app llama al enlace
   (webhook) que configuraste, y muestra una notificación + un breve
   destello de color en pantalla como confirmación visual.

## Capturas

_(agregá acá capturas de la pantalla principal y del tutorial una vez
que tengas el APK corriendo)_

## Requisitos

- Android Studio Ladybug o más reciente.
- JDK 17.
- Un dispositivo o emulador con **Android 8.0 (API 26) o superior**.
- Un endpoint HTTP que reaccione al botón (por ejemplo un webhook de
  [Home Assistant](https://www.home-assistant.io/), un applet de
  [IFTTT](https://ifttt.com/) o [VirtualSmartHome](https://virtualsmarthome.xyz/)).

## Compilar y correr

```bash
git clone https://github.com/<tu-usuario>/EncenderPCCompanion.git
cd EncenderPCCompanion
./gradlew assembleDebug
```

El APK queda en `app/build/outputs/apk/debug/`. También podés abrir la
carpeta directamente en Android Studio y correr la configuración
`app` sobre un dispositivo conectado.

## Primeros pasos dentro de la app

La primera vez que se abre la app aparece un **tutorial paso a paso**
que guía por los 4 permisos que Android exige para que todo funcione
(acceso a notificaciones, superposición de pantalla, exención de
batería y permiso de notificaciones). Podés volver a verlo en
cualquier momento desde el botón de ayuda (`?`) o "Ver el tutorial de
nuevo" en la pantalla principal.

## Estructura del proyecto

El código Kotlin está organizado por responsabilidad para que sea
fácil ubicar dónde va cada cosa (y agregar features nuevas sin que
todo quede en un único paquete):

```
app/src/main/java/com/example/bandcolorreact/
├── ui/                     Activities y lógica de pantalla
│   ├── MainActivity.kt         Pantalla principal (estado + config)
│   └── TutorialActivity.kt     Tutorial guiado ventana a ventana
├── service/                Todo lo que corre en segundo plano
│   ├── MusicButtonListenerService.kt   Escucha notificaciones/medios,
│   │                                    dispara el webhook
│   └── BandMediaButtonReceiver.kt      Recibe el evento físico de
│                                        play/pause de la pulsera
├── overlay/                 Feedback visual
│   └── ColorOverlay.kt          Destello de color en pantalla
└── data/                    Persistencia simple
    └── AppPrefs.kt               SharedPreferences (switch, URL, etc.)

app/src/main/res/
├── layout/        Pantallas y filas reutilizables (item_status_row.xml)
├── drawable/       Íconos vectoriales y fondos (shapes)
├── font/           Tipografía Manrope (ver NOTICE)
├── values/         colors.xml, styles.xml, strings.xml, dimens.xml
└── raw/            Audio silencioso usado por la sesión de medios
```

> Nota: Android **no permite subcarpetas dentro de `res/layout`,
> `res/drawable`, etc.** — todos los recursos de un mismo tipo deben
> vivir en la misma carpeta plana, por eso la organización por
> carpetas solo aplica al código Kotlin (`java/...`). Para los
> recursos, la convención es usar prefijos en el nombre del archivo
> (por ejemplo `item_status_row.xml`, `chip_ok.xml`).

### Roadmap: detección de si la PC está encendida

Está planeada una función para saber, desde la app, si la PC ya se
encendió (por ejemplo haciendo ping al mismo host del webhook, o
escuchando un segundo endpoint). Cuando se implemente, el código de
esa lógica va a vivir en un paquete nuevo, por ejemplo
`com.example.bandcolorreact.pcstate` (cliente de red + lo que sea
necesario para el estado), manteniendo `ui/` únicamente con la
presentación. Si querés ayudar con esto, hay un issue abierto — ver
[Issues](../../issues).

## Permisos que pide la app y por qué

| Permiso | Para qué se usa |
|---|---|
| Acceso a notificaciones (`NotificationListenerService`) | Saber si otra app de música está sonando, para no interferir |
| Superponerse a otras apps (`SYSTEM_ALERT_WINDOW`) | Mostrar el destello de color de confirmación |
| Exención de optimización de batería | Que Android no mate el servicio en segundo plano |
| Notificaciones (`POST_NOTIFICATIONS`, Android 13+) | Mostrar el aviso de "PC encendida" |
| Internet | Llamar al webhook configurado |

La app **no** recolecta ni envía datos a ningún servidor propio: el
único tráfico de red que genera es la llamada HTTP al enlace que vos
configuraste.

## Licencia

Este proyecto está bajo la licencia **Apache 2.0** — ver
[LICENSE](LICENSE). Incluye la tipografía Manrope bajo SIL Open Font
License; ver [NOTICE](NOTICE) para el detalle de atribuciones.

## Contribuir

¿Encontraste un bug o querés proponer una mejora? Leé
[CONTRIBUTING.md](CONTRIBUTING.md) y el
[código de conducta](CODE_OF_CONDUCT.md) antes de abrir un issue o un
pull request.
