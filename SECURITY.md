# Política de seguridad

## Reportar una vulnerabilidad

Si encontrás un problema de seguridad (por ejemplo, alguna forma en
que el enlace/webhook guardado pudiera filtrarse, o que la app
ejecute algo que no debería), por favor **no abras un issue público**.

En su lugar, usá la pestaña **"Security" → "Report a vulnerability"**
de este repositorio en GitHub (Security Advisories privados), o
contactá directamente a quien mantiene el proyecto a través de su
perfil de GitHub.

Incluí, si es posible:

- Descripción del problema y su impacto.
- Pasos para reproducirlo.
- Versión de la app / commit afectado.

## Alcance

Este proyecto es una app personal de código abierto sin backend
propio: el único dato sensible que maneja es el enlace/webhook que
vos mismo configurás, guardado localmente en `SharedPreferences` del
dispositivo (no se sincroniza a ningún servidor externo del proyecto).
Tené en cuenta esto al evaluar la severidad de un hallazgo.
