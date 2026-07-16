# EncenderPC Companion

![License](https://img.shields.io/badge/license-Apache--2.0-blue)
![Estado](https://img.shields.io/badge/estado-en%20desarrollo%20activo-yellow)

Sistema para encender y monitorear tu PC de forma remota desde una Mi Band (u otra pulsera/reloj con control de medios Bluetooth), con estado en tiempo real desde el celular.

El repo agrupa **tres proyectos independientes** que trabajan juntos:

| Carpeta | Qué es | Stack |
|---|---|---|
| [`EncenderPCCompanion/`](EncenderPCCompanion) | App de Android: escucha el botón de la pulsera y muestra el estado de tus PCs | Kotlin |
| [`EncenderPCAgent/`](EncenderPCAgent) | Servicio de Windows que reporta que la PC está prendida | C# / .NET 8 |
| [`EncenderPC-BackEnd/`](EncenderPC-BackEnd) | Backend serverless: cuentas, emparejamiento y notificaciones push | Firebase (Cloud Functions + Firestore) |
| [`Executables/`](Executables) | Builds ya compilados listos para instalar (APKs y el `.exe` del agente) | — |

> **Estado:** proyecto personal / hobby, en desarrollo activo. Las contribuciones y reportes de errores son bienvenidos — ver [CONTRIBUTING.md](CONTRIBUTING.md).

---

## Tabla de contenidos

- [¿Cómo funciona el sistema completo?](#cómo-funciona-el-sistema-completo)
- [Qué hay en cada carpeta](#qué-hay-en-cada-carpeta)
- [Instalación rápida (sin compilar nada)](#instalación-rápida-sin-compilar-nada)
- [Levantar el proyecto para desarrollo](#levantar-el-proyecto-para-desarrollo)
- [Backend en detalle (Firebase)](#backend-en-detalle-firebase)
- [Roadmap](#roadmap)
- [Licencia](#licencia)
- [Contribuir](#contribuir)

---

## ¿Cómo funciona el sistema completo?

1. **Emparejamiento.** Desde la app Android creás una cuenta y agregás una PC; el backend genera un código de 6 dígitos válido por 10 minutos. Lo tipeás una vez en el agente de Windows, que lo cambia por credenciales limitadas a esa PC puntual.
2. **Heartbeat.** Mientras Windows está prendido, el agente manda un "estoy vivo" a Firestore cada ~30 segundos.
3. **Detección de apagado.** Una Cloud Function corre cada minuto y marca offline cualquier PC sin heartbeat reciente (cubre apagados normales, cuelgues y cortes de luz).
4. **Encendido remoto.** Desde la pulsera, el botón de play/pause de una sesión de medios "fantasma" que crea la app dispara un webhook (Home Assistant, IFTTT, VirtualSmartHome, etc.) que enciende la PC.
5. **Aviso en el celular.** Apenas la PC vuelve a estar online, el backend manda una notificación push a la app.

*(El detalle técnico completo de los pasos 1, 2, 3 y 5 — colecciones de Firestore, Cloud Functions involucradas, tokens, etc. — está en [Backend en detalle](#backend-en-detalle-firebase).)*

## Qué hay en cada carpeta

### `EncenderPCCompanion/` — App Android
La app que se instala en el celular. Escucha el botón de la pulsera, dispara el webhook de encendido y (con Firebase ya integrado) muestra el estado en vivo de tus PCs. Tiene su propio [README](EncenderPCCompanion/README.md) con la estructura interna del código Kotlin, permisos que pide y cómo compilarla.

### `EncenderPCAgent/` — Agente de Windows
Servicio que corre en la PC a monitorear. Se instala con un doble click (pide permiso de Administrador una sola vez), se empareja con el código de 6 dígitos y desde ahí reporta su estado solo, incluso después de reiniciar Windows. Intercambia el `customToken` de emparejamiento por credenciales reales via `POST https://identitytoolkit.googleapis.com/v1/accounts:signInWithCustomToken`, y después hace `PATCH` a la REST API de Firestore (`https://firestore.googleapis.com/v1/projects/{project}/databases/(default)/documents/devices/{deviceId}`) cada 30s con el `idToken` como `Authorization: Bearer`. Ver su [README](EncenderPCAgent/README.md) para instrucciones de instalación end-user y de compilación.

### `EncenderPC-BackEnd/` — Backend en Firebase
Todo lo serverless: autenticación de cuentas, registro de PCs en Firestore, generación y validación de los códigos de emparejamiento, la función programada que detecta PCs offline, y el envío de notificaciones push. Ver la sección [Backend en detalle](#backend-en-detalle-firebase) más abajo para el detalle de cada Cloud Function y cómo levantar el emulador local.

### `Executables/`
Binarios ya compilados para quien no quiera o no pueda compilar desde el código fuente: los `.apk` de la app Android y el `.exe` empaquetado del agente de Windows.

### Archivos en la raíz
`LICENSE` (Apache 2.0), `NOTICE` (atribuciones de fuentes/tipografías), `CODE_OF_CONDUCT.md`, `CONTRIBUTING.md` y `SECURITY.md` aplican a los tres proyectos por igual.

## Instalación rápida (sin compilar nada)

1. Descargá el APK más reciente de [`Executables/apk's/`](Executables) e instalalo en tu Android.
2. Descargá y descomprimí `EncenderPCAgent Executable.zip` de la misma carpeta en la PC que querés monitorear, y abrí `EncenderPCAgent.exe`.
3. Seguí el tutorial dentro de la app para emparejar la PC con el código de 6 dígitos que te va a mostrar.

## Levantar el proyecto para desarrollo

Cada componente se compila por separado — mirá el README de cada carpeta (y la sección de backend más abajo) para el detalle completo:

```bash
# App Android
cd EncenderPCCompanion && ./gradlew assembleDebug

# Backend (requiere Firebase CLI y una cuenta Blaze)
cd EncenderPC-BackEnd/functions && npm install && npm run serve

# Agente de Windows (requiere .NET 8 SDK)
cd EncenderPCAgent && .\build.ps1
```

---

## Backend en detalle (Firebase)

Backend serverless en Firebase para la parte de "estado de la PC" y notificaciones push del proyecto EncenderPCCompanion. Vive en [`EncenderPC-BackEnd/`](EncenderPC-BackEnd).

### Qué hace

- Cuentas de usuario → **Firebase Auth**.
- Registro de PCs y su estado (online/offline) → **Firestore**.
- Emparejar la PC con la cuenta de forma segura, sin exponer credenciales de admin en el agente de Windows → **Cloud Functions**.
- Detectar que una PC se apagó (o se colgó) sin que avise → **Cloud Function programada (cron)**.
- Avisar al celular apenas la PC se prende → **FCM (push)**.

### Flujo completo

1. Usuario crea cuenta en la app (Firebase Auth).
2. Usuario toca "Agregar PC" → la app llama a `createDevice` → se crea `devices/{deviceId}` (status: offline) y se muestra un **código de 6 dígitos** válido por 10 minutos.
3. Usuario abre el agente de Windows, tipea el código → el agente llama a `pairDevice` → recibe un **custom token** que solo le da permiso de escribir `status`/`lastSeen` de esa PC puntual.
4. El agente manda un heartbeat (`status: "online"`, `lastSeen: now`) cada ~30s mientras Windows está prendido.
5. `checkOfflineDevices` (corre cada 1 min) pasa a `offline` cualquier PC cuyo `lastSeen` tenga más de 90s — cubre apagados, crashes o cortes de red.
6. `onDeviceStatusChange` detecta el flanco `offline → online` y le manda una push (FCM) al dueño: *"Tu PC se encendió"*.
7. La app Android muestra el estado en tiempo real con un listener de Firestore sobre `devices` (no hace falta polling manual).

### Requisitos

- Node.js 20+
- Cuenta de Firebase (plan **Blaze** — pay-as-you-go. Es obligatorio para usar Cloud Functions, pero tiene cuota gratis mensual grande; a la escala de este proyecto no debería generar costo real).
- Firebase CLI: `npm install -g firebase-tools`

### Setup

```bash
firebase login
firebase use --add          # elegí o creá tu proyecto de Firebase
cd functions
npm install
cd ..
```

### Emulador local (para probar sin gastar cuota real)

```bash
cd functions
npm run serve
```

Esto levanta el emulador de Functions + Firestore. Podés probar `createDevice` y `pairDevice` desde el emulator UI o con `curl`/Postman usando el SDK de Firebase Auth para conseguir un ID token de prueba.

### Deploy a producción

```bash
firebase deploy --only firestore:rules,firestore:indexes,functions
```

### Estructura

```
firebase.json              Config del proyecto (functions + reglas)
firestore.rules            Reglas de seguridad (quién puede leer/escribir qué)
firestore.indexes.json     Índice compuesto para la query de "PCs sin heartbeat"
functions/
  src/
    admin.ts                Init de firebase-admin + constantes (umbral offline)
    pairing.ts               createDevice, pairDevice
    presence.ts               checkOfflineDevices, onDeviceStatusChange
    users.ts                   onUserCreate, registerFcmToken
    index.ts                    Exporta todo
```

### Próximos pasos (fuera de este backend)

- **Agente de Windows** (.NET 8 Worker Service): intercambia el `customToken` por credenciales reales via `POST https://identitytoolkit.googleapis.com/v1/accounts:signInWithCustomToken`, y después hace `PATCH` a la REST API de Firestore (`https://firestore.googleapis.com/v1/projects/{project}/databases/(default)/documents/devices/{deviceId}`) cada 30s con el `idToken` como `Authorization: Bearer`.
- **App Android**: agregar Firebase Auth, el SDK de Firestore (listener en `devices` filtrado por `ownerUid`), el SDK de FCM, y las pantallas de login / "Mis PCs" / "Agregar PC".

---

## Roadmap

- [ ] Publicar releases firmadas con changelog en GitHub (hoy los builds viven solo en `Executables/`).
- [ ] Integración continua: build automático de los tres componentes en cada PR.
- [ ] Tests unitarios para la lógica de emparejamiento y heartbeat.
- [ ] Capturas de pantalla y un GIF del flujo completo en este README.

¿Querés ayudar con alguno de estos puntos? Hay issues abiertos — ver [Issues](https://github.com/TigioAguirre/EncenderPCCompanion/issues).

## Licencia

Este proyecto está bajo la licencia **Apache 2.0** — ver [LICENSE](LICENSE). Incluye la tipografía Manrope bajo SIL Open Font License; ver [NOTICE](NOTICE) para el detalle de atribuciones.

## Contribuir

¿Encontraste un bug o querés proponer una mejora? Leé [CONTRIBUTING.md](CONTRIBUTING.md) y el [código de conducta](CODE_OF_CONDUCT.md) antes de abrir un issue o un pull request.
